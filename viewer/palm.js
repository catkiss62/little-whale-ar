// Store the tap in a triangle of anatomical landmarks. Reconstruct it from the
// current hand every frame, including when the palm turns or changes apparent size.
export class PalmSurface {
    constructor(){
        this.guide=document.getElementById('palm-outline');
        this.polygon=this.guide.querySelector('polygon');
        this.reset();
    }
    lose(){
        this.frame=null;this.guide.hidden=true;
    }
    reset(){
        this.site=null;this.lose();
    }
    update(points,width,side){
        if(this.site && side!==this.site.side){this.lose();return null;}
        let cx=0,cy=0;
        for(let i=0;i<points.length;i+=2){cx+=points[i]/6;cy+=points[i+1]/6;}
        this.frame={points,cx,cy,width,side,time:performance.now()};
        this.guide.hidden=!!this.site;
        if(!this.site){
            const vertices=[];
            for(let i=0;i<points.length;i+=2)vertices.push(points[i]*1000+','+points[i+1]*1000);
            this.polygon.setAttribute('points',vertices.join(' '));
        }
        return this.foot();
    }
    pick(x,y){
        if(!this.frame || this.site || performance.now()-this.frame.time>600)return null;
        const {points,cx,cy,side}=this.frame;
        for(let i=0;i<points.length;i+=2){
            const j=(i+2)%points.length;
            const ax=points[i]-cx,ay=points[i+1]-cy,bx=points[j]-cx,by=points[j+1]-cy;
            const det=ax*by-ay*bx;
            if(Math.abs(det)<1e-7)continue;
            const u=((x-cx)*by-(y-cy)*bx)/det,v=(ax*(y-cy)-ay*(x-cx))/det;
            if(u<0 || v<0 || u+v>1)continue;
            this.site={i,j,u,v,side};
            this.guide.hidden=true;
            return this.foot();
        }
        return null;
    }
    foot(){
        if(!this.site || !this.frame)return null;
        const {points,cx,cy,width}=this.frame,{i,j,u,v}=this.site;
        return [cx*(1-u-v)+points[i]*u+points[j]*v,cy*(1-u-v)+points[i+1]*u+points[j+1]*v,width];
    }
}
