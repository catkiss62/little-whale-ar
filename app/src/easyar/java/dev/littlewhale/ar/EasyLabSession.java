package dev.littlewhale.ar;

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import java.nio.ByteBuffer;
import java.util.List;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import cn.easyar.*;
import cn.easyar.samples.helloarmotiontracking.BGRenderer;
import cn.easyar.samples.helloarmotiontracking.BoxRenderer;

/** SDK frames, camera background and cube all use the same projection and frame. */
final class EasyLabSession implements GLSurfaceView.Renderer {
    private final EasyLabActivity activity;
    private final boolean surfaceMode;
    private final boolean character;
    private final float[] model = new float[16], lens = new float[16];
    private float characterHeight;
    private long lastCameraFrame;
    private volatile long started;
    private OutputFrameBuffer output;
    private InputFrameThrottler throttler;
    private InputFrameToOutputFrameAdapter adapter;
    private MotionTrackerCameraDevice motion;
    private CameraDevice camera;
    private SurfaceTracker surface;
    private BGRenderer background;
    private BoxRenderer cube;
    private int width, height, lastIndex = -1;
    private ByteBuffer pixels;
    private Vec3F anchor;
    private long first = -1;
    private String status = "";
    private boolean running;
    private final float[] inverse = new float[16];
    private final Vec3F size;
    private final Matrix44F view = new Matrix44F();

