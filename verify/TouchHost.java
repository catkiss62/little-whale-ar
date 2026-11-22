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
    void startBoundary(){
        view.setSize(50);
        view.editArea();
        view.evaluateJavascript("!document.getElementById('area-panel').hidden",result->{
            if(!"true".equals(result)){Log.e("WhaleTouchTest","area menu unavailable");return;}
            int[] at=new int[2];view.getLocationOnScreen(at);
            StringBuilder coords=new StringBuilder("boundary");
            for(float[] p:new float[][]{{-.23f,-.42f},{.23f,-.42f},{.23f,.22f},{-.23f,.22f}}){
                float depth=1.15f-p[1]; int h=view.getHeight();
                coords.append(" ").append(at[0]+view.getWidth()/2+Math.round(.75f*h*p[0]/depth));
                coords.append(" ").append(at[1]+Math.round(h*(.5f+.375f/depth)));
            }
            editing=true;Log.i("WhaleTouchTest",coords.toString());
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
                            if("true".equals(saved))Log.i("WhaleTouchTest","native area retained");
                        });
                    }
                });
            }
        }else if("error".equals(view.state))Log.e("WhaleTouchTest","renderer error");
        handler.postDelayed(this::tick,45);
    }
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
