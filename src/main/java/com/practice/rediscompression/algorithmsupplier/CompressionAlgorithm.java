package com.practice.rediscompression.algorithmsupplier;

import java.io.IOException;

@FunctionalInterface
public interface CompressionAlgorithm {

    byte[] apply(byte[] data) throws IOException;
}