    EasyLabSession(EasyLabActivity activity, boolean surfaceMode, boolean character, long started) {
        this.activity = activity;
        this.surfaceMode = surfaceMode;
        this.character = character;
        this.started = started;
        float side = surfaceMode ? .35f : .08f;
        view.data = new float[16];
        size = new Vec3F(side, side, side);
    }
    void resetClock(long now) { started = now; }

    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        if (output != null) close();
        first = -1;
        lastIndex = -1;
        anchor = null;
        characterHeight = 0;
        lastCameraFrame = 0;
        status = "";
        try {
            background = new BGRenderer();
            if (!character) cube = new BoxRenderer();
            output = OutputFrameBuffer.create();
            if (surfaceMode) {
                camera = CameraDeviceSelector.createCameraDevice(CameraDevicePreference.PreferSurfaceTracking);
                if (!camera.openWithPreferredType(CameraDeviceType.Back)) throw new IllegalStateException("后置相机打开失败");
                camera.setFocusMode(CameraDeviceFocusMode.Continousauto);
                surface = SurfaceTracker.create();
                throttler = InputFrameThrottler.create();
                camera.inputFrameSource().connect(throttler.input());
                throttler.output().connect(surface.inputFrameSink());
                surface.outputFrameSource().connect(output.input());
                output.signalOutput().connect(throttler.signalInput());
                camera.setBufferCapacity(throttler.bufferRequirement() + surface.bufferRequirement() + output.bufferRequirement() + 2);
                running = surface.start() && camera.start();
            } else {
                motion = new MotionTrackerCameraDevice();
                adapter = InputFrameToOutputFrameAdapter.create();
                motion.inputFrameSource().connect(adapter.input());
                adapter.output().connect(output.input());
                motion.setBufferCapacity(output.bufferRequirement() + 2);
                running = motion.start();
            }
            if (!running) throw new IllegalStateException("定位相机启动失败");
        } catch (RuntimeException | LinkageError e) {
            activity.report("启动失败 · " + e.getMessage());
            close();
        }
    }

    public void onSurfaceChanged(GL10 gl, int w, int h) {
        width = w; height = h;
        GLES30.glViewport(0, 0, w, h);
    }

    public void onDrawFrame(GL10 gl) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        if (!running || height == 0) return;
        OutputFrame frame = output.peek();
        long now = SystemClock.elapsedRealtime();
        if (character && now - lastCameraFrame > 600) activity.frame(null, null);
        if (frame == null) return;
        InputFrame input = null;
        CameraParameters params = null;
        Image image = null;
        try {
            input = frame.inputFrame();
            if (input == null) return;
            params = input.cameraParameters();
            if (params == null) return;
            float aspect = (float)width / height;
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation() * 90;
            if (lastIndex != input.index()) {
                image = input.image();
                if (image == null) return;
                Buffer buf = image.buffer();
                try {
                    if (pixels == null || pixels.capacity() != buf.size()) pixels = ByteBuffer.allocate(buf.size());
                    pixels.clear();
                    buf.copyToByteArray(pixels.array());
                    background.upload(image.format(), image.width(), image.height(), image.pixelWidth(), image.pixelHeight(), pixels);
                } finally { buf.dispose(); }
                lastIndex = input.index();
                lastCameraFrame = now;
            }
            background.render(params.imageProjection(aspect, rotation, true, false));
            if (character && now - lastCameraFrame > 600) return;
            Matrix44F pose = null;
            if (surfaceMode) {
                for (FrameFilterResult result : frame.results()) {
                    if (result instanceof SurfaceTrackerResult) pose = ((SurfaceTrackerResult)result).transform();
                    if (result != null) result.dispose();
                }
            } else if (input.trackingStatus() == MotionTrackingStatus.Tracking) {
                if (anchor == null) {
                    Vec2F center = params.imageCoordinatesFromScreenCoordinates(aspect, rotation, true, false, new Vec2F(.5f, .5f));
                    List<Vec3F> hits = motion.hitTestAgainstHorizontalPlane(center);
                    if (!hits.isEmpty()) anchor = hits.get(0);
                }
                if (anchor != null) pose = input.cameraTransform();
            }
            if (pose != null && Matrix.invertM(inverse, 0, pose.data, 0)) {
                // Both SDK matrices are row-major. Invert via the transpose identity,
                // then multiply the view by a world-space translation on its right.
                System.arraycopy(inverse, 0, view.data, 0, 16);
                float x = surfaceMode ? 0 : anchor.data[0];
                float y = (surfaceMode ? 0 : anchor.data[1]) + (character ? 0 : size.data[1] / 2);
                float z = surfaceMode ? 0 : anchor.data[2];
                for (int i = 0; i < 16; i += 4)
                    view.data[i+3] += inverse[i]*x + inverse[i+1]*y + inverse[i+2]*z;
                Matrix44F projection = params.projection(.01f, 500, aspect, rotation, true, false);
                if (character) {
                    Matrix.transposeM(model, 0, view.data, 0);
                    Matrix.transposeM(lens, 0, projection.data, 0);
                    if (characterHeight == 0 && model[14] < 0) characterHeight = -model[14] * .34f;
                    if (characterHeight <= 0) { activity.frame(null, null); return; }
                    Matrix.scaleM(model, 0, characterHeight, characterHeight, characterHeight);
                    activity.frame(model, lens);
                } else cube.render(projection, view, size);
                if (first < 0) first = SystemClock.elapsedRealtime() - started;
                report("首次可放置 " + first + (character ? " ms · 横移观察脚底" : " ms · 横移检查底角"));
            } else {
                if (character) activity.frame(null, null);
                report(first < 0 ? "对准桌面，缓慢横移" : "跟踪中断 · 首次可放置 " + first + " ms");
            }
        } catch (RuntimeException e) {
            activity.report("跟踪错误 · " + e.getMessage());
            close();
        } finally {
            if (image != null) image.dispose();
            if (params != null) params.dispose();
            if (input != null) input.dispose();
            frame.dispose();
        }
    }

    private void report(String text) {
        if (!text.equals(status)) { status = text; activity.report(text); }
    }

    void close() {
        running = false;
        if (character) activity.frame(null, null);
        if (motion != null) { motion.stop(); motion.close(); motion.dispose(); motion = null; }
        if (camera != null) { camera.stop(); camera.close(); camera.dispose(); camera = null; }
        if (surface != null) { surface.stop(); surface.dispose(); surface = null; }
        if (throttler != null) { throttler.dispose(); throttler = null; }
        if (adapter != null) { adapter.dispose(); adapter = null; }
        if (output != null) { output.dispose(); output = null; }
        if (cube != null) { cube.dispose(); cube = null; }
        if (background != null) { background.dispose(); background = null; }
    }
}
