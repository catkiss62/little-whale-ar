package dev.littlewhale.ar;

import android.graphics.Matrix;

/** Reference coordinates run right/down on the printed page, with its width as one unit. */
final class ReferencePlane {
    private final Matrix page = new Matrix(), inverse = new Matrix();
    private final float aspect, focal, cx, cy;
    private final float[] h = new float[9];

    ReferencePlane(float[] corners, float aspect, int width, int height, float focalPixels) {
        this.aspect = aspect;
        focal = focalPixels;
        cx = width * .5f;
        cy = height * .5f;
        float[] paper = {0,0, 1,0, 1,aspect, 0,aspect};
        if (!page.setPolyToPoly(paper,0,corners,0,4) || !page.invert(inverse))
            throw new IllegalArgumentException("degenerate reference");
        page.getValues(h);
    }
    float[] pick(float x, float y) {
        float[] uv = {x,y};
        inverse.mapPoints(uv);
        return uv[0]>=0 && uv[0]<=1 && uv[1]>=0 && uv[1]<=aspect ? uv : null;
    }
    private static float length(float[] v) {
        return (float)Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);
    }
    float[] model(float[] uv) {
        float[] right = {(h[0]-cx*h[6])/focal, -(h[3]-cy*h[6])/focal, -h[6]};
        float[] down = {(h[1]-cx*h[7])/focal, -(h[4]-cy*h[7])/focal, -h[7]};
        float a=length(right), b=length(down);
        if (!(a>0 && b>0)) return null;
        for(int i=0;i<3;i++) right[i]/=a;
        float dot=right[0]*down[0]+right[1]*down[1]+right[2]*down[2];
        for(int i=0;i<3;i++) down[i]-=dot*right[i];
        float c=length(down);
        if (!(c>b*.15f)) return null;
        for(int i=0;i<3;i++) down[i]/=c;
        float[] up={down[1]*right[2]-down[2]*right[1],down[2]*right[0]-down[0]*right[2],down[0]*right[1]-down[1]*right[0]};
        float depth=(h[6]*uv[0]+h[7]*uv[1]+h[8])*2/(a+b);
        if (!(depth>.02f && Float.isFinite(depth))) return null;
        float[] pixel=uv.clone(); page.mapPoints(pixel);
        float[] m=new float[16];
        for(int i=0;i<3;i++){m[i]=right[i]*.62f;m[4+i]=up[i]*.62f;m[8+i]=down[i]*.62f;}
        m[12]=(pixel[0]-cx)*depth/focal;m[13]=(cy-pixel[1])*depth/focal;m[14]=-depth;m[15]=1;
        return m;
    }
}
