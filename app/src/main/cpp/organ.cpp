#include <jni.h>
#include "llama.h"
#include <vector>
#include <string>
#include <chrono>
#include <cmath>
#include <memory>
#include <sys/resource.h>
#include <unistd.h>
#include <stdexcept>
#include <cstdio>
#include <cstring>

// Global callback with bounded thread-local errors, never stack-backed callback data.
static thread_local std::string load_error;
static void runtime_log(ggml_log_level level, const char * text, void *) {
 if(level==GGML_LOG_LEVEL_ERROR && load_error.size()<1024) {
  const size_t size=strnlen(text,1024-load_error.size());
  for(size_t i=0;i<size;i++) {
   const auto ch=static_cast<unsigned char>(text[i]);
   load_error+=(ch>=32 && ch<=126)?text[i]:' ';
  }
 }
 std::fputs(text,stderr);
}

using Clock=std::chrono::steady_clock;
struct Organ {
 llama_model * model=nullptr; llama_context * ctx=nullptr; Clock::time_point until; bool truncated=false;
 ~Organ(){if(ctx)llama_free(ctx);if(model)llama_model_free(model);}
};
static bool aborting(void * p){return Clock::now()>static_cast<Organ*>(p)->until;}
static bool progress(float,void * p){return !aborting(p);}
static std::string bytes(JNIEnv*e,jbyteArray a){jsize n=e->GetArrayLength(a);std::string s(n,'\0');e->GetByteArrayRegion(a,0,n,reinterpret_cast<jbyte*>(s.data()));return s;}
static void fail(JNIEnv*e,const char* s){e->ThrowNew(e->FindClass("java/lang/IllegalStateException"),s);}
static Organ* get(jlong h){if(!h)throw std::runtime_error("unloaded organ");return reinterpret_cast<Organ*>(h);}
static std::vector<llama_token> tokenize(Organ*o,const std::string&s){auto v=llama_model_get_vocab(o->model);int n=llama_tokenize(v,s.data(),s.size(),nullptr,0,true,true);if(n>=0)throw std::runtime_error("tokenization failed");std::vector<llama_token> t(-n);n=llama_tokenize(v,s.data(),s.size(),t.data(),t.size(),true,true);if(n<=0)throw std::runtime_error("empty tokenization");t.resize(n);return t;}
extern "C" JNIEXPORT jlong JNICALL Java_com_mavyy_localyuki_inference_NativeOrgan_load(JNIEnv*e,jobject,jint fd,jint context,jint threads,jint batch,jboolean embeddings,jlong deadline){
 try {
  load_error.clear();llama_log_set(runtime_log,nullptr);
  llama_backend_init();auto o=std::make_unique<Organ>();o->until=Clock::now()+std::chrono::milliseconds(deadline);
  auto p=llama_model_default_params();p.n_gpu_layers=0;p.use_mmap=true;p.use_mlock=false;p.check_tensors=true;p.progress_callback=progress;p.progress_callback_user_data=o.get();
  o->model=llama_model_load_from_fd(fd,p);
  if(!o->model)throw std::runtime_error(aborting(o.get())?"Model loading exceeded safe deadline":"Model load failed: "+(load_error.empty()?std::string("no native diagnostic"):load_error));
  auto c=llama_context_default_params();c.n_ctx=context;c.n_batch=embeddings?context:batch;c.n_ubatch=embeddings?context:batch;c.n_threads=threads;c.n_threads_batch=threads;c.n_seq_max=1;
  c.embeddings=embeddings;c.offload_kqv=false;c.abort_callback=aborting;c.abort_callback_data=o.get();
  if(embeddings)c.pooling_type=LLAMA_POOLING_TYPE_MEAN;
  o->ctx=llama_init_from_model(o->model,c);if(!o->ctx)throw std::runtime_error("context allocation rejected");return reinterpret_cast<jlong>(o.release());
 }catch(const std::exception&x){fail(e,x.what());return 0;}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_mavyy_localyuki_inference_NativeOrgan_generate(JNIEnv*e,jobject,jlong handle,jbyteArray system,jbyteArray user,jbyteArray grammar,jint maxTokens,jlong deadline){
 try{
  auto o=get(handle);o->until=Clock::now()+std::chrono::milliseconds(deadline);llama_kv_self_clear(o->ctx);
  std::string s=bytes(e,system),u=bytes(e,user),prompt;const char*tmpl=llama_model_chat_template(o->model,nullptr);
  std::vector<llama_token> t;o->truncated=false;
  for(int attempt=0;attempt<32;attempt++) {
   llama_chat_message msg[2]={{"system",s.c_str()},{"user",u.c_str()}};
   int n=llama_chat_apply_template(tmpl,msg,2,true,nullptr,0);if(n<=0)throw std::runtime_error("unsupported/missing chat template");
   std::vector<char> buf(n+1);llama_chat_apply_template(tmpl,msg,2,true,buf.data(),buf.size());prompt.assign(buf.data(),n);t=tokenize(o,prompt);
   if(t.size()+maxTokens<llama_n_ctx(o->ctx))break;
   if(u.size()<256)throw std::runtime_error("system/output exceeds safe context");
   size_t end=u.size()*3/4;while(end>0 && (static_cast<unsigned char>(u[end])&0xc0)==0x80)--end;
   u.resize(end);u+="\n[Context shortened for safety. Omitted information is unknown.]";o->truncated=true;
  }
  if(t.size()+maxTokens>=llama_n_ctx(o->ctx))throw std::runtime_error("context cannot fit safe budget");
  for(size_t pos=0;pos<t.size();){int count=std::min<size_t>(llama_n_batch(o->ctx),t.size()-pos);auto b=llama_batch_get_one(t.data()+pos,count);if(llama_decode(o->ctx,b))throw std::runtime_error("prefill failed or cancelled");pos+=count;}
  auto sampler=llama_sampler_chain_init(llama_sampler_chain_default_params());
  auto g=bytes(e,grammar);if(!g.empty()){auto constrained=llama_sampler_init_grammar(llama_model_get_vocab(o->model),g.c_str(),"root");if(!constrained){llama_sampler_free(sampler);throw std::runtime_error("runtime grammar rejected");}llama_sampler_chain_add(sampler,constrained);}
  llama_sampler_chain_add(sampler,llama_sampler_init_greedy());std::string out;
  for(int i=0;i<maxTokens && !aborting(o);i++){
   auto tok=llama_sampler_sample(sampler,o->ctx,-1);if(llama_vocab_is_eog(llama_model_get_vocab(o->model),tok))break;
   char piece[512];int len=llama_token_to_piece(llama_model_get_vocab(o->model),tok,piece,sizeof(piece),0,false);if(len<0||len>512){llama_sampler_free(sampler);throw std::runtime_error("invalid token piece");}out.append(piece,len);if(out.size()>16384)break;
   auto b=llama_batch_get_one(&tok,1);if(llama_decode(o->ctx,b)){llama_sampler_free(sampler);throw std::runtime_error("decode failed or cancelled");}
  }
  llama_sampler_free(sampler);if(aborting(o))throw std::runtime_error("inference deadline");
  auto a=e->NewByteArray(out.size());e->SetByteArrayRegion(a,0,out.size(),reinterpret_cast<const jbyte*>(out.data()));return a;
 }catch(const std::exception&x){fail(e,x.what());return nullptr;}
}
extern "C" JNIEXPORT jfloatArray JNICALL Java_com_mavyy_localyuki_inference_NativeOrgan_embed(JNIEnv*e,jobject,jlong h,jbyteArray input,jlong deadline){
 try{
  auto o=get(h);o->until=Clock::now()+std::chrono::milliseconds(deadline);llama_kv_self_clear(o->ctx);auto t=tokenize(o,bytes(e,input));if(t.size()>=llama_n_ctx(o->ctx))throw std::runtime_error("embedding context exceeded");
  auto b=llama_batch_get_one(t.data(),t.size());if(llama_model_has_encoder(o->model)?llama_encode(o->ctx,b):llama_decode(o->ctx,b))throw std::runtime_error("embedding failed");
  auto v=llama_get_embeddings_seq(o->ctx,0);if(!v)throw std::runtime_error("embedding pooling unsupported");int n=llama_model_n_embd(o->model);if(n<=0||n>8192)throw std::runtime_error("embedding dimension rejected");
  double sum=0;for(int i=0;i<n;i++)sum+=v[i]*v[i];if(!std::isfinite(sum)||sum<=0)throw std::runtime_error("invalid embedding");std::vector<float> norm(n);for(int i=0;i<n;i++)norm[i]=v[i]/std::sqrt(sum);
  auto a=e->NewFloatArray(n);e->SetFloatArrayRegion(a,0,n,norm.data());return a;
 }catch(const std::exception&x){fail(e,x.what());return nullptr;}
}
extern "C" JNIEXPORT jlongArray JNICALL Java_com_mavyy_localyuki_inference_NativeOrgan_metrics(JNIEnv*e,jobject,jlong h){
 auto o=get(h);rusage r{};getrusage(RUSAGE_SELF,&r);auto perf=llama_perf_context(o->ctx);jlong data[6]={static_cast<jlong>(llama_model_n_params(o->model)),static_cast<jlong>(llama_model_size(o->model)),r.ru_maxrss*1024LL,llama_n_ctx(o->ctx),perf.n_eval,o->truncated?1:0};auto a=e->NewLongArray(6);e->SetLongArrayRegion(a,0,6,data);return a;
}
extern "C" JNIEXPORT void JNICALL Java_com_mavyy_localyuki_inference_NativeOrgan_unload(JNIEnv*,jobject,jlong h){delete reinterpret_cast<Organ*>(h);}
