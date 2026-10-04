package com.practice.rediscompression.compressionalgos;

import com.practice.rediscompression.DynamicRedisPayloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class DeflateCompression {

    private static final Logger log = LoggerFactory.getLogger(DeflateCompression.class);

    private DeflateCompression() {
        // Prevent instantiation of utility class
    }

    public static byte[] compress(byte[] data) throws IOException {
        log.info("----- Compressing with DEFLATE Algorithm -----");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(outputStream)) {
            deflater.write(data);
        }
        return outputStream.toByteArray();
    }

    public static byte[] decompress(byte[] compressedData) throws IOException {
        log.info("----- De-Compressing with DEFLATE Algorithm -----");
        ByteArrayInputStream inputStream = new ByteArrayInputStream(compressedData);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (InflaterInputStream inflater = new InflaterInputStream(inputStream)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inflater.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
        }
        return outputStream.toByteArray();
    }
}