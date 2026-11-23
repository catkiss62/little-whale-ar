package dev.littlewhale.ar;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.opengl.GLSurfaceView;
import android.view.ViewGroup;
import android.widget.*;
import com.google.ar.core.*;
import com.google.ar.core.exceptions.*;

/** Independent native camera/anchor comparison, using one ARCore frame per draw. */
public final class CoreLabActivity extends Activity {
    private GLSurfaceView surface;
    private TextView status;
    private Button mode;
    private CoreLabRenderer renderer;
    private Session session;
    private boolean resumed, running, requestedInstall;
    private CharacterView whale;
    private final float[] modelView = new float[16], lens = new float[16];
    private boolean hasPose, resetPose, posted;
    private final Runnable display = () -> {
        synchronized (this) {
            posted = false;
            if (!resumed) return;
            if (resetPose) whale.clearPose(true);
            resetPose = false;
            if (hasPose) whale.referencePose(modelView, lens);
            else whale.clearPose(false);
        }
    };
    synchronized void pose(float[] mv, float[] projection, boolean reset) {
        hasPose = mv != null;
        resetPose |= reset;
        if (hasPose) {
            System.arraycopy(mv, 0, modelView, 0, 16);
            System.arraycopy(projection, 0, lens, 0, 16);
        }
        if (!posted) { posted = true; whale.post(display); }
    }
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable retry=this::start;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        surface=new GLSurfaceView(this);surface.setEGLContextClientVersion(2);
        surface.setPreserveEGLContextOnPause(true);
        renderer=new CoreLabRenderer(this);surface.setRenderer(renderer);
        FrameLayout preview = new FrameLayout(this);
        preview.addView(surface);
        whale = new CharacterView(this);whale.captureFrom(surface);
        android.content.SharedPreferences prefs = getSharedPreferences("TableActivity", MODE_PRIVATE);
        whale.setSize(prefs.getInt("sizePercent", 100));
        whale.setFrameLimit(prefs.getInt("frameLimit", 60));
        whale.setReferenceWalk(prefs.getBoolean("referenceWalk", true));
        whale.surfaceTap = (x,y) -> {
            if (running) {
                float px=x*surface.getWidth(), py=y*surface.getHeight();
                surface.queueEvent(() -> renderer.tap(px,py));
            }
        };
        preview.addView(whale);
        page.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setPadding(16,10,16,10);page.addView(status);
        mode=new Button(this);mode.setText("水平桌面 · 点击切换深度");mode.setEnabled(false);
        mode.setOnClickListener(v->surface.queueEvent(()->renderer.changeMode()));
        page.addView(mode);
        Button back=new Button(this);back.setText("返回现有引擎");back.setOnClickListener(v->finish());page.addView(back);
        setContentView(page);
    }
    void message(String text){runOnUiThread(()->{if(!isDestroyed())status.setText(text);});}
    void modeLabel(boolean depth){runOnUiThread(()->mode.setText(depth?"深度落点 · 点击切换水平桌面":"水平桌面 · 点击切换深度"));}
    @Override protected void onResume(){super.onResume();resumed=true;whale.onResume();start();}
    private void start(){
        if(!resumed||running)return;
        try{
            if(session==null){
                ArCoreApk api=ArCoreApk.getInstance();ArCoreApk.Availability availability=api.checkAvailability(this);
                if(availability==ArCoreApk.Availability.UNKNOWN_CHECKING){
                    message("正在查询本机 ARCore 能力…");handler.postDelayed(retry,500);return;
                }
                if(!availability.isSupported()){
                    message(availability==ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE?
                        "本机报告不支持 ARCore。可返回测试现有引擎或参考图。":"ARCore 能力暂未确定 · "+availability+"；可稍后重新进入");
                    return;
                }
                if(api.requestInstall(this,!requestedInstall)==ArCoreApk.InstallStatus.INSTALL_REQUESTED){requestedInstall=true;return;}
                session=new Session(this);
                Config config=new Config(session);config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL);
                boolean depth=session.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
                if(depth)config.setDepthMode(Config.DepthMode.AUTOMATIC);
                config.setFocusMode(Config.FocusMode.AUTO);session.configure(config);
                mode.setEnabled(depth);
                if(!depth)mode.setText("水平桌面 · 本机不支持深度");
            }
            session.resume();renderer.attach(session);running=true;surface.onResume();
            message("ARCore 已启动 · 缓慢横移，对准桌面轻点放置");
        }catch(Exception e){message("ARCore 未能启动 · "+e.getClass().getSimpleName()+"；可返回现有引擎");}
    }
    @Override protected void onPause(){
        resumed=false;handler.removeCallbacks(retry);
        if(running){surface.onPause();running=false;session.pause();}
        whale.removeCallbacks(display);
        synchronized (this) { posted=false; hasPose=false; resetPose=false; }
        whale.clearPose(false);whale.onPause();
        super.onPause();
    }
    @Override protected void onDestroy(){if(session!=null)session.close();((ViewGroup)whale.getParent()).removeView(whale);whale.close();super.onDestroy();}
}
