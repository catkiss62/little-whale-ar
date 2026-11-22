package dev.littlewhale.ar;

import android.graphics.*;
import android.os.Bundle;
import android.util.Size;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Same-frame visual reference test. The central marked patch is the only registered surface. */
public final class ImageLabActivity extends ComponentActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean capture=new AtomicBoolean(),awaitDisplay=new AtomicBoolean();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private ImageView preview; private TextView status; private Button record;
    private ImageAnalysis analysis;private long target;private volatile boolean closed;
    private static native long registerImage(Bitmap bitmap);
    private static native float[] locate(long handle,Bitmap bitmap);
    private static native void releaseImage(long handle);
    private File saved(){return new File(getFilesDir(),"image-test-reference.png");}
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        page.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        status=new TextView(this);status.setText("将有纹理的平面正对镜头，填满白框");page.addView(status);
        record=new Button(this);record.setText("记录白框内的参考图");record.setEnabled(false);record.setOnClickListener(v->capture.set(true));page.addView(record);
        Button back=new Button(this);back.setText("返回现有引擎");back.setOnClickListener(v->finish());page.addView(back);setContentView(page);
        try{System.loadLibrary("MenthaAR_Engine");}catch(UnsatisfiedLinkError e){status.setText("当前设备无法加载参考图识别库");return;}
        worker.execute(()->{Bitmap old=BitmapFactory.decodeFile(saved().toString());if(old!=null){target=registerImage(old);old.recycle();}});
        com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{
            if(closed)return;
            try{
                analysis=new ImageAnalysis.Builder().setTargetResolution(new Size(640,480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                analysis.setAnalyzer(worker,this::frame);
                future.get().bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,analysis);record.setEnabled(true);
            }catch(Exception e){status.setText("参考图相机未启动 · "+e.getClass().getSimpleName());}
        },getMainExecutor());
    }
    private void frame(ImageProxy input){
        if(closed||!awaitDisplay.compareAndSet(false,true)){input.close();return;}
        try{
            Bitmap raw=input.toBitmap();android.graphics.Matrix rotate=new android.graphics.Matrix();
            rotate.postRotate(input.getImageInfo().getRotationDegrees());
            Bitmap turned=Bitmap.createBitmap(raw,0,0,raw.getWidth(),raw.getHeight(),rotate,true);
            if(raw!=turned)raw.recycle();
            Bitmap image=turned.copy(Bitmap.Config.ARGB_8888,true);turned.recycle();
            int w=image.getWidth(),h=image.getHeight();
            Rect box=new Rect(w/6,h/4,5*w/6,3*h/4);
            String notice=null;
            if(capture.getAndSet(false)){
                Bitmap crop=Bitmap.createBitmap(image,box.left,box.top,box.width(),box.height());
                long next=registerImage(crop);
                if(next!=0){
                    try{
                        File tmp=new File(getFilesDir(),"image-test-reference.tmp");
                        try(FileOutputStream out=new FileOutputStream(tmp)){if(!crop.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException();}
                        if(!tmp.renameTo(saved()))throw new IOException();
                        releaseImage(target);target=next;next=0;
                    }catch(IOException ex){notice="参考图保存失败，请重试";}finally{releaseImage(next);}
                }else notice="纹理太少 · 换有图案的区域再记录";
                crop.recycle();
            }
            float[] p=locate(target,image);Canvas canvas=new Canvas(image);
            paint.setStyle(Paint.Style.STROKE);paint.setColor(0x99ffffff);paint.setStrokeWidth(1.5f);canvas.drawRect(box,paint);
            if(p!=null){
                paint.setColor(0xff51ffd3);paint.setStrokeWidth(3);
                Path border=new Path();border.moveTo(p[0],p[1]);for(int i=1;i<4;i++)border.lineTo(p[2*i],p[2*i+1]);
                border.close();canvas.drawPath(border,paint);canvas.drawCircle(p[8],p[9],7,paint);
            }
            String label=notice!=null?notice:p!=null?"已认出参考图 · 绿框和圆心随图案移动":target==0?"正对书封或图片，让图案填满白框后记录":"寻找已保存的图案 · 对准原图案可重新识别";
            if(notice!=null){lastNotice=label;noticeUntil=android.os.SystemClock.elapsedRealtime()+2300;}
            final String shown=android.os.SystemClock.elapsedRealtime()<noticeUntil?lastNotice:label;
            runOnUiThread(()->{try{if(!closed){preview.setImageBitmap(image);if(!shown.contentEquals(status.getText()))status.setText(shown);}}finally{awaitDisplay.set(false);}});
        }catch(RuntimeException e){runOnUiThread(()->status.setText("参考图帧不可用 · "+e.getClass().getSimpleName()));awaitDisplay.set(false);}
        finally{input.close();}
    }
    private String lastNotice;private long noticeUntil;
    @Override protected void onDestroy(){closed=true;if(analysis!=null)analysis.clearAnalyzer();worker.execute(()->{if(target!=0)releaseImage(target);});worker.shutdown();super.onDestroy();}
}
