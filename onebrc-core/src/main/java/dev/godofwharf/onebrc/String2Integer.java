package dev.godofwharf.onebrc;

public class String2Integer {

    // This array is used for quick conversion of fractional part
    private static final double[] DOUBLES = new double[]{ 0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9 };
    // This array is used for quick conversion from ASCII to digit
    private static final int[] DIGIT_LOOKUP = new int[]{
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, 0, 1,
            2, 3, 4, 5, 6, 7, 8, 9, -1, -1 };

    public static int toDigit(final char c) {
        return DIGIT_LOOKUP[c];
    }

    public static int fastMul10(final int i) {
        return (i << 1) + (i << 3);
    }

    public static int parseInt(final byte[] b,
                               final int offset,
                               final int len) {
        try {
            char ch0 = (char) b[offset];
            char ch1 = (char) b[offset + 1];
            char ch2 = (char) b[offset + 2];
            char ch3 = len > 3 ? (char) b[offset + 3] : ' ';
            char ch4 = len > 4 ? (char) b[offset + 4] : ' ';
            if (len == 3) {
                return fastMul10(toDigit(ch0)) + toDigit(ch2);
            }
            else if (len == 4) {
                // -1.2 or 11.2
                int decimal = (ch0 == '-' ? toDigit(ch1) : (fastMul10(toDigit(ch0)) + toDigit(ch1)));
                int fractional = toDigit(ch3);
                if (ch0 == '-') {
                    return -1 * (fastMul10(decimal) + fractional);
                }
                else {
                    return fastMul10(decimal) + fractional;
                }
            }
            else {
                int decimal = fastMul10(toDigit(ch1)) + toDigit(ch2);
                int fractional = toDigit(ch4);
                return -1 * (fastMul10(decimal) + fractional);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new RuntimeException("Array index out of bounds for string: %s%n".formatted(new String(b, 0, len)));
        } catch (StringIndexOutOfBoundsException e) {
            throw new RuntimeException("String index out of bounds for string: %s%n".formatted(new String(b, 0, len)));
        }
    }
}
