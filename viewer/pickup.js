import * as THREE from 'three';


// The carried pose is temporary. Only releasing over a verified floor target commits it.
export class Pickup {
    constructor(anchor,camera,actor,roam,area){
        Object.assign(this,{anchor,camera,actor,roam,area});
        this.tip=document.getElementById('pickup-tip');
        this.point=new THREE.Vector3();
        this.vertices=Array.from({length:3},()=>new THREE.Vector3());
        this.grip=new THREE.Vector3();this.desired=new THREE.Vector3();
        this.marker=new THREE.Mesh(new THREE.RingGeometry(.10,.14,32),
            new THREE.MeshBasicMaterial({color:0x71ffd3,side:THREE.DoubleSide,depthTest:false}));
        this.marker.rotation.x=-Math.PI/2;
        this.marker.renderOrder=5;this.marker.visible=false;
        this.ripple=new THREE.Mesh(new THREE.RingGeometry(.086,.095,48),new THREE.MeshBasicMaterial({
            color:0x9feaff,transparent:true,depthTest:false,depthWrite:false,side:THREE.DoubleSide
        }));
        this.marker.add(this.ripple);
        anchor.add(this.marker);
    }
    updateRipple(now){
        if(!this.marker.visible)return;
        const t=((now-this.since)%900)/900;
        this.ripple.scale.setScalar(.25+.8*t);
        this.ripple.material.opacity=.9*(1-t);

    }
    tailContact(hit){
        const mesh=hit.object,face=hit.face;
        if(!face)return null;
        const indices=[face.a,face.b,face.c];
        const vertices=indices.map(i=>mesh.getVertexPosition(i,new THREE.Vector3()));
        const local=mesh.worldToLocal(hit.point.clone());
        const weights=THREE.Triangle.getBarycoord(local,...vertices,new THREE.Vector3());
        if(!weights)return null;
        return {mesh,indices,weights:weights.toArray(),depth:hit.point.clone().project(this.camera).z};
    }
    followTail(g){
        const c=g.tailContact;
        c.mesh.updateWorldMatrix(true,false);
        if(c.mesh.isSkinnedMesh)for(const bone of c.mesh.skeleton.bones)bone.updateWorldMatrix(true,false);
        this.grip.set(0,0,0);
        for(let i=0;i<3;i++)this.grip.addScaledVector(c.mesh.getVertexPosition(c.indices[i],this.vertices[i]),c.weights[i]);
        this.anchor.worldToLocal(c.mesh.localToWorld(this.grip));
        this.desired.set(2*g.x/innerWidth-1,1-2*g.y/innerHeight,c.depth).unproject(this.camera);
        this.actor.position.add(this.anchor.worldToLocal(this.desired).sub(this.grip));
        const {x,z}=this.actor.position;
        this.target=g.x>=0 && g.x<=innerWidth && g.y>=0 && g.y<=innerHeight ? {x,z}:null;
        this.marker.visible=!!this.target;
        this.marker.position.set(x,.006*this.actor.scale.x,z);
        this.marker.scale.setScalar(this.actor.scale.x);
        this.tip.textContent='拖动尾鳍 · 松手落在绿圈';
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
        if(g.tailContact){this.followTail(g);return;}
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
