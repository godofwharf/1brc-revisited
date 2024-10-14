package dev.godofwharf.onebrc;

import dev.godofwharf.onebrc.models.AggregationKey;
import dev.godofwharf.onebrc.models.AggregationResult;
import dev.godofwharf.onebrc.models.AggregationState;
import java.io.PrintStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Solver {
    public static final int LOOKAHEAD_BYTES = 128;
    private Path inputFilePath;
    private int nCores;
    private ExecutorService executorService;

    public Solver(final Path inputFilePath,
                  final int nCores) {
        this.inputFilePath = inputFilePath;
        this.nCores = nCores;
        this.executorService = Executors.newFixedThreadPool(nCores);
    }

    public void solve() throws Exception {
        // create a memory mapped file
        MemoryMappedFile file = TimedCallable.call(() -> {
            try {
                return mmapFile();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "mmapFile");
        // split the memory mapped file into multiple chunks based on number of threads
        List<Chunk> chunks = TimedCallable.call(() -> splitFileIntoChunks(file), "splitFile");
        System.out.printf("Chunks = [%s]%n", chunks);
        AggregationState aggregationState = new AggregationState();
        List<Future<?>> futures = new ArrayList<>();
        TimedCallable.call(() -> {
            try {
                for (Chunk chunk: chunks) {
                    // create a memory segment corresponding to the chunk under scope
                    MemorySegment chunkSegment = file.fileSegment.asSlice(chunk.offset, chunk.length);
                    // parse each chunk on a separate thread

                    futures.add(executorService.submit(() -> {
                        long t1 = System.nanoTime();
                        LinearProbedMap<AggregationKey, AggregationResult> threadLocalState = aggregationState.getOrCreateState();
                        parseChunkVectorized(chunkSegment, threadLocalState);
                        System.out.println("Work completed for thread: %s in %d ms"
                                .formatted(Thread.currentThread().threadId(), Duration.ofNanos(System.nanoTime() - t1).toMillis()));
                    }));
                }
                // wait for all the workers to finish
                for (Future<?> future: futures) {
                    future.get();
                }
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "parseChunk");

        // merge the thread-local state maps
        Map<AggregationKey, AggregationResult> merged = TimedCallable.call(aggregationState::merge, "merge");
        // print the results ordered by key in alphabetical order
        TimedCallable.call(() -> {
            print(merged, System.out);
            return null;
        }, "print");
        System.exit(0);
    }

    public MemoryMappedFile mmapFile() throws Exception {
        // Create a memory-mapped file
        FileChannel fc = FileChannel.open(inputFilePath);
        // Get size of file
        // Offset range is from [0, fileSize)
        long fileSize = fc.size();
        MemorySegment fileSegment = fc.map(FileChannel.MapMode.READ_ONLY, 0, fileSize, Arena.global());
        return new MemoryMappedFile(fileSegment, fileSize);
    }

    public List<Chunk> splitFileIntoChunks(final MemoryMappedFile file) {
        List<Chunk> chunks = new ArrayList<>();
        // Calculate average chunk size
        long averageChunkSize = file.size / nCores;
        long prevOffset = 0;
        long nextOffset = 0;
        for (int i = 0; i < nCores; i++) {
            // Max value of offset is fileSize - 1
            nextOffset = Math.min(nextOffset + averageChunkSize, file.size - 1);
            // Adjust lookahead if it goes beyond end of file
            int lookahead = (nextOffset + LOOKAHEAD_BYTES) > file.size ? (int) (file.size - nextOffset): LOOKAHEAD_BYTES;
            MemorySegment chunkSegment = file.fileSegment.asSlice(nextOffset, lookahead);
            byte[] bytes = chunkSegment.toArray(ValueLayout.OfByte.JAVA_BYTE);
            int j = 0;
            // Iterate until a newline character is found
            while (j < lookahead && bytes[j] != '\n') {
                j++;
            }
            // Go one past the newline character
            j++;
            // Adjust nextOffset if we go past fileSize
            nextOffset = Math.min(nextOffset + j, file.size);
            chunks.add(new Chunk(prevOffset, nextOffset - prevOffset));
            prevOffset = nextOffset;
        }
        return chunks;
    }

    // Loop with Default HashMap: 47 seconds
    // Loop with LinearProbedMap: 43 seconds
    // Loop with String2Integer.parseInt: 38.7 seconds
    // Loop with split replaced by System.arraycopy: 28.2 seconds
    public void parseChunk(final MemorySegment chunkSegment,
                           final LinearProbedMap<AggregationKey, AggregationResult> state) {
        long i = 0;
        int j = 0;
        int k = 0;
        byte[] trail = new byte[128];
        long n = chunkSegment.byteSize();
        while (i < n) {
            byte b = chunkSegment.get(ValueLayout.OfChar.JAVA_BYTE, i);
            if (b == ((byte) '\n') || i + 1 == n) {
                byte[] station = new byte[k - 1];
                System.arraycopy(trail, 0, station, 0, k - 1);
                int d = String2Integer.parseInt(trail, k, j - k);
                AggregationState.updateStateMap(state, new AggregationKey(station), d);
                j = 0;
                k = 0;
            } else {
                trail[j] = b;
                if (b == ';') {
                    k = j + 1;
                }
                j++;
            }
            i++;
        }
    }

    // Vectorized Parsing with 1 MiB pages: 23.9 seconds (lot of variance in running time for threads)
    // Vectorized Parsing with 2 MiB pages - byte[] as keys: 21.5 seconds
    // Vectorized Parsing with 2 MiB pages - byte[] as keys - fork-join pool: 21.5 seconds
    public void parseChunkVectorized(final MemorySegment chunkSegment,
                                     final LinearProbedMap<AggregationKey, AggregationResult> state) {
        int chunkSize = (int) chunkSegment.byteSize();
        int pageSize = 2 * 1024 * 1024;
        int startPageOffset = 0;
        VectorizedParser parser = new VectorizedParser();
        while (startPageOffset < chunkSize) {
            int truePageSize = startPageOffset + pageSize >= chunkSize ? chunkSize - startPageOffset: pageSize;
            int endPageOffset = startPageOffset + truePageSize;
            while (endPageOffset < chunkSize &&
                    chunkSegment.get(ValueLayout.OfByte.JAVA_BYTE, endPageOffset) != '\n') {
                endPageOffset++;
            }
            parser.parse(state, chunkSegment.asSlice(startPageOffset, endPageOffset - startPageOffset).toArray(ValueLayout.OfByte.JAVA_BYTE));
            //parser.parse2(state, chunkSegment.asSlice(startPageOffset, endPageOffset - startPageOffset).toArray(ValueLayout.OfByte.JAVA_BYTE));
            startPageOffset = endPageOffset + 1;
        }
    }

    public void print(final Map<AggregationKey, AggregationResult> map,
                      final PrintStream out) {
        Map<String, AggregationResult> sortedMap = new TreeMap<>();
        for (Map.Entry<AggregationKey, AggregationResult> entry: map.entrySet()) {
            sortedMap.put(
                    new String(entry.getKey().getBytes(), StandardCharsets.UTF_8), entry.getValue());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        int size = sortedMap.size();
        AtomicInteger count = new AtomicInteger();
        sortedMap.forEach((k, v) -> {
            sb.append(k);
            sb.append("=");
            sb.append(v);
            if (count.incrementAndGet() != size) {
                sb.append(",");
            }
        });
        sb.append("}");
        out.println(sb);
    }

    record Chunk(long offset, long length) {}

    record MemoryMappedFile(MemorySegment fileSegment, long size) {}
}
