window.initFixture=async()=>{
    root=new THREE.Group();actor.add(root);
    const names={};
    for(const [k,y] of [['hips',.45],['head',.78],['leftFoot',0],['rightFoot',0],['leftUpperArm',.65],['rightLowerLeg',.25]]){
        let b=new THREE.Group();root.add(b);b.name=k;b.position.y=y;names[k]=k;
    }
    head=root.getObjectByName('head');tail=new THREE.Group();root.add(tail);tail.position.set(.4,.5,0);headRadius=.15;
    tail.add(new THREE.Mesh(new THREE.SphereGeometry(.10),new THREE.MeshBasicMaterial()));
    const mesh=new THREE.Mesh(new THREE.SphereGeometry(.18),new THREE.MeshBasicMaterial());head.add(mesh);mesh.position.y=.09;
    ready=true;face={apply(){}};
    motions=new ReferenceMotion(root,names);await motions.load();
    window.placePose=()=>{const m=new THREE.Matrix4().makeRotationX(.31);m.scale(new THREE.Vector3(.34,.34,.34));m.setPosition(0,-.15,-1);
        const c=new THREE.PerspectiveCamera(48,innerWidth/innerHeight,.01,100);pose(m.toArray(),c.projectionMatrix.toArray());};
    window.tailPixel=()=>{const v=tail.getWorldPosition(new THREE.Vector3()).project(camera);return [(1+v.x)*innerWidth/2,(1-v.y)*innerHeight/2];};
    window.tailError=()=>{
        if(!gesture?.tailContact || !pickup.source)return null;
        const v=pickup.grip.clone(); // recompute animated surface contact in display coordinates
        const c=gesture.tailContact;v.set(0,0,0);
        for(let i=0;i<3;i++)v.addScaledVector(c.mesh.getVertexPosition(c.indices[i],new THREE.Vector3()),c.weights[i]);
        c.mesh.localToWorld(v).project(camera);
        return Math.hypot((1+v.x)*innerWidth/2-gesture.x,(1-v.y)*innerHeight/2-gesture.y);
    };
    window.coinPixel=()=>{const v=new THREE.Vector3(.30,0,.40);anchor.localToWorld(v).project(camera);return [(1+v.x)*innerWidth/2,(1-v.y)*innerHeight/2];};
    window.coinState=()=>coin.state;
    // Public CI exercises the interaction with stand-in clips when optional source packs are absent.
    motions.kay_pickup??=motions.grabbed;
    motions.ual2_walk_carry_loop??=motions.locomotion_walk;
    window.peek=()=>({clip:motions.grabbed.duration,angle:root.getObjectByName('leftUpperArm').quaternion.angleTo(new THREE.Quaternion()),holding:motions.holding,y:actor.position.y,landing:landingHeight,bounds:headBounds()});
};

window.walkActive=()=>roam.manual;

window.speechState=()=>({time:voice.player.currentTime,rate:voice.player.playbackRate,group:voice.group,text:voice.bubble.textContent,visible:!voice.bubble.hidden,pitch:voice.player.preservesPitch});

window.testTailVoice=()=>{voice.stop();voice.next=0;delete voice.last.tail;return voice.say('tail');};
