package dev.godofwharf.onebrc.models;

public class AggregationResult {
    private int min;
    private int max;
    private long sum;
    private int count;

    public AggregationResult() {
        this.min = Integer.MAX_VALUE;
        this.max = Integer.MIN_VALUE;
        this.sum = 0;
        this.count = 0;
    }

    public AggregationResult(final int min,
                             final int max,
                             final long sum,
                             final int count) {
        this.min = min;
        this.max = max;
        this.sum = sum;
        this.count = count;
    }

    public AggregationResult(final int temperature) {
        this.min = temperature;
        this.max = temperature;
        this.sum = temperature;
        this.count = 1;
    }

    public AggregationResult update(final int temperature) {
        this.min = Math.min(min, temperature);
        this.max = Math.max(max, temperature);
        this.sum += temperature;
        this.count += 1;
        return this;
    }

    public void merge(final AggregationResult other) {
        this.min = Math.min(min, other.min);
        this.max = Math.max(max, other.max);
        this.sum += other.sum;
        this.count += other.count;
    }

    @Override
    public String toString() {
        double minD = min / 10.0;
        double maxD = max / 10.0;
        double meanD = round(round(sum / 10.0) / count);
        return minD + "/" + meanD + "/" + maxD;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
