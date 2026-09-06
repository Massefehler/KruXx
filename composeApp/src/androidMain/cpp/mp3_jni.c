#include <jni.h>
#include <stdint.h>
#include "lame.h"

JNIEXPORT jlong JNICALL
Java_app_kreate_android_downloads_Mp3Encoder_create(JNIEnv *env, jobject self, jint rate, jint channels) {
    if (channels < 1 || channels > 2 || rate <= 0) return 0;
    lame_t encoder = lame_init();
    if (!encoder) return 0;
    lame_set_in_samplerate(encoder, rate);
    lame_set_num_channels(encoder, channels);
    /* 320 kbit/s requires MPEG-1; preserve 44.1/48 kHz sources. */
    lame_set_out_samplerate(encoder, rate >= 48000 ? 48000 : 44100);
    lame_set_VBR(encoder, vbr_off);
    lame_set_brate(encoder, 320);
    lame_set_quality(encoder, 0);
    lame_set_bWriteVbrTag(encoder, 0);
    if (lame_init_params(encoder) < 0) {
        lame_close(encoder);
        return 0;
    }
    return (jlong)(intptr_t)encoder;
}

JNIEXPORT jint JNICALL
Java_app_kreate_android_downloads_Mp3Encoder_encode(JNIEnv *env, jobject self, jlong handle,
                                                  jshortArray pcm, jint samples, jbyteArray output) {
    lame_t encoder = (lame_t)(intptr_t)handle;
    if (!encoder || samples < 0 ||
        samples > (*env)->GetArrayLength(env, pcm) / lame_get_num_channels(encoder)) return -1;
    jshort *input = (*env)->GetShortArrayElements(env, pcm, NULL);
    if (!input) return -1;
    jbyte *bytes = (*env)->GetByteArrayElements(env, output, NULL);
    if (!bytes) { (*env)->ReleaseShortArrayElements(env, pcm, input, JNI_ABORT); return -1; }
    int size = (*env)->GetArrayLength(env, output);
    int count = lame_get_num_channels(encoder) == 2
        ? lame_encode_buffer_interleaved(encoder, input, samples, (unsigned char *)bytes, size)
        : lame_encode_buffer(encoder, input, input, samples, (unsigned char *)bytes, size);
    (*env)->ReleaseShortArrayElements(env, pcm, input, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, output, bytes, 0);
    return count;
}

JNIEXPORT jint JNICALL
Java_app_kreate_android_downloads_Mp3Encoder_flush(JNIEnv *env, jobject self, jlong handle, jbyteArray output) {
    if (!handle) return -1;
    jbyte *bytes = (*env)->GetByteArrayElements(env, output, NULL);
    if (!bytes) return -1;
    int count = lame_encode_flush((lame_t)(intptr_t)handle, (unsigned char *)bytes,
                                 (*env)->GetArrayLength(env, output));
    (*env)->ReleaseByteArrayElements(env, output, bytes, 0);
    return count;
}

JNIEXPORT void JNICALL
Java_app_kreate_android_downloads_Mp3Encoder_close(JNIEnv *env, jobject self, jlong handle) {
    if (handle) lame_close((lame_t)(intptr_t)handle);
}
