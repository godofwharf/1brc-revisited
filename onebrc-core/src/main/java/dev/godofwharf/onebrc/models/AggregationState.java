package dev.godofwharf.onebrc.models;

import dev.godofwharf.onebrc.LinearProbedMap;

import java.util.*;

public class AggregationState {
    private Map<Long, LinearProbedMap<AggregationKey, AggregationResult>> state;

    public AggregationState() {
        this.state = new HashMap<>();
    }

    public LinearProbedMap<AggregationKey, AggregationResult> getOrCreateState() {
        synchronized (this) {
            long key = Thread.currentThread().threadId();
            LinearProbedMap<AggregationKey, AggregationResult> value = new LinearProbedMap<>(4096);
            state.put(key, value);
            return value;
        }
    }

    public Map<AggregationKey, AggregationResult> merge() {
        if (state.isEmpty()) {
            return new HashMap<>();
        }
        Map<AggregationKey, AggregationResult> merged = new HashMap<>();
        for (LinearProbedMap<AggregationKey, AggregationResult> map: state.values()) {
            map.entrySet().forEach(entry ->
                merged.compute(entry.getKey(), (ignored, v1) -> {
                    if (v1 == null) {
                        v1 = entry.getValue();
                    } else {
                        v1.merge(entry.getValue());
                    }
                    return v1;
                }));
        }
        return merged;
    }

    // This method was re-written so as to not use compute which uses lambda functions
    // This was done so that the compiler may inline the function call
    public static void updateStateMap(final LinearProbedMap<AggregationKey, AggregationResult> map,
                                      final AggregationKey key,
                                      final int value) {
        AggregationResult result = map.get(key);
        if (result != null) {
            result.update(value);
        } else {
            // slow path
            map.put(key, new AggregationResult(value));
        }
    }
}
