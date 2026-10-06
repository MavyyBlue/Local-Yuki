package com.mavyy.localyuki.inference;
import java.io.*;import java.lang.reflect.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Executes shipping JNI and real owner-supplied weights. Not an Android device benchmark. */
public final class NativeOrgan {
 static {System.loadLibrary("yuki-organ");}
 native long load(int fd,int context,int threads,int batch,boolean embeddings,long deadline);
 native byte[] generate(long h,byte[] system,byte[] user,byte[] grammar,int output,long deadline);
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
    h=organ.load(fd(input),2048,2,64,false,60000);long loaded=System.nanoTime();
    String role=System.getProperty("yuki.role","language");
    String system=role.equals("one")?"Interpret owner greeting. Return JSON only with route, confidence(0..100), salience(0..100), intent, affect, meaning, uncertainty, sources, updates. Allowed source IDs: input only. Do not use memory sources. No actions or fabricated memories.":role.equals("two")?"Resolve the question using only supplied evidence. Return JSON points,uncertainty,sources,actions. Use source input; empty actions. Do not expose chain-of-thought. Example structure: {\"points\":[\"The evidence cannot confirm a cause.\"],\"uncertainty\":[\"More evidence is required.\"],\"sources\":[\"input\"],\"actions\":[]}. Put complete natural-language conclusions inside points.":"You are an advisory language organ. Express only supplied meaning. Return JSON only: text, pointIds.";
    String user=role.equals("one")?"Owner input: Hello, Yuki. Prepare Yuki greeting Mavyy back, not a description of the input. Return a concise prepared greeting. uncertainty and updates should be empty.":role.equals("two")?"Owner input: I have a headache. Is its cause certain? Prepared conclusion: the supplied evidence cannot establish a diagnosis. Sources: input. No action is required.":"{\"points\":[{\"id\":0,\"meaning\":\"Hello, Mavyy. I am glad you are here.\"}],\"uncertainty\":[]}";
    if(System.getProperty("yuki.system")!=null)system=java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("yuki.system")));
    if(System.getProperty("yuki.user")!=null)user=java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("yuki.user")));
    String output=new String(organ.generate(h,utf(system),utf(user),java.nio.file.Files.readAllBytes(java.nio.file.Path.of(System.getProperty("yuki.grammar"))),256,60000),StandardCharsets.UTF_8);

    System.out.println("GENERATION model="+new File(model).getName()+" startupMs="+(loaded-start)/1000000+" inferenceMs="+(System.nanoTime()-loaded)/1000000+" metrics="+Arrays.toString(organ.metrics(h))+" outputBase64="+Base64.getEncoder().encodeToString(output.getBytes(StandardCharsets.UTF_8)));
    if(output.isBlank()||!output.trim().endsWith("}"))throw new AssertionError("incomplete real inference");
   }finally{if(h!=0)organ.unload(h);}System.out.println("UNLOAD completed");
   }
   if(!roleIsLanguage()&&!System.getProperty("yuki.role").equals("embedding"))continue;
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
