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
import cn.easyar.CalibrationDownloader;
import cn.easyar.Engine;
import cn.easyar.ImmediateCallbackScheduler;
import cn.easyar.MotionTrackerCameraDevice;
import cn.easyar.SurfaceTracker;

/** Independent camera session for testing EasyAR on the same desk as the normal engine. */
public final class EasyLabActivity extends Activity {
    private TextView status;
    private FrameLayout root;
    private GLSurfaceView view;
    private EasyLabSession session;
    private CalibrationDownloader calibration;
    private boolean initialized, resumed, surface;
    private long started;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        surface = getIntent().getBooleanExtra("surface", false);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setBackgroundColor(0xcc102030);
        status.setPadding(22, 16, 22, 16);
        root.addView(status, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        try (BufferedReader in = new BufferedReader(new InputStreamReader(getAssets().open("easyar-license.txt"), "UTF-8"))) {
            initialized = Engine.initialize(this, in.readLine().trim());
            if (!initialized) show("授权初始化失败 · " + Engine.errorMessage());
        } catch (Exception | LinkageError error) {
            show("EasyAR 初始化失败 · " + error.getClass().getSimpleName());
        }
    }

    private void show(String text) { status.setText((surface ? "轻量表面" : "水平面") + " · " + text); }

    private void openView() {
        if (!resumed || view != null || isFinishing()) return;
        view = new GLSurfaceView(this);
        view.setEGLContextClientVersion(3);
        view.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        session = new EasyLabSession(this, surface, started);
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
        if (!initialized) return;
        started = SystemClock.elapsedRealtime();
        Engine.onResume();
        if (view != null) { session.resetClock(started); view.onResume(); return; }
        if (surface) {
            if (SurfaceTracker.isAvailable()) openView(); else show("设备不支持轻量表面跟踪");
        } else if (MotionTrackerCameraDevice.isAvailable()) {
            openView();
        } else if (calibration == null) {
            show("正在检查设备标定");
            calibration = new CalibrationDownloader();
            calibration.download(10000, ImmediateCallbackScheduler.getDefault(), (result, detail) ->
                runOnUiThread(() -> {
                    if (isDestroyed() || !resumed) return;
                    if (MotionTrackerCameraDevice.isAvailable()) openView();
                    else show("运动跟踪不可用 · 标定状态 " + result + "，可返回试轻量表面");
                }));
        } else show("运动跟踪不可用，可返回试轻量表面");
    }

    @Override protected void onPause() {
        resumed = false;
        if (view != null) {
            view.queueEvent(session::close);
            view.onPause();
        }
        if (initialized) Engine.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (calibration != null) calibration.dispose();
        super.onDestroy();
    }
}
