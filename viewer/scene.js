import * as THREE from 'three';
import {GLTFLoader} from 'three/addons/loaders/GLTFLoader.js';
import {prepareCharacter} from './model.js';

const scene=new THREE.Scene(), camera=new THREE.Camera(), anchor=new THREE.Group(), actor=new THREE.Group();
scene.add(anchor);anchor.add(actor);
anchor.matrixAutoUpdate=false;camera.matrixAutoUpdate=false;
let renderer, root, head, neck, upper, tail, ready=false, shown=false, raf=0, generation=0;
let turnStart=-1, targetYaw=0, baseYaw=0, faceOnPlace=true;
const joints=[], ray=new THREE.Raycaster(), pointer=new THREE.Vector2(), eye=new THREE.Vector3();
const inverse=new THREE.Matrix4(), rayNear=new THREE.Vector3(), rayFar=new THREE.Vector3();
function feedback(state){window.WhaleHost?.status(state);}
function resize(){
    if(!renderer)return;
    renderer.setSize(innerWidth,innerHeight,false);request();
}
function request(){if(!raf)raf=requestAnimationFrame(draw);}
function draw(now){
    raf=0;
    if(!renderer)return;
    if(!shown || !ready){renderer.clear();return;}
    if(turnStart>=0){
        const t=Math.min(1,(now-turnStart)/850), smooth=x=>x*x*(3-2*x);
        const lead=Math.sin(Math.PI*t)*Math.sign(targetYaw-baseYaw);
        actor.rotation.y=baseYaw+(targetYaw-baseYaw)*smooth(t);
        joints.forEach(([bone,rest,amount])=>bone.quaternion.copy(rest).multiply(
            tempRotation.setFromAxisAngle(up,lead*amount)));
        if(t<1)request();else turnStart=-1;
    }
    renderer.render(scene,camera);
}
const up=new THREE.Vector3(0,1,0),tempRotation=new THREE.Quaternion();
function pose(mv,p){
    if(!renderer||!ready)return;
    anchor.matrix.fromArray(mv);anchor.matrixWorldNeedsUpdate=true;
    camera.projectionMatrix.fromArray(p);camera.projectionMatrixInverse.copy(camera.projectionMatrix).invert();
    if(faceOnPlace){
        eye.set(0,0,0).applyMatrix4(inverse.copy(anchor.matrix).invert());
        actor.rotation.y=Math.atan2(eye.x,eye.z);
        faceOnPlace=false;
    }
    shown=true;request();
}
function hide(){shown=false;request();}
function disposeModel(){
    ready=false;hide();joints.length=0;turnStart=-1;actor.rotation.y=0;
    if(!root)return;
    const textures=new Set();
    root.traverse(o=>{
        o.geometry?.dispose();
        for(const m of [].concat(o.material||[])){if(m.map)textures.add(m.map);m.dispose();}
        o.skeleton?.dispose();
    });
    for(const t of textures){t.dispose();t.source?.data?.close?.();}
    actor.remove(root); root=null;
}
async function load(){
    if(!renderer)return;
    const token=++generation;
    disposeModel();feedback('loading');
    try{
        const response=await fetch('model.json',{cache:'no-store'});
        if(!response.ok)throw Error('找不到模型');
        const {doc,names}=prepareCharacter(await response.json());
        const gltf=await new GLTFLoader().parseAsync(JSON.stringify(doc),location.href.replace(/[^/]*$/,''));
        if(token!==generation)return;
        root=gltf.scene;
        root.traverse(o=>{if(o.isMesh){
            o.frustumCulled=false;
            if(o.material.opacity===0 && o.material.transparent)o.visible=false;
        }});
        tail=root.getObjectByName('WhaleTail');
        head=root.getObjectByName(names.head);neck=root.getObjectByName(names.neck);
        upper=root.getObjectByName(names.chest);
        if(!tail || !head)throw Error('此模型缺少 V21 尾巴或头骨');
        root.updateMatrixWorld(true);
        const box=new THREE.Box3().setFromObject(root);
        const height=box.max.y-box.min.y;
        if(!(height>0&&Number.isFinite(height)))throw Error('模型范围无效');
        const feet=[names.leftFoot,names.rightFoot].map(n=>root.getObjectByName(n)?.getWorldPosition(new THREE.Vector3())).filter(Boolean);
        const center=feet.length===2?feet[0].add(feet[1]).multiplyScalar(.5):box.getCenter(new THREE.Vector3());
        root.scale.multiplyScalar(1/height);
        root.position.add(new THREE.Vector3(-center.x,-box.min.y,-center.z).multiplyScalar(1/height));
        actor.add(root);
        for(const [b,amount] of [[upper,.22],[neck,.30],[head,.49]])if(b)joints.push([b,b.quaternion.clone(),amount]);
        ready=true;feedback('ready');request();
    }catch(e){disposeModel();feedback('error');console.error(e);}
}
function touch(e){
    if(!shown||!ready)return;
    const bounds=renderer.domElement.getBoundingClientRect();
    pointer.set((e.clientX-bounds.left)/bounds.width*2-1,1-(e.clientY-bounds.top)/bounds.height*2);
    scene.updateMatrixWorld(true);
    rayNear.set(pointer.x,pointer.y,-1).unproject(camera);
    rayFar.set(pointer.x,pointer.y,1).unproject(camera);
    ray.ray.set(rayNear,rayFar.sub(rayNear).normalize());
    // Test the real skinned surface and the closest visible opaque body surface.
    const hits=ray.intersectObject(root,true);
    const hit=hits.find(h=>h.object.visible && h.object.material?.opacity>.15);
    if(!hit)return;
    let part=hit.object;
    while(part && part!==tail)part=part.parent;
    if(!part)return;
    eye.set(0,0,0).applyMatrix4(inverse.copy(anchor.matrixWorld).invert());
    baseYaw=actor.rotation.y;
    const desired=Math.atan2(eye.x,eye.z);
    targetYaw=baseYaw+Math.atan2(Math.sin(desired-baseYaw),Math.cos(desired-baseYaw));
    turnStart=performance.now();request();
}
function reset(){faceOnPlace=true;turnStart=-1;actor.rotation.y=0;joints.forEach(([b,q])=>b.quaternion.copy(q));hide();}
window.whale={pose,hide,load,reset};
try{
    renderer=new THREE.WebGLRenderer({alpha:true,antialias:true,premultipliedAlpha:true,powerPreference:'high-performance'});
    renderer.setPixelRatio(Math.min(devicePixelRatio,1.5));renderer.setClearColor(0,0);
    document.body.appendChild(renderer.domElement);
    renderer.domElement.addEventListener('pointerup',touch);
    renderer.domElement.addEventListener('webglcontextlost',e=>{e.preventDefault();hide();feedback('error');});
    renderer.domElement.addEventListener('webglcontextrestored',load);
    addEventListener('resize',resize);resize();feedback('page');
}catch(e){feedback('error');}
