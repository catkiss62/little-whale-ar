const {chromium}=require('playwright');
const assert=require('node:assert/strict');
(async()=>{const b=await chromium.launch({executablePath:process.env.CHROME_BIN,args:['--no-sandbox','--enable-unsafe-swiftshader','--use-angle=swiftshader']});
try{
const p=await b.newPage({viewport:{width:480,height:640},hasTouch:true});
const errs=[];p.on('pageerror',e=>{errs.push(e.message);console.error(e.message)});
await p.addInitScript(()=>{window.logs=[];window.WhaleHost={status:()=>{},surfaceTap:(...x)=>logs.push(['tap',...x]),carryState:x=>logs.push(['hold',x])}});
await p.goto('http://127.0.0.1:18811');await p.evaluate(()=>initFixture());
await p.touchscreen.tap(150,350);assert.equal(await p.evaluate(()=>logs[0][0]),'tap');
await p.evaluate(()=>{placePose();window.poseTimer=setInterval(placePose,40)});
const bounds=(await p.evaluate(()=>peek())).bounds;
const c=await p.context().newCDPSession(p);
const touch=(type,x,y)=>c.send('Input.dispatchTouchEvent',{type,touchPoints:type==='touchEnd'?[]:[{x,y}]});
await touch('touchStart',bounds.cx,bounds.cy-4);
await p.waitForFunction(()=>peek().holding);await p.waitForTimeout(450);
let held=await p.evaluate(()=>peek());assert(held.angle>.05 && held.y>0 && held.clip>4);
await touch('touchEnd');await p.waitForFunction(()=>!peek().holding&&peek().landing===0);await p.touchscreen.tap(24,550);
assert.deepEqual(await p.evaluate(()=>logs.map(x=>x.slice(0,x[0]==='hold'?2:1))),[['tap'],['hold',true],['hold',false]]);
const [tx,ty]=await p.evaluate(()=>tailPixel());
await touch('touchStart',tx,ty);await p.waitForFunction(()=>peek().holding && tailError()!==null);
await touch('touchMove',tx+20,ty-15);
await p.waitForFunction(()=>tailError()!==null && tailError()<2);
await touch('touchEnd');await p.waitForFunction(()=>!peek().holding);
{
    await p.locator('#coin-button').click();assert.equal(await p.evaluate(()=>coinState()),'placing');
    const [x,y]=await p.evaluate(()=>coinPixel());await p.touchscreen.tap(x,y);
    await p.waitForFunction(()=>coinState()==='lift');
    await p.waitForFunction(()=>coinState()==='hold');
    await p.locator('#coin-button').click();assert.equal(await p.evaluate(()=>coinState()),null);
}
const destination=await p.evaluate(()=>coinPixel());await p.touchscreen.tap(...destination);
await p.waitForFunction(()=>walkActive());await p.waitForFunction(()=>!walkActive());
await p.evaluate(()=>testTailVoice());
await p.waitForFunction(()=>speechState().group==='tail' && speechState().time>0 && speechState().visible);
assert.equal(await p.evaluate(()=>speechState().rate),1);
await p.locator('#voice').click();
assert.equal(await p.locator('#voice').getAttribute('aria-pressed'),'false');
assert.equal(await p.evaluate(()=>speechState().visible),false);
await p.evaluate(()=>testTailVoice());
assert.equal(await p.evaluate(()=>speechState().group),null);
await p.locator('#voice').click();
await p.evaluate(()=>{
    clearInterval(window.poseTimer);
    window.WhaleHost.placedOnPalm=side=>window.placedSide=side;
    whale.choosePalmPoint();
    whale.placePalmCenter();
});
assert.equal(await p.evaluate(()=>window.placedSide),undefined);
await p.evaluate(()=>{
    whale.palmSurface([.4,.4,.5,.35,.6,.4,.6,.6,.5,.65,.4,.6],.2,0,[1,0,0,0,1,0]);
    whale.placePalmCenter();
});
assert.equal(await p.evaluate(()=>window.placedSide),'Left');
await p.evaluate(()=>whale.choosePalmPoint());
await p.evaluate(()=>{clearInterval(window.poseTimer);window.WhaleHost.importModel=()=>window.importRequested=true;whale.missing();whale.reset();});
await p.waitForTimeout(100);
await p.touchscreen.tap(240,320);await p.waitForFunction(()=>window.importRequested);
assert.equal(errs.length,0);console.log('Blank tap, original hold clip '+held.clip.toFixed(2)+' s, arm rotation '+held.angle.toFixed(2)+' rad, release and next blank tap passed');
}finally{await b.close()}})().catch(e=>{console.error(e);process.exitCode=1});
