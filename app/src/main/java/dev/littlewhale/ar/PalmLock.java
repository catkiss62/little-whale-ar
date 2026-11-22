package dev.littlewhale.ar;

/** Positions and spans refer to the upright, displayed camera image. */
final class PalmLock {
    static final long HOLD_MS=335;
    static final class Hand {
        final float x,y,width;final String side;final float[] outline,axes;
        Hand(float x,float y,float width,String side,float[] outline,float[] axes){this.axes=axes;this.x=x;this.y=y;this.width=width;this.side=side;this.outline=outline;}
    }
    private long seen;
    private int samples;
    String side;
    final float[] axes=new float[6],outline=new float[12];
    boolean visible;
    float x,y,width;
    void clear(){visible=false;samples=0;side=null;}
    static Hand measure(float[][] p,float[][] world,String side,float aspect){
        float ax=p[5][0]-p[0][0],ay=(p[5][1]-p[0][1])*aspect;
        float bx=p[17][0]-p[0][0],by=(p[17][1]-p[0][1])*aspect;
        float breadth=(float)Math.hypot(ax-bx,ay-by);
        float length=(float)Math.hypot(p[9][0]-p[0][0],(p[9][1]-p[0][1])*aspect);
        float span=Math.max(breadth,length*.78f);
        // In the detector's image coordinates a camera-facing right-labelled palm winds clockwise
        // from little-finger base to index base. Mirroring swaps both handedness and winding.
        float facing=(ax*by-ay*bx)*("Right".equals(side)?-1:1);
        if(span<.045f || facing<.105f*span*span)return null;
        int open=0;
        for(int base=5;base<=17;base+=4){
            float[] m=world[base],joint=world[base+1],tip=world[base+3];
            float dot=0,u2=0,v2=0;
            for(int k=0;k<3;k++){
                float u=joint[k]-m[k],v=tip[k]-joint[k];
                dot+=u*v;u2+=u*u;v2+=v*v;
            }
            if(dot>.22*Math.sqrt(u2*v2))open++;
        }
        if(open<3)return null;
        float x=p[0][0]*.38f,y=p[0][1]*.38f;
        for(int i=5;i<=17;i+=4){x+=p[i][0]*.155f;y+=p[i][1]*.155f;}
        // Six vertices in anatomical order, including the width of the palm heel.
        float[] outline=new float[12];
        for(int axis=0;axis<2;axis++){
            float heel=(p[5][axis]-p[17][axis])*.25f;
            outline[axis]=p[0][axis]+heel;
            outline[10+axis]=p[0][axis]-heel;
            for(int finger=0;finger<4;finger++)outline[2+finger*2+axis]=p[5+finger*4][axis];
        }
        // Fit image-plane metres to the upright preview with the wrist and four knuckles.
        // Use observed X/Y for alignment and the metric model for the out-of-plane component.
        double image2=0,metric2=0;
        for(int i=5;i<=17;i+=4){
            double dx=p[i][0]-p[0][0],dy=(p[i][1]-p[0][1])*aspect;
            double mx=world[i][0]-world[0][0],my=world[i][1]-world[0][1];
            image2+=dx*dx+dy*dy;metric2+=mx*mx+my*my;
        }
        if(!(metric2>1e-7 && image2>1e-7))return null;
        float scale=(float)Math.sqrt(image2/metric2);
        float[] axes={ax-bx,by-ay,-scale*(world[5][2]-world[17][2]),0,0,0};
        for(int i=5;i<=17;i+=4){
            axes[3]+=(p[i][0]-p[0][0])*.25f;
            axes[4]-=(p[i][1]-p[0][1])*aspect*.25f;
            axes[5]-=(world[i][2]-world[0][2])*scale*.25f;
        }
        float broad=(float)Math.hypot(breadth,axes[2]);
        float reach=(float)Math.hypot(length,scale*(world[9][2]-world[0][2]));
        float fullSpan=Math.max(broad,reach*.78f);
        if(!Float.isFinite(fullSpan) || !Float.isFinite(axes[5]))return null;
        return new Hand(x,y,fullSpan,side,outline,axes);
    }
    boolean accept(Hand[] hands,long time,String preferred){
        if(!visible && samples>0 && time-seen>1000)clear();
        Hand best=null;double score=Double.POSITIVE_INFINITY;
        for(Hand h:hands){
            if(h==null || (preferred!=null && !preferred.equals(h.side)))continue;
            double d=Math.hypot(h.x-x,h.y-y);
            if(samples>0 && !h.side.equals(side))continue;
            double priority=samples==0?-h.width:d;
            if(priority<score){best=h;score=priority;}
        }
        if(best==null){if(!visible)clear();return false;}
        if(samples==0 || score>Math.max(.17,width*1.5)){
            System.arraycopy(best.outline,0,outline,0,outline.length);
            x=best.x;y=best.y;width=best.width;side=best.side;
        }else{
            double motion=Math.hypot(best.x-x,best.y-y)/Math.max(.05,width);
            double dt=Math.min(.18,(time-seen)/1000d);
            // Rotation can move the selected finger root while the palm center stays still.
            for(int i=0;i<outline.length;i+=2)
                motion=Math.max(motion,Math.hypot(best.outline[i]-outline[i],best.outline[i+1]-outline[i+1])/Math.max(.05,width));
            float alpha=(float)-Math.expm1(-dt*(9+48*Math.min(1,motion*4)));
            for(int i=0;i<outline.length;i++)outline[i]+=(best.outline[i]-outline[i])*alpha;
            x+=(best.x-x)*alpha;y+=(best.y-y)*alpha;
            width+=(best.width-width)*(float)-Math.expm1(-dt*9);
        }
        System.arraycopy(best.axes,0,axes,0,axes.length);
        seen=time;samples=Math.min(2,samples+1);
        visible=samples>=2;
        return visible;
    }
}
