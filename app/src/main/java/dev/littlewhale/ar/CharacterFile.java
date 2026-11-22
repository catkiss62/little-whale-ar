package dev.littlewhale.ar;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

final class CharacterFile {
    static final String SOURCE = "https://github.com/CF-612/SmallBlueFish-Model/releases/download/dot-test-20261104-v21-fullmodel/SmallBlueFish-V21-AllExpressions-Acceptance.vrm";
    static final long SIZE = 348998140L;
    private static final String SHA = "cf331c0f5c62f818b05bb7f132bd34424bdd99784bb908d5f4651d901f855514";
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    static volatile boolean busy;
    static volatile int percent;
    static volatile String failure;
    static File file(Context c) { return new File(c.getFilesDir(), "SmallBlueFish-V21.vrm"); }
    static synchronized void obtain(Context c, Uri uri) {
        if (busy || file(c).isFile()) return;
        final Context app=c.getApplicationContext();
        busy=true; percent=0; failure=null;
        worker.execute(()->{
            File temp=new File(app.getFilesDir(), "v21.part");
            HttpURLConnection connection=null;
            try {
                InputStream source;
                if(uri==null){
                    connection=(HttpURLConnection)new URL(SOURCE).openConnection();
                    connection.setConnectTimeout(20000); connection.setReadTimeout(45000);
                    source=connection.getInputStream();
                } else source=app.getContentResolver().openInputStream(uri);
                if(source==null) throw new IOException("无法读取所选文件");
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(InputStream in=source; FileOutputStream out=new FileOutputStream(temp)){
                    byte[] buf=new byte[131072]; long count=0; int n;
                    while((n=in.read(buf))!=-1){
                        count+=n;
                        if(count>SIZE) throw new IOException("文件与 V21 版本不符");
                        out.write(buf,0,n);digest.update(buf,0,n);
                        percent=(int)(100*count/SIZE);
                    }
                    if(count!=SIZE || !SHA.equals(hex(digest.digest()))) throw new IOException("V21 文件不完整或版本不符");
                }
                if(!temp.renameTo(file(app)))throw new IOException("保存模型失败");
            }catch(Exception e){
                Log.w("LittleWhaleAR","Model import",e);
                failure=uri==null?"下载失败，可重试或从本机导入 V21":"导入失败，请选择作者发布的完整 V21 VRM";
            }finally{temp.delete();busy=false;}
        });
    }
    private static String hex(byte[] bytes){
        StringBuilder s=new StringBuilder(64);
        for(byte b:bytes)s.append(Character.forDigit((b>>4)&15,16)).append(Character.forDigit(b&15,16));
        return s.toString();
    }
    // Re-encode only oversized material textures. Limit simultaneous bitmap allocations.
    static synchronized InputStream texture(Context c,long offset,long length)throws IOException{
        File cache=new File(c.getCacheDir(),"v21-"+offset+"-"+length+".png");
        if(cache.isFile())return new FileInputStream(cache);
        android.graphics.BitmapFactory.Options opt=new android.graphics.BitmapFactory.Options();
        opt.inJustDecodeBounds=true;
        try(InputStream in=range(c,false,offset,length)){android.graphics.BitmapFactory.decodeStream(in,null,opt);}
        int edge=Math.max(opt.outWidth,opt.outHeight);
        if(edge<=1024)return range(c,false,offset,length);
        opt.inJustDecodeBounds=false;opt.inSampleSize=1;
        while(edge/opt.inSampleSize>1024)opt.inSampleSize*=2;
        android.graphics.Bitmap bitmap;
        try(InputStream in=range(c,false,offset,length)){bitmap=android.graphics.BitmapFactory.decodeStream(in,null,opt);}
        if(bitmap==null)throw new IOException("Texture decode failed");
        File part=new File(cache.getPath()+".part");
        try{
            try(OutputStream out=new BufferedOutputStream(new FileOutputStream(part))){
                if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out))throw new IOException("Texture encode failed");
            }
            if(!part.renameTo(cache))throw new IOException("Texture cache unavailable");
        }finally{bitmap.recycle();part.delete();}
        return new FileInputStream(cache);
    }
    // Serve bounded slices from the original. Never copy the 349 MB container into the Java or JS heap.
    static InputStream range(Context c, boolean json,long offset,long length) throws IOException{
        FileInputStream f=new FileInputStream(file(c));
        try{
            byte[] header=new byte[20];new DataInputStream(f).readFully(header);
            long jsonLength=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
            long base=json?20:28+jsonLength;
            if(json){offset=0;length=jsonLength;}
            if(offset<0 || length<=0 || offset>SIZE-base || length>SIZE-base-offset)throw new IOException("invalid model slice");
            f.getChannel().position(base+offset);
            final long bytes=length;
            return new FilterInputStream(f){
                long left=bytes;
                @Override public int read(byte[] buf,int off,int len)throws IOException{
                    if(len==0)return 0;if(left==0)return -1;
                    int n=super.read(buf,off,(int)Math.min(left,len));if(n>0)left-=n;return n;
                }
                @Override public int read()throws IOException{
                    if(left==0)return -1;int value=super.read();if(value>=0)left--;return value;
                }
                @Override public long skip(long n)throws IOException{long skipped=super.skip(Math.min(n,left));left-=skipped;return skipped;}
                @Override public int available()throws IOException{return (int)Math.min(left,super.available());}
            };
        }catch(IOException|RuntimeException e){f.close();throw e;}
    }
}
