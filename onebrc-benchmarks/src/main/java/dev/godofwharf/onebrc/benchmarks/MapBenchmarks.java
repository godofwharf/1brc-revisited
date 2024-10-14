package dev.godofwharf.onebrc.benchmarks;

import dev.godofwharf.onebrc.LinearProbedMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.DoubleStream;

import static java.util.concurrent.TimeUnit.SECONDS;

@BenchmarkMode(value = { Mode.Throughput })
@Warmup(iterations = 3, time = 5, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(value = 1)
@OutputTimeUnit(SECONDS)
@State(Scope.Benchmark)
public class MapBenchmarks {
    private Input input;

    public static class Input {
        private List<String> stations = new ArrayList<>();
        private List<TemperatureMeasurement> measurements = new ArrayList<>();

        public Input() throws Exception {
            try (BufferedReader br =
                         new BufferedReader(
                                 new InputStreamReader(MapBenchmarks.class.getResourceAsStream("/weather_stations.txt")))) {
                String line;
                while ((line = br.readLine()) != null) {
                    stations.add(line.trim());
                }
            }
            DoubleStream stream = ThreadLocalRandom.current().doubles(10000000, -99.9, 99.9);
            stream.forEach(value -> {
                int idx = ThreadLocalRandom.current().nextInt(stations.size());
                measurements.add(new TemperatureMeasurement(stations.get(idx), value));
            });
        }

        public record TemperatureMeasurement(String station, double temperature) {}
    }

    @Setup
    public void setup() throws Exception {
        this.input = new Input();
    }

    @Benchmark
    public void measureHashMap(final Blackhole blackhole) {
        Map<String, Aggregate> map = new HashMap<>(16384);
        for (Input.TemperatureMeasurement measurement: input.measurements) {
            blackhole.consume(map.compute(measurement.station, (k, v) -> {
                int quantisedTemperature = (int) measurement.temperature * 10;
                if (v == null) {
                    return new Aggregate(quantisedTemperature);
                }
                v.update(quantisedTemperature);
                return v;
            }));
        }
    }

    @Benchmark
    public void measureHashMapWithLinearProbing(final Blackhole blackhole) {
        LinearProbedMap<String, Aggregate> map = new LinearProbedMap<>(16384);
        for (Input.TemperatureMeasurement measurement: input.measurements) {
            blackhole.consume(map.compute(measurement.station, (k, v) -> {
                int quantisedTemperature = (int) measurement.temperature * 10;
                if (v == null) {
                    return new Aggregate(quantisedTemperature);
                }
                v.update(quantisedTemperature);
                return v;
            }));
        }
    }

    public static class Aggregate {
        private int min;
        private int max;
        private int sum;
        private int count;

        public Aggregate(int measurement) {
            this.min = measurement;
            this.max = measurement;
            this.sum = measurement;
            this.count = 1;
        }

        public void update(int measurement) {
            this.min = Math.min(measurement, min);
            this.max = Math.max(measurement, max);
            this.sum = sum + measurement;
            this.count++;
        }
    }
}
