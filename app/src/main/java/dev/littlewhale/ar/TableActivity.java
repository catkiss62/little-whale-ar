package dev.littlewhale.ar;

import android.Manifest;
import android.app.AlertDialog;
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
    private CharacterView whale;
    private Button characterMode;
    private boolean palmMode;
    private PalmTracking palm;
    private boolean showWhale, recovering, otherMap;
    private int goodFrames, lastGoodFrame = -1;
    private final ActivityResultLauncher<String[]> pickModel = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(), uri -> { if(uri!=null) CharacterFile.obtain(this,uri); });
    private final ActivityResultLauncher<String> saveModel = registerForActivityResult(
        new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
            if(uri!=null)CharacterFile.exportOriginal(this,uri);
        });
    private final ActivityResultLauncher<String[]> pickMotions = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(), uri -> {
            if(uri==null)return;
            new Thread(()->{
                try{
                    ReferenceMotions.importFile(getApplicationContext(),uri);
                    runOnUiThread(()->{if(!isDestroyed()){whale.evaluateJavascript("window.whale?.loadMotions(true)",null);motionToast("提起和走路动作已导入");}});
                }catch(Exception e){runOnUiThread(()->motionToast("动作导入失败 · "+e.getMessage()));}
            },"import-vr-motions").start();
        });
    private void motionToast(String message){android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_LONG).show();}
    private Button action;
    private DisplayCorrection correction;
    private boolean active, tracked, placed, unavailable, placementFailed;
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
        palmMode=state!=null && state.getBoolean("palmMode");
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
        whale = new CharacterView(this);
        whale.setFrameLimit(getPreferences(MODE_PRIVATE).getInt("frameLimit",60));
        whale.setSize(getPreferences(MODE_PRIVATE).getInt("sizePercent",100));
        whale.setPalmLift(getPreferences(MODE_PRIVATE).getInt("palmLift",50));
        showWhale = palmMode || getPreferences(MODE_PRIVATE).getBoolean("character", true);
        FrameLayout.LayoutParams fit = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        preview.addView(camera, fit);
        preview.addView(cube, new FrameLayout.LayoutParams(1, 1, Gravity.CENTER));
        preview.addView(whale, new FrameLayout.LayoutParams(1, 1, Gravity.CENTER));
        whale.setVisibility(showWhale ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
        camera.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob) -> {
            ViewGroup.LayoutParams lp = cube.getLayoutParams();
            if (lp.width != r-l || lp.height != b-t) {
                lp.width = r-l; lp.height = b-t;
                cube.setLayoutParams(lp);
                whale.setLayoutParams(new FrameLayout.LayoutParams(r-l,b-t,Gravity.CENTER));
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
        characterMode = new Button(this);
        characterMode.setOnClickListener(v -> chooseMode());
        page.addView(characterMode);
        setContentView(page);
        if (!hasCamera() && state == null) permission.launch(Manifest.permission.CAMERA);
    }

    private void switchMode(boolean character) {
        if(palmMode)setPalmMode(false);
        showWhale=character;
        getPreferences(MODE_PRIVATE).edit().putBoolean("character",character).apply();
        cube.clear(false); whale.clearPose(false);
        whale.setVisibility(character ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
        if(character) whale.startIfAvailable();
        updateControls();
    }

    private void chooseMode() {
        boolean installed=CharacterFile.file(this).isFile();
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("角色与模式");
        if(installed){
            String[] modes={"小鲸鱼 V21","对照方块","角色帧率 · "+whale.frameLimit+" FPS","导出原始 VRM","模型大小 · "+whale.sizePercent+"%",palmMode?"重新选择掌上站位":"绘制活动范围",palmMode?"掌上模式（当前）":"掌上模式","掌上站位微调 · "+whale.palmLift+"%"};
            int motionIndex=palmMode?8:7;
            modes=java.util.Arrays.copyOf(modes,motionIndex+1);
            modes[motionIndex]="导入提起和走路动作";
            dialog.setItems(modes,(d,index)->{
                if(index==motionIndex){pickMotions.launch(new String[]{"*/*"});return;}
                if(index==7){
                    new AlertDialog.Builder(this).setTitle("掌上站位微调")
                        .setSingleChoiceItems(new String[]{"默认 · 向上移半身","鞋底对准选点"},whale.palmLift==50?0:1,(choice,which)->{
                            whale.setPalmLift(which==0?50:0);
                            getPreferences(MODE_PRIVATE).edit().putInt("palmLift",whale.palmLift).apply();
                            choice.dismiss();
                        }).show();
                    return;
                }
                if(index==6){setPalmMode(true);return;}
                if(index==5){if(palmMode)whale.choosePalmPoint();else whale.editArea();return;}
                if(index==4){
                    int[] sizes={25,50,75,100,125,150,200};
                    String[] labels=new String[sizes.length];
                    int selected=3;
                    for(int i=0;i<sizes.length;i++){
                        labels[i]=sizes[i]+"%";
                        if(sizes[i]==whale.sizePercent)selected=i;
                    }
                    new AlertDialog.Builder(this).setTitle("模型大小").setSingleChoiceItems(labels,selected,(choice,which)->{
                        whale.setSize(sizes[which]);
                        getPreferences(MODE_PRIVATE).edit().putInt("sizePercent",whale.sizePercent).apply();
                        choice.dismiss();
                    }).show();
                    return;
                }
                if(index==2){
                    new AlertDialog.Builder(this).setTitle("角色渲染帧率上限")
                        .setSingleChoiceItems(new String[]{"30 FPS","60 FPS"},whale.frameLimit==30?0:1,(choice,which)->{
                            whale.setFrameLimit(which==0?30:60);
                            getPreferences(MODE_PRIVATE).edit().putInt("frameLimit",whale.frameLimit).apply();
                            choice.dismiss();
                        }).show();
                    return;
                }
                if(index==3){
                    if(!CharacterFile.exporting)saveModel.launch("SmallBlueFish-V21-AllExpressions-Acceptance.vrm");
                    return;
                }
                switchMode(index==0);
                if(index==0 && "error".equals(whale.state)) recreate();
            });
        }else{
            dialog.setMessage("小鲸鱼 V21 · 333 MiB，首次获取后可离线使用。模型来自 CF-612，形象参考「这个刀子真甜」，原形象 @ZipZipPipe、@上善无形。个人非商用测试。")
                .setPositiveButton("作者原址下载",(d,w)->{switchMode(true);CharacterFile.obtain(this,null);})
                .setNeutralButton("本机导入",(d,w)->{switchMode(true);pickModel.launch(new String[]{"*/*"});})
                .setNegativeButton("对照方块",(d,w)->switchMode(false));
        }
        dialog.show();
    }

    private void setPalmMode(boolean enabled){
        if(palmMode==enabled)return;
        stopPalm();
        palmMode=enabled;resetAnchor();
        if(enabled){
            showWhale=true;whale.setVisibility(android.view.View.VISIBLE);whale.startIfAvailable();
            if(active){engine.stop();startPalm();}
        }else if(active)engine.start();
        updateControls();
    }
    private void startPalm(){
        stopPalm();
        palm=new PalmTracking(this,whale);camera.setPreviewFrameListener(palm::offer);
    }
    private void stopPalm(){
        camera.setPreviewFrameListener(null);
        if(palm!=null){palm.close();palm=null;}
    }
    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);out.putBoolean("palmMode",palmMode);
    }

    private boolean hasCamera() { return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED; }

    private void act() {
        if (!hasCamera()) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
                permission.launch(Manifest.permission.CAMERA);
            else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
        } else if (palmMode && !unavailable) {
            if(palm!=null && palm.failed()){whale.clearPose(false);startPalm();}
            else setPalmMode(false);
        } else if (placed || unavailable) {
            recreate();
        } else if (showWhale && !"ready".equals(whale.state)) {
            chooseMode();
        } else if (tracked && requestTime == 0) {
            placementFailed = false;
            requestTime = SystemClock.elapsedRealtime();
            camera.requestPlaneDetection();
        } else if (!engine.isConnected()) {
            engine.start();
        }
        updateControls();
    }

    private void resetAnchor() {
        requestTime = 0; placed = false; tracked = false; anchorMap = -1;
        placementFailed = false;
        lastResult = 0; sampleStart = 0; frames = 0; fps = 0;
        cube.clear(true); whale.clearPose(true);
        otherMap=false; recovering=false; goodFrames=0; lastGoodFrame=-1;
    }

    private void startSession() {
        if (active || !hasCamera()) return;
        resetAnchor();
        unavailable = false;
        active = true;
        camera.onResume();
        if(palmMode)startPalm();else engine.start();
        camera.enableView();
    }

    @Override protected void onResume() {
        super.onResume();
        whale.onResume();
        startSession();
        refresh.run();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        if (active) {
            active = false;stopPalm();
            camera.disableView();
            camera.onPause();
            engine.stop();
            resetAnchor();
        }
        whale.onPause();
        super.onPause();
    }

    @Override public void onReady() { runOnUiThread(this::updateControls); }
    @Override public void onDisconnected() { runOnUiThread(() -> { if(!palmMode && !engine.isConnected())resetAnchor(); updateControls(); }); }
    @Override public void onTrackingResult() {
        if (!active || palmMode || !engine.readResult(pose)) return;
        long now = SystemClock.elapsedRealtime();
        if(placed && lastResult>0 && now-lastResult>1200) losePose();
        lastResult = now;
        if (sampleStart == 0) sampleStart = lastResult;
        frames++;
        if (lastResult - sampleStart >= 1000) {
            fps = (int)Math.round(frames * 1000d / (lastResult - sampleStart));
            sampleStart = lastResult; frames = 0;
        }
        tracked = pose.isTracked();
        otherMap = placed && tracked && pose.mapId != anchorMap;
        if (!tracked || otherMap || !pose.drawable || correction == null) { losePose(); return; }
        if (recovering) {
            if (lastGoodFrame != pose.frameId) { goodFrames++; lastGoodFrame=pose.frameId; }
            if (goodFrames<3) return;
            recovering=false;
        }
        if (!placed && requestTime != 0) {
            anchorMap = pose.mapId; placed = true; requestTime = 0;
        }
        if (placed && pose.mapId == anchorMap) {
            if(showWhale) whale.pose(pose,correction);
            else cube.update(pose, correction);
        }
    }

    private void losePose() {
        if(placed)recovering=true;
        goodFrames=0;lastGoodFrame=-1;
        cube.clear(false);whale.clearPose(false);
    }

    @Override protected void onDestroy() {
        ((ViewGroup)whale.getParent()).removeView(whale);
        whale.close();
        super.onDestroy();
    }

    private void updateControls() {
        if(showWhale) whale.startIfAvailable();
        long now = SystemClock.elapsedRealtime();
        if (!palmMode && (lastResult == 0 || now - lastResult > 1200)) {
            tracked = false; fps = 0; losePose();
        }
        boolean timedOut = requestTime != 0 && now - requestTime > 3500;
        if (timedOut) { requestTime = 0; placementFailed = true; }
        String label = showWhale ? "放置小鲸鱼" : "放置方块", message;
        boolean enabled = false;
        if (!hasCamera()) {
            label = "相机权限"; message = "允许使用后置相机后即可扫描桌面"; enabled = true;
        } else if (unavailable) {
            label = "重试"; message = "相机打开失败，请关闭占用相机的应用后重试"; enabled = true;
        } else if (palmMode) {
            enabled=true;
            label=palm!=null && palm.failed()?"重试手掌识别":"返回桌面";
            message="error".equals(whale.state)?"角色显示失败，请重新载入":
                !"ready".equals(whale.state)?"正在载入小鲸鱼…":
                palm==null?"正在启动相机…":palm.status();
        } else if (!engine.isConnected()) {
            label = "重连"; message = "正在连接定位引擎"; enabled = active;
        } else if (showWhale && !"ready".equals(whale.state)) {
            label="选择角色"; enabled=true;
            if(CharacterFile.busy) message="获取小鲸鱼 " + CharacterFile.percent + "%";
            else if("error".equals(whale.state)) message="角色显示失败，点此重试或切换方块";
            else if(CharacterFile.file(this).isFile()) message="正在载入小鲸鱼…";
            else message=CharacterFile.failure==null?"首次使用，请下载或导入小鲸鱼 V21":CharacterFile.failure;
        } else if (placed) {
            label = "重新扫描"; enabled = true;
            if(otherMap) message="正在另一张地图 · 对准原来的桌面";
            else if(!tracked || recovering) message="正在找回原位 · 对准刚才的桌面";
            else if(showWhale && whale.beingPetted) message="正在摸头";
            else message=showWhale?"已放置 · 摸头、点尾巴，长按身体可提起":"已放置 · 绕桌观察不同颜色的面";
        } else if (!tracked) {
            message = "正在定位手机 · 对准桌面纹理，缓慢左右平移";
        } else if (requestTime != 0) {
            message = "手机已定位 · 正在寻找桌面…";
        } else {
            enabled = true;
            message = placementFailed ? "这次未找到桌面 · 对准书封或桌面纹理，横移后重试" : "手机已定位 · 对准目标桌面，点放置";
        }
        if (!palmMode && active && fps > 0) message += "\n定位 " + fps + " FPS";
        if (showWhale && whale.renderFps>0) message += " · 角色 " + whale.renderFps + " FPS";
        if (CharacterFile.exporting) message += "\n正在导出 VRM " + CharacterFile.exportPercent + "%";
        if (!message.contentEquals(status.getText())) status.setText(message);
        if (!label.contentEquals(action.getText())) action.setText(label);
        action.setEnabled(enabled);
        String mode=palmMode?"掌上 · 小鲸鱼 V21   ▾":showWhale?"角色 · 小鲸鱼 V21   ▾":"角色 · 对照方块   ▾";
        if(!mode.contentEquals(characterMode.getText()))characterMode.setText(mode);
    }

}
