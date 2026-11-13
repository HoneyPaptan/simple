#include <jni.h>
#include <string>
#include "whisper.h"

static struct whisper_context *ctx_from(jlong ptr) {
    return reinterpret_cast<struct whisper_context *>(ptr);
}

extern "C" JNIEXPORT jlong JNICALL
Java_sh_simple_android_voice_WhisperNative_loadModel(JNIEnv *env, jobject, jstring path_str) {
    const char *path = env->GetStringUTFChars(path_str, nullptr);
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, whisper_context_default_params());
    env->ReleaseStringUTFChars(path_str, path);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_sh_simple_android_voice_WhisperNative_freeModel(JNIEnv *, jobject, jlong context_ptr) {
    whisper_free(ctx_from(context_ptr));
}

extern "C" JNIEXPORT jstring JNICALL
Java_sh_simple_android_voice_WhisperNative_transcribe(
    JNIEnv *env,
    jobject,
    jlong context_ptr,
    jfloatArray audio_data,
    jint num_threads,
    jstring language_str
) {
    struct whisper_context *ctx = ctx_from(context_ptr);
    jfloat *samples = env->GetFloatArrayElements(audio_data, nullptr);
    const jsize sample_count = env->GetArrayLength(audio_data);
    const char *language = env->GetStringUTFChars(language_str, nullptr);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = language;
    params.n_threads = num_threads > 0 ? num_threads : 4;
    params.no_context = true;
    params.single_segment = true;

    std::string text;
    if (whisper_full(ctx, params, samples, sample_count) == 0) {
        const int segments = whisper_full_n_segments(ctx);
        for (int i = 0; i < segments; i++) {
            const char *segment = whisper_full_get_segment_text(ctx, i);
            if (segment != nullptr) {
                text += segment;
            }
        }
    }

    env->ReleaseFloatArrayElements(audio_data, samples, JNI_ABORT);
    env->ReleaseStringUTFChars(language_str, language);
    return env->NewStringUTF(text.c_str());
}
