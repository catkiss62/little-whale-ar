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
        this.target={x:this.source.x,z:this.source.z};
        this.since=now;this.tip.hidden=false;
        this.vertical(g);
        this.liftStart=true;
    }
    vertical(g){
        this.baseHeight=this.actor.position.y;
        this.baseY=g.y;
        this.liftStart=false;
    }
    plane(g){
        const feet=this.point.set(this.actor.position.x,0,this.actor.position.z);
        this.anchor.updateWorldMatrix(true,false);
        feet.applyMatrix4(this.anchor.matrixWorld).project(this.camera);
        this.offsetX=(1+feet.x)*innerWidth/2-(g.x+g.second.x)/2;
        this.offsetY=(1-feet.y)*innerHeight/2-(g.y+g.second.y)/2;
    }
    move(g,now){
        if(!this.source)return;
        const {actor,marker}=this,size=actor.scale.x;
        if(g.second){
            const x=(g.x+g.second.x)/2+this.offsetX,y=(g.y+g.second.y)/2+this.offsetY;
            this.target=x>=0 && x<=innerWidth && y>=0 && y<=innerHeight?this.area.project(x,y):null;
            if(this.target){actor.position.x=this.target.x;actor.position.z=this.target.z;}
        }else{
            const rise=this.liftStart ? .18*size*Math.min(1,(now-this.since)/140):0;
            const dy=(this.baseY-g.y)*size/Math.max(36,g.ry*3.2);
            actor.position.y=Math.min(3*size,Math.max(0,this.baseHeight+rise+dy));
        }
        marker.visible=!!this.target;
        if(this.target){
            marker.position.set(this.target.x,.006*size,this.target.z);marker.scale.setScalar(size);
        }
        const message=!this.target?'双指移回可见桌面':g.second?'双指平移 · 主指松手放在绿圈处':'上下拖动调高度 · 加一指平移 · 松手落在绿圈';
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
