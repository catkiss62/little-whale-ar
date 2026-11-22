import * as THREE from 'three';
import {GLTFLoader} from 'three/addons/loaders/GLTFLoader.js';

const quat=()=>new THREE.Quaternion();
const smooth=t=>(t=Math.max(0,Math.min(1,t)),t*t*(3-2*t));
const a=quat(),b=quat(),delta=quat(),world=quat(),parent=quat();
const up=new THREE.Vector3(),axis=new THREE.Vector3(),point=new THREE.Vector3(),flat=new THREE.Vector3();

async function readClip(name){
    const response=await fetch('motions/'+name+'.vrma',{cache:'no-store'});
    if(!response.ok)throw Error(name);
    const {parser,scene}=await new GLTFLoader().parseAsync(await response.arrayBuffer(),'');
    scene.updateMatrixWorld(true);
    const json=parser.json, animation=json.animations[0];
    const bones=json.extensions.VRMC_vrm_animation.humanoid.humanBones;
    const byNode=new Map(Object.entries(bones).map(([key,{node}])=>[node,key]));
    const tracks=new Map();
    let duration=0;
    for(const {target,sampler} of animation.channels){
        const key=byNode.get(target.node);
        if(!key || target.path!=='rotation')continue;
        const s=animation.samplers[sampler];
        if(s.interpolation && s.interpolation!=='LINEAR')throw Error('不支持的动作插值');
        const [node,input,output]=await Promise.all([parser.getDependency('node',target.node),
            parser.getDependency('accessor',s.input),parser.getDependency('accessor',s.output)]);
        const rest=node.getWorldQuaternion(quat()).invert();
        const frame=node.parent?.getWorldQuaternion(quat())||quat();
        const values=new Float32Array(output.count*4);
        for(let i=0;i<output.count;i++){
            a.fromBufferAttribute(output,i).premultiply(frame).multiply(rest).normalize().toArray(values,i*4);
        }
        const track=new THREE.QuaternionKeyframeTrack(key,input.array,values);
        tracks.set(key,{sample:track.createInterpolant(),first:quat().fromArray(values)});
        duration=Math.max(duration,input.getX(input.count-1));
    }
    if(!tracks.has('hips') || !(duration>0))throw Error(name);
    return {tracks,duration};
}

const cache=new Map();
function getClip(name){
    if(!cache.has(name))cache.set(name,readClip(name).catch(e=>{cache.delete(name);throw e;}));
    return cache.get(name);
}
export class ReferenceMotion {
    constructor(root,names){
        this.rig=new Map();
        for(const [name,id] of Object.entries(names)){
            const node=root.getObjectByName(id);
            if(!node)continue;
            this.rig.set(name,{node,rest:node.quaternion.clone(),frame:node.parent.getWorldQuaternion(quat()),
                before:quat(),exit:quat()});
        }
        this.headForward=new THREE.Vector3(0,0,1).applyQuaternion(this.rig.get('head').node.getWorldQuaternion(quat()).invert());
        this.reset();
    }
    async load(retry=false){
        if(retry)await Promise.allSettled([...cache.values()]);
        await Promise.all(['grabbed','locomotion_walk'].map(async name=>{this[name]=await getClip(name);}));
    }
    restore(){
        if(!this.applied)return;
        for(const {node,before} of this.rig.values())node.quaternion.copy(before);
        this.applied=false;
    }
    reset(){this.restore();this.holding=false;this.leave=-Infinity;}
    play(clip,time,weight,seam,isWalk){
        if(!clip || weight<=0)return;
        for(const [name,rig] of this.rig){
            const track=clip.tracks.get(name);
            if(!track || (isWalk && (name==='neck'||name==='head')))continue;
            delta.fromArray(track.sample.evaluate(time)).slerp(track.first,seam);
            // Fold the source's optional upper chest into V21's chest.
            const extra=name==='chest'&&!this.rig.has('upperChest')&&clip.tracks.get('upperChest');
            if(extra)delta.multiply(b.fromArray(extra.sample.evaluate(time)).slerp(extra.first,seam));
            a.copy(rig.frame).invert().multiply(delta).multiply(rig.frame).multiply(rig.rest);
            rig.node.quaternion.slerp(a,weight);
        }
    }
    upright(weight){
        const get=n=>this.rig.get(n).node;
        const hips=get('hips'),head=get('head');
        up.set(0,1,0).transformDirection(this.actor.matrixWorld);
        axis.copy(get('leftFoot').getWorldPosition(point)).add(get('rightFoot').getWorldPosition(point)).multiplyScalar(.5).negate().add(head.getWorldPosition(point)).normalize();
        const rotate=(node,q)=>{
            node.parent.getWorldQuaternion(parent);node.getWorldQuaternion(world);
            node.quaternion.copy(parent.invert()).multiply(q).multiply(world);
        };
        b.identity().slerp(a.setFromUnitVectors(axis,up),weight*.55);rotate(hips,b);
        axis.copy(this.headForward).applyQuaternion(head.getWorldQuaternion(world));
        flat.copy(axis).addScaledVector(up,-axis.dot(up));
        if(flat.lengthSq()>.001){
            const excess=Math.max(0,Math.asin(THREE.MathUtils.clamp(axis.dot(up),-1,1))+.14);
            rotate(head,b.setFromAxisAngle(flat.crossVectors(up,flat).normalize(),excess*weight));
        }
    }
    update(actor,now,held,phase,gait){
        if(!held && !this.holding && now-this.leave>=300 && (!this.locomotion_walk || gait<.001))return;
        this.actor=actor;
        for(const r of this.rig.values())r.before.copy(r.node.quaternion);
        this.applied=true;
        if(this.locomotion_walk)this.play(this.locomotion_walk,(phase/(Math.PI*2)%1)*this.locomotion_walk.duration,gait,0,true);
        if(held && this.grabbed){
            if(!this.holding)this.start=now;
            this.holding=true;
            const age=(now-this.start)/1000,d=this.grabbed.duration,t=age%(d+.2),w=smooth(age/.25);
            this.play(this.grabbed,Math.min(t,d),w,smooth((t-d)/.2),false);
            this.upright(w);
            for(const r of this.rig.values())r.exit.copy(r.node.quaternion);
        }else{
            if(this.holding){this.leave=now;this.holding=false;}
            const w=1-smooth((now-this.leave)/300);
            if(w>0)for(const r of this.rig.values())r.node.quaternion.slerp(r.exit,w);
        }
    }
}
