package com.practice.rediscompression.compressionalgos;

import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class Lz4Compression {

    private Lz4Compression() {
        // Prevent instantiation
    }

    public static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // LZ4FrameOutputStream handles the metadata (like original size) automatically
        try (LZ4FrameOutputStream out = new LZ4FrameOutputStream(baos)) {
            out.write(data);
        }
        return baos.toByteArray();
    }

    public static byte[] decompress(byte[] compressedData) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (LZ4FrameInputStream in = new LZ4FrameInputStream(bais)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
        }
        return baos.toByteArray();
    }
}