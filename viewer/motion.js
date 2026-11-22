// Weights and offsets come from the six selected expressions in the original VRM.
export class Face {
    constructor(scene, parser, expressions, uv){
        this.morphs=[];
        this.maps=[];
        const materials=new Set();
        scene.traverse(obj=>{
            if(!obj.isMesh)return;
            const index=parser.associations.get(obj)?.meshes;
            if(obj.morphTargetInfluences && expressions[index]?.length)
                this.morphs.push([obj.morphTargetInfluences,expressions[index]]);
            const material=obj.material;
            if(materials.has(material) || !material.map)return;
            materials.add(material);
            const binds=uv[parser.associations.get(material)?.materials];
            if(binds?.length){
                // Only these iris materials own a changing UV transform.
                material.map=material.map.clone();
                this.maps.push([material.map,binds]);
            }
        });
        this.weights={blink:0,relaxed:0,lookLeft:0,lookRight:0,lookUp:0,lookDown:0};
    }

    apply(blink,relaxed,x,y){
        const w=this.weights;
        Object.assign(w,{blink,relaxed,lookLeft:Math.max(0,x),lookRight:Math.max(0,-x),
            lookUp:Math.max(0,y),lookDown:Math.max(0,-y)});
        for(const [influences,slots] of this.morphs){
            for(let i=0;i<slots.length;i++){
                let value=0;
                for(const {channel,weight} of slots[i])value+=w[channel]*weight;
                influences[i]=Math.min(1,value);
            }
        }
        for(const [texture,binds] of this.maps){
            let u=0,v=0;
            for(const {channel,offset} of binds){u+=w[channel]*offset[0];v+=w[channel]*offset[1];}
            texture.offset.set(u,v);
        }
    }
}
