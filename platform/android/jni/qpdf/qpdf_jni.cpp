#include <jni.h>
#include <android/log.h>
#include <qpdf/QPDF.hh>
#include <qpdf/QPDFJob.hh>
#include <string>
#include <vector>

static const char* kTag = "qpdf-jni";

static void log_error(const char* message, const std::exception& e) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "%s: %s", message, e.what());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_penandpdf_qpdf_QpdfNative_version(JNIEnv* env, jobject /*thiz*/) {
    const std::string& v = QPDF::QPDFVersion();
    return env->NewStringUTF(v.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_penandpdf_qpdf_QpdfNative_run(JNIEnv* env,
                                       jobject /*thiz*/,
                                       jobjectArray j_args) {
    const jsize len = env->GetArrayLength(j_args);
    std::vector<std::string> args;
    args.reserve(static_cast<size_t>(len));

    std::vector<const char*> argv;
    argv.reserve(static_cast<size_t>(len + 1));

    // Keep UTF strings to release after the run.
    std::vector<std::pair<jstring, const char*>> pinned;
    pinned.reserve(static_cast<size_t>(len));

    for (jsize i = 0; i < len; ++i) {
        jstring jstr = static_cast<jstring>(env->GetObjectArrayElement(j_args, i));
        const char* utf = env->GetStringUTFChars(jstr, nullptr);
        pinned.emplace_back(jstr, utf);
        args.emplace_back(utf);
    }

    for (auto& s : args) {
        argv.push_back(s.c_str());
    }
    argv.push_back(nullptr);

    bool ok = false;
    try {
        QPDFJob job;
        job.initializeFromArgv(argv.data());
        job.run();
        ok = true;
    } catch (std::exception const& e) {
        log_error("qpdf run failed", e);
    }

    for (auto& p : pinned) {
        env->ReleaseStringUTFChars(p.first, p.second);
        env->DeleteLocalRef(p.first);
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_penandpdf_qpdf_QpdfNative_merge(JNIEnv* env,
                                         jobject /*thiz*/,
                                         jstring j_input_a,
                                         jstring j_input_b,
                                         jstring j_output) {
    const char* input_a = env->GetStringUTFChars(j_input_a, nullptr);
    const char* input_b = env->GetStringUTFChars(j_input_b, nullptr);
    const char* output = env->GetStringUTFChars(j_output, nullptr);

    bool ok = false;
    try {
        std::vector<std::string> args{
                "qpdf", "--empty", "--pages", input_a, "1-z", input_b, "1-z", "--", output};
        std::vector<jstring> jstrings;
        jstrings.reserve(args.size());
        for (auto& s : args) {
            jstrings.push_back(env->NewStringUTF(s.c_str()));
        }
        jobjectArray jarray =
                env->NewObjectArray(static_cast<jsize>(args.size()),
                                    env->FindClass("java/lang/String"),
                                    nullptr);
        for (jsize i = 0; i < static_cast<jsize>(args.size()); ++i) {
            env->SetObjectArrayElement(jarray, i, jstrings[i]);
        }
        ok = Java_com_penandpdf_qpdf_QpdfNative_run(env, nullptr, jarray);

        for (auto& js : jstrings) {
            env->DeleteLocalRef(js);
        }
        env->DeleteLocalRef(jarray);
    } catch (std::exception const& e) {
        log_error("merge failed", e);
    }

    env->ReleaseStringUTFChars(j_input_a, input_a);
    env->ReleaseStringUTFChars(j_input_b, input_b);
    env->ReleaseStringUTFChars(j_output, output);
    return ok ? JNI_TRUE : JNI_FALSE;
}
