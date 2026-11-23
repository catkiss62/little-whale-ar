const rates=[.75,1,1.25,1.5];
export class WhaleSpeech{
    constructor(){
        this.player=new Audio();
        this.rate=window.WhaleHost?.speechSpeed?.()??-1;
        this.bags={};this.last={};this.next=0;this.serial=0;
        this.bubble=document.querySelector('#speech');
        this.speed=document.querySelector('#voice-speed');
        this.speed.value=String(this.rate);
        this.speed.onchange=()=>{
            this.rate=Number(this.speed.value);
            window.WhaleHost?.saveSpeechSpeed?.(this.rate);
            this.player.playbackRate=rates[this.rate+1];
        };
        document.querySelector('#voice-preview').onclick=()=>this.say('held',true);
        this.library=fetch('voices/lines.json').then(r=>r.json()).catch(()=>null);
        this.player.onended=()=>{
            if(this.line){
                this.bubble.textContent=this.line.text;
                this.tailUntil=performance.now()+1500;
            }
        };
        this.resetIdle();
    }
    resetIdle(){this.idleAt=performance.now()+28000+Math.random()*27000;}
    idle(quiet){
        if(!quiet)return;
        if(performance.now()>this.idleAt){this.resetIdle();this.say('idle');}
    }
    pick(group,items){
        const bag=this.bags[group]??=[];
        if(!bag.length){
            bag.push(...items);
            for(let i=bag.length-1;i>0;i--){
                const j=Math.floor(Math.random()*(i+1));[bag[i],bag[j]]=[bag[j],bag[i]];
            }
            if(bag.length>1 && bag[bag.length-1]===this.last[group])
                [bag[0],bag[bag.length-1]]=[bag[bag.length-1],bag[0]];
        }
        return this.last[group]=bag.pop();
    }
    async say(group,preview=false){
        const now=performance.now();
        if(document.hidden || (!preview && (now<this.next || this.line) && !(group==='dizzy' && this.group==='held')))return;
        if(!preview && this.last[group] && Math.random()<.25)return;
        this.stop();const token=this.serial;
        this.next=now+(group==='idle'?5000:3200);
        try{
            const library=await this.library;
            if(token!==this.serial || !library)return;
            const line=preview?library.held[0]:this.pick(group,library[group]);
            const res=await fetch('voices/'+line.id+'.wav');
            if(!res.ok)throw Error();
            const blob=await res.blob();
            if(token!==this.serial)return;
            this.url=URL.createObjectURL(blob);this.player.src=this.url;
            this.player.preservesPitch=true;
            this.player.playbackRate=rates[this.rate+1];
            this.group=group;this.line=line;this.tailUntil=0;
            await this.player.play();
        }catch{if(token===this.serial)this.stop();}
    }
    stop(){
        this.serial++;this.player.pause();this.line=null;this.group=null;
        this.bubble.hidden=true;this.player.removeAttribute('src');
        if(this.url){URL.revokeObjectURL(this.url);this.url=null;}
        this.resetIdle();
    }
    update(bounds){
        const line=this.line;
        if(!line || this.player.readyState<2)return null;
        if(this.tailUntil && performance.now()>this.tailUntil){this.stop();return null;}
        const t=this.player.currentTime*1000;
        const cues=line.cues;
        let i=0;while(i<cues.length && cues[i][1]<=t)i++;
        const end=i<cues.length?cues[i][0]:line.text.length;
        const text=line.text.slice(0,end);
        if(this.bubble.textContent!==text)this.bubble.textContent=text;
        const {cx,cy,ry}=bounds();
        this.bubble.hidden=!text || cx<0 || cx>innerWidth || cy<0 || cy>innerHeight;
        if(!this.bubble.hidden){
            const half=this.bubble.offsetWidth/2+6;
            this.bubble.style.left=Math.max(half,Math.min(innerWidth-half,cx))+'px';
            this.bubble.style.top=Math.max(this.bubble.offsetHeight+4,cy-ry-5)+'px';
        }
        if(this.player.ended || this.tailUntil)return null;
        const elapsed=i?t-cues[i-1][1]:Infinity;
        const span=i<cues.length && i?Math.min(115,cues[i][1]-cues[i-1][1]):110;
        return elapsed<span && span>0?.58*Math.sin(Math.PI*elapsed/span):0;
    }
}
