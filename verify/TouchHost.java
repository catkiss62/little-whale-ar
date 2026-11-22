package dev.littlewhale.ar;

import android.app.Activity;
import android.os.Bundle;
import android.opengl.Matrix;
import android.util.Log;
import android.os.Handler;
import com.menthaar.sdk.MenthaAR;
import com.orb.slam2s.graphics.DisplayCorrection;

/** Test host using the shipping CharacterView, private-file loader, and renderer extracted from the signed APK. */
public final class TouchHost extends Activity {
    CharacterView view;
    final Handler handler=new Handler();
    final MenthaAR.Result pose=new MenthaAR.Result();
    final DisplayCorrection correction=new DisplayCorrection();
    boolean announced,previous;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        view=new CharacterView(this);
        view.setWebChromeClient(new android.webkit.WebChromeClient(){
            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage m){
                Log.i("WhaleTouchTest",m.message());return true;
            }
        });
        setContentView(view);
        Matrix.setIdentityM(pose.model,0);
        float scale=1.15f*.34f;
        Matrix.setIdentityM(pose.view,0);
        pose.view[0]=1/scale;pose.view[5]=pose.view[10]=-1/scale;
        pose.view[13]=-.5f;pose.view[14]=-1.15f;
        pose.projection[5]=1.5f;pose.projection[10]=-1.01f;pose.projection[11]=-1;pose.projection[14]=-.02f;
        handler.post(this::tick);
    }
    boolean editing,checking;
    void traceContour(int[][] vertices){
        int[] origin=new int[2];
        view.getLocationOnScreen(origin);
        sendContourPoint(vertices,origin,0,android.os.SystemClock.uptimeMillis());
    }
    void sendContourPoint(int[][] vertices,int[] origin,int index,long start){
        boolean release=index==vertices.length;
        int[] vertex=vertices[release?index-1:index];
        int x=vertex[0]-origin[0],y=vertex[1]-origin[1];
        int action=index==0?android.view.MotionEvent.ACTION_DOWN:
            release?android.view.MotionEvent.ACTION_UP:android.view.MotionEvent.ACTION_MOVE;
        android.view.MotionEvent event=android.view.MotionEvent.obtain(start,android.os.SystemClock.uptimeMillis(),action,x,y,0);
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        view.dispatchTouchEvent(event);event.recycle();
        if(release){
            handler.postDelayed(()->view.evaluateJavascript(
                "JSON.stringify([document.getElementById('area-tip').textContent,window.__point])",
                result->Log.i("WhaleTouchTest","end of stroke "+result)),800);
        }else awaitContourPoint(vertices,origin,index,start,x,y,0);
    }
    void awaitContourPoint(int[][] vertices,int[] origin,int index,long start,int x,int y,int attempts){
        String query="window.__point && Math.hypot(window.__point[0]*"+view.getWidth()+"/innerWidth-("+x+"),"+
            "window.__point[1]*"+view.getHeight()+"/innerHeight-("+y+"))<5";
        view.evaluateJavascript(query,result->{
            if("true".equals(result)){
                handler.postDelayed(()->sendContourPoint(vertices,origin,index+1,start),90);
            }else if(attempts<40){
                handler.postDelayed(()->awaitContourPoint(vertices,origin,index,start,x,y,attempts+1),100);
            }else Log.e("WhaleTouchTest","missing contour point "+index+" at "+x+","+y);
        });
    }
    void startBoundary(){
        view.setSize(50);
        view.editArea();
        view.evaluateJavascript("!document.getElementById('area-panel').hidden",result->{
            if(!"true".equals(result)){Log.e("WhaleTouchTest","area menu unavailable");return;}
            int[] at=new int[2];view.getLocationOnScreen(at);
            StringBuilder coords=new StringBuilder("boundary");
            int[][] vertices=new int[4][2];int i=0;
            for(float[] p:new float[][]{{-.23f,-.42f},{.23f,-.42f},{.23f,.22f},{-.23f,.22f}}){
                float depth=1.15f-p[1]; int h=view.getHeight();
                vertices[i][0]=at[0]+view.getWidth()/2+Math.round(.75f*h*p[0]/depth);
                vertices[i][1]=at[1]+Math.round(h*(.5f+.375f/depth));
                coords.append(" ").append(vertices[i][0]).append(" ").append(vertices[i][1]);i++;
            }
            editing=true;Log.i("WhaleTouchTest",coords.toString());
            view.evaluateJavascript("window.__point=null; ['pointerdown','pointermove','pointerup'].forEach(k=>addEventListener(k,e=>window.__point=[e.clientX,e.clientY],true))",done->traceContour(vertices));
        });
    }
    void testPickup(){
        // Wait for the earlier contour pointer-up before starting a new OS gesture.
        view.evaluateJavascript("document.getElementById('area-cancel').click();true",done->{
            handler.postDelayed(()->view.evaluateJavascript(
                "(()=>{const tip=document.getElementById('pickup-tip');let visible=false;"+
                "new MutationObserver(()=>{if(!tip.hidden&&tip.textContent.includes('绿圈')){visible=true;console.log('valid floor shown')}"+
                "else if(visible&&tip.hidden){console.log('body held, dragged and released on valid floor');visible=false}})"+
                ".observe(tip,{attributes:true,childList:true,subtree:true});"+
                "for(const k of ['pointerdown','pointermove','pointerup','pointercancel'])addEventListener(k,e=>console.log(k+' '+e.clientX+' '+e.clientY),true);return true})()",
                ready->{
                    int[] pos=new int[2];view.getLocationOnScreen(pos);
                    int x=pos[0]+view.getWidth()/2;
                    int y=pos[1]+Math.round(view.getHeight()*(.5f+.3f*.75f/1.15f));
                    Log.i("WhaleTouchTest","grip coordinates "+x+" "+y+" "+(x+Math.round(view.getHeight()*.048f)));
                }),250);
        });
    }
    void tick(){
        if(view.getWidth()>0 && "ready".equals(view.state)){
            pose.projection[0]=1.5f*view.getHeight()/view.getWidth();
            view.pose(pose,correction);
            if(!announced && view.renderFps>0){
                int[] origin=new int[2];view.getLocationOnScreen(origin);
                int cx=origin[0]+view.getWidth()/2, h=view.getHeight();
                int y=origin[1]+Math.round(h*.222f);
                Log.i("WhaleTouchTest","stroke "+(cx-h/22)+" "+y+" "+(cx+h/22)+" "+y);
                announced=true;
            }
            if(previous!=view.beingPetted){
                previous=view.beingPetted;Log.i("WhaleTouchTest",previous?"pet entered":"pet released");
                if(!previous)handler.postDelayed(this::startBoundary,300);
            }
            if(editing && !checking){
                checking=true;
                view.evaluateJavascript("document.getElementById('area-panel').hidden",result->{
                    checking=false;
                    if("true".equals(result)){
                        editing=false;view.editArea();
                        view.evaluateJavascript("!document.getElementById('area-clear').hidden",saved->{
                            if("true".equals(saved)){Log.i("WhaleTouchTest","native area retained");testPickup();}
                        });
                    }
                });
            }
        }else if("error".equals(view.state))Log.e("WhaleTouchTest","renderer error");
        handler.postDelayed(this::tick,45);
    }
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
