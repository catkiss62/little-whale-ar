package dev.littlewhale.ar;

import android.opengl.*;
import com.google.ar.core.*;
import java.nio.*;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import static android.opengl.GLES20.*;

final class CoreLabRenderer implements GLSurfaceView.Renderer {
    private static final int OES=GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
    private final CoreLabActivity activity;
    private final FloatBuffer quad=buffer(-1,-1,1,-1,-1,1,1,1),uv=buffer(0,0,0,0,0,0,0,0);
    private float characterHeight;
    private final float[] projection=new float[16],view=new float[16],model=new float[16],mv=new float[16];
    private volatile Session session;
    private Anchor anchor;
    private int bg,texture,width,height;
    private float touchX=-1,touchY;
    private boolean depthMode,textureBound,missed;
    private String last="";
    CoreLabRenderer(CoreLabActivity activity){
        this.activity=activity;
    }
    void attach(Session s){session=s;}
    void tap(float x,float y){touchX=x;touchY=y;}
    void changeMode(){depthMode=!depthMode;if(anchor!=null)anchor.detach();anchor=null;characterHeight=0;activity.pose(null,null,true);touchX=-1;missed=false;activity.modeLabel(depthMode);}
    static FloatBuffer buffer(float... a){
        FloatBuffer b=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(a).rewind();return b;
    }
    private static int shader(int type,String source){
        int s=glCreateShader(type);glShaderSource(s,source);glCompileShader(s);
        int[] ok={0};glGetShaderiv(s,GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(glGetShaderInfoLog(s));return s;
    }
    private static int program(String vertex,String fragment){
        int p=glCreateProgram(),v=shader(GL_VERTEX_SHADER,vertex),f=shader(GL_FRAGMENT_SHADER,fragment);
        glAttachShader(p,v);glAttachShader(p,f);glBindAttribLocation(p,0,"pos");glBindAttribLocation(p,1,"uv");
        glLinkProgram(p);glDeleteShader(v);glDeleteShader(f);
        int[] ok={0};glGetProgramiv(p,GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(glGetProgramInfoLog(p));return p;
    }
    public void onSurfaceCreated(GL10 gl,EGLConfig ignored){
        bg=program("attribute vec2 pos,uv;varying vec2 t;void main(){gl_Position=vec4(pos,0.,1.);t=uv;}",
            "#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES cam;varying vec2 t;void main(){gl_FragColor=texture2D(cam,t);}");
        int[] tex={0};glGenTextures(1,tex,0);texture=tex[0];glBindTexture(OES,texture);
        glTexParameteri(OES,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(OES,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(OES,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(OES,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        textureBound=false;
    }
    public void onSurfaceChanged(GL10 gl,int w,int h){width=w;height=h;glViewport(0,0,w,h);}
    private void status(String s){if(!last.equals(s)){last=s;activity.message(s);}}
    public void onDrawFrame(GL10 ignored){
        glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
        Session s=session;if(s==null || width==0)return;
        try{
            if(!textureBound){s.setCameraTextureName(texture);textureBound=true;}
            s.setDisplayGeometry(activity.getWindowManager().getDefaultDisplay().getRotation(),width,height);
            Frame frame=s.update();Camera camera=frame.getCamera();
            if(frame.getTimestamp()==0)return;
            frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,quad,Coordinates2d.TEXTURE_NORMALIZED,uv);
            glDisable(GL_DEPTH_TEST);glUseProgram(bg);glActiveTexture(GL_TEXTURE0);glBindTexture(OES,texture);
            glEnableVertexAttribArray(0);glEnableVertexAttribArray(1);
            glVertexAttribPointer(0,2,GL_FLOAT,false,0,quad);glVertexAttribPointer(1,2,GL_FLOAT,false,0,uv);glDrawArrays(GL_TRIANGLE_STRIP,0,4);glDisableVertexAttribArray(1);
            if(camera.getTrackingState()!=TrackingState.TRACKING){touchX=-1;activity.pose(null,null,false);status("ARCore 定位中 · "+camera.getTrackingFailureReason());return;}
            if(touchX>=0){
                missed=true;
                for(HitResult hit:frame.hitTest(touchX,touchY)){
                    Trackable t=hit.getTrackable();
                    boolean valid=depthMode?t instanceof DepthPoint:t instanceof Plane && ((Plane)t).getType()==Plane.Type.HORIZONTAL_UPWARD_FACING && ((Plane)t).isPoseInPolygon(hit.getHitPose());
                    if(valid && t.getTrackingState()==TrackingState.TRACKING){Anchor next=hit.createAnchor();if(anchor!=null)anchor.detach();anchor=next;characterHeight=0;activity.pose(null,null,true);missed=false;break;}
                }
                touchX=-1;
            }
            if(anchor==null||anchor.getTrackingState()!=TrackingState.TRACKING){
                activity.pose(null,null,false);status(missed?"此处尚无有效落点 · 横移后再点":anchor==null?"定位正常 · 轻点桌面放小鲸鱼":"锚点恢复中");return;
            }
            camera.getViewMatrix(view,0);camera.getProjectionMatrix(projection,0,.02f,30);
            Pose pose=anchor.getPose();Matrix.setIdentityM(model,0);Matrix.translateM(model,0,pose.tx(),pose.ty(),pose.tz());
            Matrix.multiplyMM(mv,0,view,0,model,0);
            if(characterHeight==0 && mv[14]<0)characterHeight=-mv[14]*.34f;
            if(characterHeight<=0){activity.pose(null,null,false);return;}
            Matrix.scaleM(mv,0,characterHeight,characterHeight,characterHeight);
            activity.pose(mv,projection,false);
            status(missed?"新点未命中 · 保留原位":depthMode?"小鲸鱼 · 深度落点":"小鲸鱼 · 水平桌面");
        }catch(Exception e){activity.pose(null,null,false);status("ARCore 帧状态 · "+e.getClass().getSimpleName());}
    }
}
