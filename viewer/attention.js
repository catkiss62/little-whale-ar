// Camera positions use anchor space, where one unit is the standing character height.
export class Attention {
    constructor(){
        this.reset();
    }
    reset(){
        this.since=null;
        this.lastPose=null;
    }
    stationaryBehind(now,eye,yaw,blocked){
        const gap=this.lastPose===null || now-this.lastPose>350;
        this.lastPose=now;
        const bearing=Math.atan2(eye.x,eye.z);
        const offset=Math.atan2(Math.sin(bearing-yaw),Math.cos(bearing-yaw));
        if(blocked || Math.abs(offset)<Math.PI/3){
            this.since=null;
            return false;
        }
        if(gap || this.since===null || Math.hypot(eye.x-this.x,eye.y-this.y,eye.z-this.z)>.13 ||
            Math.abs(Math.atan2(Math.sin(bearing-this.bearing),Math.cos(bearing-this.bearing)))>.13){
            this.since=now;this.bearing=bearing;
            this.x=eye.x;this.y=eye.y;this.z=eye.z;
        }
        if(now-this.since<2000)return false;
        this.since=null;
        return true;
    }
}
