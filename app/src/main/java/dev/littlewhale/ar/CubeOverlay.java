package dev.littlewhale.ar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.opengl.Matrix;
import android.view.View;

import com.menthaar.sdk.MenthaAR;
import com.orb.slam2s.graphics.DisplayCorrection;

/** Eight world-space vertices. All perspective and rotation come from the tracking camera. */
final class CubeOverlay extends View {
    private static final int[][] FACES = {
        {0,1,3,2}, {4,5,7,6}, {0,1,5,4}, {2,3,7,6}, {0,2,6,4}, {1,3,7,5}
    };
    private static final int[] COLORS = {
        0xff3473a1, 0xffedb572, 0xff255b76, 0xffb2e7e6, 0xff4fa7ab, 0xff817fc2
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] mv = new float[16], mvp = new float[16], display = new float[16];
    private final float[] vertex = {0,0,0,1}, clip = new float[4];
    private final float[] x = new float[8], y = new float[8], depth = new float[8];
    private final float[] faceDepth = new float[6];
    private final int[] order = new int[6];
    private boolean visible;
    private float side;

    CubeOverlay(Context context) {
        super(context);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    void clear(boolean resetSize) {
        if (resetSize) side = 0;
        if (visible) { visible = false; invalidate(); }
    }

    void update(MenthaAR.Result result, DisplayCorrection correction) {
        Matrix.multiplyMM(mv, 0, result.view, 0, result.model, 0);
        Matrix.multiplyMM(mvp, 0, result.projection, 0, mv, 0);
        Matrix.multiplyMM(display, 0, correction.getNdcTransform(), 0, mvp, 0);
        // Monocular SLAM has arbitrary map units. Scale once at placement, then keep it fixed.
        if (side == 0) {
            float distance = -mv[14];
            if (!(distance > 0)) return;
            side = distance * .16f;
        }
        visible = true;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (!visible) return;
        for (int i = 0; i < 8; i++) {
            vertex[0] = ((i & 1) - .5f) * side;
            // MenthaAR uses RDF. Negative local Y extends out of a horizontal plane.
            vertex[1] = -((i >> 1) & 1) * side;
            vertex[2] = (((i >> 2) & 1) - .5f) * side;
            Matrix.multiplyMV(clip, 0, display, 0, vertex, 0);
            if (!(clip[3] > .0001f)) return;
            x[i] = (.5f + .5f * clip[0] / clip[3]) * getWidth();
            y[i] = (.5f - .5f * clip[1] / clip[3]) * getHeight();
            depth[i] = clip[3];
        }
        for (int f = 0; f < FACES.length; f++) {
            int[] vertices = FACES[f];
            faceDepth[f] = depth[vertices[0]] + depth[vertices[1]] + depth[vertices[2]] + depth[vertices[3]];
            int j = f;
            while (j > 0 && faceDepth[order[j-1]] < faceDepth[f]) {
                order[j] = order[j-1];
                j--;
            }
            order[j] = f;
        }
        for (int face : order) {
            int[] corners = FACES[face];
            path.rewind();
            path.moveTo(x[corners[0]], y[corners[0]]);
            for (int i = 1; i < 4; i++) path.lineTo(x[corners[i]], y[corners[i]]);
            path.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(COLORS[face]);
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0xff214d68);
            paint.setStrokeWidth(getResources().getDisplayMetrics().density);
            canvas.drawPath(path, paint);
        }
    }
}
