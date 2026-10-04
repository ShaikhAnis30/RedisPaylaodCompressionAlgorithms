package com.practice.rediscompression;

import com.practice.rediscompression.algorithmsupplier.CompressionAlgorithmSupplierFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class DynamicRedisPayloadService {

    private static final Logger log = LoggerFactory.getLogger(DynamicRedisPayloadService.class);

    // Flag bytes to indicate compression status
    private static final byte FLAG_RAW = 0x00;
    private static final byte FLAG_DEFLATE = 0x01;
    private static final byte FLAG_SNAPPY = 0x02;
    private static final byte FLAG_LZ4 = 0x03;
    private static final byte FLAG_ZSTD = 0x04;

    private final RedisTemplate<String, byte[]> binaryRedisTemplate;
    private final int compressionThreshold;
    private final CompressionAlgorithmSupplierFactory compressionAlgorithmSupplierFactory;
    private final String algorithm;

    private final Map<String, Byte> magicFlagSupplier =
            Map.of("DEFLATE", FLAG_DEFLATE,
                    "SNAPPY", FLAG_SNAPPY,
                    "LZ4", FLAG_LZ4,
                    "ZSTD", FLAG_ZSTD);

    public DynamicRedisPayloadService(
            RedisTemplate<String, byte[]> binaryRedisTemplate,
            @Value("${app.redis.compression-threshold:1048576}") int compressionThreshold,
            @Value("${app.redis.compression-algorithm:DEFLATE}") String algorithm,
            CompressionAlgorithmSupplierFactory compressionAlgorithmSupplierFactory) {
        this.binaryRedisTemplate = binaryRedisTemplate;
        this.compressionThreshold = compressionThreshold;
        this.compressionAlgorithmSupplierFactory = compressionAlgorithmSupplierFactory;
        this.algorithm = algorithm;
    }

    public void savePayload(String key, String payload) {
        try {
            byte[] rawBytes = payload.getBytes(StandardCharsets.UTF_8);
            byte[] finalData;

            if (rawBytes.length > compressionThreshold) {
                log.info("Payload size {} MB, exceeds threshold. Compressing...",
                        rawBytes.length / (1024.0 * 1024.0));
                log.info("-----SPEED BENCHMARK-----");
                byte[] compressedBytes = new byte[0];
                for (int i = 1; i <= 3; i++) {
                    long start = System.nanoTime();
                    compressedBytes = compressionAlgorithmSupplierFactory.compress(algorithm, rawBytes);
                    log.info("-----Iteration {} ===> {} compression time {}", i, algorithm, (System.nanoTime() - start) / 1_000_000.0);
                }


                byte flag = magicFlagSupplier.get(algorithm);

                // Create a new buffer: 1 byte for flag + length of compressed data
                finalData = ByteBuffer.allocate(1 + compressedBytes.length)
                        .put(flag) //stored the flag first
                        .put(compressedBytes) //then actual data
                        .array();
            } else {
                log.info("Payload size {} MB, is under threshold. Storing as raw bytes.",
                        rawBytes.length / (1024.0 * 1024.0));

                // Create a new buffer: 1 byte for flag + length of raw data
                finalData = ByteBuffer.allocate(1 + rawBytes.length)
                        .put(FLAG_RAW)
                        .put(rawBytes)
                        .array();
            }

            binaryRedisTemplate.opsForValue().set(key, finalData);
            log.info("Successfully saved payload to Redis for key: {}", key);

        } catch (Exception e) {
            log.error("Failed to save payload to Redis for key: {}", key, e);
            throw new RuntimeException("Redis save operation failed", e);
        }
    }

    public String getPayload(String key) {
        try {
            byte[] retrievedData = binaryRedisTemplate.opsForValue().get(key);

            if (retrievedData == null || retrievedData.length == 0) {
                return null;
            }

            // Read the first byte to check the flag
            ByteBuffer buffer = ByteBuffer.wrap(retrievedData);
            byte flag = buffer.get();

            // Read the remaining bytes (the actual data)
            byte[] actualData = new byte[buffer.remaining()];
            buffer.get(actualData);

            // Dynamically decompress based on the flag
            byte[] decompressedBytes = new byte[0];
            for (int i = 1; i <= 3; i++) {
                long start = System.nanoTime();
                if (flag == FLAG_DEFLATE) {
                    decompressedBytes = compressionAlgorithmSupplierFactory
                            .decompress("DEFLATE", actualData);
                } else if (flag == FLAG_SNAPPY) {
                    decompressedBytes = compressionAlgorithmSupplierFactory
                            .decompress("SNAPPY", actualData);
                } else if (flag == FLAG_LZ4) {
                        decompressedBytes = compressionAlgorithmSupplierFactory
                                .decompress("LZ4", actualData);
                } else if (flag == FLAG_ZSTD) {
                    decompressedBytes = compressionAlgorithmSupplierFactory
                            .decompress("ZSTD", actualData);
                } else if (flag == FLAG_RAW) {
                    decompressedBytes = actualData;
                } else {
                    throw new IllegalStateException("Unknown flag byte: " + flag);
                }

                log.info("-----Iteration {} ===> {} de-compression time : {}", i, algorithm, (System.nanoTime() - start) / 1_000_000.0);
            }
            return new String(decompressedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Failed to retrieve or decompress payload for key: {}", key, e);
            throw new RuntimeException("Redis retrieve operation failed", e);
        }
    }
}