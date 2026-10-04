package com.practice.rediscompression.algorithmsupplier;

import com.practice.rediscompression.compressionalgos.DeflateCompression;
import com.practice.rediscompression.compressionalgos.Lz4Compression;
import com.practice.rediscompression.compressionalgos.SnappyCompression;
import com.practice.rediscompression.compressionalgos.ZstdCompression;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class CompressionAlgorithmSupplierFactory {

    public final Map<String, CompressionAlgorithm> compressor = new HashMap<>();
    public final Map<String, CompressionAlgorithm> deCompressor = new HashMap<>();

    private CompressionAlgorithmSupplierFactory() {

        compressor.put("DEFLATE", DeflateCompression::compress);
        compressor.put("SNAPPY", SnappyCompression::compress);
        compressor.put("LZ4", Lz4Compression::compress);
        compressor.put("ZSTD", ZstdCompression::compress);

        deCompressor.put("DEFLATE", DeflateCompression::decompress);
        deCompressor.put("SNAPPY", SnappyCompression::decompress);
        deCompressor.put("LZ4", Lz4Compression::decompress);
        deCompressor.put("ZSTD", ZstdCompression::decompress);

    }

    public byte[] compress(String algo, byte[] data) throws IOException {
        CompressionAlgorithm compressionAlgorithm = compressor.get(algo.toUpperCase(Locale.ENGLISH));
        if (compressionAlgorithm == null) {
            throw new RuntimeException("Compression not supported for : " + algo);
        }

        return compressionAlgorithm.apply(data);
    }

    public byte[] decompress(String algo, byte[] data) throws IOException {
        CompressionAlgorithm compressionAlgorithm = deCompressor.get(algo.toUpperCase(Locale.ENGLISH));
        if (compressionAlgorithm == null) {
            throw new RuntimeException("Decompression not supported for : " + algo);
        }
        return compressionAlgorithm.apply(data);
    }


}
