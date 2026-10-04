# Enterprise Redis Payload Compression Strategy

## 1. The Problem: Netty OOM & Redis Latency
When a Spring Boot application pushes massive data (e.g., a 5.24 MB JSON payload of 80,000 rows) into Redis, it creates two system-killing bottlenecks:
1. **Netty OOM (Out of Memory):** Netty, the underlying network engine for Lettuce, allocates massive memory buffers to handle the 5.24 MB blob. Multiple concurrent requests will quickly exhaust the Java Heap space.
2. **Redis Blocking:** Redis is single-threaded. Writing a massive 5MB string blocks all other database operations, causing system-wide latency spikes.

**The Solution:** Binary compression at the application level. By compressing the payload *before* passing it to Netty, we eliminate the OOM crashes and drastically reduce Redis RAM usage.

---

## 2. The Benchmark: Choosing the Right Algorithm
We benchmarked the four major enterprise compression algorithms against our specific 5.24 MB JSON payload.

*Note: All speed metrics represent "warmed-up" Java Virtual Machine (JVM) speeds. Native JNI libraries (Snappy, LZ4, Zstd) experience a ~100ms+ penalty on their very first execution while loading native C++ bindings, but drop to single-digit milliseconds immediately after.*

| Algorithm | Compression (Write) | Decompression (Read) | Payload Size | Memory Saved |
| :--- | :--- | :--- | :--- | :--- |
| **Deflate** (Java Native) | ~75.0 ms | ~24.0 ms | 0.40 MB | 92.4% |
| **Snappy** (Google) | ~5.5 ms | **~3.1 ms** | 0.77 MB | 85.4% |
| **LZ4** | **~4.7 ms** | ~7.0 ms | ~0.90 MB | ~80.0% |
| **Zstd** (Facebook) | ~6.1 ms | ~6.1 ms | **0.10 MB** | **98.0%** |

### The Verdict: Zstandard (Zstd)
**Zstd is the undisputed champion.** It compressed a 5.24 MB payload down to an astonishing 100 KB (averaging ~1.25 bytes per JSON row) by mapping the repetitive JSON structure perfectly. It achieved this while maintaining single-digit millisecond CPU speeds, giving us the file size of Deflate with the blazing speed of Snappy.

---

## 3. The Architecture: Dynamic Routing

To safely implement this in production without messy `if/else` statements, we utilized two design patterns: **The Magic Byte** and **The Factory Pattern**.

### A. The "Magic Byte" Flag
When retrieving a binary payload from Redis, the application is blind—it doesn't know if the data is raw text, Deflate, or Zstd.
We solve this by prepending a single 1-byte "Flag" to the front of every byte array before saving it.
* `0x00` = RAW (No compression)
* `0x01` = DEFLATE
* `0x02` = SNAPPY
* `0x03` = LZ4
* `0x04` = ZSTD

Visually, the Redis payload looks like this: `[0x04] [compressed zstd bytes...]`

### B. The Factory Pattern Implementation
We map functional interfaces to dynamically route the data based on the application's configuration (for writes) or the Magic Byte (for reads).

**1. The Functional Interface:**
```java
@FunctionalInterface
public interface CompressionAlgorithm {
    byte[] apply(byte[] data) throws IOException;
}