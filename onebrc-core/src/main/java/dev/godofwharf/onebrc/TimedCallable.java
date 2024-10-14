package dev.godofwharf.onebrc;

import java.time.Duration;
import java.util.function.Supplier;

public class TimedCallable<T> {
    public Supplier<T> wrap(final Supplier<T> supplier,
                            final String stage) throws Exception {
        return () -> {
            long t1 = System.nanoTime();
            try {
                return supplier.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                System.out.printf(
                        "Stage -> %s took %d ms %n",
                        stage, Duration.ofNanos(System.nanoTime() - t1).toMillis());
            }
        };
    }

    public static <T> T call(final Supplier<T> supplier,
                             final String stage) throws Exception {
        return new TimedCallable<T>().wrap(supplier, stage).get();
    }
}
