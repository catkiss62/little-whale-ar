import * as THREE from 'three';
import {GLTFLoader} from 'three/addons/loaders/GLTFLoader.js';
import {prepareCharacter} from './model.js';
import {Face} from './motion.js';

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
let face, gesture=null, frameLimit=60, nextFrame=0, lastDraw=0, sample=0, count=0;
let blinkAt=0, tapAt=-Infinity, affection=0, gazeX=0, gazeY=0;
const angles=new THREE.Euler(), deltaRotation=new THREE.Quaternion(), focus=new THREE.Vector3();
const headPoint=new THREE.Vector3(), headCenter=new THREE.Vector3(), headTop=new THREE.Vector3();
let headRadius=0;
const clamp=(v,limit)=>Math.max(-limit,Math.min(limit,v));
function draw(now){
    raf=0;
    if(!renderer)return;
    if(!shown || !ready || document.hidden){renderer.clear();return;}
    request();
    if(now+1<nextFrame)return;
    const step=1000/frameLimit;
    nextFrame=nextFrame && now-nextFrame<step ? nextFrame+step : now+step;
    const dt=lastDraw ? Math.min(.1,(now-lastDraw)/1000) : 0;
    lastDraw=now;
    let lead=0;
    if(turnStart>=0){
        const t=Math.min(1,(now-turnStart)/850);
        lead=Math.sin(Math.PI*t)*Math.sign(targetYaw-baseYaw);
        actor.rotation.y=baseYaw+(targetYaw-baseYaw)*t*t*(3-2*t);
        if(t>=1)turnStart=-1;
    }
    actor.updateWorldMatrix(true,false);
    head.getWorldPosition(headPoint);
    const direction=actor.worldToLocal(focus.set(0,0,0)).sub(actor.worldToLocal(headPoint));
    const front=direction.z>0;
    let x=front?clamp(Math.atan2(direction.x,direction.z)*1.8,.72):0;
    let y=front?clamp(Math.atan2(direction.y,Math.hypot(direction.x,direction.z)),.5):0;
    if(gesture?.part==='head'){
        // Screen right is character left when facing the camera.
        x=clamp((gesture.x-gesture.cx)/gesture.radius,.75);
        y=clamp((gesture.cy-gesture.y)/gesture.radius,.6);
    }
    const blend=1-Math.exp(-dt*8);
    gazeX+=(x-gazeX)*blend; gazeY+=(y-gazeY)*blend;
    affection+=((gesture?.petting?1:0)-affection)*(1-Math.exp(-dt*(gesture?.petting?7:3.5)));
    if(now>blinkAt+200)blinkAt=now+2200+Math.random()*2800;
    const blink=Math.max(0,1-Math.abs(now-blinkAt-90)/90);
    const tap=Math.max(0,1-(now-tapAt)/650);
    face.apply(Math.max(blink,tap*.75),affection*.72,gazeX,gazeY);
    const breath=Math.sin(now*.0017);
    for(const [bone,rest,amount] of joints){
        const pitch=bone===upper?breath*.018 : -gazeY*.10*amount + tap*.10*amount;
        angles.set(pitch,amount*(lead+gazeX*.30),affection*.12*amount,'YXZ');
        bone.quaternion.copy(rest).multiply(deltaRotation.setFromEuler(angles));
    }
    renderer.render(scene,camera);
    if(!sample){sample=now;count=0;}
    else if(++count && now-sample>=1000){
        window.WhaleHost?.reportFps?.(Math.round(1000*count/(now-sample)));
        sample=now; count=0;
    }
}
function cancelTouch(){gesture=null;}
function hide(){
    shown=false; cancelTouch(); affection=0;tapAt=-Infinity;gazeX=gazeY=0;
    sample=lastDraw=nextFrame=0;
    if(turnStart>=0){actor.rotation.y=targetYaw;turnStart=-1;}
    request();
}
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
        const {doc,names,expressions,uv}=prepareCharacter(await response.json());
        const gltf=await new GLTFLoader().parseAsync(JSON.stringify(doc),location.href.replace(/[^/]*$/,''));
        if(token!==generation)return;
        root=gltf.scene;
        face=new Face(root,gltf.parser,expressions,uv);
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
        headRadius=height*.155;
        blinkAt=performance.now()+2400;
        for(const [b,amount] of [[upper,.22],[neck,.30],[head,.49]])if(b)joints.push([b,b.quaternion.clone(),amount]);
        ready=true;feedback('ready');request();
    }catch(e){if(token!==generation)return;disposeModel();feedback('error');console.error(e);}
}
function headBounds(){
    // Project the forehead and its radius for the current zoom and viewpoint.
    head.getWorldPosition(headCenter);
    headTop.set(0,headRadius,0).applyMatrix4(head.matrixWorld);
    headCenter.lerp(headTop,.80);
    headCenter.project(camera);
    const radius=Math.max(9,Math.abs(headTop.project(camera).y-headCenter.y)*innerHeight*2.5);
    return {cx:(headCenter.x+1)*innerWidth/2,cy:(1-headCenter.y)*innerHeight/2,radius};
}
function insideHead(x,y,b){ return Math.hypot(x-b.cx,y-b.cy)<b.radius; }
function beginTouch(e){
    if(!shown || !ready || gesture || !e.isPrimary)return;
    const bounds=renderer.domElement.getBoundingClientRect();
    pointer.set((e.clientX-bounds.left)/bounds.width*2-1,1-(e.clientY-bounds.top)/bounds.height*2);
    scene.updateMatrixWorld(true);
    rayNear.set(pointer.x,pointer.y,-1).unproject(camera);
    rayFar.set(pointer.x,pointer.y,1).unproject(camera);
    ray.ray.set(rayNear,rayFar.sub(rayNear).normalize());
    const hit=ray.intersectObject(root,true).find(h=>h.object.visible && h.object.material?.opacity>.15);
    if(!hit)return;
    const b=headBounds();
    let obj=hit.object;
    while(obj && obj!==tail)obj=obj.parent;
    const part=obj?'tail':insideHead(e.clientX,e.clientY,b)?'head':null;
    if(!part)return;
    gesture={...b,id:e.pointerId,part,x:e.clientX,y:e.clientY,start:performance.now(),distance:0,petting:false};
    renderer.domElement.setPointerCapture(e.pointerId);
}
function moveTouch(e){
    if(gesture?.id!==e.pointerId)return;
    const g=gesture;
    if(g.part==='head'){
        Object.assign(g,headBounds());
        if(!insideHead(e.clientX,e.clientY,g)){cancelTouch();return;}
    }
    g.distance+=Math.hypot(e.clientX-g.x,e.clientY-g.y);
    g.x=e.clientX;g.y=e.clientY;
    if(g.part==='head' && performance.now()-g.start>=250 && g.distance>=Math.max(6,g.radius*.25))g.petting=true;
}
function endTouch(e){
    if(gesture?.id!==e.pointerId)return;
    moveTouch(e);
    const g=gesture;
    cancelTouch();
    if(!g || g.petting || g.distance>Math.max(9,g.radius*.30) || performance.now()-g.start>650)return;
    if(g.part==='head'){tapAt=performance.now();return;}
    eye.set(0,0,0).applyMatrix4(inverse.copy(anchor.matrixWorld).invert());
    baseYaw=actor.rotation.y;
    const desired=Math.atan2(eye.x,eye.z);
    targetYaw=baseYaw+Math.atan2(Math.sin(desired-baseYaw),Math.cos(desired-baseYaw));
    turnStart=performance.now();request();
}
function reset(){faceOnPlace=true;turnStart=-1;actor.rotation.y=0;joints.forEach(([b,q])=>b.quaternion.copy(q));hide();}
function setFrameLimit(value){frameLimit=value===30?30:60;nextFrame=sample=0;}
window.whale={pose,hide,load,reset,setFrameLimit};
try{
    renderer=new THREE.WebGLRenderer({alpha:true,antialias:true,premultipliedAlpha:true,powerPreference:'high-performance'});
    renderer.setPixelRatio(Math.min(devicePixelRatio,1.5));renderer.setClearColor(0,0);
    document.body.appendChild(renderer.domElement);
    const canvas=renderer.domElement;
    canvas.addEventListener('pointerdown',beginTouch);
    canvas.addEventListener('pointermove',moveTouch);
    canvas.addEventListener('pointerup',endTouch);
    for(const event of ['pointercancel','lostpointercapture'])
        canvas.addEventListener(event,e=>{if(gesture?.id===e.pointerId)cancelTouch();});
    document.addEventListener('visibilitychange',()=>{if(document.hidden)hide();});
    renderer.domElement.addEventListener('webglcontextlost',e=>{e.preventDefault();hide();feedback('error');});
    renderer.domElement.addEventListener('webglcontextrestored',load);
    addEventListener('resize',resize);resize();feedback('page');
}catch(e){feedback('error');}
