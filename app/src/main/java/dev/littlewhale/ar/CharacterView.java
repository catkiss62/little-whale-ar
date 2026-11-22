package dev.littlewhale.ar;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.opengl.Matrix;
import android.webkit.*;
import com.menthaar.sdk.MenthaAR;
import com.orb.slam2s.graphics.DisplayCorrection;
import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class CharacterView extends WebView {
    private static final String HOST="whale.invalid";
    private final float[] mv=new float[16], projection=new float[16];
    private float scale;
    private boolean page, pending, visiblePose, disposed, started;
    boolean beingPetted;
    int renderFps, frameLimit=60;
    String state="starting";
    CharacterView(Context c){
        super(c);
        setBackgroundColor(Color.TRANSPARENT);
        getSettings().setJavaScriptEnabled(true);
        getSettings().setAllowContentAccess(false);
        getSettings().setAllowFileAccess(false);
        setWebChromeClient(new WebChromeClient());
        addJavascriptInterface(new Object(){
            @JavascriptInterface public void petting(boolean on){post(()->beingPetted=on && visiblePose && !disposed);}
            @JavascriptInterface public void reportFps(int value){post(()->{if(visiblePose && !disposed)renderFps=value;});}
            @JavascriptInterface public void status(String s){post(()->{
                if(disposed)return;
                if(s.equals("page")){page=true;state="waiting";setFrameLimit(frameLimit);startIfAvailable();}
                else if(s.equals("ready")||s.equals("loading")||s.equals("error"))state=s;
            });}
        },"WhaleHost");
        setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                Uri u=r.getUrl(); List<String> parts=u.getPathSegments();
                try{
                    if(!"https".equals(u.getScheme()) || !HOST.equals(u.getHost()))return missing();
                    if(parts.size()==1){
                        String name=parts.get(0);
                        if(name.equals("index.html")||name.equals("scene.bundle.js"))
                            return reply(name.endsWith(".js")?"application/javascript":"text/html",c.getAssets().open("viewer/"+name));
                        if(name.equals("model.json"))return reply("application/json",CharacterFile.range(c,true,0,0));
                    }
                    if(parts.size()==3 && parts.get(0).equals("image"))
                        return reply("image/png",CharacterFile.texture(c,Long.parseLong(parts.get(1)),Long.parseLong(parts.get(2))));
                    if(parts.size()==3 && parts.get(0).equals("bin"))
                        return reply("application/octet-stream",CharacterFile.range(c,false,Long.parseLong(parts.get(1)),Long.parseLong(parts.get(2))));
                }catch(Exception ignored){}
                return missing();
            }
        });
        loadUrl("https://"+HOST+"/index.html");
    }
    private static WebResourceResponse reply(String mime,InputStream in){
        return new WebResourceResponse(mime,null,200,"OK",Collections.singletonMap("Cache-Control","no-store"),in);
    }
    private static WebResourceResponse missing(){return new WebResourceResponse("text/plain","utf-8",404,"Not Found",Collections.emptyMap(),null);}
    void startIfAvailable(){
        if(page&&!started&&!disposed&&CharacterFile.file(getContext()).isFile()){
            started=true;state="loading";evaluateJavascript("whale.load()",null);
        }
    }
    void setFrameLimit(int limit){
        frameLimit=limit==30?30:60;
        if(page&&!disposed)evaluateJavascript("whale.setFrameLimit("+frameLimit+")",null);
    }
    void clearPose(boolean reset){
        if(!visiblePose&&!reset)return;
        visiblePose=false;beingPetted=false;renderFps=0;if(reset)scale=0;
        if(page&&!disposed)evaluateJavascript(reset?"whale.reset()":"whale.hide()",null);
    }
    void pose(MenthaAR.Result r,DisplayCorrection correction){
        Matrix.multiplyMM(mv,0,r.view,0,r.model,0);
        if(scale==0){if(!(mv[14]<0))return;scale=-mv[14]*.34f;}
        if(disposed||pending||!state.equals("ready"))return;
        Matrix.scaleM(mv,0,scale,-scale,-scale);
        Matrix.multiplyMM(projection,0,correction.getNdcTransform(),0,r.projection,0);
        pending=true;visiblePose=true;
        evaluateJavascript("whale.pose("+Arrays.toString(mv)+","+Arrays.toString(projection)+")", unused->pending=false);
    }
    void close(){disposed=true;removeJavascriptInterface("WhaleHost");destroy();}
}
