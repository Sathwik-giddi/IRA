#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <cstring>
#include "llama.h"
#include "ggml.h"

#define TAG "VP_LLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct Session {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    int pp_tokens = 0;
    long long pp_ms = 0;
    int tg_tokens = 0;
    long long tg_ms = 0;
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_voicepoco_app_LlamaBridge_load(JNIEnv * env, jclass, jstring jpath, jint n_ctx, jint n_threads) {
    const char * path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) return 0;

    llama_log_set([](enum ggml_log_level level, const char * text, void * user_data) {
        int prio = level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR
                 : level == GGML_LOG_LEVEL_WARN  ? ANDROID_LOG_WARN
                 : ANDROID_LOG_INFO;
        __android_log_print(prio, "VP_LLAMA", "%s", text);
    }, nullptr);

    llama_backend_init();

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    mp.load_mode = LLAMA_LOAD_MODE_MMAP;

    llama_model * model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (!model) {
        LOGE("model load failed");
        return 0;
    }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = (uint32_t) n_ctx;
    cp.n_batch = 128;
    cp.n_ubatch = 64;
    cp.n_threads = n_threads;
    cp.n_threads_batch = n_threads;
    cp.type_k = GGML_TYPE_Q8_0;
    cp.type_v = GGML_TYPE_Q8_0;

    llama_context * ctx = llama_init_from_model(model, cp);
    if (!ctx) {
        LOGE("context init failed");
        llama_model_free(model);
        return 0;
    }

    auto * s = new Session();
    s->model = model;
    s->ctx = ctx;
    s->vocab = llama_model_get_vocab(model);
    LOGI("loaded ctx=%d threads=%d", n_ctx, n_threads);
    return (jlong) s;
}

extern "C" JNIEXPORT void JNICALL
Java_com_voicepoco_app_LlamaBridge_free(JNIEnv *, jclass, jlong handle) {
    auto * s = (Session *) handle;
    if (!s) return;
    if (s->ctx) llama_free(s->ctx);
    if (s->model) llama_model_free(s->model);
    delete s;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_voicepoco_app_LlamaBridge_generate(JNIEnv * env, jclass, jlong handle,
                                            jstring jprompt, jint max_tokens) {
    auto * s = (Session *) handle;
    if (!s || !s->ctx) return env->NewStringUTF("");

    const char * p = env->GetStringUTFChars(jprompt, nullptr);
    if (!p) return env->NewStringUTF("");
    std::string prompt(p);
    env->ReleaseStringUTFChars(jprompt, p);

    const bool add_special = true;
    int n = -llama_tokenize(s->vocab, prompt.c_str(), (int32_t) prompt.size(),
                            nullptr, 0, add_special, true);
    if (n <= 0) {
        LOGE("tokenize sizing failed n=%d", n);
        return env->NewStringUTF("");
    }
    std::vector<llama_token> toks(n);
    if (llama_tokenize(s->vocab, prompt.c_str(), (int32_t) prompt.size(),
                       toks.data(), (int32_t) toks.size(), add_special, true) < 0) {
        LOGE("tokenize failed");
        return env->NewStringUTF("");
    }

    llama_memory_clear(llama_get_memory(s->ctx), true);
    LOGI("prompt tokens=%d chars=%d", (int) toks.size(), (int) prompt.size());
    int64_t t0 = ggml_time_us();

    int decoded = 0;
    int n_past = 0;
    while (decoded < (int) toks.size()) {
        int chunk = (int) toks.size() - decoded;
        if (chunk > 128) chunk = 128;
        llama_batch batch = llama_batch_init(chunk, 0, 1);
        batch.n_tokens = chunk;
        for (int i = 0; i < chunk; i++) {
            batch.token[i] = toks[decoded + i];
            batch.pos[i] = n_past + i;
            batch.n_seq_id[i] = 1;
            batch.seq_id[i][0] = 0;
            batch.logits[i] = (i == chunk - 1) ? 1 : 0;
        }
        int rc = llama_decode(s->ctx, batch);
        llama_batch_free(batch);
        if (rc != 0) {
            LOGE("decode failed at %d", decoded);
            return env->NewStringUTF("");
        }
        decoded += chunk;
        n_past += chunk;
        LOGI("prompt %d/%d ms=%lld", decoded, (int) toks.size(), (long long) ((ggml_time_us() - t0) / 1000));
    }

    s->pp_tokens = (int) toks.size();
    s->pp_ms = (long long) ((ggml_time_us() - t0) / 1000);
    LOGI("prompt eval ms=%lld", s->pp_ms);
    int64_t t1 = ggml_time_us();
    llama_sampler_chain_params sp = llama_sampler_chain_default_params();
    llama_sampler * smpl = llama_sampler_chain_init(sp);
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    std::string out;
    char buf[256];
    int tg_count = 0;
    for (int i = 0; i < max_tokens; i++) {
        llama_token id = llama_sampler_sample(smpl, s->ctx, -1);
        if (llama_vocab_is_eog(s->vocab, id)) break;
        int len = llama_token_to_piece(s->vocab, id, buf, sizeof(buf), 0, true);
        if (len > 0) out.append(buf, len);
        llama_sampler_accept(smpl, id);
        if (out.find("<end_function_call>") != std::string::npos) { tg_count++; break; }

        llama_batch one = llama_batch_init(1, 0, 1);
        one.n_tokens = 1;
        one.token[0] = id;
        one.pos[0] = n_past;
        one.n_seq_id[0] = 1;
        one.seq_id[0][0] = 0;
        one.logits[0] = 1;
        int rc1 = llama_decode(s->ctx, one);
        llama_batch_free(one);
        n_past++;
        tg_count++;
        if (rc1 != 0) break;
        if (i < 3 || i % 16 == 0) LOGI("gen i=%d ms=%lld", i, (long long) ((ggml_time_us() - t1) / 1000));
    }
    llama_sampler_free(smpl);
    s->tg_tokens = tg_count;
    s->tg_ms = (long long) ((ggml_time_us() - t1) / 1000);
    LOGI("generate done ms=%lld out=%d", s->tg_ms, (int) out.size());

    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_voicepoco_app_LlamaBridge_stats(JNIEnv * env, jclass, jlong handle) {
    auto * s = (Session *) handle;
    if (!s) return env->NewStringUTF("no_session");
    int pp_tps = s->pp_ms > 0 ? (int) (s->pp_tokens * 1000LL / s->pp_ms) : 0;
    int tg_tps = s->tg_ms > 0 ? (int) (s->tg_tokens * 1000LL / s->tg_ms) : 0;
    char buf[256];
    snprintf(buf, sizeof(buf), "pp_tok=%d pp_ms=%lld tg_tok=%d tg_ms=%lld pp_tps=%d tg_tps=%d",
             s->pp_tokens, s->pp_ms, s->tg_tokens, s->tg_ms, pp_tps, tg_tps);
    return env->NewStringUTF(buf);
}
