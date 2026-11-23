package dev.littlewhale.ar;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Two stored ZIP members of the author's pinned Quest release. */
final class ReferenceMotions {
    private static final String URL_BASE="https://github.com/CF-612/YuChat/releases/download/v0.5.1-quest-test/Desktop-SmallBlueFish-Quest.apk";
    enum Clip {
        HOLD("grabbed","local-grab",197100332,182916,"c51caaa527309216f5edccefdadc2ac73cc96837735e8124041d52a6646d0185"),
        WALK("locomotion_walk","local-locomotion",200436260,68156,"0d68383cffb93d9c1d1e98ab3cdd303562908bb4cabd24d9ae1eca438797eb92");
        final String name,path,digest;
        final int start,length;
        Clip(String name,String folder,int start,int length,String digest){
            this.name=name+".vrma";this.path="assets/Motions/"+folder+"/"+this.name;
            this.start=start;this.length=length;this.digest=digest;
        }
        File file(Context c){return new File(c.getFilesDir(),digest+".vrma");}
        byte[] read(InputStream in) throws Exception {
            byte[] data=new byte[length];
            new DataInputStream(in).readFully(data);
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(data);
            for(int i=0;i<hash.length;i++)
                if((hash[i]&255)!=Integer.parseInt(digest.substring(i*2,i*2+2),16))throw new IOException("动作指纹不符");
            return data;
        }
        void save(Context c,byte[] data)throws IOException{
            File tmp=File.createTempFile("motion-", ".tmp",c.getFilesDir());
            try {
                try(FileOutputStream out=new FileOutputStream(tmp)){out.write(data);}
                if(!tmp.renameTo(file(c)))throw new IOException("动作保存失败");
            } finally {tmp.delete();}
        }
        void download(Context c)throws Exception {
            HttpURLConnection h=(HttpURLConnection)new URL(URL_BASE).openConnection();
            int end=start+length-1;
            h.setRequestProperty("Range","bytes="+start+"-"+end);
            h.setRequestProperty("Accept-Encoding","identity");
            h.setConnectTimeout(8000);h.setReadTimeout(12000);
            try {
                if(h.getResponseCode()!=206 || !("bytes "+start+"-"+end+"/269644895").equals(h.getHeaderField("Content-Range")))
                    throw new IOException("原址不支持分段下载");
                try(InputStream in=h.getInputStream()){save(c,read(in));}
            } finally {h.disconnect();}
        }
    }
    static InputStream open(Context c,String name)throws Exception{
        if(!name.matches("[A-Za-z0-9_]+\\.vrma"))throw new FileNotFoundException(name);
        try { return c.getAssets().open("motions/"+name); }
        catch(FileNotFoundException missingBundledClip) { /* Older test builds use the import cache. */ }
        for(Clip clip:Clip.values())if(clip.name.equals(name)){
            if(!clip.file(c).isFile())clip.download(c);
            return new FileInputStream(clip.file(c));
        }
        throw new FileNotFoundException();
    }
    static void importFile(Context c,Uri uri)throws Exception{
        byte[][] data=new byte[Clip.values().length][];
        try(ZipInputStream z=new ZipInputStream(new BufferedInputStream(c.getContentResolver().openInputStream(uri)))){
            for(ZipEntry e;(e=z.getNextEntry())!=null;){
                for(Clip clip:Clip.values())if(clip.path.equals(e.getName())||clip.name.equals(e.getName()))
                    data[clip.ordinal()]=clip.read(z);
                if(!Arrays.asList(data).contains(null))break;
            }
        }
        if(Arrays.asList(data).contains(null))throw new IOException("未找到两个原版动作，请选动作包或 Quest 原包");
        for(Clip clip:Clip.values())clip.save(c,data[clip.ordinal()]);
    }
}
