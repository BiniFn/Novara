#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include "realcugan.h"
#include "cpu.h"
#include "gpu.h"

#define TAG "RealCuganJni"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static RealCUGAN* realcugan = nullptr;

extern "C" JNIEXPORT jlong JNICALL
Java_org_skepsun_kototoro_reader_translate_data_RealCuganNcnnEngine_initNative(JNIEnv *env, jobject thiz, jstring paramPath, jstring binPath, jint gpuId, jboolean ttaMode) {
    const char *param_str = env->GetStringUTFChars(paramPath, nullptr);
    const char *bin_str = env->GetStringUTFChars(binPath, nullptr);

    int gpuid = gpuId;
    if (gpuid >= 0) {
        if (ncnn::get_gpu_count() == 0) gpuid = -1;
    }

    if (realcugan != nullptr) {
        delete realcugan;
        realcugan = nullptr;
    }

    realcugan = new RealCUGAN(gpuid, ttaMode);
    
    // Default model args for RealCUGAN anime models
    realcugan->noise = -1;
    realcugan->scale = 2; // Can be parameterized if needed
    realcugan->tilesize = 400;
    realcugan->syncgap = 3;
    realcugan->prepadding = 3;

    int ret = realcugan->load(param_str, bin_str);

    env->ReleaseStringUTFChars(paramPath, param_str);
    env->ReleaseStringUTFChars(binPath, bin_str);

    if (ret != 0) {
        LOGE("Failed to load RealCUGAN models.");
        delete realcugan;
        realcugan = nullptr;
        return 0;
    }
    LOGD("RealCUGAN initialized successfully on GPU %d", gpuid);
    return reinterpret_cast<jlong>(realcugan);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_skepsun_kototoro_reader_translate_data_RealCuganNcnnEngine_processNative(JNIEnv *env, jobject thiz, jlong nativeHandle, jobject inBitmap, jobject outBitmap) {
    auto* cugan = reinterpret_cast<RealCUGAN*>(nativeHandle);
    if (!cugan) return JNI_FALSE;

    AndroidBitmapInfo inInfo, outInfo;
    AndroidBitmap_getInfo(env, inBitmap, &inInfo);
    AndroidBitmap_getInfo(env, outBitmap, &outInfo);

    void* inPixels;
    void* outPixels;
    AndroidBitmap_lockPixels(env, inBitmap, &inPixels);
    AndroidBitmap_lockPixels(env, outBitmap, &outPixels);

    // NCNN's RealCUGAN wrapper expects tightly packed flat buffers without padding.
    // Android bitmaps can have stride padding, so we must pack them densely if stride != width * 4.
    bool inNeedsCopy = (inInfo.stride != inInfo.width * 4);
    bool outNeedsCopy = (outInfo.stride != outInfo.width * 4);

    uint8_t* denseIn = (uint8_t*)inPixels;
    if (inNeedsCopy) {
        denseIn = new uint8_t[inInfo.width * inInfo.height * 4];
        for (uint32_t y = 0; y < inInfo.height; y++) {
            memcpy(denseIn + y * inInfo.width * 4, 
                   (uint8_t*)inPixels + y * inInfo.stride, 
                   inInfo.width * 4);
        }
    }

    uint8_t* denseOut = (uint8_t*)outPixels;
    if (outNeedsCopy) {
        denseOut = new uint8_t[outInfo.width * outInfo.height * 4];
    }

    ncnn::Mat inMat(inInfo.width, inInfo.height, denseIn, (size_t)4u, 4);
    ncnn::Mat outMat(outInfo.width, outInfo.height, denseOut, (size_t)4u, 4);
    
    int ret = cugan->process(inMat, outMat);

    if (outNeedsCopy && ret == 0) {
        for (uint32_t y = 0; y < outInfo.height; y++) {
            memcpy((uint8_t*)outPixels + y * outInfo.stride, 
                   denseOut + y * outInfo.width * 4, 
                   outInfo.width * 4);
        }
    }

    if (inNeedsCopy) delete[] denseIn;
    if (outNeedsCopy) delete[] denseOut;

    AndroidBitmap_unlockPixels(env, inBitmap);
    AndroidBitmap_unlockPixels(env, outBitmap);

    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_org_skepsun_kototoro_reader_translate_data_RealCuganNcnnEngine_releaseNative(JNIEnv *env, jobject thiz, jlong nativeHandle) {
    auto* cugan = reinterpret_cast<RealCUGAN*>(nativeHandle);
    if (cugan) {
        delete cugan;
        if (cugan == realcugan) realcugan = nullptr;
        LOGD("RealCUGAN native instance released.");
    }
}
