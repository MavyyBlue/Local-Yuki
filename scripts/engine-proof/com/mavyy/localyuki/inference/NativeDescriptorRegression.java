package com.mavyy.localyuki.inference;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;

/** Shipping JNI with actual weights under enforced denial of all pathname opens. */
public final class NativeDescriptorRegression {
    static { System.loadLibrary("yuki-descriptor-test"); }
    private static native void denyPathOpens();
    private static native boolean reopenIsDenied(int fd);
    private static native int openDescriptorCount();
    private static int fd(FileDescriptor descriptor) throws Exception {
        Field field = FileDescriptor.class.getDeclaredField("fd");field.setAccessible(true);
        return field.getInt(descriptor);
    }
    private static void rejected(NativeOrgan organ, int fd, String expected) {
        try {
            long handle = organ.load(fd, 512, 2, 64, false, 30000);
            if (handle != 0) organ.unload(handle);
            throw new AssertionError("unsafe descriptor admitted");
        } catch (IllegalStateException failure) {
            if (!failure.getMessage().contains(expected)) throw new AssertionError(failure);
        }
    }
    public static void main(String[] args) throws Exception {
        NativeOrgan organ = new NativeOrgan();
        Path corrupt = Files.createTempFile("yuki-invalid", ".gguf");
        Files.write(corrupt, new byte[24]);
        try (RandomAccessFile writable = new RandomAccessFile(corrupt.toFile(), "rw")) {
            rejected(organ, fd(writable.getFD()), "read-only regular file");
        }
        rejected(organ, -1, "invalid model descriptor");
        try (FileInputStream nonregular = new FileInputStream("/dev/null")) {
            rejected(organ, fd(nonregular.getFD()), "read-only regular file");
        }
        FileInputStream closed = new FileInputStream(corrupt.toFile());int stale = fd(closed.getFD());closed.close();
        rejected(organ, stale, "invalid model descriptor");
        try (FileInputStream malformed = new FileInputStream(corrupt.toFile())) {
            int before = openDescriptorCount();
            for (int i = 0; i < 8; ++i) rejected(organ, fd(malformed.getFD()), "Model load failed:");
            if (openDescriptorCount() != before) throw new AssertionError("failed load leaked descriptors");
        }
        Files.delete(corrupt);
        System.out.println("PASS invalid/closed/writable/nonregular/corrupt descriptor rejection and failure cleanup");
        // Open both files BEFORE the restriction. A native runtime must not reopen them.
        try (FileInputStream decoder = new FileInputStream(args[0]); FileInputStream embedding = new FileInputStream(args[1])) {
            int decoderFd = fd(decoder.getFD()), embeddingFd = fd(embedding.getFD());
            denyPathOpens();
            if (!reopenIsDenied(decoderFd)) throw new AssertionError("restriction not effective");
            System.out.println("PASS legacy /proc/self/fd reopen returns EACCES");
            for (int run = 0; run < 2; ++run) {
                int before = openDescriptorCount();long handle = 0;
                try {
                    handle = organ.load(decoderFd, 512, 2, 64, false, 30000);
                    byte[] text = organ.generate(handle, NativeOrgan.utf("Reply briefly."), NativeOrgan.utf("Say hello."), new byte[0], 16, 30000);
                    if (text.length == 0 || organ.metrics(handle)[0] <= 0) throw new AssertionError("real decoder did not execute");
                } finally { if (handle != 0) organ.unload(handle); }
                if (openDescriptorCount() != before) throw new AssertionError("unload leaked model descriptor");
                decoder.getChannel().position(0);
                if (decoder.read() != 'G') throw new AssertionError("caller descriptor was closed or damaged");
            }
            System.out.println("PASS real decoder generation/repeated unload/borrowed descriptor ownership with path opens forbidden");
            long handle = 0;int before = openDescriptorCount();
            try {
                handle = organ.load(embeddingFd, 512, 2, 64, true, 30000);
                float[] vector = organ.embed(handle, NativeOrgan.utf("Yuki remembers our conversation."), 30000);
                double norm = 0;for (float value : vector) norm += value * value;
                if (vector.length != 384 || Math.abs(norm - 1) > 0.01) throw new AssertionError("real embedding failed");
            } finally { if (handle != 0) organ.unload(handle); }
            if (openDescriptorCount() != before) throw new AssertionError("embedding unload leaked descriptor");
            System.out.println("PASS real BGE embedding/unload with path opens forbidden");
        }
    }
}
