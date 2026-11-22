import * as THREE from 'three';


// The carried pose is temporary. Only releasing over a verified floor target commits it.
export class Pickup {
    constructor(anchor,camera,actor,roam,area){
        Object.assign(this,{anchor,camera,actor,roam,area});
        this.tip=document.getElementById('pickup-tip');
        this.point=new THREE.Vector3();
        this.marker=new THREE.Mesh(new THREE.RingGeometry(.10,.14,32),
            new THREE.MeshBasicMaterial({color:0x71ffd3,side:THREE.DoubleSide,depthTest:false}));
        this.marker.rotation.x=-Math.PI/2;
        this.marker.renderOrder=5;this.marker.visible=false;
        anchor.add(this.marker);
    }
    begin(g,now){
        this.source=this.actor.position.clone();
        const feet=this.point.copy(this.source).applyMatrix4(this.anchor.matrixWorld).project(this.camera);
        this.offsetX=(1+feet.x)*innerWidth/2-g.x;
        this.offsetY=(1-feet.y)*innerHeight/2-g.y;
        this.since=now;this.tip.hidden=false;
    }
    move(g,now){
        if(!this.source)return;
        const {actor,marker}=this,size=actor.scale.x;
        const x=g.x+this.offsetX,y=g.y+this.offsetY;
        const visible=g.x>=0 && g.x<=innerWidth && g.y>=0 && g.y<=innerHeight &&
            x>=0 && x<=innerWidth && y>=0 && y<=innerHeight;
        this.target=visible?this.area.project(x,y):null;
        marker.visible=!!this.target;
        if(this.target){
            const {x,z}=this.target;
            actor.position.set(x,.23*size*Math.min(1,(now-this.since)/160),z);
            marker.position.set(x,.006*size,z);marker.scale.setScalar(size);
        }
        const message=this.target?'松手放在绿圈处':'请把落点移回可见的桌面';
        if(this.tip.textContent!==message)this.tip.textContent=message;
    }
    end(commit=false){
        if(!this.source)return;
        this.actor.position.copy(this.source);
        if(commit && this.target)this.actor.position.set(this.target.x,0,this.target.z);
        this.source=this.target=null;
        this.marker.visible=false;this.tip.hidden=true;this.roam.pause(2.5);
    }
}
