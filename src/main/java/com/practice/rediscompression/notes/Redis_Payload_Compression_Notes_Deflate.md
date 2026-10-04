# Redis Payload Compression Notes: How Deflate Works

This document breaks down how the Java `DeflaterOutputStream` (which uses the **Deflate** algorithm) achieves massive memory savings—often **90% or more**—when compressing repetitive data like large JSON arrays before they are sent to Redis.

## The Two-Step Compression Process

The Deflate algorithm combines two distinct compression techniques in a specific order:

1. **LZ77 (Sliding Window):** Replaces repeating sequences of characters with tiny pointers.
2. **Huffman Coding:** Replaces standard 8-bit characters with shorter bit codes based on frequency.

## Step 1: The LZ77 "Sliding Window" Algorithm

LZ77 works by maintaining a sliding window of the last **32 Kilobytes** of text it has processed. As it reads new text, it constantly checks its history to see if it has already processed that exact sequence of characters.

If it finds a match, it deletes the redundant text and replaces it with a pointer that essentially says:

```text
[Go back X characters, Copy Y characters]
```

This is commonly referred to as a **length/distance pair**.

### Visualizing the Pointer Mechanism

Imagine parsing repetitive JSON rows. Here is how the search buffer identifies matches and creates pointers:

```text
Plaintext

[SEARCH BUFFER (History)]        [LOOK-AHEAD BUFFER (Current)]

{"id":1001, "status":"active"}   {"id":1002, "status":"active"}
  ▲                                ▲
  |________________________________|
            Match found!
```

Replace the matching sequence with a pointer:

```text
[Go back 31, Copy 9]
```

> The exact distance and length depend on where the matching bytes occur in the compression window.

## JSON Example: Compressing 80,000 Rows

Because JSON payloads are highly structured, the keys, spacing, and brackets repeat identically on every single row. Here is how LZ77 strips that out.

### The Raw Data

```text
Row 1: {"id":1001, "status":"active", "role":"admin"}
Row 2: {"id":1002, "status":"failed", "role":"user"}
Row 3: {"id":1003, "status":"active", "role":"admin"}
```

### How LZ77 Processes It

#### Row 1 (The Baseline)

Since the history buffer is empty, the first row is written out exactly as it appears. This populates the search buffer with the structure of your JSON.

```text
Output: {"id":1001, "status":"active", "role":"admin"}
```

#### Row 2 (Handling Changing Values)

LZ77 begins reading the second row and immediately finds matches in its history.

```text
Reads {"id":100 → Found in Row 1!
Inserts pointer: [Go back 44, Copy 9]

Reads 2 → New data.
Writes literal 2.

Reads , "status":" → Found in Row 1!
Inserts pointer: [Go back 43, Copy 12]

Reads failed" → New data.
Writes literal failed".

Reads , "role":" → Found in Row 1!
Inserts pointer: [Go back 42, Copy 10]

Reads user"} → New data.
Writes literal user"}.
```

Conceptually, the compressed output becomes:

```text
[Pointer] 2 [Pointer] failed" [Pointer] user"}
```

#### Row 3 (The Jackpot)

By Row 3, LZ77 finds massive contiguous chunks of text that match.

```text
Reads {"id":100 → Found in history!
Inserts pointer.

Reads 3 → New data.
Writes literal 3.

Reads , "status":"active", "role":"admin"} 
→ This entire chunk matches the end of Row 1 perfectly.
→ It groups it all into one pointer.
```

Conceptually:

```text
[Pointer] 3 [Pointer]
```

## Step 2: Huffman Coding (The "Morse Code" Phase)

After LZ77 strips out all the repeating JSON structure, you are left with pointers and the raw, changing values (like the numbers `2` and `3`, or the word `failed`).

Normally, every character takes exactly **8 bits**. Huffman Coding scans this remaining text and assigns new, shorter bit-codes based on how frequently a character appears.

```text
Common characters → shorter codes
Rare characters   → longer codes
```

For example, common characters such as `"`, `{`, `}`, `e`, and `a` may get shorter codes, while rare characters such as `z`, `q`, and `x` may get longer codes.

> In Deflate, Huffman coding is applied to the literals and the length/distance symbols produced by the LZ77 stage.

By reducing the physical size of the most common remaining symbols, the final payload shrinks down to a fraction of its original size—allowing Netty to easily buffer the data and Redis to store it effortlessly.
