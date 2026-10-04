package com.practice.rediscompression;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class ProductionCompressionTestRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionCompressionTestRunner.class);

    private final DynamicRedisPayloadService payloadService;
    private final RedisTemplate<String, byte[]> binaryRedisTemplate;

    public ProductionCompressionTestRunner(DynamicRedisPayloadService payloadService,
                                           RedisTemplate<String, byte[]> binaryRedisTemplate) {
        this.payloadService = payloadService;
        this.binaryRedisTemplate = binaryRedisTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("--- Starting Production Compression Test ---");

        // 1. Generate the massive 80k row payload
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 80000; i++) {
            sb.append("{\"id\":").append(i).append(", \"name\":\"User ").append(i)
                    .append("\", \"status\":\"active\", \"role\":\"admin\"}\n");
        }
        String originalPayload = sb.toString();

        // Calculate what the size WOULD be without compression
        byte[] rawBytes = originalPayload.getBytes(StandardCharsets.UTF_8);
        double uncompressedSizeMB = rawBytes.length / (1024.0 * 1024.0);

        // 2. Save using our production service (It will automatically compress if > 1MB)
        String testKey = "prod:user:80k:payload";
        payloadService.savePayload(testKey, originalPayload);

        // 3. Peek directly into Redis to see the actual size stored (including our 1-byte flag)
        byte[] storedBytes = binaryRedisTemplate.opsForValue().get(testKey);
        double storedSizeMB = storedBytes.length / (1024.0 * 1024.0);

        // 4. Verify we can read it back correctly
        String retrievedPayload = payloadService.getPayload(testKey);
        boolean isMatch = originalPayload.equals(retrievedPayload);

        // 5. Print the results
        System.out.println("\n=============================================");
        System.out.println("          COMPRESSION TEST RESULTS           ");
        System.out.println("=============================================");
        System.out.printf("Original Size (Before):  %.2f MB%n", uncompressedSizeMB);
        System.out.printf("Stored Size (After):     %.2f MB%n", storedSizeMB);
        System.out.println("---------------------------------------------");
        System.out.printf("Netty Memory Saved:      %.1f%%%n",
                (1.0 - (storedSizeMB / uncompressedSizeMB)) * 100);
        System.out.println("Data Integrity Intact?   " + (isMatch ? "YES" : "NO"));
        System.out.println("=============================================\n");
    }
}