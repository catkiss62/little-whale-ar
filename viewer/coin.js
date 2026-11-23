import * as THREE from 'three';
import {contains,clearRoute} from './roam.js';

const ease=t=>{t=Math.min(1,Math.max(0,t));return t*t*(3-2*t);};
export class Coin {
    constructor(anchor,actor,camera,area,roam){
        Object.assign(this,{anchor,actor,camera,area,roam});
        this.button=document.querySelector('#coin-button');
        this.button.onclick=()=>{if(this.state)this.clear();else this.setState('placing');};
        this.mesh=new THREE.Mesh(new THREE.CylinderGeometry(1,1,.16,32),
            new THREE.MeshBasicMaterial({color:0xffc653}));
        const stamp=new THREE.Mesh(new THREE.TorusGeometry(.72,.04,6,32),
            new THREE.MeshBasicMaterial({color:0xab7227}));
        stamp.rotation.x=-Math.PI/2;stamp.position.y=.085;this.mesh.add(stamp);
        anchor.add(this.mesh);this.mesh.visible=false;
        this.point=new THREE.Vector3();this.target=new THREE.Vector3();this.goal=new THREE.Vector3();
        this.start=new THREE.Vector3();this.floor=new THREE.Vector3();
        this.left=new THREE.Vector3();this.right=new THREE.Vector3();
        this.from=new THREE.Vector3();this.to=new THREE.Vector3();this.origin=new THREE.Vector3();
        this.q=new THREE.Quaternion();this.parentQ=new THREE.Quaternion();
        this.clear();
    }
    setState(state){
        this.state=state;
        this.button.textContent=state==='placing'?'点桌面放硬币 · 取消':state?'收回硬币':'放硬币';
    }
    clear(){
        if(this.state)this.roam.pause();
        this.setState(null);this.mesh.visible=false;
    }
    place(x,y){
        const p=this.area.project(x,y),actor=this.actor,scale=actor.scale.x;
        if(!p || Math.hypot(actor.position.x-p.x,actor.position.z-p.z)>3*scale ||
            (this.roam.polygon.length && (!contains(this.roam.polygon,p) ||
             !clearRoute(this.roam.polygon,actor.position,p,.02*scale)))){
            this.button.textContent='请点附近可走的桌面 · 取消';return;
        }
        this.start.copy(actor.position);
        this.radius=.115*scale;
        this.floor.set(p.x,this.radius*.08,p.z);
        this.mesh.position.copy(this.floor);this.mesh.rotation.set(0,0,0);
        this.mesh.scale.setScalar(this.radius);this.mesh.visible=true;
        this.goal.copy(this.start).sub(this.floor);this.goal.y=0;
        if(this.goal.lengthSq()<.0001)this.goal.set(-Math.sin(actor.rotation.y),0,-Math.cos(actor.rotation.y));
        this.goal.setLength(Math.min(.22*scale,this.start.distanceTo(this.floor))).add(this.floor);this.goal.y=0;
        this.roam.pause();this.setState('approach');
    }
    step(now,dt){
        const {actor,roam}=this;
        roam.speed=0;roam.turn=null;
        if(!['approach','return','lift','hold'].includes(this.state))return;
        if(this.state==='lift'){
            if(now-this.since>=1200)this.setState('return');
            return;
        }
        if(this.state==='hold')return;
        const goal=this.state==='return'?this.start:this.goal;
        const dx=goal.x-actor.position.x,dz=goal.z-actor.position.z,d=Math.hypot(dx,dz);
        const reached=d<.009*actor.scale.x;
        const dir=reached && this.state==='approach'?
            Math.atan2(this.floor.x-actor.position.x,this.floor.z-actor.position.z):Math.atan2(dx,dz);
        const diff=Math.atan2(Math.sin(dir-actor.rotation.y),Math.cos(dir-actor.rotation.y));
        if(!reached || this.state==='approach'){
            actor.rotation.y+=Math.sign(diff)*Math.min(Math.abs(diff),dt*2.9);
            if(Math.abs(diff)>.22){roam.turn=diff>0?'locomotion_left':'locomotion_right';return;}
        }
        if(reached){this.setState(this.state==='return'?'hold':'lift');this.since=now;return;}
        roam.speed=Math.min(1,d/(.13*actor.scale.x)+.12);
        const step=Math.min(d,dt*.31*actor.scale.x*roam.speed);
        actor.position.x+=dx/d*step;actor.position.z+=dz/d*step;roam.phase+=step/actor.scale.x*23;
    }
    alignArm(rig,side,target,weight){
        const hand=rig.get(side+'Hand')?.node;
        if(!hand)return;
        for(let i=0;i<2;i++)for(const key of ['LowerArm','UpperArm']){
            const bone=rig.get(side+key)?.node;
            if(!bone)continue;
            bone.getWorldPosition(this.origin);
            this.from.copy(hand.getWorldPosition(this.point)).sub(this.origin).normalize();
            this.to.copy(target).sub(this.origin).normalize();
            this.q.setFromUnitVectors(this.from,this.to);
            const parent=bone.parent.getWorldQuaternion(this.parentQ);
            this.q.premultiply(parent.clone().invert()).multiply(parent).multiply(bone.quaternion);
            bone.quaternion.slerp(this.q,weight);
        }
    }
    animate(motions,now){
        if(!motions || !['lift','return','hold'].includes(this.state))return;
        const t=this.state==='lift'?Math.min(1,(now-this.since)/1200):1;
        const pickup=motions.kay_pickup,carry=motions.ual2_walk_carry_loop;
        const blend=ease((t-.35)/.65);
        if(pickup && t<1)motions.play(pickup,t*.60*pickup.duration,ease(t/.25)*(1-blend),0,false,true);
        if(carry)motions.play(carry,(now/1000)%carry.duration,blend,0,true,true);
        // Bring the coin from its desk position to the chest, with a contact on each rim.
        this.target.set(0,.52,.20);this.actor.localToWorld(this.target);this.anchor.worldToLocal(this.target);
        this.mesh.position.lerpVectors(this.floor,this.target,blend);
        this.mesh.rotation.order='YXZ';this.mesh.rotation.y=this.actor.rotation.y;this.mesh.rotation.x=blend*Math.PI/2;
        this.mesh.updateWorldMatrix(true,false);
        this.left.set(1,0,0);this.right.set(-1,0,0);
        this.mesh.localToWorld(this.left);this.mesh.localToWorld(this.right);
        this.alignArm(motions.rig,'left',this.left,ease(t/.38));
        this.alignArm(motions.rig,'right',this.right,ease(t/.38));
    }
}
