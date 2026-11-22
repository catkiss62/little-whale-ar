package dev.littlewhale.ar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.IntConsumer;

final class VrmCopy {
    static final long SIZE = 348998140L;
    private static final String HASH = "cf331c0f5c62f818b05bb7f132bd34424bdd99784bb908d5f4651d901f855514";

    static void copy(InputStream in, OutputStream out, IntConsumer progress) throws IOException {
        MessageDigest digest;
        try { digest=MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException impossible){ throw new AssertionError(impossible); }
        byte[] buffer=new byte[128*1024];
        long count=0;
        int n;
        while((n=in.read(buffer))!=-1){
            count+=n;
            if(count>SIZE)throw new IOException("文件大于完整 V21");
            out.write(buffer,0,n);
            digest.update(buffer,0,n);
            progress.accept((int)(100*count/SIZE));
        }
        StringBuilder hash=new StringBuilder(64);
        for(byte b:digest.digest())hash.append(Character.forDigit((b>>4)&15,16)).append(Character.forDigit(b&15,16));
        if(count!=SIZE || !HASH.contentEquals(hash))throw new IOException("V21 文件不完整或版本不符");
    }
}
