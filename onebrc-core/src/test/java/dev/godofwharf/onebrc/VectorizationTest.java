package dev.godofwharf.onebrc;

import jdk.incubator.vector.ByteVector;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

public class VectorizationTest {
    private static final long MAGIC_MULTIPLIER = (100 * 0x1000000 + 10 * 0x10000 + 1);
    private static final long DOT_DETECTOR = 0x10101000;
    private static final long ASCII_TO_DIGIT_MASK = 0x0F000F0F00L;

    private static final Unsafe unsafe;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    @Test
    public void testSimd() throws IOException {
        byte[] bytes =
                VectorizationTest.class.getResourceAsStream("/test_measurements.txt").readAllBytes();
        ByteVector semiColonBroadcast = ByteVector.broadcast(ByteVector.SPECIES_PREFERRED, (byte) ';');
        int currentOffset = 0;
        int endOffset = bytes.length;
        while (currentOffset < endOffset - ByteVector.SPECIES_PREFERRED.vectorByteSize() - 1) {
            ByteVector v1 = ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, bytes, currentOffset);
            long eqResult = semiColonBroadcast.eq(v1).toLong();
            if (eqResult == 0) {
                currentOffset = parseSlowTillNewline(currentOffset, bytes);
            }
            int prevOffset = currentOffset;
            while (eqResult > 0) {
                int trailingZerosCnt = Long.numberOfTrailingZeros(eqResult);
                if (trailingZerosCnt < 64) {
                    int temperature;
                    int semiColonPos = trailingZerosCnt;
                    byte[] station = new byte[semiColonPos];
                    System.arraycopy(bytes, currentOffset, station, 0, semiColonPos);
                    int temperatureStartOffset = currentOffset + semiColonPos + 1;
                    long x = extractLong(bytes, temperatureStartOffset);
                    ParseResult parseResult = parseDataPoint(x);
                    currentOffset = (int) (temperatureStartOffset + parseResult.nextLineStart);
                    temperature = parseResult.temperature;
//                    if (currentOffset + semiColonPos + 4 < endOffset &&
//                            bytes[currentOffset + semiColonPos + 4] == '\n') {
//                        temperature = String2Integer.parseInt(bytes, temperatureStartOffset, 3);
//                        currentOffset += (semiColonPos + 5);
//                    } else if (currentOffset + semiColonPos + 5 < endOffset &&
//                            bytes[currentOffset + semiColonPos + 5] == '\n') {
//                        temperature = String2Integer.parseInt(bytes, temperatureStartOffset, 4);
//                        currentOffset += (semiColonPos + 6);
//                    } else if (currentOffset + semiColonPos + 6 < endOffset &&
//                            bytes[currentOffset + semiColonPos + 6] == '\n') {
//                        temperature = String2Integer.parseInt(bytes, temperatureStartOffset, 5);
//                        currentOffset += (semiColonPos + 7);
//                    } else {
//                        temperature = String2Integer.parseInt(bytes,
//                                temperatureStartOffset, endOffset - (temperatureStartOffset));
//                        currentOffset = endOffset;
//                    }
                    System.out.printf("station=%s, temperature=%d%n",
                            new String(station, StandardCharsets.UTF_8), temperature);
                } else {
                    break;
                }
                eqResult = eqResult >> (currentOffset - prevOffset);
                prevOffset = currentOffset;
            }
        }
        parseSlow(currentOffset, endOffset, bytes);
    }

    private static long extractLong(final byte[] b,
                                    final int offset) {
        long x = 0;
        for (int i = offset; i < b.length && i < offset + 8; i++) {
            x |= ((b[i] & 0xFFL) << ((i - offset) * 8));
        }
        return x;
    }

    private static void parseSlow(final int currentOffset,
                                  final int n,
                                  final byte[] b) {
        int i = currentOffset;
        while (i < n) {
            i = parseSlowTillNewline(currentOffset, b);
        }
    }

    private static int parseSlowTillNewline(final int currentOffset,
                                            final byte[] b) {
        byte[] trail = new byte[100];
        int i = currentOffset;
        int j = 0;
        while (i < b.length && b[i] != '\n') {
            trail[j++] = b[i++];
        }
        if (j > 0) {
            processTrail(trail, j);
        }
        return i < b.length ? i + 1: i;
    }

    private static void processTrail(byte[] trail, int j) {
        int k = 0;
        while (k < j && trail[k] != ';') {
            k++;
        }
        String station = new String(trail, 0, k, StandardCharsets.UTF_8);
        int temperature = String2Integer.parseInt(trail, k + 1, j - k - 1);
        System.out.printf("station=%s, temperature=%d%n", station, temperature);
    }

    private static ParseResult parseDataPoint(final long inputData) {
        long negatedInput = ~inputData;
        long broadcastSign = (negatedInput << 59) >> 63;
        long maskToRemoveSign = ~(broadcastSign & 0xFF);
        long withSignRemoved = inputData & maskToRemoveSign;
        int dotPos = Long.numberOfTrailingZeros(negatedInput & DOT_DETECTOR);
        long alignedToTemplate = withSignRemoved << (28 - dotPos);
        long digits = alignedToTemplate & ASCII_TO_DIGIT_MASK;
        long absValue = ((digits * MAGIC_MULTIPLIER) >>> 32) & 0x3FF;
        long temperature = (absValue ^ broadcastSign) - broadcastSign;
        long nextLineStart = (dotPos >>> 3) + 3;
        return new ParseResult(nextLineStart, (int) temperature);
    }

    record ParseResult(long nextLineStart, int temperature) {}
}
