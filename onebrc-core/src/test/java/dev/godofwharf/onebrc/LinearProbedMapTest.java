package dev.godofwharf.onebrc;

import dev.godofwharf.onebrc.models.AggregationKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class LinearProbedMapTest {

    @Test
    public void testByComparison() throws Exception {
        List<String> stations = new ArrayList<>();
        try (BufferedReader br =
                     new BufferedReader(
                             new InputStreamReader(
                                     LinearProbedMapTest.class.getResourceAsStream("/weather_stations.txt")))) {
            String line;
            while ((line = br.readLine()) != null) {
                stations.add(line.trim());
            }
        }
        Map<AggregationKey, Integer> a = new HashMap<>();
        LinearProbedMap<AggregationKey, Integer> b = new LinearProbedMap<>(1024);
        for (int i = 0; i < 10000; i++) {
            String s = stations.get(ThreadLocalRandom.current().nextInt(stations.size()));
            a.compute(AggregationKey.from(s), (k, v) -> {
                if (v == null) {
                    v = 1;
                    return v;
                }
                v++;
                return v;
            });
            b.compute(AggregationKey.from(s), (k, v) -> {
                if (v == null) {
                    v = 1;
                    return v;
                }
                v++;
                return v;
            });
        }
        Map<AggregationKey, Integer> c = new HashMap<>();
        b.entrySet().forEach(entry -> c.put(entry.getKey(), entry.getValue()));
        for (AggregationKey ak: a.keySet()) {
            if (!c.containsKey(ak)) {
                System.out.println("Key missing: %s"
                        .formatted(new String(ak.getBytes(), StandardCharsets.UTF_8)));
            }
        }
        Assertions.assertEquals(a.size(), c.size());
        Assertions.assertEquals(a, c);
    }

    @Test
    public void testCompute() {
        LinearProbedMap<AggregationKey, Integer> b = new LinearProbedMap<>(128);
        b.put(AggregationKey.from("abc"), 12);
        Assertions.assertEquals(12, b.get(AggregationKey.from("abc")));
        b.put(AggregationKey.from("abc"), 15);
        Assertions.assertEquals(15, b.get(AggregationKey.from(("abc"))));
        b.put(AggregationKey.from("def"), -10);
        Assertions.assertEquals(-10, b.get(AggregationKey.from(("def"))));
        Assertions.assertEquals(null, b.get(AggregationKey.from(("def1"))));
        b.compute(AggregationKey.from("def"), (k, v) -> {
            if (v == null) {
                v = 1;
                return v;
            }
            v++;
            return v;
        });
        Assertions.assertEquals(-9, b.get(AggregationKey.from("def")));
        b.compute(AggregationKey.from("def1"), (k, v) -> {
            if (v == null) {
                v = 1;
                return v;
            }
            v++;
            return v;
        });
        Assertions.assertEquals(1, b.get(AggregationKey.from("def1")));
    }
}
