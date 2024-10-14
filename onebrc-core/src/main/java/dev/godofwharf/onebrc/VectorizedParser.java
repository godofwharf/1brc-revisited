package dev.godofwharf.onebrc;

import dev.godofwharf.onebrc.models.AggregationKey;
import dev.godofwharf.onebrc.models.AggregationResult;
import jdk.incubator.vector.ByteVector;

import static dev.godofwharf.onebrc.models.AggregationState.updateStateMap;

public class VectorizedParser {
    private static final ByteVector semiColonBroadcast = ByteVector.broadcast(ByteVector.SPECIES_PREFERRED, (byte) ';');

    private static final long MAGIC_MULTIPLIER = (100 * 0x1000000 + 10 * 0x10000 + 1);
    private static final long DOT_DETECTOR = 0x10101000;
    private static final long ASCII_TO_DIGIT_MASK = 0x0F000F0F00L;

    // How to vectorize parsing?
    // 1. Read n bytes at a time and create a byte vector v1
    // 2. Create broadcast byte vector v2 for ';'
    // 3. Check for equality between v1 and v2
    // 4. Iterate over positions where a ';' appears using POPCNT instructions
    //
    // We can scan for ";" (or semi-colon) characters
    public void parse(final LinearProbedMap<AggregationKey, AggregationResult> map,
                      final byte[] bytes) {
        int currentOffset = 0;
        int endOffset = bytes.length;
        while (currentOffset < endOffset - ByteVector.SPECIES_PREFERRED.vectorByteSize() - 1) {
            ByteVector v1 = ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, bytes, currentOffset);
            long eqResult = semiColonBroadcast.eq(v1).toLong();
            if (eqResult == 0) {
                currentOffset = parseSlowTillNewline(map, currentOffset, bytes);
                continue;
            }
            int prevOffset = currentOffset;
            while (eqResult > 0) {
                int trailingZerosCnt = Long.numberOfTrailingZeros(eqResult);
                if (trailingZerosCnt < 64) {
                    int semiColonPos = trailingZerosCnt;
                    int temperature;
                    byte[] station = new byte[semiColonPos];
                    System.arraycopy(bytes, currentOffset, station, 0, semiColonPos);
                    int temperatureStartOffset = currentOffset + semiColonPos + 1;
                    if (currentOffset + semiColonPos + 4 < endOffset &&
                            bytes[currentOffset + semiColonPos + 4] == '\n') {
                        temperature = String2Integer.parseInt(bytes, temperatureStartOffset, 3);
                        currentOffset += (semiColonPos + 5);
                    } else if (currentOffset + semiColonPos + 5 < endOffset &&
                            bytes[currentOffset + semiColonPos + 5] == '\n') {
                        temperature = String2Integer.parseInt(bytes, temperatureStartOffset, 4);
                        currentOffset += (semiColonPos + 6);
                    } else if (currentOffset + semiColonPos + 6 < endOffset &&
                            bytes[currentOffset + semiColonPos + 6] == '\n') {
                        temperature = String2Integer.parseInt(bytes, temperatureStartOffset, 5);
                        currentOffset += (semiColonPos + 7);
                    } else {
                        temperature = String2Integer.parseInt(bytes,
                                temperatureStartOffset, endOffset - (temperatureStartOffset));
                        currentOffset = endOffset;
                    }
                    updateStateMap(map, new AggregationKey(station), temperature);
                } else {
                    break;
                }
                eqResult = eqResult >> (currentOffset - prevOffset);
                prevOffset = currentOffset;
            }
        }
        parseSlow(map, currentOffset, endOffset, bytes);
    }

    public void parse2(final LinearProbedMap<AggregationKey, AggregationResult> map,
                       final byte[] bytes) {
        int currentOffset = 0;
        int endOffset = bytes.length;
        while (currentOffset < endOffset - ByteVector.SPECIES_PREFERRED.vectorByteSize() - 1) {
            ByteVector v1 = ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, bytes, currentOffset);
            long eqResult = semiColonBroadcast.eq(v1).toLong();
            if (eqResult == 0) {
                currentOffset = parseSlowTillNewline(map, currentOffset, bytes);
                continue;
            }
            int prevOffset = currentOffset;
            while (eqResult > 0) {
                int trailingZerosCnt = Long.numberOfTrailingZeros(eqResult);
                if (trailingZerosCnt < 64) {
                    int semiColonPos = trailingZerosCnt;
                    byte[] station = new byte[semiColonPos];
                    System.arraycopy(bytes, currentOffset, station, 0, semiColonPos);
                    int temperatureStartOffset = currentOffset + semiColonPos + 1;
                    long x = extractLong(bytes, temperatureStartOffset);
                    ParseResult parseResult = parseDataPoint(x);
                    currentOffset = (int) (temperatureStartOffset + parseResult.nextLineStart);
                    int temperature = parseResult.temperature;
                    updateStateMap(map, new AggregationKey(station, null), temperature);
                } else {
                    break;
                }
                eqResult = eqResult >> (currentOffset - prevOffset);
                prevOffset = currentOffset;
            }
        }
        parseSlow(map, currentOffset, endOffset, bytes);
    }

    private static void parseSlow(final LinearProbedMap<AggregationKey, AggregationResult> map,
                                  final int currentOffset,
                                  final int maxOffset,
                                  final byte[] bytes) {
        int i = currentOffset;
        while (i < maxOffset) {
            i = parseSlowTillNewline(map, i, bytes);
        }
    }

    private static int parseSlowTillNewline(final LinearProbedMap<AggregationKey, AggregationResult> map,
                                            final int currentOffset,
                                            final byte[] bytes) {
        byte[] trail = new byte[100];
        int i = currentOffset;
        int j = 0;
        while (i < bytes.length && bytes[i] != '\n') {
            trail[j++] = bytes[i++];
        }
        if (j > 0) {
            processTrail(map, trail, j);
        }
        return i < bytes.length ? i + 1: i;
    }

    private static void processTrail(final LinearProbedMap<AggregationKey, AggregationResult> map,
                                     final byte[] trail,
                                     final int j) {
        int k = 0;
        while (k < j && trail[k] != ';') {
            k++;
        }
        byte[] station = new byte[k];
        System.arraycopy(trail, 0, station, 0, k);
        int temperature = String2Integer.parseInt(trail, k + 1, j - k - 1);
        updateStateMap(map, new AggregationKey(station), temperature);
    }

    private static long extractLong(final byte[] b,
                                    final int offset) {
        long x = 0;
        for (int i = offset; i < b.length && i < offset + 8; i++) {
            x |= ((b[i] & 0xFFL) << ((i - offset) * 8));
        }
        return x;
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
