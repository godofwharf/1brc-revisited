package dev.godofwharf.onebrc.benchmarks;

public class ParsingBenchmarks {
    private static final long MAGIC_MULTIPLIER = (100 * 0x1000000 + 10 * 0x10000 + 1);
    private static final long DOT_DETECTOR = 0x10101000;
    private static final long ASCII_TO_DIGIT_MASK = 0x0F000F0F00L;

    // Based on merrykitty's submission and the QuestDB blog
    private static int parseDataPoint(final long inputData) {
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
        return (int) temperature;
    }
}
