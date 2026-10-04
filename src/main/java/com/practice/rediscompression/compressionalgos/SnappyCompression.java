package com.practice.rediscompression.compressionalgos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xerial.snappy.Snappy;

import java.io.IOException;

public class SnappyCompression {

    private static final Logger log = LoggerFactory.getLogger(SnappyCompression.class);

    private SnappyCompression() {
        // Prevent instantiation
    }

    public static byte[] compress(byte[] data) throws IOException {
        log.info("----- Compressing with SNAPPY Algorithm -----");
        // Snappy calculates the max compressed size and processes the array in one highly optimized step
        return Snappy.compress(data);
    }

    public static byte[] decompress(byte[] compressedData) throws IOException {
        log.info("----- De-Compressing with SNAPPY Algorithm -----");
        // Snappy reads the uncompressed length from the header and decompresses instantly
        return Snappy.uncompress(compressedData);
    }
}