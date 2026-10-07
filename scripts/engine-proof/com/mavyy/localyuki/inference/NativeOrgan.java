package com.mavyy.localyuki.inference;
import java.io.*;import java.lang.reflect.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Executes shipping JNI and real owner-supplied weights. Not an Android device benchmark. */
public final class NativeOrgan {
 static {System.loadLibrary("yuki-organ");}
 native long load(int fd,int context,int threads,int batch,boolean embeddings,long deadline);
 native byte[] generate(long h,byte[] system,byte[] user,byte[] grammar,int output,long deadline);
 native byte[] generateBounded(long h,byte[] system,byte[][] users,byte[][] grammars,boolean[] shortened,int output,int promptLimit,long deadline);
 native float[] embed(long h,byte[] text,long deadline);
 native long[] metrics(long h);native void unload(long h);
 static byte[] utf(String s){return s.getBytes(StandardCharsets.UTF_8);}
 static int fd(FileInputStream f)throws Exception{Field n=FileDescriptor.class.getDeclaredField("fd");n.setAccessible(true);return n.getInt(f.getFD());}
 static boolean roleIsLanguage(){return System.getProperty("yuki.role","language").equals("language");}
 public static void main(String[] args)throws Exception {
  NativeOrgan organ=new NativeOrgan();
  for(String model:args) {
   long h=0;long start=System.nanoTime();
   if(!System.getProperty("yuki.role","language").equals("embedding")) {
   try(FileInputStream input=new FileInputStream(model)) {
    int outputLimit=Integer.getInteger("yuki.output",256);long deadline=Long.getLong("yuki.deadline",60000L);
    h=organ.load(fd(input),Integer.getInteger("yuki.context",2048),Integer.getInteger("yuki.threads",2),64,false,deadline);long loaded=System.nanoTime();
    String role=System.getProperty("yuki.role","language");
    String system=role.equals("one")?"Interpret owner greeting. Return JSON only with route, confidence(0..100), salience(0..100), intent, affect, meaning, uncertainty, sources, updates. Allowed source IDs: input only. Do not use memory sources. No actions or fabricated memories.":role.equals("two")?"Resolve the question using only supplied evidence. Return JSON points,uncertainty,sources,actions. Use source input; empty actions. Do not expose chain-of-thought. Example structure: {\"points\":[\"The evidence cannot confirm a cause.\"],\"uncertainty\":[\"More evidence is required.\"],\"sources\":[\"input\"],\"actions\":[]}. Put complete natural-language conclusions inside points.":"You are an advisory language organ. Express only supplied meaning. Return JSON only: text, pointIds.";
    String user=role.equals("one")?"Owner input: Hello, Yuki. Prepare Yuki greeting Mavyy back, not a description of the input. Return a concise prepared greeting. uncertainty and updates should be empty.":role.equals("two")?"Owner input: I have a headache. Is its cause certain? Prepared conclusion: the supplied evidence cannot establish a diagnosis. Sources: input. No action is required.":"{\"points\":[{\"id\":0,\"meaning\":\"Hello, Mavyy. I am glad you are here.\"}],\"uncertainty\":[]}";
    if(System.getProperty("yuki.system")!=null)system=java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("yuki.system")));
    if(System.getProperty("yuki.user")!=null)user=java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("yuki.user")));
    if(Boolean.getBoolean("yuki.production"))system+="\nOutput limit: "+outputLimit+" tokens. Brief, complete JSON.";
    byte[] grammar=java.nio.file.Files.readAllBytes(java.nio.file.Path.of(System.getProperty("yuki.grammar")));
    long inferenceStart=System.nanoTime();
    byte[][] users={utf(user)},grammars={grammar};boolean[] shortened={false};
    if(System.getProperty("yuki.planPrefix")!=null) {
     java.util.List<byte[]> u=new java.util.ArrayList<>(),g=new java.util.ArrayList<>();java.util.List<Boolean> f=new java.util.ArrayList<>();
     String prefix=System.getProperty("yuki.planPrefix");
     for(int i=0;i<4;i++) {
      var path=java.nio.file.Path.of(prefix+i+"-user.json");if(!java.nio.file.Files.exists(path))break;
      u.add(java.nio.file.Files.readAllBytes(path));g.add(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(prefix+i+".gbnf")));
      f.add(Boolean.parseBoolean(java.nio.file.Files.readString(java.nio.file.Path.of(prefix+i+"-shortened.txt")).trim()));
     }
     users=u.toArray(new byte[0][]);grammars=g.toArray(new byte[0][]);shortened=new boolean[f.size()];for(int i=0;i<f.size();i++)shortened[i]=f.get(i);
    }
    long remaining=deadline-(System.nanoTime()-start)/1000000;
    if(remaining<=0)throw new AssertionError("Startup exhausted organ deadline");
    String output=new String(Boolean.getBoolean("yuki.production")?
      organ.generateBounded(h,utf(system),users,grammars,shortened,outputLimit,Integer.getInteger("yuki.promptLimit",256),remaining):
      organ.generate(h,utf(system),utf(user),grammar,outputLimit,remaining),StandardCharsets.UTF_8);
    long inferenceMs=(System.nanoTime()-inferenceStart)/1000000;
    long[] metrics=organ.metrics(h);
    if(metrics.length<8||metrics[4]<=0||metrics[6]<=0||metrics[7]<=0||metrics[6]+metrics[7]>inferenceMs+3)
     throw new AssertionError("invalid split prompt/generation measurements: "+Arrays.toString(metrics)+" wall="+inferenceMs);

    System.out.println("GENERATION model="+new File(model).getName()+" startupMs="+(loaded-start)/1000000+" inferenceMs="+inferenceMs+" metrics="+Arrays.toString(metrics)+" outputBase64="+Base64.getEncoder().encodeToString(output.getBytes(StandardCharsets.UTF_8)));
    if(output.isBlank()||!output.trim().endsWith("}"))throw new AssertionError("incomplete real inference");
   }finally{if(h!=0)organ.unload(h);}System.out.println("UNLOAD completed");
   }
   if(Boolean.getBoolean("yuki.production")||(!roleIsLanguage()&&!System.getProperty("yuki.role").equals("embedding")))continue;
   try(FileInputStream input=new FileInputStream(model)) {
    h=organ.load(fd(input),512,2,512,true,60000);
    float[] a=organ.embed(h,utf("A cat sits quietly in the garden."),30000),b=organ.embed(h,utf("A feline rests outside among flowers."),30000),c=organ.embed(h,utf("The price of the cryptocurrency collapsed."),30000);
    if(a.length!=b.length||a.length!=c.length)throw new AssertionError("dimension changed");
    double ab=0,ac=0,norm=0;for(int i=0;i<a.length;i++){if(!Float.isFinite(a[i]))throw new AssertionError("nonfinite embedding");ab+=a[i]*b[i];ac+=a[i]*c[i];norm+=a[i]*a[i];}
    if(Math.abs(norm-1)>0.01||ab<=ac)throw new AssertionError("embedding quality/normalization check failed");
    System.out.println("EMBEDDING dimensions="+a.length+" relatedCosine="+ab+" unrelatedCosine="+ac+" norm="+norm);
   }finally{if(h!=0)organ.unload(h);}System.out.println("EMBEDDING UNLOAD completed");
  }
 }
}
