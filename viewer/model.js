// Keep the neutral skin and visible materials. The author's original VRM stays on disk.
export function prepareCharacter(doc) {
    const vrm = doc.extensions?.VRMC_vrm;
    if (!vrm?.humanoid || doc.buffers.length !== 1 || doc.buffers[0].uri)
        throw Error('需要作者发布的内嵌 VRM 1.0 模型');
    const names = {};
    for (const [key, value] of Object.entries(vrm.humanoid.humanBones))
        names[key] = doc.nodes[value.node].name;
    const channels = ['blink','relaxed','lookLeft','lookRight','lookUp','lookDown',
        'aa','eyeCircleShock','eyeSpiral','eyeTroubled','mouthOpenCircle','eyeSmile','mouthSmile','browHappy'];
    const targets = new Map(), uvBindings = new Map(), colors = new Map();
    for(const channel of channels){
        const expression=vrm.expressions?.preset?.[channel] ?? vrm.expressions?.custom?.[channel];
        for(const {material,type,targetValue} of expression?.materialColorBinds || []){
            if(type!=='color')continue;
            if(!colors.has(material))colors.set(material,[]);
            colors.get(material).push({channel,targetValue});
        }
        for(const {node,index,weight} of expression?.morphTargetBinds || []){
            const mesh=doc.nodes[node].mesh;
            if(!targets.has(mesh))targets.set(mesh,new Map());
            const slots=targets.get(mesh);
            if(!slots.has(index))slots.set(index,[]);
            slots.get(index).push({channel,weight});
        }
        for(const {material,offset} of expression?.textureTransformBinds || []){
            if(!uvBindings.has(material))uvBindings.set(material,[]);
            uvBindings.get(material).push({channel,offset});
        }
    }
    const expressions=[];
    const acc = new Set(), views = new Set();
    for (const node of doc.nodes) {
        delete node.weights;
        // GLTFLoader copies extras into every render object. Model editing metadata is not needed here.
        delete node.extras;
    }
    const liveMeshes=[], meshIds=new Map(), materials=new Set();
    for (const [i,m] of doc.meshes.entries()) {
        m.primitives=m.primitives.filter(p=>{
            const mat=doc.materials[p.material];
            return colors.has(p.material) || mat?.alphaMode!=='BLEND' || (mat.pbrMetallicRoughness?.baseColorFactor?.[3]??1)>0;
        });
        if(m.primitives.length){
            const slots=targets.get(i) || new Map();
            const indices=[...slots.keys()];
            expressions.push([...slots.values()]);
            for(const prim of m.primitives){
                if(indices.length)prim.targets=indices.map(id=>prim.targets[id]);
                else delete prim.targets;
            }
            meshIds.set(i,liveMeshes.length);liveMeshes.push(m);
            m.primitives.forEach(p=>materials.add(p.material));
        }
    }
    for(const n of doc.nodes) if(n.mesh!==undefined) {
        if(meshIds.has(n.mesh)) n.mesh=meshIds.get(n.mesh);
        else {delete n.mesh;delete n.skin;}
    }
    const materialIds=[...materials];
    doc.meshes=liveMeshes;
    for(const m of liveMeshes)for(const p of m.primitives)p.material=materialIds.indexOf(p.material);
    doc.materials=materialIds.map(i=>doc.materials[i]);
    for (const mesh of doc.meshes) {
        delete mesh.weights; delete mesh.extras;
        for (const p of mesh.primitives) {
            for(const target of p.targets || [])for(const id of Object.values(target))acc.add(id);
            for (const id of Object.values(p.attributes)) acc.add(id);
            if (p.indices !== undefined) acc.add(p.indices);
        }
    }
    for (const s of doc.skins) if (s.inverseBindMatrices !== undefined) acc.add(s.inverseBindMatrices);
    const accessors = [...acc].sort((a,b)=>a-b);
    const remap = new Map(accessors.map((id,i)=>[id,i]));
    for (const mesh of doc.meshes) for (const p of mesh.primitives) {
        for(const target of p.targets || [])for(const key in target)target[key]=remap.get(target[key]);
        for (const k in p.attributes) p.attributes[k] = remap.get(p.attributes[k]);
        if (p.indices !== undefined) p.indices = remap.get(p.indices);
    }
    for (const s of doc.skins) if (s.inverseBindMatrices !== undefined) s.inverseBindMatrices = remap.get(s.inverseBindMatrices);
    doc.accessors = accessors.map(id=>doc.accessors[id]);
    const useView = (obj) => { if (obj && obj.bufferView !== undefined) views.add(obj.bufferView); };
    for (const a of doc.accessors) { useView(a); useView(a.sparse?.indices); useView(a.sparse?.values); }
    // Use the author's neutral base colors and textures with unlit shading on the phone.
    const tex = new Set();
    for (const m of doc.materials) {
        const base = m.pbrMetallicRoughness || {};
        const color = base.baseColorTexture;
        if (color) tex.add(color.index);
        m.pbrMetallicRoughness = {baseColorFactor:base.baseColorFactor || [1,1,1,1]};
        if (color) m.pbrMetallicRoughness.baseColorTexture = color;
        delete m.normalTexture; delete m.occlusionTexture; delete m.emissiveTexture;
        m.extensions = {KHR_materials_unlit:{}};
    }
    const textures = [...tex], images = [...new Set(textures.map(i=>doc.textures[i].source))];
    for (const m of doc.materials) {
        const t = m.pbrMetallicRoughness.baseColorTexture;
        if (t) t.index = textures.indexOf(t.index);
    }
    doc.textures = textures.map(i=>doc.textures[i]);
    for (const t of doc.textures) t.source = images.indexOf(t.source);
    doc.images = images.map(i=>doc.images[i]);
    for (const image of doc.images) {
        const v=doc.bufferViews[image.bufferView];
        image.uri='image/'+(v.byteOffset||0)+'/'+v.byteLength;
        delete image.bufferView;delete image.mimeType;
    }
    const ids = [...views].sort((a,b)=>(doc.bufferViews[a].byteOffset||0)-(doc.bufferViews[b].byteOffset||0));
    const map = new Map(ids.map((id,i)=>[id,i]));
    const fix = obj=>{if (obj && obj.bufferView !== undefined) obj.bufferView=map.get(obj.bufferView);};
    for (const a of doc.accessors) {fix(a);fix(a.sparse?.indices);fix(a.sparse?.values);}

    const segments = [];
    doc.bufferViews = ids.map(id=>{
        const v = doc.bufferViews[id], start = v.byteOffset || 0, end = start + v.byteLength;
        let s = segments.at(-1);
        if (!s || start-s.end>1024 || end-s.start>8*1024*1024) {
            s={start,end};segments.push(s);
        } else s.end=Math.max(s.end,end);
        v.buffer=segments.length-1;v.byteOffset=start-s.start;return v;
    });
    doc.buffers=segments.map(s=>({uri:'bin/'+s.start+'/'+(s.end-s.start),byteLength:s.end-s.start}));
    doc.extensionsUsed=['KHR_materials_unlit']; delete doc.extensionsRequired; delete doc.extensions;
    delete doc.animations;
    return {doc,names,expressions,colors:materialIds.map(id=>colors.get(id)||[]),uv:materialIds.map(id=>uvBindings.get(id)||[])};
}
