// Weights and offsets come from the selected expressions in the original VRM.
export class Face {
    constructor(scene, parser, expressions, uv, colors=[]){
        this.morphs=[];
        this.maps=[];
        this.colors=[];
        const materials=new Set();
        scene.traverse(obj=>{
            if(!obj.isMesh)return;
            const index=parser.associations.get(obj)?.meshes;
            if(obj.morphTargetInfluences && expressions[index]?.length)
                this.morphs.push([obj.morphTargetInfluences,expressions[index]]);
            const material=obj.material;
            if(materials.has(material))return;
            materials.add(material);
            const id=parser.associations.get(material)?.materials;
            const bindings=colors[id];
            if(bindings?.length){
                const {r,g,b}=material.color;
                this.colors.push({material,base:[r,g,b,material.opacity],bindings});
            }
            const binds=material.map && uv[id];
            if(binds?.length){
                // Only these iris materials own a changing UV transform.
                material.map=material.map.clone();
                this.maps.push([material.map,binds]);
            }
        });
        this.weights={blink:0,relaxed:0,lookLeft:0,lookRight:0,lookUp:0,lookDown:0};
    }

    apply(blink,relaxed,x,y,held=false,dizzy=false,squeeze=false,surprise=false){
        const w=this.weights;
        const pet=held || squeeze || surprise?0:relaxed;
        if(held || squeeze || pet>.05){blink=0;x=0;y=0;}
        Object.assign(w,{blink,relaxed:0,eyeCircleShock:held && !dizzy && !squeeze?1:0,
            eyeSpiral:dizzy?1:0,eyeTroubled:squeeze?1:0,mouthOpenCircle:held?1:0,
            eyeSmile:pet,browHappy:pet,mouthSmile:.5*pet,lookLeft:Math.max(0,x),lookRight:Math.max(0,-x),
            lookUp:Math.max(0,y),lookDown:Math.max(0,-y)});
        for(const [influences,slots] of this.morphs){
            for(let i=0;i<slots.length;i++){
                let value=0;
                for(const {channel,weight} of slots[i])value+=w[channel]*weight;
                influences[i]=Math.min(1,value);
            }
        }
        for(const {material,base,bindings} of this.colors){
            let [r,g,b,a]=base;
            for(const {channel,targetValue:t} of bindings){
                const v=w[channel]; if(!v)continue;
                r+=(t[0]-base[0])*v;g+=(t[1]-base[1])*v;b+=(t[2]-base[2])*v;a+=(t[3]-base[3])*v;
            }
            material.color.setRGB(r,g,b);material.opacity=Math.max(0,Math.min(1,a));
            material.visible=material.opacity>.001;
        }
        for(const [texture,binds] of this.maps){
            let u=0,v=0;
            for(const {channel,offset} of binds){u+=w[channel]*offset[0];v+=w[channel]*offset[1];}
            texture.offset.set(u,v);
        }
    }
}
