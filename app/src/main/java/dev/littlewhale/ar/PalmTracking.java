package dev.littlewhale.ar;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.components.containers.Landmark;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Single inference in flight. All display state and expiry timers belong to the UI thread. */
final class PalmTracking {
    private final Context context;
    private final CharacterView view;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Paint filter=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect dest=new Rect();
    private Bitmap input;
    private Canvas canvas;
    private MPImage image;
    private HandLandmarker detector;
    private volatile boolean closed,failed;
    private boolean busy;
    private long lastSubmit;
    private boolean missing;
    private final PalmLock lock=new PalmLock();
    private final Runnable expire=this::expire;
    private void expire(){lock.clear();view.clearPose(false);}
    private boolean initialized;

    PalmTracking(Context context,CharacterView view){this.context=context.getApplicationContext();this.view=view;}

    synchronized void offer(Bitmap preview){
        long now=SystemClock.elapsedRealtime();
        if(closed || failed || busy || now-lastSubmit<65)return;
        float factor=Math.min(1f,512f/Math.max(preview.getWidth(),preview.getHeight()));
        int w=Math.max(1,Math.round(preview.getWidth()*factor)),h=Math.max(1,Math.round(preview.getHeight()*factor));
        if(input==null || input.getWidth()!=w || input.getHeight()!=h){
            if(image!=null)image.close();
            input=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);canvas=new Canvas(input);dest.set(0,0,w,h);
            image=new BitmapImageBuilder(input).build();
        }
        canvas.drawBitmap(preview,null,dest,filter);
        busy=true;lastSubmit=now;
        worker.execute(()->detect(now));
    }

    private void detect(long time){
        try{
            if(detector==null){
                BaseOptions base=BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build();
                detector=HandLandmarker.createFromOptions(context,HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(base).setRunningMode(RunningMode.VIDEO).setNumHands(2)
                    .setMinHandDetectionConfidence(.55f).setMinTrackingConfidence(.55f).build());
            }
            HandLandmarkerResult result=detector.detectForVideo(image,time);
            PalmLock.Hand[] hands=new PalmLock.Hand[result.landmarks().size()];
            for(int i=0;i<hands.length;i++){
                List<NormalizedLandmark> points=result.landmarks().get(i);
                List<Landmark> world=result.worldLandmarks().get(i);
                float[][] screen=new float[21][2], xyz=new float[21][3];
                for(int k=0;k<21;k++){
                    NormalizedLandmark n=points.get(k);Landmark p=world.get(k);
                    screen[k][0]=n.x();screen[k][1]=n.y();
                    xyz[k][0]=p.x();xyz[k][1]=p.y();xyz[k][2]=p.z();
                }
                String side=result.handedness().get(i).get(0).categoryName();
                hands[i]=PalmLock.measure(screen,xyz,side,input.getHeight()/(float)input.getWidth());
            }
            ui.post(()->sample(hands,time));
        }catch(RuntimeException|LinkageError e){
            failed=true;Log.e("WhalePalm","hand detector failed",e);
            ui.post(()->{if(!closed){ui.removeCallbacks(expire);expire.run();}});
        }finally{synchronized(this){busy=false;}}
    }
    private void sample(PalmLock.Hand[] hands,long time){
        if(closed || failed)return;
        initialized=true;
        long age=SystemClock.elapsedRealtime()-time;
        if(age>500)return;
        // Inference time is already spent when an observation reaches the display.
        // Allow a full hold interval for the *next* observation on a busy phone.
        if(!lock.accept(hands,time)){missing=true;return;}
        missing=false;
        view.palmPose(lock.x,lock.y,lock.width);
        ui.removeCallbacks(expire);ui.postDelayed(expire,PalmLock.HOLD_MS);
    }
    String status(){
        if(failed)return "手掌识别未能启动，请重试";
        if(!initialized)return "正在启动手掌识别…";
        if(!lock.visible)return "摊开一只手，让镜头看清掌心";
        return missing?"正在找回掌心":"已托住小鲸鱼";
    }
    boolean failed(){return failed;}
    synchronized void close(){
        closed=true;ui.removeCallbacks(expire);
        worker.execute(()->{if(detector!=null)detector.close();if(image!=null)image.close();});
        worker.shutdown();
    }
}
