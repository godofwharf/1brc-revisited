package dev.godofwharf.onebrc.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.DoubleStream;

import static java.util.concurrent.TimeUnit.SECONDS;

@BenchmarkMode(value = { Mode.Throughput })
@Warmup(iterations = 3, time = 5, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(value = 1)
@OutputTimeUnit(SECONDS)
@State(Scope.Benchmark)
public class FloatingPointBenchmarks {
    private Input input;

    public static class Input {
        private List<Double> measurements1 = new ArrayList<>();
        private List<Integer> measurements2 = new ArrayList<>();
        private List<Short> measurements3 = new ArrayList<>();

        public Input() {
            DoubleStream stream = ThreadLocalRandom.current().doubles(10000000, -99.9, 99.9);
            stream.forEach(value -> {
                measurements1.add(value);
                measurements2.add((int) value * 10);
                measurements3.add((short) (value * 10));
            });
        }
    }

    @Setup
    public void setup() {
        this.input = new Input();
    }

    @Benchmark
    public void measureFloatingPointArithmetic(Blackhole blackhole) {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        double sum = 0.0;
        int count = 0;
        for (double measurement: input.measurements1) {
            min = Math.min(min, measurement);
            max = Math.max(max, measurement);
            sum += measurement;
            count++;
        }
        blackhole.consume(min);
        blackhole.consume(max);
        blackhole.consume(sum);
        blackhole.consume(count);
    }

    @Benchmark
    public void measureIntegerArithmetic(Blackhole blackhole) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int sum = 0;
        int count = 0;
        for (int measurement: input.measurements2) {
            min = Math.min(min, measurement);
            max = Math.max(max, measurement);
            sum += measurement;
            count++;
        }
        blackhole.consume(min);
        blackhole.consume(max);
        blackhole.consume(sum);
        blackhole.consume(count);
    }

    @Benchmark
    public void measureShortArithmetic(Blackhole blackhole) {
        short min = Short.MAX_VALUE;
        short max = Short.MIN_VALUE;
        int sum = 0;
        int count = 0;
        for (short measurement: input.measurements3) {
            min = (short) Math.min(min, measurement);
            max = (short) Math.max(max, measurement);
            sum += measurement;
            count++;
        }
        blackhole.consume(min);
        blackhole.consume(max);
        blackhole.consume(sum);
        blackhole.consume(count);
    }
}
