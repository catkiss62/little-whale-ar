package dev.littlewhale.ar;

import android.app.Activity;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import cn.easyar.Engine;
import cn.easyar.SurfaceTracker;

/** Independent camera session for testing EasyAR on the same desk as the normal engine. */
public final class EasyLabActivity extends Activity {
    private TextView status;
    private FrameLayout root;
    private GLSurfaceView view;
    private EasyLabSession session;
    private boolean initialized, resumed;
    private long started;
    private CharacterView whale;
    private final float[] model = new float[16], projection = new float[16];
    private boolean visible, posted;
    private String trackingStatus = "";
    private final Runnable deliver = () -> {
        synchronized (this) {
            posted = false;
            if (!resumed || whale == null) return;
            updateStatus();
            if (visible) whale.referencePose(model, projection);
            else whale.clearPose(false);
        }
    };

    synchronized void frame(float[] mv, float[] lens) {
        if (whale == null) return;
        visible = mv != null;
        if (visible) {
            System.arraycopy(mv, 0, model, 0, 16);
            System.arraycopy(lens, 0, projection, 0, 16);
        }
        if (!posted) {
            posted = true;
            root.post(deliver);
        }
    }


    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        whale = new CharacterView(this);
        android.content.SharedPreferences prefs = getSharedPreferences("TableActivity", MODE_PRIVATE);
        whale.setFrameLimit(prefs.getInt("frameLimit", 60));
        whale.setReferenceWalk(prefs.getBoolean("referenceWalk", true));
        whale.setSize(prefs.getInt("sizePercent", 100));
        root.addView(whale, new FrameLayout.LayoutParams(-1, -1));
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setBackgroundColor(0xcc102030);
        status.setPadding(22, 16, 22, 16);
        android.widget.LinearLayout bar = new android.widget.LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xcc102030);
        bar.addView(status, new android.widget.LinearLayout.LayoutParams(0, -2, 1));
        if (whale != null) {
            android.widget.Button area = new android.widget.Button(this);
            area.setText("活动范围");
            area.setOnClickListener(v -> whale.editArea());
            bar.addView(area);
        }
        root.addView(bar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        try (BufferedReader in = new BufferedReader(new InputStreamReader(getAssets().open("easyar-license.txt"), "UTF-8"))) {
            initialized = Engine.initialize(this, in.readLine().trim());
            if (!initialized) show("授权初始化失败 · " + Engine.errorMessage());
        } catch (Exception | LinkageError error) {
            show("EasyAR 初始化失败 · " + error.getClass().getSimpleName());
        }
    }

    private void show(String text) { trackingStatus = text; updateStatus(); }

    private void updateStatus() {
        String text = "EasyAR 轻量表面" + " · " + trackingStatus;
        if (whale != null && !"ready".equals(whale.state))
            text += "error".equals(whale.state) ? " · 角色载入失败" : " · 正在载入小鲸鱼";
        if (!text.contentEquals(status.getText())) status.setText(text);
    }

    private void openView() {
        if (!resumed || view != null || isFinishing()) return;
        view = new GLSurfaceView(this);
        whale.captureFrom(view);
        view.setEGLContextClientVersion(3);
        view.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        session = new EasyLabSession(this, started);
        view.setRenderer(session);
        root.addView(view, 0, new FrameLayout.LayoutParams(-1, -1));
        TextView aim = new TextView(this);
        aim.setText("＋");
        aim.setGravity(Gravity.CENTER);
        aim.setTextColor(Color.WHITE);
        aim.setShadowLayer(3, 0, 0, Color.BLACK);
        root.addView(aim, 1, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        show("对准桌面纹理，缓慢横移");
    }

    void report(String message) { runOnUiThread(() -> { if (resumed) show(message); }); }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (whale != null) whale.onResume();
        if (!initialized) return;
        started = SystemClock.elapsedRealtime();
        Engine.onResume();
        if (view != null) { session.resetClock(started); view.onResume(); return; }
        if (SurfaceTracker.isAvailable()) openView(); else show("设备不支持轻量表面跟踪");
    }

    @Override protected void onPause() {
        resumed = false;
        if (view != null) {
            view.queueEvent(session::close);
            view.onPause();
        }
        if (whale != null) {
            root.removeCallbacks(deliver);
            synchronized (this) { posted = visible = false; }
            whale.clearPose(true);
            whale.onPause();
        }
        if (initialized) Engine.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (whale != null) { root.removeView(whale); whale.close(); }
        super.onDestroy();
    }
}
