package dev.littlewhale.ar;

/** Positions and spans refer to the upright, displayed camera image. */
final class PalmLock {
    static final long HOLD_MS=335;
    static final class Hand {
        final float x,y,width;final String side;
        Hand(float x,float y,float width,String side){this.x=x;this.y=y;this.width=width;this.side=side;}
    }
    private long seen;
    private int samples;
    private String side;
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
        return new Hand(x,y,span,side);
    }
    boolean accept(Hand[] hands,long time){
        if(!visible && samples>0 && time-seen>1000)clear();
        Hand best=null;double score=Double.POSITIVE_INFINITY;
        for(Hand h:hands){
            if(h==null)continue;
            double d=Math.hypot(h.x-x,h.y-y);
            if(samples>0 && (!h.side.equals(side)||d>Math.max(.14,width*1.5)))continue;
            double priority=samples==0?-h.width:d;
            if(priority<score){best=h;score=priority;}
        }
        if(best==null){if(!visible)clear();return false;}
        if(samples==0){
            x=best.x;y=best.y;width=best.width;side=best.side;
        }else{
            double motion=Math.hypot(best.x-x,best.y-y)/Math.max(.05,width);
            double dt=Math.min(.18,(time-seen)/1000d);
            float alpha=(float)-Math.expm1(-dt*(6+32*Math.min(1,motion*3)));
            x+=(best.x-x)*alpha;y+=(best.y-y)*alpha;
            width+=(best.width-width)*(float)-Math.expm1(-dt*9);
        }
        seen=time;samples=Math.min(2,samples+1);
        visible=samples>=2;
        return visible;
    }
}
