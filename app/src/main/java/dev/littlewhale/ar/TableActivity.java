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
    private TextView status;
    private CharacterView whale;
    private Button characterMode, labs;
    private boolean palmMode;
    private PalmTracking palm;
    private boolean recovering, otherMap;
    private int goodFrames, lastGoodFrame = -1;
    private final ActivityResultLauncher<String[]> pickModel = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(), uri -> { if(uri!=null) CharacterFile.obtain(this,uri); });
    private void motionToast(String message){android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_LONG).show();}
    private Button action;
    private DisplayCorrection correction;
    private long sessionStart,firstFix=-1,firstPlane=-1,planeRequest;
    private int candidateMap=-1;
    private final float[] modelView=new float[16], clipPlane=new float[16];
    private final float[] displayPlane = new float[16];
    private boolean anchorVisible(){
        android.opengl.Matrix.multiplyMM(modelView,0,pose.view,0,pose.model,0);
        android.opengl.Matrix.multiplyMM(clipPlane,0,pose.projection,0,modelView,0);
        android.opengl.Matrix.multiplyMM(displayPlane,0,correction.getNdcTransform(),0,clipPlane,0);
        float w=displayPlane[15];
        return w>0 && Math.abs(displayPlane[12])<=w && Math.abs(displayPlane[13])<=w;
    }
    private boolean active, tracked, placed, unavailable;
    private int anchorMap = -1, frames, fps;
    private long lastResult, sampleStart;
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
        page.setBackgroundColor(0xff16131b);
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        FrameLayout preview = new FrameLayout(this){
            @Override protected void onMeasure(int width,int height){
                super.onMeasure(width,height);
                whale.measure(MeasureSpec.makeMeasureSpec(camera.getMeasuredWidth(),MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(camera.getMeasuredHeight(),MeasureSpec.EXACTLY));
            }
        };
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
        whale = new CharacterView(this);
        whale.importModel=this::pickLocalModel;
        whale.setSize(getPreferences(MODE_PRIVATE).getInt("sizePercent",100));
        whale.setPalmLift(getPreferences(MODE_PRIVATE).getInt("palmLift",50));
        FrameLayout.LayoutParams fit = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        preview.addView(camera, fit);
        preview.addView(whale, new FrameLayout.LayoutParams(1, 1, Gravity.CENTER));
        preview.setOnClickListener(v -> camera.autoFocusCenter());
        page.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        bar.setPadding(pad, pad/2, pad, 0);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(14);
        status.setTextColor(0xffd4cedc);status.setPadding(0,0,pad,0);
        bar.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        action = new Button(this);
        action.setBackgroundTintList(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff36313c,0xffb082ff}));
        action.setTextColor(0xff21162d);
        action.setOnClickListener(v -> act());
        bar.addView(action);
        page.addView(bar);
        characterMode = new Button(this);
        characterMode.setOnClickListener(v -> chooseMode());
        LinearLayout modesBar=new LinearLayout(this);modesBar.setPadding(pad,0,pad,pad/2);
        modesBar.addView(characterMode,new LinearLayout.LayoutParams(0,-2,1));
        labs=new Button(this);labs.setText("定位对照");
        labs.setOnClickListener(v -> {if(palmMode){setPalmMode(false);return;}new AlertDialog.Builder(this).setTitle("定位对照")
            .setItems(new String[]{"原生引擎","参考图","EasyAR","ARCore"}, (dialog, index) -> {
                if (index == 0) { selectNative(); return; }
                if (!hasCamera()) { permission.launch(Manifest.permission.CAMERA); return; }
                if (!CharacterFile.file(this).isFile()) {
                    pickLocalModel(); return;
                }
                if (index == 2) openEasyAR();
                else startActivity(new Intent(this, index == 1 ? ImageLabActivity.class : CoreLabActivity.class));
            }).show();});
        modesBar.addView(labs,new LinearLayout.LayoutParams(0,-2,1));page.addView(modesBar);
        setContentView(page);
        if (!hasCamera() && state == null) permission.launch(Manifest.permission.CAMERA);
    }

    private void openEasyAR() {
        String lab = "dev.littlewhale.ar.EasyLabActivity";
        try {
            Class.forName(lab, false, getClassLoader());
            startActivity(new Intent().setClassName(this, lab));
        } catch (ClassNotFoundException missingSdk) {
            motionToast("此构建未包含 EasyAR，请安装已签名测试版");
        }
    }

    private void selectNative() {
        if(palmMode)setPalmMode(false);
        whale.startIfAvailable();
        updateControls();
    }

    private void pickLocalModel(){
        if(CharacterFile.busy){motionToast("模型正在导入");return;}
        pickModel.launch(new String[]{"*/*"});
    }
    private void chooseMode() {
        boolean installed=CharacterFile.file(this).isFile();
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("角色与模式");
        if(installed){
            String[] modes={"导入模型","删除模型","模型大小 · "+whale.sizePercent+"%",palmMode?"重新选择掌上站位":"绘制活动范围","掌上模式","掌上站位微调 · "+whale.palmLift+"%"};
            if(!palmMode)modes=java.util.Arrays.copyOf(modes,5);
            dialog.setItems(modes,(d,index)->{
                if(index==0){pickLocalModel();return;}
                if(index==1){
                    new AlertDialog.Builder(this).setTitle("删除模型？").setMessage("删除应用内的小鲸鱼和纹理缓存。你保存在手机里的原始 VRM 可以再次导入。")
                        .setNegativeButton("取消",null).setPositiveButton("删除",(confirm,w)->{
                            if(CharacterFile.remove(this)){setPalmMode(false);recreate();}
                            else motionToast("模型正在使用或无法删除，请稍后再试");
                        }).show();
                    return;
                }
                if(index==5){
                    new AlertDialog.Builder(this).setTitle("掌上站位微调")
                        .setSingleChoiceItems(new String[]{"默认 · 向上移半身","鞋底对准选点"},whale.palmLift==50?0:1,(choice,which)->{
                            whale.setPalmLift(which==0?50:0);
                            getPreferences(MODE_PRIVATE).edit().putInt("palmLift",whale.palmLift).apply();
                            choice.dismiss();
                        }).show();
                    return;
                }
                if(index==4){setPalmMode(true);return;}
                if(index==3){if(palmMode)whale.choosePalmPoint();else whale.editArea();return;}
                if(index==2){
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
            });
        }else{
            dialog.setItems(new String[]{"导入模型"},(d,index)->pickLocalModel());
        }
        if(!palmMode)dialog.setNeutralButton("定位信息",(d,which)->{
            String timing="首次定位  "+(firstFix<0?"等待中":firstFix+" ms")+"\n首次可放置  "+(firstPlane<0?"等待中":firstPlane+" ms");
            new AlertDialog.Builder(this).setTitle("定位信息").setMessage("定位  "+fps+" FPS\n角色  "+whale.renderFps+" FPS\n"+timing).setPositiveButton("关闭",null).show();
        });
        dialog.show();
    }

    private void setPalmMode(boolean enabled){
        if(palmMode==enabled)return;
        stopPalm();
        palmMode=enabled;resetAnchor();
        if(enabled){
            whale.startIfAvailable();
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
            else if(whale.palmSide!=null)whale.choosePalmPoint();
            else if(palm!=null && palm.canPlace())whale.evaluateJavascript("whale.placePalmCenter()",null);
        } else if (placed || unavailable) {
            recreate();
        } else if (!"ready".equals(whale.state)) {
            chooseMode();
        } else if (canPlace(SystemClock.elapsedRealtime())) {
            anchorMap = candidateMap;
            placed = true;
            whale.pose(pose, correction);
        } else if (!engine.isConnected()) {
            engine.start();
        }
        updateControls();
    }

    private boolean canPlace(long now) {
        return active && tracked && candidateMap >= 0 && candidateMap == pose.mapId
            && now - lastResult <= 1200;
    }

    private void resetAnchor() {
        sessionStart=SystemClock.elapsedRealtime();firstFix=firstPlane=-1;
        planeRequest=0;candidateMap=-1;
        placed = false; tracked = false; anchorMap = -1;
        lastResult = 0; sampleStart = 0; frames = 0; fps = 0;
        whale.clearPose(true);
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
        if(!placed){
            boolean valid=tracked && pose.drawable && correction!=null && anchorVisible();
            candidateMap=valid?pose.mapId:-1;
            if(valid){
                if(firstPlane<0)firstPlane=now-sessionStart;
            }
            if(tracked && firstFix<0)firstFix=now-sessionStart;
            if(tracked && !valid && now-planeRequest>=1400){
                camera.requestPlaneDetection();planeRequest=now;
            }
        }
        otherMap = placed && tracked && pose.mapId != anchorMap;
        if (!tracked || otherMap || !pose.drawable || correction == null) { losePose(); return; }
        if (recovering) {
            if (lastGoodFrame != pose.frameId) { goodFrames++; lastGoodFrame=pose.frameId; }
            if (goodFrames<3) return;
            recovering=false;
        }
        if (placed && pose.mapId == anchorMap) {
            whale.pose(pose,correction);
        }
    }

    private void losePose() {
        if(placed)recovering=true;
        goodFrames=0;lastGoodFrame=-1;
        whale.clearPose(false);
    }

    @Override protected void onDestroy() {
        ((ViewGroup)whale.getParent()).removeView(whale);
        whale.close();
        super.onDestroy();
    }

    private void updateControls() {
        whale.startIfAvailable();
        long now = SystemClock.elapsedRealtime();
        if (!palmMode && (lastResult == 0 || now - lastResult > 1200)) {
            tracked = false; candidateMap=-1;fps = 0;losePose();
        }
        String label = "放置小鲸鱼", message;
        boolean enabled = false;
        if (!hasCamera()) {
            label = "相机权限"; message = "允许使用后置相机后即可扫描桌面"; enabled = true;
        } else if (unavailable) {
            label = "重试"; message = "相机打开失败，请关闭占用相机的应用后重试"; enabled = true;
        } else if (palmMode) {
            enabled=palm!=null && (palm.failed() || "ready".equals(whale.state) && (whale.palmSide!=null || palm.canPlace()));
            label=palm!=null && palm.failed()?"重试识别":whale.palmSide!=null?"重新选点":"放置小鲸鱼";
            message="error".equals(whale.state)?"角色显示失败，请重新载入":
                !"ready".equals(whale.state)?"正在载入小鲸鱼…":
                palm==null?"正在启动相机…":palm.status();
        } else if (!engine.isConnected()) {
            label = "重连"; message = "正在连接定位引擎"; enabled = active;
        } else if (!"ready".equals(whale.state)) {
            label="选择角色"; enabled=true;
            if(CharacterFile.busy) message="获取小鲸鱼 " + CharacterFile.percent + "%";
            else if("error".equals(whale.state)) message="角色显示失败，点此重试";
            else if(CharacterFile.file(this).isFile()) message="正在载入小鲸鱼…";
            else message=CharacterFile.failure==null?"首次使用，请下载或导入小鲸鱼 V21":CharacterFile.failure;
        } else if (placed) {
            label = "重新扫描"; enabled = true;
            if(otherMap) message="正在另一张地图 · 对准原来的桌面";
            else if(!tracked || recovering) message="正在找回原位 · 对准刚才的桌面";
            else if(whale.beingPetted) message="正在摸头";
            else message="已放置 · 摸头、点尾巴，长按头顶提起";
        } else if (!tracked) {
            message = "正在定位手机 · 对准桌面纹理，缓慢左右平移";
        } else {
            enabled = canPlace(now);
            message = enabled ? "桌面已就绪 · 点放置" : "手机已定位 · 正在找桌面，请对准纹理缓慢横移";
        }
        labs.setText(palmMode?"退出模式":"定位对照");
        if(!CharacterFile.file(this).isFile() && !CharacterFile.busy){
            if(label.equals("放置小鲸鱼"))label="放置方块";
            if(placed && tracked)message="缺少模型 · 点方块导入";
        }
        if(CharacterFile.busy)message="导入模型 "+CharacterFile.percent+"%";
        else if(CharacterFile.failure!=null)message=CharacterFile.failure;
        if (!message.contentEquals(status.getText())) status.setText(message);
        if (!label.contentEquals(action.getText())) action.setText(label);
        action.setEnabled(enabled);
        String mode=palmMode?"掌上 · 小鲸鱼   ▾":"原生 · 小鲸鱼 ▾";
        if(!mode.contentEquals(characterMode.getText()))characterMode.setText(mode);
    }

}
