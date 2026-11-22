package dev.littlewhale.ar;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.InputStream;

/** Feed real camera fixtures through the production Java tracker, native graph and shipping WebView. */
public class PalmProbe extends Activity {
    final Handler handler=new Handler();
    CharacterView view;
    PalmTracking tracker;
    Bitmap open,moved,returned,back,fist;
    float firstX,lastX;
    Bitmap place(Bitmap hand,int left){
        Bitmap image=Bitmap.createBitmap(680,640,Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c=new android.graphics.Canvas(image);
        c.drawColor(android.graphics.Color.GRAY);
        c.drawBitmap(hand,null,new android.graphics.Rect(left,25,left+390,610),new android.graphics.Paint(2));
        return image;
    }
    float palmX(){
        try{
            java.lang.reflect.Field field=PalmTracking.class.getDeclaredField("lock");field.setAccessible(true);
            Object lock=field.get(tracker);
            java.lang.reflect.Field x=lock.getClass().getDeclaredField("x");x.setAccessible(true);return x.getFloat(lock);
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    int phase;
    long start;
    Bitmap load(String name)throws Exception{
        try(InputStream stream=getAssets().open(name)){return BitmapFactory.decodeStream(stream);}
    }
    public void onCreate(Bundle state){
        super.onCreate(state);
        try{
            Bitmap fixture=load("woman_hands.jpg");open=place(fixture,10);moved=place(fixture,70);returned=place(fixture,255);back=load("right_hands.jpg");fist=load("fist.jpg");
            view=new CharacterView(this);setContentView(view);
            tracker=new PalmTracking(this,view);handler.post(this::tick);
        }catch(Exception e){Log.e("WhalePalmQA","setup",e);}
    }
    void next(String check){
        Log.i("WhalePalmQA",check);
        phase++;start=SystemClock.elapsedRealtime();
    }
    void tick(){
        if(!"ready".equals(view.state)){
            if("error".equals(view.state)){Log.e("WhalePalmQA","model failed");return;}
            handler.postDelayed(this::tick,90);return;
        }
        if(start==0)start=SystemClock.elapsedRealtime();
        long elapsed=SystemClock.elapsedRealtime()-start;
        boolean visible=tracker.status().equals("已托住小鲸鱼")||tracker.status().equals("正在找回掌心");
        if(tracker.failed()||elapsed>25000){Log.e("WhalePalmQA","phase "+phase+" failed: "+tracker.status());return;}
        if(phase==0){
            tracker.offer(open);
            if(visible && view.renderFps>0){firstX=palmX();next("palm and V21 visible");}
        }else if(phase==1){
            tracker.offer(moved);
            if(visible && palmX()>firstX+.055f){lastX=palmX();next("followed moving palm");}
        }else if(phase==2){
            tracker.offer(moved);
            if(elapsed>750 && visible && Math.abs(lastX-palmX())<.045)next("stationary palm held");
        }else if(phase==3){
            tracker.offer(back);
            if(elapsed>1400 && !visible && view.renderFps==0)next("back of hand hidden");
        }else if(phase==4){
            tracker.offer(fist);
            if(elapsed>600 && visible){Log.e("WhalePalmQA","accepted closed fist");return;}
            if(elapsed>1500)next("closed fist hidden");
        }else if(phase==5){
            tracker.offer(returned);
            if(visible && view.renderFps>0 && palmX()>firstX+.26f)next("palm recovered at new position");
        }else if(elapsed>800){
            if(visible || view.renderFps!=0){Log.e("WhalePalmQA","stale preview");return;}
            Log.i("WhalePalmQA","stopped feed hidden");tracker.close();return;
        }
        handler.postDelayed(this::tick,80);
    }
}
