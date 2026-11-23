#!/usr/bin/env bash
set -eu
# Run the shipping image matcher with deterministic bitmap fixtures.
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir "$work/android"
cat >"$work/android/bitmap.h" <<'HEADER'
#pragma once
#include <jni.h>
#include <opencv2/core.hpp>
#define ANDROID_BITMAP_FORMAT_RGBA_8888 1
struct AndroidBitmapInfo { unsigned width,height,stride,format; };
static int AndroidBitmap_getInfo(JNIEnv*,jobject obj,AndroidBitmapInfo* out){
    auto& m=*reinterpret_cast<cv::Mat*>(obj);
    *out={unsigned(m.cols),unsigned(m.rows),unsigned(m.step),1};return 0;
}
static int AndroidBitmap_lockPixels(JNIEnv*,jobject obj,void** pixels){*pixels=reinterpret_cast<cv::Mat*>(obj)->data;return 0;}
static int AndroidBitmap_unlockPixels(JNIEnv*,jobject){return 0;}
HEADER
cat >"$work/test.cpp" <<'CPP'
#include "image_reference.cpp"
#include <cassert>
#include <iostream>
jfloat values[10];
jfloatArray JNICALL make(JNIEnv*,jsize n){assert(n==10);return reinterpret_cast<jfloatArray>(values);}
void JNICALL writeValues(JNIEnv*,jfloatArray,jsize,jsize n,const jfloat* p){std::copy(p,p+n,values);}
jobject bitmap(cv::Mat& m){return reinterpret_cast<jobject>(&m);}
int main(){
    JNINativeInterface_ table{};table.NewFloatArray=make;table.SetFloatArrayRegion=writeValues;
    JNIEnv env{&table};
    cv::Mat image(320,320,CV_8UC4,cv::Scalar(233,233,233,255));
    cv::RNG rng(431);
    for(int i=0;i<240;i++){
        cv::Point p(rng.uniform(5,315),rng.uniform(5,315));
        cv::rectangle(image,cv::Rect(p.x,p.y,rng.uniform(2,12),rng.uniform(3,10)),cv::Scalar(rng.uniform(0,150),46,98,255),-1);
    }
    auto target=Java_dev_littlewhale_ar_ImageLabActivity_registerImage(&env,nullptr,bitmap(image));assert(target);
    cv::Mat blank(480,640,CV_8UC4,cv::Scalar(100,100,100,255));
    assert(!Java_dev_littlewhale_ar_ImageLabActivity_registerImage(&env,nullptr,bitmap(blank)));
    assert(!Java_dev_littlewhale_ar_ImageLabActivity_locate(&env,nullptr,target,bitmap(blank)));
    std::vector<cv::Point2f> src={{0,0},{320,0},{320,320},{0,320}},dest={{111,65},{443,91},{409,380},{128,354}};
    auto matrix=cv::getPerspectiveTransform(src,dest);cv::Mat frame;
    cv::warpPerspective(image,frame,matrix,blank.size());
    auto detect=[&](){assert(Java_dev_littlewhale_ar_ImageLabActivity_locate(&env,nullptr,target,bitmap(frame)));
        for(int i=0;i<4;i++)assert(cv::norm(cv::Point2f(values[2*i],values[2*i+1])-dest[i])<5);};
    detect();
    // Force a missed canonical match and exercise the recently recognized appearance.
    auto* saved=reinterpret_cast<Target*>(target);
    cv::Mat original=saved->descriptors.clone();
    saved->descriptors=cv::Mat::zeros(original.size(),original.type());
    detect();
    for(int i=0;i<17;i++)assert(!Java_dev_littlewhale_ar_ImageLabActivity_locate(&env,nullptr,target,bitmap(blank)));
    assert(!Java_dev_littlewhale_ar_ImageLabActivity_locate(&env,nullptr,target,bitmap(frame)));
    saved->descriptors=original;detect();
    cv::Mat wrong=blank.clone();rng.fill(wrong,cv::RNG::UNIFORM,0,255);
    assert(!Java_dev_littlewhale_ar_ImageLabActivity_locate(&env,nullptr,target,bitmap(wrong)));
    detect();Java_dev_littlewhale_ar_ImageLabActivity_releaseImage(&env,nullptr,target);
    std::cout<<"reference registered, perspective corners within 5px, loss, false target and re-entry passed\n";
}
CPP
g++ -std=c++17 -O2 -I"$work" -Iapp/src/main/cpp -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -I/usr/include/opencv4 "$work/test.cpp" -lopencv_calib3d -lopencv_features2d -lopencv_imgproc -lopencv_core -o "$work/test"
"$work/test"

