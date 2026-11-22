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
    Bitmap open,moved,rolled,returned,back,fist;
    double priorAngle=Double.NaN,latestAngle=Double.NaN;
    float firstX,lastX;
    Bitmap place(Bitmap hand,int left){
        Bitmap image=Bitmap.createBitmap(680,640,Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c=new android.graphics.Canvas(image);
        c.drawColor(android.graphics.Color.GRAY);
        c.drawBitmap(hand,null,new android.graphics.Rect(left,25,left+390,610),new android.graphics.Paint(2));
        return image;
    }
    Bitmap roll(Bitmap frame){
        Bitmap result=Bitmap.createBitmap(680,640,Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c=new android.graphics.Canvas(result);
        c.drawColor(android.graphics.Color.GRAY);
        c.rotate(24,265,317);
        c.drawBitmap(frame,0,0,new android.graphics.Paint(2));
        return result;
    }
    float palmX(){
        try{
            java.lang.reflect.Field field=PalmTracking.class.getDeclaredField("lock");field.setAccessible(true);
            Object lock=field.get(tracker);
            java.lang.reflect.Field x=lock.getClass().getDeclaredField("x");x.setAccessible(true);return x.getFloat(lock);
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    int phase;
    long start,lastLog;
    boolean observing,tapping;
    void tryPlace(){
        if(tapping)return;tapping=true;
        view.evaluateJavascript("(()=>{const g=document.querySelector('#palm-outline');if(g.hidden)return null;const pts=g.querySelector('polygon').points;if(pts.length!==6)return null;const a=pts.getItem(1),b=pts.getItem(4),w=pts.getItem(0);return [(.78*a.x+.12*b.x+.10*w.x)/1000,(.78*a.y+.12*b.y+.10*w.y)/1000]})()",raw->{
            tapping=false;
            if(raw==null || !raw.startsWith("["))return;
            try{
                org.json.JSONArray point=new org.json.JSONArray(raw);
                float x=(float)point.getDouble(0)*view.getWidth(),y=(float)point.getDouble(1)*view.getHeight();
                long t=SystemClock.uptimeMillis();
                for(int action:new int[]{0,1}){
                    android.view.MotionEvent e=android.view.MotionEvent.obtain(t,t+action,action,x,y,0);
                    e.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);view.dispatchTouchEvent(e);e.recycle();
                }
            }catch(Exception e){throw new AssertionError(e);}
        });
    }
    Bitmap load(String name)throws Exception{
        try(InputStream stream=getAssets().open(name)){return BitmapFactory.decodeStream(stream);}
    }
    public void onCreate(Bundle state){
        super.onCreate(state);
        try{
            Bitmap fixture=load("woman_hands.jpg");open=place(fixture,10);moved=place(fixture,70);rolled=roll(moved);returned=place(fixture,255);back=load("right_hands.jpg");fist=load("fist.jpg");
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
        if(!observing){
            observing=true;
            view.evaluateJavascript("window.palmCalls=0;window.hides=0;const p=whale.palmSurface,h=whale.hide;whale.palmSurface=(...a)=>{if(a[3]?.length!==6||!a[3].every(Number.isFinite)||Math.abs(a[3][2])+Math.abs(a[3][5])<0.00001)return;window.axisAngle=Math.atan2(a[3][1],a[3][0]);p(...a);window.palmCalls++};whale.hide=()=>{h();window.hides++}",null);
        }
        if(start==0)start=SystemClock.elapsedRealtime();
        if(SystemClock.elapsedRealtime()-lastLog>1500){
            lastLog=SystemClock.elapsedRealtime();
            String line="phase "+phase+" "+tracker.status()+" fps "+view.renderFps;
            view.evaluateJavascript("[window.palmCalls,window.hides]",res->Log.i("WhalePalmQA",line+" calls/hides "+res));
        }
        if(phase==2 || phase==3)view.evaluateJavascript("window.axisAngle ?? null",angle->{
            if(angle!=null && !angle.equals("null"))latestAngle=Double.parseDouble(angle);
        });
        long elapsed=SystemClock.elapsedRealtime()-start;
        boolean visible=tracker.status().equals("已托住小鲸鱼")||tracker.status().equals("正在找回掌心");
        if(tracker.failed()||elapsed>25000){Log.e("WhalePalmQA","phase "+phase+" failed: "+tracker.status());return;}
        if(phase==0){
            tracker.offer(open);
            if(tracker.status().startsWith("点掌面") && view.renderFps==0)tryPlace();
            if(visible && view.renderFps>0){firstX=palmX();next("tap near index base placed V21");}
        }else if(phase==1){
            tracker.offer(moved);
            if(visible && palmX()>firstX+.055f){lastX=palmX();next("followed moving palm");}
        }else if(phase==2){
            tracker.offer(moved);
            if(elapsed>1000 && visible && Math.abs(lastX-palmX())<.045 && Double.isFinite(latestAngle)){
                priorAngle=latestAngle;next("stationary palm held");
            }
        }else if(phase==3){
            tracker.offer(rolled);
            double change=Math.atan2(Math.sin(latestAngle-priorAngle),Math.cos(latestAngle-priorAngle));
            if(elapsed>600 && visible && view.renderFps>0 && change<-.22 && change>-.65)
                next("24 degree hand roll reached active renderer");
        }else if(phase==4){
            tracker.offer(back);
            if(elapsed>1400 && !visible && view.renderFps==0)next("back of hand hidden");
        }else if(phase==5){
            tracker.offer(fist);
            if(elapsed>600 && visible){Log.e("WhalePalmQA","accepted closed fist");return;}
            if(elapsed>1500)next("closed fist hidden");
        }else if(phase==6){
            tracker.offer(returned);
            if(visible && view.renderFps>0 && palmX()>firstX+.26f)next("palm recovered at new position");
        }else if(elapsed>800){
            if(visible || view.renderFps!=0){Log.e("WhalePalmQA","stale preview");return;}
            Log.i("WhalePalmQA","stopped feed hidden");tracker.close();return;
        }
        handler.postDelayed(this::tick,80);
    }
}
