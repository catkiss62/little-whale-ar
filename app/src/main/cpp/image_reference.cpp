#include <cmath>
#include <memory>
#include <algorithm>
#include <android/bitmap.h>
#include <jni.h>
#include <opencv2/calib3d.hpp>
#include <opencv2/features2d.hpp>
#include <opencv2/imgproc.hpp>
#include <array>
#include <vector>

namespace {
struct Target {
    cv::Ptr<cv::ORB> orb=cv::ORB::create(900);
    std::vector<cv::KeyPoint> points;
    cv::Mat descriptors;
    cv::Size size;
};
cv::Mat gray(JNIEnv* env,jobject bitmap){
    AndroidBitmapInfo info{};
    void* pixels=nullptr;
    if(AndroidBitmap_getInfo(env,bitmap,&info)!=0 || info.format!=ANDROID_BITMAP_FORMAT_RGBA_8888 ||
       AndroidBitmap_lockPixels(env,bitmap,&pixels)!=0)return {};
    cv::Mat result;
    try{
        cv::Mat rgba(info.height,info.width,CV_8UC4,pixels,info.stride);
        cv::cvtColor(rgba,result,cv::COLOR_RGBA2GRAY);
    }catch(...){AndroidBitmap_unlockPixels(env,bitmap);throw;}
    AndroidBitmap_unlockPixels(env,bitmap);return result;
}
}
extern "C" JNIEXPORT jlong JNICALL Java_dev_littlewhale_ar_ImageLabActivity_registerImage(JNIEnv* e,jclass,jobject bitmap){
    try{
        std::unique_ptr<Target> target(new Target());auto image=gray(e,bitmap);if(image.empty())return 0;
        target->size=image.size();
        target->orb->detectAndCompute(image,cv::noArray(),target->points,target->descriptors);
        if(target->points.size()<35)return 0;
        return reinterpret_cast<jlong>(target.release());
    }catch(...){return 0;}
}
extern "C" JNIEXPORT void JNICALL Java_dev_littlewhale_ar_ImageLabActivity_releaseImage(JNIEnv*,jclass,jlong handle){
    delete reinterpret_cast<Target*>(handle);
}
extern "C" JNIEXPORT jfloatArray JNICALL Java_dev_littlewhale_ar_ImageLabActivity_locate(JNIEnv* e,jclass,jlong handle,jobject bitmap){
    try{
        auto* target=reinterpret_cast<Target*>(handle);if(!target)return nullptr;
        auto image=gray(e,bitmap);if(image.empty())return nullptr;
        std::vector<cv::KeyPoint> points;cv::Mat descriptors;
        target->orb->detectAndCompute(image,cv::noArray(),points,descriptors);
        if(points.size()<16)return nullptr;
        std::vector<std::vector<cv::DMatch>> pairs;
        cv::BFMatcher(cv::NORM_HAMMING).knnMatch(target->descriptors,descriptors,pairs,2);
        std::vector<cv::Point2f> src,dst;
        std::vector<bool> used(points.size(),false);
        for(const auto& pair:pairs){
            if(pair.size()!=2 || pair[0].distance>=.75f*pair[1].distance)continue;
            const auto& m=pair[0];if(used[m.trainIdx])continue;
            used[m.trainIdx]=true;src.push_back(target->points[m.queryIdx].pt);dst.push_back(points[m.trainIdx].pt);
        }
        if(src.size()<14)return nullptr;
        cv::Mat mask,H=cv::findHomography(src,dst,cv::RANSAC,3,mask);
        if(H.empty() || cv::countNonZero(mask)<std::max(12,int(.48*src.size())))return nullptr;
        // Reject a homography supported by only a tiny logo or a strip of text.
        std::vector<cv::Point2f> support,hull;
        for(int i=0;i<mask.rows;i++)if(mask.at<uchar>(i))support.push_back(src[i]);
        cv::convexHull(support,hull);
        if(std::abs(cv::contourArea(hull))<.09*target->size.area())return nullptr;
        float w=target->size.width,h=target->size.height;
        std::vector<cv::Point2f> corners={{0,0},{w,0},{w,h},{0,h},{w*.5f,h*.5f}},projected;
        cv::perspectiveTransform(corners,projected,H);
        for(auto p:projected)if(!std::isfinite(p.x)||!std::isfinite(p.y)||p.x < -image.cols || p.x>2*image.cols || p.y < -image.rows || p.y>2*image.rows)return nullptr;
        std::array<float,10> out{};
        for(int i=0;i<5;i++){out[2*i]=projected[i].x;out[2*i+1]=projected[i].y;}
        projected.pop_back();
        double area=cv::contourArea(projected,true);
        if(!cv::isContourConvex(projected)||area<.012*image.size().area()||area>2*image.size().area())return nullptr;
        jfloatArray result=e->NewFloatArray(out.size());if(result)e->SetFloatArrayRegion(result,0,out.size(),out.data());return result;
    }catch(...){return nullptr;}
}
