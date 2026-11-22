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
    private long lastSubmit,lastGood,missingSince,inferenceGrace;
    private volatile long lastPreview;
    private final PalmLock lock=new PalmLock();
    private final Runnable expire=this::expire;
    private void expire(){
        if(!lock.visible)return;
        long deadline=Math.min(lastPreview+PalmLock.HOLD_MS,lastGood+inferenceGrace);
        if(missingSince>0)deadline=Math.min(deadline,missingSince+PalmLock.HOLD_MS);
        long remaining=deadline-SystemClock.elapsedRealtime();
        if(remaining>0){ui.postDelayed(expire,remaining);return;}
        lock.clear();missingSince=0;view.clearPose(false);
    }
    private boolean initialized;

    PalmTracking(Context context,CharacterView view){this.context=context.getApplicationContext();this.view=view;}

    synchronized void offer(Bitmap preview){
        long now=SystemClock.elapsedRealtime();
        if(closed || failed)return;
        lastPreview=now;
        if(busy || now-lastSubmit<65)return;
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
            long completed=SystemClock.elapsedRealtime();
            ui.post(()->sample(hands,time,completed));
        }catch(RuntimeException|LinkageError e){
            failed=true;Log.e("WhalePalm","hand detector failed",e);
            ui.post(()->{if(!closed){ui.removeCallbacks(expire);lock.clear();view.clearPose(false);}});
        }finally{synchronized(this){busy=false;}}
    }
    private void sample(PalmLock.Hand[] hands,long time,long completed){
        if(closed || failed)return;
        initialized=true;
        long now=SystemClock.elapsedRealtime();
        if(now-completed>250 || now-lastPreview>=PalmLock.HOLD_MS || now-time>1000)return;
        boolean found=lock.accept(hands,time,view.palmSide);
        if(found){
            missingSince=0;lastGood=now;
            // The live preview may deliver many frames while one inference is still running.
            inferenceGrace=Math.max(580,Math.min(1400,3*(completed-time)+160));
            view.palmSurface(lock.outline,lock.width,lock.side);
        }else if(lock.visible && missingSince==0)missingSince=time;
        ui.removeCallbacks(expire);
        if(lock.visible)expire();
    }
    String status(){
        if(failed)return "手掌识别未能启动，请重试";
        if(!initialized)return "正在启动手掌识别…";
        if(!lock.visible)return "摊开一只手，让镜头看清掌心";
        if(missingSince>0)return "正在找回掌心";
        return view.palmSide==null?"点掌面轮廓内，放置小鲸鱼":"已托住小鲸鱼";
    }
    boolean failed(){return failed;}
    synchronized void close(){
        closed=true;ui.removeCallbacks(expire);
        worker.execute(()->{if(detector!=null)detector.close();if(image!=null)image.close();});
        worker.shutdown();
    }
}
