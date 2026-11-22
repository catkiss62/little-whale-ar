import * as THREE from 'three';
import {GLTFLoader} from 'three/addons/loaders/GLTFLoader.js';
import {prepareCharacter} from './model.js';
import {Face} from './motion.js';
import {Attention} from './attention.js';
import {Roam} from './roam.js';
import {PalmSurface} from './palm.js';
import {ReferenceMotion} from './reference-motion.js';
import {Pickup} from './pickup.js';
import {AreaEditor} from './area.js';

const scene=new THREE.Scene(), camera=new THREE.Camera(), anchor=new THREE.Group(), actor=new THREE.Group();
scene.add(anchor);anchor.add(actor);
anchor.matrixAutoUpdate=false;camera.matrixAutoUpdate=false;
let renderer, root, head, neck, upper, tail, ready=false, shown=false, raf=0, generation=0;
let turnStart=-1, targetYaw=0, baseYaw=0, faceOnPlace=true;
const attention=new Attention(),roam=new Roam();
let area,pickup,motions,deskPoseTime=-Infinity;
let swayX=0,swayY=0,lidsUntil=-Infinity;
let landedAt=-Infinity,landingHeight=0,standingY=0;
const palm=new PalmSurface();
let settleYaw=0,settleStart=-Infinity;
const walkBones=[];
let headYaw=0,headPitch=0,gait=0;
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
let headRadius=0, turnDuration=850;
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
    if(gesture?.part==='body'){
        if(now-deskPoseTime>1800)cancelTouch();
        else if(!pickup.source && now-gesture.start>=480){
            pickup.begin(gesture,now);
            if(turnStart>=0)turnStart=-1;
        }
        if(pickup.source)pickup.move(gesture,now);
    }
    motions?.restore();
    if(landingHeight){
        const t=Math.min(1,Math.max(0,(now-landedAt)/180));
        root.position.y=standingY+landingHeight*(1-t*t);
        if(t===1)landingHeight=0;
    }
    let turning=0,lateYaw=0;
    if(turnStart>=0){
        const t=Math.min(1,(now-turnStart)/turnDuration);
        turning=4*t*(1-t);
        actor.rotation.y=baseYaw+(targetYaw-baseYaw)*t*t*(3-2*t);
        // Leave a few degrees for the head to finish after the shoulders.
        const finish=Math.max(0,Math.min(1,(t-.55)/.38));
        lateYaw=clamp((targetYaw-baseYaw)*.13,.15)*finish*finish*(3-2*finish);
        if(t>=1){turnStart=-1;settleStart=now;settleYaw=lateYaw;}
    }else{
        const t=Math.min(1,(now-settleStart)/260);
        lateYaw=settleYaw*(1-t*t*(3-2*t));
    }
    roam.advance(actor,dt,!!gesture || area.editing || turnStart>=0);
    gait+=(roam.speed-gait)*(1-Math.exp(-dt*10));
    actor.updateWorldMatrix(true,false);
    head.getWorldPosition(headPoint);
    const direction=actor.worldToLocal(focus.set(0,0,0)).sub(actor.worldToLocal(headPoint));
    const horizontal=Math.hypot(direction.x,direction.z);
    const yaw=Math.atan2(direction.x,direction.z);
    const pitch=Math.atan2(direction.y,horizontal);
    // Ease sideways gaze back to center directly behind or overhead.
    const sideWeight=Math.min(1,(Math.PI-Math.abs(yaw))/(Math.PI/2)) *
        Math.min(1,4*horizontal/Math.max(direction.length(),1e-6));
    let x=clamp(yaw*1.8,.72)*sideWeight;
    let y=clamp(pitch,.5);
    if(gesture?.outsideSince && now-gesture.outsideSince>220)cancelTouch();
    let strokeX=0,strokeY=0;
    if(gesture?.part==='head'){
        strokeX=clamp((gesture.x-gesture.cx)/gesture.rx,.75);
        strokeY=clamp((gesture.cy-gesture.y)/gesture.ry,.6);
        x=clamp(x+strokeX*.23,.75);y=clamp(y+strokeY*.18,.6);
    }
    const wantedYaw=clamp(clamp(yaw*(1-.45*turning),.78*(1-.35*turning))*sideWeight+strokeX*.15,.85);
    const wantedPitch=clamp(clamp(pitch,.60)+strokeY*.10,.66);
    const headBlend=1-Math.exp(-dt*5);
    headYaw+=(wantedYaw-headYaw)*headBlend; headPitch+=(wantedPitch-headPitch)*headBlend;
    const blend=1-Math.exp(-dt*8);
    gazeX+=(x-gazeX)*blend; gazeY+=(y-gazeY)*blend;
    affection+=((gesture?.petting?1:0)-affection)*(1-Math.exp(-dt*(gesture?.petting?7:3.5)));
    if(now>blinkAt+200)blinkAt=now+2200+Math.random()*2800;
    const blink=Math.max(0,1-Math.abs(now-blinkAt-90)/90);
    const tap=Math.max(0,1-(now-tapAt)/650);
    const eyelids=now<lidsUntil?Math.min(1,(lidsUntil-now)/280):0;
    face.apply(Math.max(blink,tap*.75,affection*.74,eyelids*.55),affection,gazeX,gazeY);
    const recoilAge=(now-tapAt)/420;
    const recoil=recoilAge<1?Math.sin(Math.PI*recoilAge)*Math.exp(-2.1*recoilAge):0;
    const trail=Math.exp(-dt/.13);swayX*=trail;swayY*=trail;
    const breath=Math.sin(now*.0017);
    for(const [bone,rest,amount,basis] of joints){
        const share=bone===upper ? .08 : bone===neck ? .34 : .58;
        const touchShare=bone===upper?.15:bone===neck?.20:.65;
        const pitch=(bone===upper?breath*.018:0)-headPitch*share + affection*.09*amount
            + swayY*touchShare - recoil*(bone===upper?.26:bone===head?.14:0);
        angles.set(pitch,(headYaw+lateYaw)*share,-swayX*touchShare,'YXZ');
        applyRotation(bone,rest,basis,angles);
    }
    const stride=Math.sin(roam.phase);
    for(const [bone,rest,basis,side,kind] of walkBones){
        let swing=side*stride;
        if(kind==='UpperArm')swing*=-.22;
        else if(kind==='LowerLeg')swing=Math.max(0,-swing)*.48;
        else swing*=.40;
        angles.set(motions?.locomotion_walk?0:swing*gait,0,0);
        applyRotation(bone,rest,basis,angles);
    }
    motions?.update(actor,now,!!pickup.source,roam.phase,gait);
    renderer.render(scene,camera);
    if(!sample){sample=now;count=0;}
    else if(++count && now-sample>=1000){
        window.WhaleHost?.reportFps?.(Math.round(1000*count/(now-sample)));
        sample=now; count=0;
    }
}
const localDelta=new THREE.Quaternion();
function applyRotation(bone,rest,basis,angles){
    localDelta.copy(basis).invert().multiply(deltaRotation.setFromEuler(angles)).multiply(basis);
    bone.quaternion.copy(rest).multiply(localDelta);
}
function cancelTouch(){
    if(gesture?.petting)window.WhaleHost?.petting?.(false);
    pickup?.end();gesture=null;
}
function hide(){
    deskPoseTime=-Infinity;motions?.reset();swayX=swayY=0;lidsUntil=-Infinity;
    if(landingHeight && root)root.position.y=standingY;
    landingHeight=0;
    palm.lose();settleYaw=0;settleStart=-Infinity;
    if(area?.editing)area.cancel();
    roam.pause();gait=0;headYaw=headPitch=0;
    shown=false; attention.reset(); cancelTouch(); affection=0;tapAt=-Infinity;gazeX=gazeY=0;
    sample=lastDraw=nextFrame=0;
    if(turnStart>=0){actor.rotation.y=targetYaw;turnStart=-1;}
    request();
}
function pose(mv,p){
    if(!renderer||!ready)return;
    const now=performance.now();
    deskPoseTime=now;
    anchor.matrix.fromArray(mv);anchor.matrixWorldNeedsUpdate=true;
    camera.projectionMatrix.fromArray(p);camera.projectionMatrixInverse.copy(camera.projectionMatrix).invert();
    if(gesture?.released){
        if(now-gesture.released<=1500){
            pickup.move(gesture,now);
            if(pickup.target){
                landingHeight=actor.position.y/actor.scale.y;landedAt=now;
            }
            pickup.end(true);
        }
        cancelTouch();
    }
    eye.set(0,0,0).applyMatrix4(inverse.copy(anchor.matrix).invert()).sub(actor.position);
    if(faceOnPlace){
        actor.rotation.y=Math.atan2(eye.x,eye.z);
        faceOnPlace=false;
    }else if(attention.stationaryBehind(performance.now(),eye,actor.rotation.y,!!gesture || area.editing || !!roam.target || turnStart>=0)){
        roam.pause(2.5);turnToCamera(1300);
    }
    shown=true;request();
}
function disposeModel(){
    ready=false;hide();walkBones.length=0;joints.length=0;turnStart=-1;actor.rotation.y=0;
    motions=null;
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
function relaxArms(root,names){
    // Rotate in world space toward the hip on the same side, then convert to the parent frame.
    const hips=root.getObjectByName(names.hips).getWorldPosition(new THREE.Vector3());
    for(const side of ['left','right']){
        const arm=root.getObjectByName(names[side+'UpperArm']);
        const elbow=root.getObjectByName(names[side+'LowerArm']);
        const shoulder=arm.getWorldPosition(new THREE.Vector3());
        const from=elbow.getWorldPosition(new THREE.Vector3()).sub(shoulder).normalize();
        const to=new THREE.Vector3(Math.sign(shoulder.x-hips.x)*.25,-1,.03).normalize();
        const rotation=new THREE.Quaternion().setFromUnitVectors(from,to).multiply(arm.getWorldQuaternion(new THREE.Quaternion()));
        arm.quaternion.copy(arm.parent.getWorldQuaternion(new THREE.Quaternion()).invert().multiply(rotation));
        arm.updateWorldMatrix(false,true);
    }
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
        motions=new ReferenceMotion(root,names);
        relaxArms(root,names);
        root.scale.multiplyScalar(1/height);
        root.position.add(new THREE.Vector3(-center.x,-box.min.y,-center.z).multiplyScalar(1/height));
        standingY=root.position.y;
        actor.add(root);
        headRadius=height*.155;
        blinkAt=performance.now()+2400;
        const actorInverse=actor.getWorldQuaternion(new THREE.Quaternion()).invert();
        const basis=b=>actorInverse.clone().multiply(b.getWorldQuaternion(new THREE.Quaternion()));
        for(const [b,amount] of [[upper,.22],[neck,.30],[head,.49]])if(b)joints.push([b,b.quaternion.clone(),amount,basis(b)]);
        for(const [side,sign] of [['left',1],['right',-1]])for(const kind of ['UpperArm','UpperLeg','LowerLeg']){
            const b=root.getObjectByName(names[side+kind]);
            if(b)walkBones.push([b,b.quaternion.clone(),basis(b),sign,kind]);
        }
        ready=true;feedback('ready');request();loadMotions();
    }catch(e){if(token!==generation)return;disposeModel();feedback('error');console.error(e);}
}
async function loadMotions(retry=false){
    const current=motions;
    if(!current)return;
    try{await current.load(retry);}
    catch(e){
        if(motions===current)window.WhaleHost?.motionError?.();
    }
}
function headBounds(){
    // Use the projected crown and temples. Screen-space width stays usable from above.
    head.updateWorldMatrix(true,false);
    headCenter.set(0,headRadius*1.03,0).applyMatrix4(head.matrixWorld);
    const radius=headRadius*head.getWorldScale(headTop).length()/Math.sqrt(3);
    const p=camera.projectionMatrix.elements;
    const pixelX=radius*Math.hypot(p[0],p[4])*innerWidth/(2*Math.max(.001,-headCenter.z));
    const pixelY=radius*Math.hypot(p[1],p[5])*innerHeight/(2*Math.max(.001,-headCenter.z));
    headCenter.project(camera);
    return {cx:(headCenter.x+1)*innerWidth/2,cy:(1-headCenter.y)*innerHeight/2,
        rx:Math.max(12,pixelX*1.52),ry:Math.max(14,pixelY*1.77)};
}
function insideHead(x,y,b,margin=1){
    return Math.hypot((x-b.cx)/b.rx,(y-b.cy)/b.ry)<=margin;
}
function beginTouch(e){
    if(area.editing){area.down(e);return;}
    if(!shown && ready && e.isPrimary && palm.frame){
        const b=renderer.domElement.getBoundingClientRect();
        const point=palm.pick((e.clientX-b.left)/b.width,(e.clientY-b.top)/b.height);
        if(point){
            showOnPalm(...point);
            window.WhaleHost?.placedOnPalm?.(palm.site.side===0?'Left':'Right');
        }
        return;
    }
    if(!shown || !ready || gesture || !e.isPrimary)return;
    const bounds=renderer.domElement.getBoundingClientRect();
    pointer.set((e.clientX-bounds.left)/bounds.width*2-1,1-(e.clientY-bounds.top)/bounds.height*2);
    scene.updateMatrixWorld(true);
    rayNear.set(pointer.x,pointer.y,-1).unproject(camera);
    rayFar.set(pointer.x,pointer.y,1).unproject(camera);
    ray.ray.set(rayNear,rayFar.sub(rayNear).normalize());
    const hit=ray.intersectObject(root,true).find(h=>h.object.visible && h.object.material?.opacity>.15);
    const b=headBounds();
    let obj=hit?.object;
    while(obj && obj!==tail)obj=obj.parent;
    const part=obj?'tail':insideHead(e.clientX,e.clientY,b) && (hit || b.rx<=18)?'head':hit && !palm.site?'body':null;
    if(!part)return;
    gesture={...b,id:e.pointerId,part,x:e.clientX,y:e.clientY,start:performance.now(),distance:0,outsideSince:0,petting:false};
    if(part==='body'){
        gesture.grab=anchor.worldToLocal(hit.point.clone());
        gesture.downX=e.clientX;gesture.downY=e.clientY;
    }
    roam.pause(2.5);attention.since=null;
    renderer.domElement.setPointerCapture(e.pointerId);
}
function moveTouch(e){
    if(area.editing){area.move(e);return;}
    if(gesture?.id!==e.pointerId)return;
    const g=gesture;
    if(g.part==='body' && !pickup.source && Math.hypot(e.clientX-g.downX,e.clientY-g.downY)>12){
        cancelTouch();return;
    }
    if(g.part==='head'){
        Object.assign(g,headBounds());
        if(!insideHead(e.clientX,e.clientY,g,1.23)){
            g.outsideSince ||= performance.now();
            g.x=e.clientX;g.y=e.clientY;
            return;
        }
        if(g.outsideSince){g.x=e.clientX;g.y=e.clientY;g.outsideSince=0;}
    }
    if(g.part==='head'){
        const time=performance.now();
        if(time-(g.sampleTime??g.start)<150){
            swayX=clamp(swayX+(e.clientX-g.x)/Math.max(8,g.rx)*.27,.16);
            swayY=clamp(swayY+(e.clientY-g.y)/Math.max(8,g.ry)*.22,.13);
        }
        g.sampleTime=time;
    }
    g.distance+=Math.hypot(e.clientX-g.x,e.clientY-g.y);
    g.x=e.clientX;g.y=e.clientY;
    if(g.part==='head' && !g.petting && g.distance>=Math.max(4,Math.min(12,g.rx*.16))){
        g.petting=true;
        window.WhaleHost?.petting?.(true);
    }
}
function endTouch(e){
    if(area.editing){area.up(e);return;}
    if(gesture?.id!==e.pointerId)return;
    moveTouch(e);
    const g=gesture;
    if(g?.part==='body'){
        const now=performance.now();
        // A release is acknowledged by the next good same-map pose. Native tracking
        // loss cancels the provisional position even if WebGL is temporarily slow.
        if(pickup.source)g.released=now;
        else cancelTouch();
        request();return;
    }
    if(g?.petting)lidsUntil=performance.now()+450;
    cancelTouch();
    if(!g || g.outsideSince || g.petting || g.distance>Math.max(9,g.rx*.30) || performance.now()-g.start>650)return;
    if(g.part==='head'){tapAt=performance.now();return;}
    eye.set(0,0,0).applyMatrix4(inverse.copy(anchor.matrixWorld).invert()).sub(actor.position);
    turnToCamera(850);
}
function turnToCamera(duration){
    turnDuration=duration;
    baseYaw=actor.rotation.y;
    const desired=Math.atan2(eye.x,eye.z);
    targetYaw=baseYaw+Math.atan2(Math.sin(desired-baseYaw),Math.cos(desired-baseYaw));
    turnStart=performance.now();request();
}
// The detector and the WebView share the corrected preview rectangle. Project the feet
// onto the measured palm center and choose apparent height in proportion to palm width.
const palmCamera=new THREE.PerspectiveCamera(45,1,.02,100);
const across=new THREE.Vector3(),towardFingers=new THREE.Vector3(),palmUp=new THREE.Vector3();
const palmRight=new THREE.Vector3(),palmFront=new THREE.Vector3(),contact=new THREE.Vector3();
const palmBasis=new THREE.Matrix4(),measuredRotation=new THREE.Quaternion(),palmRotation=new THREE.Quaternion();
let palmPoseTime=0,palmLift=.5;
function showOnPalm(x,y,width,axes){
    if(!ready || !renderer || innerWidth<=0 || innerHeight<=0)return;
    palmCamera.aspect=innerWidth/innerHeight;
    palmCamera.updateProjectionMatrix();
    camera.projectionMatrix.copy(palmCamera.projectionMatrix);
    camera.projectionMatrixInverse.copy(palmCamera.projectionMatrixInverse);
    const fy=camera.projectionMatrix.elements[5],fx=camera.projectionMatrix.elements[0];
    const depth=fy/(2*Math.max(.018,Math.min(.85,width*1.38*palmCamera.aspect)));
    across.fromArray(axes);towardFingers.fromArray(axes,3);
    palmUp.crossVectors(across,towardFingers).multiplyScalar(palm.frame.side===1?1:-1);
    if(palmUp.lengthSq()<1e-10){hide();return;}
    palmUp.normalize();
    palmRight.crossVectors(towardFingers,palmUp).normalize();
    palmFront.crossVectors(palmRight,palmUp);
    palmBasis.makeBasis(palmRight,palmUp,palmFront);
    measuredRotation.setFromRotationMatrix(palmBasis);
    const now=performance.now(),dt=(now-palmPoseTime)/1000;
    if(!shown || dt>.335)palmRotation.copy(measuredRotation);
    else palmRotation.slerp(measuredRotation,1-Math.exp(-Math.max(0,dt)*18));
    palmPoseTime=now;
    contact.set((2*x-1)*depth/fx,(1-2*y)*depth/fy,-depth);
    if(palm.site.yaw===undefined){
        // Choose a bearing once. A straight-down view uses the wrist as the front.
        eye.copy(contact).negate().normalize().applyQuaternion(measuredRotation.clone().invert());
        palm.site.yaw=Math.hypot(eye.x,eye.z)>.32?Math.atan2(eye.x,eye.z):0;
        actor.rotation.y=palm.site.yaw;
    }
    // Track the camera relative to the physical hand, independent of the display lift.
    eye.copy(contact).negate().applyQuaternion(measuredRotation.copy(palmRotation).invert());
    const overhead=Math.hypot(eye.x,eye.z)<.30*eye.length();
    if(attention.stationaryBehind(now,eye,actor.rotation.y,overhead || !!gesture || turnStart>=0))
        turnToCamera(1300);
    anchor.matrix.makeRotationFromQuaternion(palmRotation);
    // V21's sole runs from heel to toes in front of the ankle midpoint.
    const sole=headCenter.set(0,0,.035*actor.scale.x).applyQuaternion(actor.quaternion).applyQuaternion(palmRotation);
    if(palmLift){
        const crown=headTop.set(0,actor.scale.y,0).applyQuaternion(palmRotation).add(contact);
        const projectedHeight=Math.abs(crown.y/Math.max(.02,-crown.z)-contact.y/depth);
        contact.y+=depth*projectedHeight*palmLift;
    }
    anchor.matrix.setPosition(contact.sub(sole));
    anchor.matrixWorldNeedsUpdate=true;
    shown=true;request();
}
function palmSurface(points,width,side,axes){
    if(!ready)return;
    const point=palm.update(points,width,side,axes);
    if(point)showOnPalm(...point);
}
function setPalmLift(percent){
    palmLift=Math.max(0,Math.min(50,percent))/100;
    const point=palm.foot();
    if(point)showOnPalm(...point);
}
function choosePalmPoint(){palm.reset();hide();}
function reset(){cancelTouch();palm.reset();roam.clear();area?.cancel();actor.position.set(0,0,0);faceOnPlace=true;turnStart=-1;actor.rotation.y=0;joints.forEach(([b,q])=>b.quaternion.copy(q));hide();}
function setFrameLimit(value){frameLimit=value===30?30:60;nextFrame=sample=0;}
function setSize(percent){
    actor.scale.setScalar(Math.max(.25,Math.min(2,percent/100)));
    roam.pause();attention.since=null;cancelTouch();if(area?.editing)area.cancel();request();
}
function editArea(){if(!shown || !ready)return false;cancelTouch();area.open();return true;}
window.whale={loadMotions,pose,palmSurface,choosePalmPoint,hide,load,reset,setFrameLimit,setSize,setPalmLift,editArea};
try{
    renderer=new THREE.WebGLRenderer({alpha:true,antialias:true,premultipliedAlpha:true,powerPreference:'high-performance'});
    renderer.setPixelRatio(Math.min(devicePixelRatio,1.5));renderer.setClearColor(0,0);
    document.body.appendChild(renderer.domElement);
    area=new AreaEditor(anchor,camera,actor,roam);
    pickup=new Pickup(anchor,camera,actor,roam,area);
    const canvas=renderer.domElement;
    canvas.addEventListener('contextmenu',e=>e.preventDefault());
    canvas.addEventListener('pointerdown',beginTouch);
    canvas.addEventListener('pointermove',moveTouch);
    canvas.addEventListener('pointerup',endTouch);
    for(const event of ['pointercancel','lostpointercapture'])
        canvas.addEventListener(event,e=>{
            if(area.editing && area.id===e.pointerId)area.fail('触摸中断，请重新画一圈');
            if(gesture?.id===e.pointerId && (event==='pointercancel' || !gesture.released))cancelTouch();
        });
    document.addEventListener('visibilitychange',()=>{if(document.hidden)hide();});
    renderer.domElement.addEventListener('webglcontextlost',e=>{e.preventDefault();hide();feedback('error');});
    renderer.domElement.addEventListener('webglcontextrestored',load);
    addEventListener('resize',resize);resize();feedback('page');
}catch(e){feedback('error');}
