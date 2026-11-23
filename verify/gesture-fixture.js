window.initFixture=async()=>{
    root=new THREE.Group();actor.add(root);
    const names={};
    for(const [k,y] of [['hips',.45],['head',.78],['leftFoot',0],['rightFoot',0],['leftUpperArm',.65],['rightLowerLeg',.25]]){
        let b=new THREE.Group();root.add(b);b.name=k;b.position.y=y;names[k]=k;
    }
    head=root.getObjectByName('head');tail=new THREE.Group();headRadius=.15;
    const mesh=new THREE.Mesh(new THREE.SphereGeometry(.18),new THREE.MeshBasicMaterial());head.add(mesh);mesh.position.y=.09;
    ready=true;face={apply(){}};
    motions=new ReferenceMotion(root,names);await motions.load();
    window.placePose=()=>{const m=new THREE.Matrix4().makeRotationX(.31);m.scale(new THREE.Vector3(.34,.34,.34));m.setPosition(0,-.15,-1);
        const c=new THREE.PerspectiveCamera(48,innerWidth/innerHeight,.01,100);pose(m.toArray(),c.projectionMatrix.toArray());};
    window.peek=()=>({clip:motions.grabbed.duration,angle:root.getObjectByName('leftUpperArm').quaternion.angleTo(new THREE.Quaternion()),holding:motions.holding,y:actor.position.y,landing:landingHeight,bounds:headBounds()});
};
