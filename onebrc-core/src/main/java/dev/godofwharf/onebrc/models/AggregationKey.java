package dev.godofwharf.onebrc.models;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class AggregationKey {
    private final byte[] bytes;
    private final Integer hashcode;

    public AggregationKey(final byte[] bytes) {
        this.bytes = bytes;
        this.hashcode = null;
    }

    public AggregationKey(final byte[] bytes,
                          final Integer hashcode) {
        this.bytes = bytes;
        this.hashcode = hashcode;
    }

    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public int hashCode() {
        if (hashcode != null) {
            return hashcode;
        }
        return Arrays.hashCode(bytes);
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof AggregationKey ak)) {
            return false;
        }
        return (hashcode != null && (hashcode == ak.hashcode)) ||
                Arrays.mismatch(bytes, ak.bytes) == -1;
    }

    @Override
    public String toString() {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static AggregationKey from(final String s) {
        return new AggregationKey(s.getBytes(StandardCharsets.UTF_8));
    }
}
