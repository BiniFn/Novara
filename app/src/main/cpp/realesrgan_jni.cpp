#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "realesrgan.h"
#include "cpu.h"
#include "gpu.h"

#define TAG "RealEsrganJni"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static RealESRGAN* realesrgan = nullptr;

extern "C" JNIEXPORT jlong JNICALL
Java_org_skepsun_kototoro_reader_translate_data_RealEsrganNcnnEngine_initNative(JNIEnv *env, jobject thiz, jstring paramPath, jstring binPath, jint gpuId, jboolean ttaMode) {
    const char *param_str = env->GetStringUTFChars(paramPath, nullptr);
    const char *bin_str = env->GetStringUTFChars(binPath, nullptr);

    int gpuid = gpuId;
    if (gpuid >= 0) {
        if (ncnn::get_gpu_count() == 0) gpuid = -1;
    }

    if (realesrgan != nullptr) {
        delete realesrgan;
        realesrgan = nullptr;
    }

    realesrgan = new RealESRGAN(gpuid, ttaMode);
    
    // Default model args for RealESRGAN anime models
    realesrgan->scale = 4; // Most RealESRGAN anime models are 4x by default
    realesrgan->tilesize = 400; // 400 is optimal for modern mobile Adreno/Mali
    realesrgan->prepadding = 10;
    
    int ret = realesrgan->load(param_str, bin_str);

    env->ReleaseStringUTFChars(paramPath, param_str);
    env->ReleaseStringUTFChars(binPath, bin_str);

    if (ret != 0) {
        LOGE("Failed to load RealESRGAN models.");
        delete realesrgan;
        realesrgan = nullptr;
        return 0;
    }
    LOGD("RealESRGAN initialized successfully on GPU %d", gpuid);
    return reinterpret_cast<jlong>(realesrgan);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_skepsun_kototoro_reader_translate_data_RealEsrganNcnnEngine_processNative(JNIEnv *env, jobject thiz, jlong nativeHandle, jobject inBitmap, jobject outBitmap) {
    auto* esrgan = reinterpret_cast<RealESRGAN*>(nativeHandle);
    if (!esrgan) return JNI_FALSE;

    AndroidBitmapInfo inInfo, outInfo;
    AndroidBitmap_getInfo(env, inBitmap, &inInfo);
    AndroidBitmap_getInfo(env, outBitmap, &outInfo);

    void* inPixels;
    void* outPixels;
    AndroidBitmap_lockPixels(env, inBitmap, &inPixels);
    AndroidBitmap_lockPixels(env, outBitmap, &outPixels);

    // NCNN's RealESRGAN wrapper expects tightly packed flat buffers without padding.
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
    
    int ret = esrgan->process(inMat, outMat);

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
Java_org_skepsun_kototoro_reader_translate_data_RealEsrganNcnnEngine_releaseNative(JNIEnv *env, jobject thiz, jlong nativeHandle) {
    auto* esrgan = reinterpret_cast<RealESRGAN*>(nativeHandle);
    if (esrgan) {
        delete esrgan;
        if (esrgan == realesrgan) realesrgan = nullptr;
        LOGD("RealESRGAN native instance released.");
    }
}
