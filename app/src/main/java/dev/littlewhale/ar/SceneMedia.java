package dev.littlewhale.ar;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.widget.ImageView;
import android.widget.Toast;
import java.io.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class SceneMedia {
    private final Context context;
    private final ContentResolver resolver;
    private final Handler main=new Handler(Looper.getMainLooper());
    volatile View preview;
    private volatile boolean closed;
    private Uri pending;
    private OutputStream stream;
    SceneMedia(Context c){context=c.getApplicationContext();resolver=c.getContentResolver();}

    InputStream cameraFrame(boolean photo) throws Exception {
        CompletableFuture<Bitmap> result=new CompletableFuture<>();
        main.post(()->{
            View source=preview;
            try{
                if(closed || source==null || !source.isShown() || source.getWidth()<2 || source.getHeight()<2)
                    throw new IOException("相机画面未就绪");
                float ratio=Math.min(1f,(photo?1920f:960f)/Math.max(source.getWidth(),source.getHeight()));
                int w=Math.max(2,Math.round(source.getWidth()*ratio/2)*2);
                int h=Math.max(2,Math.round(source.getHeight()*ratio/2)*2);
                Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                if(source instanceof SurfaceView){
                    PixelCopy.request((SurfaceView)source,bitmap,status->{
                        if(status==PixelCopy.SUCCESS && result.complete(bitmap))return;
                        bitmap.recycle();
                        result.completeExceptionally(new IOException("预览帧暂不可用"));
                    },main);
                }else{
                    if(!(source instanceof ImageView) || ((ImageView)source).getDrawable()==null)throw new IOException("缺少参考图预览帧");
                    Canvas canvas=new Canvas(bitmap);
                    canvas.drawColor(android.graphics.Color.BLACK);
                    canvas.scale((float)w/source.getWidth(),(float)h/source.getHeight());
                    ImageView image=(ImageView)source;
                    canvas.concat(image.getImageMatrix());
                    image.getDrawable().draw(canvas);
                    if(!result.complete(bitmap))bitmap.recycle();
                }
            }catch(Exception e){result.completeExceptionally(e);}
        });
        Bitmap frame;
        try{frame=result.get(2,TimeUnit.SECONDS);}finally{result.cancel(false);}
        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            if(!frame.compress(Bitmap.CompressFormat.JPEG,photo?95:85,bytes))throw new IOException();
            return new ByteArrayInputStream(bytes.toByteArray());
        }finally{frame.recycle();}
    }

    @JavascriptInterface public synchronized boolean begin(boolean video){
        if(closed || pending!=null)return false;
        try{
            ContentValues values=new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME,"LittleWhale-"+System.currentTimeMillis()+(video?".mp4":".jpg"));
            values.put(MediaStore.MediaColumns.MIME_TYPE,video?"video/mp4":"image/jpeg");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,(video?"Movies":"Pictures")+"/LittleWhale");
            values.put(MediaStore.MediaColumns.IS_PENDING,1);
            pending=resolver.insert(video?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
            if(pending==null)return false;
            stream=resolver.openOutputStream(pending);
            if(stream==null)throw new IOException();
            return true;
        }catch(Exception e){finish(false);return false;}
    }
    @JavascriptInterface public synchronized boolean append(String block){
        if(stream==null || block.length()>131072)return false;
        try{stream.write(Base64.decode(block,Base64.DEFAULT));return true;}
        catch(Exception e){finish(false);return false;}
    }
    @JavascriptInterface public synchronized boolean finish(boolean publish){
        if(pending==null)return false;
        Uri item=pending;
        boolean saved=false;
        try{
            if(stream!=null)stream.close();
            if(publish){
                ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.IS_PENDING,0);
                saved=resolver.update(item,v,null,null)==1;
            }
        }catch(Exception ignored){}
        finally{
            if(!saved){try{resolver.delete(item,null,null);}catch(Exception ignored){}}
            pending=null;stream=null;
        }
        return saved;
    }
    @JavascriptInterface public void notice(String message){
        main.post(()->Toast.makeText(context,message,Toast.LENGTH_SHORT).show());
    }
    synchronized void close(){closed=true;preview=null;finish(false);}
}
