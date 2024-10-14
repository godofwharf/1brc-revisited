package dev.godofwharf.onebrc.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

@BenchmarkMode(value = { Mode.Throughput })
@Warmup(iterations = 3, time = 5, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(value = 1)
@OutputTimeUnit(MILLISECONDS)
@State(Scope.Benchmark)
public class StringBenchmarks {
    private Input input;

    public static class Input {
        private List<String> strings = new ArrayList<>();
        private List<byte[]> byteArrays = new ArrayList<>();

        public Input() {
            for (int i = 0; i < 1000; i++) {
                StringBuilder s = new StringBuilder();
                for (int j = 0; j < 100; j++) {
                    char c = (char) (97 + ThreadLocalRandom.current().nextInt(26));
                    s.append(c);
                }
                strings.add(s.toString());
                byteArrays.add(s.toString().getBytes(StandardCharsets.UTF_8));
            }
        }

    }

    @Setup(Level.Iteration)
    public void setup() {
        this.input = new Input();
    }

    @Benchmark
    public void measureStringHashCode(Blackhole bh) throws InterruptedException {
        for (String s: input.strings) {
            bh.consume(s.hashCode());
        }
    }

    @Benchmark
    public void measureArrayHashCode(Blackhole bh) {
        for (byte[] b : input.byteArrays) {
            bh.consume(Arrays.hashCode(b));
        }
    }

}
