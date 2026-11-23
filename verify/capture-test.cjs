const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROME_BIN,args:['--no-sandbox','--enable-unsafe-swiftshader','--use-angle=swiftshader']});
 try{
  const page=await browser.newPage({viewport:{width:480,height:640}});
  await page.addInitScript(()=>{
   window.WhaleHost={status(){},reportFps(){}};
   window.saved=[];
   const original=window.fetch;
   window.fetch=(url,options)=>{
    if(!String(url).startsWith('camera.jpg'))return original(url,options);
    const bg=document.createElement('canvas');bg.width=480;bg.height=640;
    const c=bg.getContext('2d');c.fillStyle='#b6322b';c.fillRect(0,0,480,640);
    return new Promise(resolve=>bg.toBlob(blob=>resolve(new Response(blob)),'image/jpeg'));
   };
   let parts;
   window.SceneAlbum={
    begin(){parts=[];return true;},append(data){parts.push(data);return true;},
    finish(ok){if(ok)saved.push(parts);return ok;},notice(message){console.log(message);}
   };
  });
  page.on('console',m=>console.log(m.text()));
  await page.goto('http://127.0.0.1:18811');
  await page.evaluate(async()=>{await initFixture();placePose();setInterval(placePose,40);});
  await page.getByText('拍照',{exact:true}).click();
  await page.waitForFunction(()=>saved.length===1);
  const pixels=await page.evaluate(async()=>{
   const img=await createImageBitmap(await new Response(new Blob(saved[0].map(s=>Uint8Array.from(atob(s),x=>x.charCodeAt(0))))).blob());
   const c=document.createElement('canvas');c.width=img.width;c.height=img.height;
   const ctx=c.getContext('2d');ctx.drawImage(img,0,0);
   const a=ctx.getImageData(0,0,c.width,c.height).data;
   let model=0;for(let i=0;i<a.length;i+=4)if(a[i+1]>125 && a[i+2]>125)model++;
   return [a[0],a[1],model];
  });
  assert(pixels[0]>160 && pixels[1]<65 && pixels[2]>30,'background and WebGL model visible');
  await page.getByText('录像',{exact:true}).click();
  await page.getByText('停止录像',{exact:true}).waitFor();
  const bounds=await page.evaluate(()=>peek().bounds);
  await page.waitForTimeout(400);await page.mouse.click(bounds.cx,bounds.cy);
  await page.waitForTimeout(1200);await page.getByText('停止录像',{exact:true}).click();
  await page.waitForFunction(()=>saved.length===2);
  const chunks=await page.evaluate(()=>saved[1]);
  const bytes=Buffer.concat(chunks.map(x=>Buffer.from(x,'base64')));
  assert(bytes.includes(Buffer.from('ftyp')) && bytes.includes(Buffer.from('soun')) && bytes.includes(Buffer.from('vide')));
  await fs.writeFile('/tmp/whale-capture-test.mp4',bytes);
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
