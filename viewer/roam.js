export const walkSpeed=.465; // Model heights per second
// Points are in the anchor's XZ plane. Camera motion never changes the stored polygon.
const cross=(a,b,p)=>(b.x-a.x)*(p.z-a.z)-(b.z-a.z)*(p.x-a.x);
const distance=(a,b)=>Math.hypot(a.x-b.x,a.z-b.z);
function edgeDistance(p,a,b){
    const x=b.x-a.x,z=b.z-a.z,len=x*x+z*z;
    const t=len?Math.max(0,Math.min(1,((p.x-a.x)*x+(p.z-a.z)*z)/len)):0;
    return Math.hypot(p.x-a.x-x*t,p.z-a.z-z*t);
}
function intersects(a,b,c,d){
    if(Math.max(a.x,b.x)<Math.min(c.x,d.x)||Math.max(c.x,d.x)<Math.min(a.x,b.x)||
       Math.max(a.z,b.z)<Math.min(c.z,d.z)||Math.max(c.z,d.z)<Math.min(a.z,b.z))return false;
    return cross(a,b,c)*cross(a,b,d)<=0 && cross(c,d,a)*cross(c,d,b)<=0;
}
export function contains(poly,p){
    let inside=false;
    for(let i=0,j=poly.length-1;i<poly.length;j=i++){
        const a=poly[i],b=poly[j];
        if((a.z>p.z)!==(b.z>p.z) && p.x<(b.x-a.x)*(p.z-a.z)/(b.z-a.z)+a.x)inside=!inside;
    }
    return inside;
}
export function clearance(poly,p){
    return Math.min(...poly.map((a,i)=>edgeDistance(p,a,poly[(i+1)%poly.length])));
}
export function clearRoute(poly,p,q,margin){
    if(!contains(poly,p)||!contains(poly,q))return false;
    return poly.every((a,i)=>{
        const b=poly[(i+1)%poly.length];
        return !intersects(a,b,p,q) && Math.min(edgeDistance(p,a,b),edgeDistance(q,a,b),
            edgeDistance(a,p,q),edgeDistance(b,p,q))>=margin;
    });
}
export function validate(poly,feet,size){
    const n=poly.length;
    if(n<3)return '范围太小，请重新画一圈';
    let area=0;
    for(let i=0;i<n;i++){
        const a=poly[i],b=poly[(i+1)%n];
        area+=a.x*b.z-b.x*a.z;
        for(let j=i+2;j<n;j++){
            if(i===0 && j===n-1)continue;
            if(intersects(a,b,poly[j],poly[(j+1)%n]))return '边线交叉了，请重新画一圈';
        }
    }
    if(Math.abs(area)<.12*size*size)return '范围太小，请画大一些';
    if(!contains(poly,feet)||clearance(poly,feet)<.02*size)return '请把她的脚下也圈进来';
    return '';
}
export class Roam {
    constructor(){this.clear();}
    pause(seconds=1.6){this.speechPlan=null;this.spoke=false;this.manual=false;this.target=null;this.delay=seconds;this.speed=0;}
    clear(){this.polygon=[];this.phase=0;this.pause();}
    set(points){
        this.polygon=points.slice();
        this.minX=Math.min(...points.map(p=>p.x));this.maxX=Math.max(...points.map(p=>p.x));
        this.minZ=Math.min(...points.map(p=>p.z));this.maxZ=Math.max(...points.map(p=>p.z));
        this.pause(.7);
    }
    choose(p,size){
        const margin=Math.min(.075*size,clearance(this.polygon,p)*.85);
        for(let i=0;i<32;i++){
            const q={x:this.minX+Math.random()*(this.maxX-this.minX),z:this.minZ+Math.random()*(this.maxZ-this.minZ)};
            if(distance(p,q)>.12*size && clearRoute(this.polygon,p,q,margin))return q;
        }
        return null;
    }
    direct(actor,q){
        const p=actor.position, size=actor.scale.x;
        if(!q || distance(p,q)>3*size || distance(p,q)<.025*size)return false;
        if(this.polygon.length && !clearRoute(this.polygon,p,q,.02*size))return false;
        this.speechPlan=null;this.target=q;this.manual=true;this.delay=0;
        return true;
    }
    advance(actor,dt,blocked){
        this.speed=0;this.turn=null;
        if((!this.polygon.length && !this.manual) || blocked || dt<=0)return;
        const p=actor.position,size=actor.scale.x;
        if(this.polygon.length && !contains(this.polygon,p))return;
        this.delay-=dt;
        if(this.delay>0)return;
        if(!this.target){
            this.target=this.choose(p,size);
            if(!this.target){this.delay=.8;return;}
        }
        const dx=this.target.x-p.x,dz=this.target.z-p.z,remaining=Math.hypot(dx,dz);
        if(remaining<.006*size){
            const say=!this.spoke && this.speechPlan?.event==='arrived';
            this.pause(1.5+Math.random()*3);
            if(say)return 'arrived';
            return;
        }
        const diff=Math.atan2(Math.sin(Math.atan2(dx,dz)-actor.rotation.y),Math.cos(Math.atan2(dx,dz)-actor.rotation.y));
        actor.rotation.y+=Math.sign(diff)*Math.min(Math.abs(diff),dt*2.9);
        if(Math.abs(diff)>.22){this.turn=diff>0?'locomotion_left':'locomotion_right';return;}
        this.speed=Math.min(1,remaining/(.13*size)+.12);
        const step=Math.min(remaining,dt*walkSpeed*size*this.speed);
        p.x+=dx/remaining*step;p.z+=dz/remaining*step;
        this.phase+=step/size*23;
        if(this.spoke)return;
        if(!this.speechPlan){
            const options=['walking','walking','nearGoal','arrived'];
            if(this.manual)options.push('directed');
            if(remaining>size*.65)options.push('longWalk');
            this.speechPlan={event:options[Math.floor(Math.random()*options.length)],length:remaining};
        }
        const {event,length}=this.speechPlan;
        if(event==='arrived' || event==='nearGoal' && remaining>length*.22 ||
           event==='longWalk' && remaining>length*.5)return;
        this.spoke=true;
        return event;
    }
}
