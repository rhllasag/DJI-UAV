//
// Created by homer on 04/10/2018.
//
#include <android/log.h>
#include <android/bitmap.h>

#include "opencv2/objdetect.hpp"
#include "opencv2/imgproc.hpp"
using cv::Mat;
using cv::Size;
using cv::Point;
using cv::Rect;
using cv::ellipse;
using cv::Scalar;
using cv::getTickCount;
using cv::getTickFrequency;
#define  LOG_TAG    "PedestrianDetection"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define  DEBUG 1
/** Global variables */
char full_body_cascade_path[100];
char humanDetectionPath[100];
cv::CascadeClassifier humanDetection;
cv::CascadeClassifier full_body_cascade;
double t;
char evar[200];
std::string hello;
extern "C"
JNIEXPORT void JNICALL
Java_com_dji_videostreamdecodingsample_activities_MainActivity_computerVision(JNIEnv *pEnv,
                                                                              jobject instance,
                                                                              jobject pTarget,
                                                                              jbyteArray pSource) {
    AndroidBitmapInfo bitmapInfo;
    uint32_t* bitmapContent; // Links to Bitmap content


    if(AndroidBitmap_getInfo(pEnv, pTarget, &bitmapInfo) < 0) abort();
    if(bitmapInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) abort();
    if(AndroidBitmap_lockPixels(pEnv, pTarget, (void**)&bitmapContent) < 0) abort();

    /// Access source array data... OK
    jbyte* source = (jbyte*)pEnv->GetPrimitiveArrayCritical(pSource, 0);
    if (source == NULL) abort();

    /// cv::Mat for YUV420sp source and output BGRA
    Mat srcGray(bitmapInfo.height, bitmapInfo.width, CV_8UC1, (unsigned char *)source);
    Mat mbgra(bitmapInfo.height, bitmapInfo.width, CV_8UC4, (unsigned char *)bitmapContent);
    /// Native Image Processing HERE...
    if(DEBUG){
        __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "Starting native image processing...");
    }

    if (full_body_cascade.empty()){
        t = (double)getTickCount();
        sprintf( full_body_cascade_path, "%s/%s",evar , "visionary.net_pedestrian_cascade_web_LBP.xml");


        if( !full_body_cascade.load(full_body_cascade_path) ){
            __android_log_write(ANDROID_LOG_ERROR, LOG_TAG, "Error loading cat face cascade");
            abort();
        };

        t = 1000*((double)getTickCount() - t)/getTickFrequency();
        if(DEBUG){
            __android_log_write(ANDROID_LOG_DEBUG, LOG_TAG, "Loading full body cascade took  milliseconds");
        }
    }


    std::vector<Rect> fbody;


    //-- Detect full body
    t = (double)getTickCount();

    /// Detection took cat_face_cascade.detectMultiScale() time = 655.334471 ms
    // cat_face_cascade.detectMultiScale( srcGray, faces, 1.1, 2 , 0 , Size(30, 30) ); // Scaling factor = 1.1;  minNeighbors = 2 ; flags = 0; minimumSize = 30,30

    // cat_face_cascade.detectMultiScale() time = 120.117185 ms
    // cat_face_cascade.detectMultiScale( srcGray, faces, 1.2, 3 , 0 , Size(64, 64));



    full_body_cascade.detectMultiScale( srcGray, fbody, 1.2, 2 , 0 , Size(14, 28));  // Size(double width, double height)
    // scalingFactor parameters determine how much the classifier will be scaled up after each run.
    // minNeighbors parameter specifies how many positive neighbors a positive face rectangle should have to be considered a possible match;
    // when a potential face rectangle is moved a pixel and does not trigger the classifier any more, it is most likely that it’s a false positive.
    // Face rectangles with fewer positive neighbors than minNeighbors are rejected.
    // If minNeighbors is set to zero, all potential face rectangles are returned.
    // The flags parameter is from the OpenCV 1.x API and should always be 0.
    // minimumSize specifies the smallest face rectangle we’re looking for.

    t = 1000*((double)getTickCount() - t)/getTickFrequency();
    if(DEBUG){

        __android_log_write(ANDROID_LOG_DEBUG, LOG_TAG, "full_body_cascade.detectMultiScale()");

    }


    // Iterate through all faces and detect eyes
    t = (double)getTickCount();

    for( size_t i = 0; i < fbody.size(); i++ )
    {
        rectangle(srcGray,Point(fbody[i].x,fbody[i].y),Point(fbody[i].x + fbody[i].width,fbody[i].y + fbody[i].height ),Scalar(0,0,0),4,8,0);
    }//endfor

    t = 1000*((double)getTickCount() - t)/getTickFrequency();
    if(DEBUG){
        __android_log_write(ANDROID_LOG_DEBUG, LOG_TAG, "Iterate through all faces and detecting eyes took");

    }

    /// Display to Android
    //cvtColor(srcGray, mbgra, CV_GRAY2BGRA);

    cvtColor(srcGray,mbgra,CV_GRAY2BGRA);
    equalizeHist(srcGray,srcGray);

    if(DEBUG){
        __android_log_write(ANDROID_LOG_DEBUG, LOG_TAG, "Successfully finished native image processing...");

    }

    pEnv-> ReleasePrimitiveArrayCritical(pSource,source,0);
    if (AndroidBitmap_unlockPixels(pEnv, pTarget) < 0) abort();
}extern "C"
JNIEXPORT jstring JNICALL
Java_com_dji_videostreamdecodingsample_services_Assetbridge_loadXml(JNIEnv *env, jobject instance,
                                                                    jstring path_) {
    const char *path = env->GetStringUTFChars(path_, 0);
    strcpy(evar, path);
    strcat(evar, "/");
    putenv(evar);
    hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
extern "C"
JNIEXPORT void JNICALL
Java_com_dji_videostreamdecodingsample_activities_MainActivity_humanDetection(JNIEnv *env,
                                                                              jobject instance,
                                                                              jlong addrRgba) {
    Mat& frame= *(Mat*) addrRgba;
    if(frame.channels()>0)
    __android_log_write(ANDROID_LOG_DEBUG, LOG_TAG, "has channels");
    if (humanDetection.empty()){
        t = (double)getTickCount();
        sprintf( humanDetectionPath, "%s/%s",evar , "visionary.net_pedestrian_cascade_web_LBP.xml");


        if( !humanDetection.load(humanDetectionPath) ){
            abort();
        };

        t = 1000*((double)getTickCount() - t)/getTickFrequency();
    }

    //-- Detect full body
    t = (double)getTickCount();
    std::vector<Rect> fbody;
    Mat frameGray;
    cvtColor(frame,frameGray,CV_BGR2GRAY);
    equalizeHist(frameGray,frameGray);
    humanDetection.detectMultiScale( frameGray, fbody, 1.1, 2 , 0|CV_HAAR_SCALE_IMAGE , Size(36, 36));  // Size(double width, double height)
    t = 1000*((double)getTickCount() - t)/getTickFrequency();
    // Iterate through all faces and detect eyes
    t = (double)getTickCount();

    for( size_t i = 0; i < fbody.size(); i++ )
    {
        rectangle(frame,Point(fbody[i].x,fbody[i].y),Point(fbody[i].x + fbody[i].width,fbody[i].y + fbody[i].height ),Scalar(0,128,0),4,8,0);
    }
}