package dev.littlewhale.ar;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.menthaar.sdk.MenthaAR;
import com.orb.slam2s.camera.CameraPreviewView;
import com.orb.slam2s.graphics.DisplayCorrection;

public final class TableActivity extends ComponentActivity implements MenthaAR.Listener {
    private final MenthaAR.Result pose = new MenthaAR.Result();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MenthaAR engine;
    private CameraPreviewView camera;
    private CubeOverlay cube;
    private TextView status;
    private Button action;
    private DisplayCorrection correction;
    private boolean active, tracked, placed, unavailable;
    private int anchorMap = -1, frames, fps;
    private long requestTime, lastResult, sampleStart;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            updateControls();
            handler.postDelayed(this, 300);
        }
    };
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted && getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
                startSession();
            updateControls();
        });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        engine = new MenthaAR(this);
        engine.setPointCloudVisible(false);
        engine.setPoseAssistEnabled(true);
        engine.setListener(this);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xff101d2b);
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        FrameLayout preview = new FrameLayout(this);
        camera = new CameraPreviewView(this);
        camera.setMenthaAR(engine);
        camera.setDisplayCorrectionListener(value -> {
            correction = value;
            engine.setCameraRotation(value.rotationDegrees);
        });
        camera.setFrameListener(new CameraPreviewView.FrameListener() {
            public void onCameraFrame() {}
            public void onCameraStopped() {}
            public void onCameraStarted(int width, int height) { runOnUiThread(() -> unavailable = false); }
            public void onCameraUnavailable(int count) { runOnUiThread(() -> unavailable = true); }
        });
        camera.init();
        cube = new CubeOverlay(this);
        FrameLayout.LayoutParams fit = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        preview.addView(camera, fit);
        preview.addView(cube, new FrameLayout.LayoutParams(1, 1, Gravity.CENTER));
        camera.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob) -> {
            ViewGroup.LayoutParams lp = cube.getLayoutParams();
            if (lp.width != r-l || lp.height != b-t) {
                lp.width = r-l; lp.height = b-t;
                cube.setLayoutParams(lp);
            }
        });
        preview.setOnClickListener(v -> camera.autoFocusCenter());
        page.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        bar.setPadding(pad, pad, pad, pad);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(15);
        bar.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        action = new Button(this);
        action.setOnClickListener(v -> act());
        bar.addView(action);
        page.addView(bar);
        setContentView(page);
        if (!hasCamera() && state == null) permission.launch(Manifest.permission.CAMERA);
    }

    private boolean hasCamera() { return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED; }

    private void act() {
        if (!hasCamera()) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
                permission.launch(Manifest.permission.CAMERA);
            else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
        } else if (placed || unavailable) {
            recreate();
        } else if (tracked && requestTime == 0) {
            requestTime = SystemClock.elapsedRealtime();
            camera.requestPlaneDetection();
        } else if (!engine.isConnected()) {
            engine.start();
        }
        updateControls();
    }

    private void resetAnchor() {
        requestTime = 0; placed = false; tracked = false; anchorMap = -1;
        lastResult = 0; sampleStart = 0; frames = 0; fps = 0;
        cube.clear(true);
    }

    private void startSession() {
        if (active || !hasCamera()) return;
        resetAnchor();
        unavailable = false;
        active = true;
        camera.onResume();
        engine.start();
        camera.enableView();
    }

    @Override protected void onResume() {
        super.onResume();
        startSession();
        refresh.run();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        if (active) {
            active = false;
            camera.disableView();
            camera.onPause();
            engine.stop();
            resetAnchor();
        }
        super.onPause();
    }

    @Override public void onReady() { runOnUiThread(this::updateControls); }
    @Override public void onDisconnected() { runOnUiThread(() -> { resetAnchor(); updateControls(); }); }
    @Override public void onTrackingResult() {
        if (!active || !engine.readResult(pose)) return;
        lastResult = SystemClock.elapsedRealtime();
        if (sampleStart == 0) sampleStart = lastResult;
        frames++;
        if (lastResult - sampleStart >= 1000) {
            fps = (int)Math.round(frames * 1000d / (lastResult - sampleStart));
            sampleStart = lastResult; frames = 0;
        }
        tracked = pose.isTracked();
        if (placed && pose.mapId != anchorMap && tracked) resetAnchor();
        if (!tracked || !pose.drawable || correction == null) { cube.clear(false); return; }
        if (!placed && requestTime != 0) {
            anchorMap = pose.mapId; placed = true; requestTime = 0;
        }
        if (placed && pose.mapId == anchorMap) cube.update(pose, correction);
    }

    private void updateControls() {
        long now = SystemClock.elapsedRealtime();
        if (lastResult == 0 || now - lastResult > 1200) {
            tracked = false; fps = 0; cube.clear(false);
        }
        boolean timedOut = requestTime != 0 && now - requestTime > 3500;
        if (timedOut) { requestTime = 0; placementFailed = true; }
        String label = "放置方块", message;
        boolean enabled = false;
        if (!hasCamera()) {
            label = "相机权限"; message = "允许使用后置相机后即可扫描桌面"; enabled = true;
        } else if (unavailable) {
            label = "重试"; message = "相机打开失败，请关闭占用相机的应用后重试"; enabled = true;
        } else if (!engine.isConnected()) {
            label = "重连"; message = "正在连接定位引擎"; enabled = active;
        } else if (placed) {
            label = "重新扫描"; enabled = true;
            message = tracked ? "已放置 · 绕桌观察不同颜色的面" : "跟踪中断 · 将镜头移回刚才的桌面";
        } else if (!tracked) {
            message = "对准有纹理的桌面，缓慢左右平移手机";
        } else if (requestTime != 0) {
            message = "正在拟合镜头中的桌面…";
        } else {
            enabled = true;
            message = placementFailed ? "未找到平面 · 再扫一段桌面后重试" : "跟踪就绪 · 让目标桌面占满画面";
        }
        if (active && fps > 0) message += "\n定位 " + fps + " FPS";
        if (!message.contentEquals(status.getText())) status.setText(message);
        if (!label.contentEquals(action.getText())) action.setText(label);
        action.setEnabled(enabled);
    }

    private boolean placementFailed;
}
