import * as THREE from 'three';

function label(rows,color){
    const canvas=document.createElement('canvas');
    canvas.width=canvas.height=256;
    const ctx=canvas.getContext('2d');
    ctx.fillStyle=color;ctx.fillRect(0,0,256,256);
    ctx.fillStyle='#fff';ctx.textAlign='center';ctx.textBaseline='middle';
    ctx.font='bold '+(rows.length===1?53:84)+'px sans-serif';
    rows.forEach((row,i)=>ctx.fillText(row,128,rows.length===1?128:80+i*94));
    const map=new THREE.CanvasTexture(canvas);map.colorSpace=THREE.SRGBColorSpace;
    return new THREE.MeshBasicMaterial({map});
}
export class MissingCube{
    constructor(){
        this.scene=new THREE.Scene();this.camera=new THREE.PerspectiveCamera(45,1,.01,100);
        const front=label(['导入','模型'],'#345972'),side=label(['导入','模型'],'#28465d');
        this.mesh=new THREE.Mesh(new THREE.BoxGeometry(1,1,1),[side,side,label(['缺少模型'],'#54869a'),side,front,front]);
        this.scene.add(this.mesh);this.mesh.matrixAutoUpdate=false;
        this.ray=new THREE.Raycaster();this.ray.far=Infinity;
        this.preview();
    }
    preview(){
        this.anchored=false;this.visible=true;
        this.mesh.matrix.makeRotationFromEuler(new THREE.Euler(.45,-.45,0));
        this.mesh.matrix.setPosition(0,0,-4.8);this.mesh.matrixWorldNeedsUpdate=true;
    }
    pose(mv,projection){
        this.anchored=true;this.visible=true;
        this.camera.projectionMatrix.fromArray(projection);
        this.mesh.matrix.fromArray(mv).multiply(new THREE.Matrix4().makeTranslation(0,.5,0));
        this.mesh.matrixWorldNeedsUpdate=true;
    }
    draw(renderer){
        if(!this.visible){renderer.clear();return;}
        if(!this.anchored){
            this.camera.aspect=innerWidth/innerHeight;this.camera.updateProjectionMatrix();
        }
        this.camera.projectionMatrixInverse.copy(this.camera.projectionMatrix).invert();
        renderer.render(this.scene,this.camera);
    }
    hit(x,y){
        const point=new THREE.Vector2(2*x/innerWidth-1,1-2*y/innerHeight);
        this.ray.setFromCamera(point,this.camera);
        return this.visible && this.ray.intersectObject(this.mesh).length>0;
    }
    dispose(){
        this.mesh.geometry.dispose();
        for(const m of new Set(this.mesh.material)){m.map.dispose();m.dispose();}
    }
}
