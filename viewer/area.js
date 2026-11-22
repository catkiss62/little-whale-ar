import * as THREE from 'three';
import {validate} from './roam.js';

export class AreaEditor {
    constructor(anchor,camera,actor,roam){
        Object.assign(this,{anchor,camera,actor,roam});
        this.panel=document.getElementById('area-panel');
        this.tip=document.getElementById('area-tip');
        this.clearButton=document.getElementById('area-clear');
        document.getElementById('area-cancel').onclick=()=>this.cancel();
        this.clearButton.onclick=()=>{roam.clear();this.cancel();};
        this.line=new THREE.Line(new THREE.BufferGeometry(),new THREE.LineBasicMaterial({
            color:0x88e4ef,transparent:true,opacity:.8,depthWrite:false
        }));
        this.line.visible=false;anchor.add(this.line);
        this.cancel();
    }
    outline(points,closed){
        const vertices=points.map(p=>new THREE.Vector3(p.x,.006,p.z));
        if(closed && vertices.length)vertices.push(vertices[0]);
        this.line.visible=vertices.length>=2;
        this.line.geometry.dispose();
        this.line.geometry=new THREE.BufferGeometry().setFromPoints(vertices);
    }
    cancel(){
        this.editing=false;this.id=null;this.draft=[];this.panel.hidden=true;
        this.outline(this.roam.polygon,true);
    }
    open(){
        this.cancel();this.editing=true;this.panel.hidden=false;
        this.clearButton.hidden=!this.roam.polygon.length;
        this.tip.textContent='沿桌面画一圈，把她的脚下圈进去';
        this.roam.pause();
    }
    project(x,y){
        const u=2*x/innerWidth-1,v=1-2*y/innerHeight;
        this.anchor.updateWorldMatrix(true,false);
        const inverse=this.anchor.matrixWorld.clone().invert();
        const at=z=>new THREE.Vector3(u,v,z).unproject(this.camera).applyMatrix4(inverse);
        const start=at(-1),direction=at(1).sub(start).normalize();
        if(Math.abs(direction.y)<.035)return null;
        const t=-start.y/direction.y;
        if(t<0 || !Number.isFinite(t))return null;
        const p={x:start.x+t*direction.x,z:start.z+t*direction.z};
        return Math.hypot(p.x,p.z)<=12?p:null;
    }
    point(e,force=false){
        if(!force && Math.hypot(e.clientX-this.x,e.clientY-this.y)<5)return;
        this.x=e.clientX;this.y=e.clientY;
        const p=this.project(this.x,this.y);
        if(!p){this.fail('请从上方对准桌面，再重新画一圈');return;}
        const prev=this.draft.at(-1);
        if(prev && Math.hypot(p.x-prev.x,p.z-prev.z)<.018)return;
        if(this.draft.length>=240){this.fail('边线太长，请重新画一个简单轮廓');return;}
        this.draft.push(p);this.outline(this.draft,false);
    }
    fail(message){
        this.id=null;this.draft=[];this.outline(this.roam.polygon,true);this.tip.textContent=message;
    }
    down(e){
        if(this.id!==null || !e.isPrimary)return;
        this.id=e.pointerId;this.draft=[];this.tip.textContent='松手闭合范围';
        e.currentTarget.setPointerCapture(e.pointerId);this.point(e,true);
    }
    move(e){if(this.id===e.pointerId)this.point(e);}
    up(e){
        if(this.id!==e.pointerId)return;
        this.point(e,true);
        if(this.id===null)return;
        const p=this.draft,first=p[0],last=p.at(-1);
        if(p.length>3 && Math.hypot(first.x-last.x,first.z-last.z)<.06*this.actor.scale.x)p.pop();
        const error=validate(p,this.actor.position,this.actor.scale.x);
        if(error){this.fail(error);return;}
        this.roam.set(p);this.cancel();
    }
}
