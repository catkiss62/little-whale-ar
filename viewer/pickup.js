import * as THREE from 'three';
import {contains,clearance} from './roam.js';

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
        this.depth=g.grab.clone().applyMatrix4(this.anchor.matrixWorld).project(this.camera).z;
        this.gripOffset=g.grab.clone().sub(this.source);
        this.since=now;this.tip.hidden=false;
    }
    move(g,now){
        if(!this.source)return;
        const {actor,anchor,camera,marker}=this,size=actor.scale.x;
        const rise=.23*size*Math.min(1,(now-this.since)/160);
        this.point.set(2*g.x/innerWidth-1,1-2*g.y/innerHeight,this.depth).unproject(camera);
        anchor.worldToLocal(this.point);
        actor.position.copy(this.point).sub(this.gripOffset);
        actor.position.y=Math.max(0,actor.position.y)+rise;
        // Validate the floor directly below the suspended feet along the table normal.
        this.point.set(actor.position.x,0,actor.position.z).applyMatrix4(anchor.matrixWorld).project(camera);
        const onScreen=Math.abs(this.point.x)<=1 && Math.abs(this.point.y)<=1 && Math.abs(this.point.z)<1 &&
            g.x>=0 && g.x<=innerWidth && g.y>=0 && g.y<=innerHeight;
        const floor=onScreen?this.area.project((1+this.point.x)*innerWidth/2,(1-this.point.y)*innerHeight/2):null;
        const poly=this.roam.polygon;
        this.target=floor && contains(poly,floor) && clearance(poly,floor)>=.11*size ? floor:null;
        marker.visible=!!this.target;
        if(this.target){marker.position.set(floor.x,.006*size,floor.z);marker.scale.setScalar(size);}
        const message=this.target?'松手放在绿圈处':poly.length?'移到活动区内，松手可放下':'先画活动范围才能换位置，松手回原位';
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
