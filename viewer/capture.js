// Compose only the camera preview and the transparent scene. Controls never enter the movie.
async function writeBlob(album,blob){
    const bytes=new Uint8Array(await blob.arrayBuffer());
    for(let i=0;i<bytes.length;i+=32768){
        if(!album.append(btoa(String.fromCharCode(...bytes.subarray(i,i+32768)))))
            throw Error('相册写入失败');
    }
}

export function installCapture(drawOverlay,recordAudio){
    const album=window.SceneAlbum;
    if(!album)return;
    const panel=document.getElementById('capture');
    const [photo,video]=panel.querySelectorAll('button');
    panel.hidden=false;
    let active=null;
    function buttons(busy,recording=false){
        photo.disabled=busy;
        video.disabled=busy && !recording;
        video.textContent=recording?'停止录像':'录像';
    }
    async function paint(session,still){
        const response=await fetch('camera.jpg?mode='+(still?'photo':'video'),{cache:'no-store'});
        if(!response.ok)throw Error('还没有可拍摄的相机画面');
        const image=await createImageBitmap(await response.blob());
        try{
            if(active!==session || session.stopping)return;
            const {canvas,ctx}=session;
            if(!session.ready){canvas.width=image.width;canvas.height=image.height;session.ready=true;}
            ctx.drawImage(image,0,0,canvas.width,canvas.height);
            drawOverlay(ctx,canvas.width,canvas.height);
        }finally{image.close();}
    }
    function begin(){
        const canvas=document.createElement('canvas');
        const session={canvas,ctx:canvas.getContext('2d',{alpha:false}),ready:false,stopping:false};
        active=session;buttons(true);return session;
    }
    function done(s){
        clearTimeout(s.timer);
        s.stream?.getVideoTracks().forEach(t=>t.stop());
        s.sound?.close();
        if(active===s){active=null;buttons(false);}
    }
    photo.onclick=async()=>{
        if(active)return;
        const s=begin();
        try{
            await paint(s,true);
            if(active!==s || !s.ready || document.hidden)throw Error('拍摄已中断');
            const blob=await new Promise(resolve=>s.canvas.toBlob(resolve,'image/jpeg',.95));
            if(!blob || !album.begin(false))throw Error('照片保存失败');
            await writeBlob(album,blob);
            if(!album.finish(true))throw Error('照片保存失败');
            album.notice('照片已存入相册');
        }catch(e){album.finish(false);album.notice(e.message);}
        finally{done(s);}
    };
    function stop(s){
        s.stopping=true;clearTimeout(s.timer);buttons(true);
        if(s.recorder?.state==='recording')s.recorder.stop();
    }
    video.onclick=async()=>{
        if(active){if(active.recorder)stop(active);return;}
        const s=begin();
        try{
            const mime=['video/mp4;codecs=avc1.42E01E,mp4a.40.2','video/mp4'].find(
                type=>window.MediaRecorder?.isTypeSupported(type));
            if(!mime)throw Error('当前 Android WebView 不支持 MP4 录像');
            s.sound=recordAudio();
            await s.sound.ready;
            await paint(s,false);
            if(!s.ready || s.stopping || document.hidden)throw Error('录像已中断');
            s.stream=s.canvas.captureStream(24);
            s.stream.addTrack(s.sound.track);
            const rec=new MediaRecorder(s.stream,{mimeType:mime,videoBitsPerSecond:3500000});
            if(!album.begin(true))throw Error('无法创建相册视频');
            s.recorder=rec;
            let writes=Promise.resolve(),failed=false,hasData=false;
            rec.ondataavailable=e=>{
                if(!e.data.size)return;
                writes=writes.then(async()=>{
                    if(failed)return;
                    await writeBlob(album,e.data);hasData=true;
                }).catch(()=>{failed=true;stop(s);});
            };
            rec.onerror=()=>{failed=true;stop(s);};
            rec.onstop=async()=>{
                s.stopping=true;clearTimeout(s.timer);
                await writes;
                const ok=album.finish(!failed && hasData);
                done(s);
                album.notice(ok?'视频已存入相册':'录像保存失败');
            };
            rec.start(500);buttons(true,true);
            let misses=0;
            async function frame(){
                if(s.stopping || active!==s)return;
                const start=performance.now();
                try{await paint(s,false);misses=0;}
                catch{if(++misses>=5){stop(s);return;}}
                if(!s.stopping)s.timer=setTimeout(frame,Math.max(0,50-(performance.now()-start)));
            }
            frame();
        }catch(e){album.finish(false);done(s);album.notice(e.message);}
    };
    function pause(){if(active?.recorder || active?.sound)stop(active);}
    document.addEventListener('visibilitychange',()=>{if(document.hidden)pause();});
    return pause;
}
