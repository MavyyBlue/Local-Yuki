package com.mavyy.localyuki.inference;
import java.io.*;
import java.nio.file.*;
/** Real bounded-plan selection, refusal-before-decode, deadline diagnostics and unload. */
public final class NativePreparationRegression {
 public static void main(String[] args)throws Exception {
  NativeOrgan organ=new NativeOrgan();long h=0;
  try(FileInputStream file=new FileInputStream(args[0])) {
   h=organ.load(NativeOrgan.fd(file),2048,2,64,false,30000);
   byte[] system=Files.readAllBytes(Path.of(args[1])),grammar=Files.readAllBytes(Path.of(args[2]));
   byte[] huge=NativeOrgan.utf("{\"ownerInput\":\"Hello, My Love 🥰\",\"history\":\""+"long history ".repeat(1000)+"\"}");
   byte[] lean=NativeOrgan.utf("{\"ownerInput\":\"Hello, My Love 🥰\",\"uncertainty\":[\"Context omitted; unknown.\"]}");
   byte[] result=organ.generateBounded(h,system,new byte[][]{huge,lean},new byte[][]{grammar,grammar},new boolean[]{false,true},64,256,30000);
   long[] m=organ.metrics(h);
   if(result.length==0||m[8]>256||m[9]!=m[8]||m[10]!=1||m[5]!=1)throw new AssertionError("Candidate selection/measurements failed");
   try {organ.generateBounded(h,system,new byte[][]{huge},new byte[][]{grammar},new boolean[]{false},64,128,30000);throw new AssertionError("Oversized owner request silently cut");}
   catch(IllegalStateException e){if(!e.getMessage().contains("Owner input was not cut"))throw e;}
   if(organ.metrics(h)[9]!=0)throw new AssertionError("Oversized prompt was decoded");
   try {organ.generateBounded(h,system,new byte[][]{lean},new byte[][]{grammar},new boolean[]{false},64,256,0);throw new AssertionError("Expired preparation executed");}
   catch(IllegalStateException e){if(!e.getMessage().contains("Prompt preparation deadline"))throw e;}
   m=organ.metrics(h);if(m[8]<=0||m[9]>=m[8])throw new AssertionError("Failed preparation progress missing");
   System.out.println("PASS whole-candidate selection, oversized-input refusal and real deadline diagnostics");
  }finally{if(h!=0)organ.unload(h);}
  System.out.println("UNLOAD completed");
 }
}
