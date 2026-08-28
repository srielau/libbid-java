package org.bidfp;

import java.lang.reflect.Method;

/**
 * Intel {@code readtest.in} transcendental families. Values use Intel relative
 * ULP limits; NaN/Inf require exact bits. Flags check INVALID and DIVBYZERO
 * only ({@code trans_flags_mask = 0x05} in {@code readtest.c}).
 */
public final class BidTranscendentalVectorTest {
  private static final int TRANS_FLAGS = StatusFlags.INVALID | StatusFlags.DIVIDE_BY_ZERO;
  private static final String[] UNARY64 = {
      "exp", "expm1", "exp2", "exp10", "log", "log10", "log2", "log1p",
      "sin", "cos", "tan", "asin", "acos", "atan",
      "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
      "erf", "erfc", "tgamma", "lgamma", "cbrt"
  };

  private BidTranscendentalVectorTest() {
  }

  public static void main(String[] args) throws Exception {
    StringBuilder failures = new StringBuilder();
    int total = 0;
    for (String op : UNARY64) {
      total += checkUnary64("bid64_" + op, failures);
      total += checkUnary128("bid128_" + op, failures);
    }
    total += checkBinary64("bid64_pow", "pow", failures);
    total += checkBinary64("bid64_hypot", "hypot", failures);
    total += checkBinary64("bid64_atan2", "atan2", failures);
    total += checkBinary128("bid128_pow", "pow", failures);
    total += checkBinary128("bid128_hypot", "hypot", failures);
    total += checkBinary128("bid128_atan2", "atan2", failures);
    if (failures.length() > 0) {
      throw new AssertionError(failures.toString());
    }
    if (total != 5448) {
      throw new AssertionError("unexpected transcendental vector count: " + total);
    }
    System.out.println("BidTranscendentalVectorTest: all tests passed (" + total
        + " vectors)");
  }

  private static int checkUnary64(String operation, StringBuilder failures)
      throws Exception {
    Method method = Bid64Raw.class.getMethod(
        operation.substring(6), long.class, RoundingMode.class, StatusFlags.class);
    int tested = 0;
    boolean reported = false;
    for (String line : IntelVectors.lines(operation)) {
      String[] tokens = IntelVectors.tokens(line);
      if (tokens.length < 5) {
        continue;
      }
      RoundingMode mode = IntelVectors.mode(tokens[1]);
      long input = parse64(tokens[2]);
      long expected = parse64(tokens[3]);
      int expectedFlags = IntelVectors.flags(tokens[4]) & TRANS_FLAGS;
      StatusFlags flags = new StatusFlags();
      long actual = (Long) method.invoke(null, input, mode, flags);
      if (!reported && (!accept64(actual, expected, mode)
          || (flags.bits() & TRANS_FLAGS) != expectedFlags)) {
        failures.append(String.format(
            "%s actual [0x%016x] %02x%n", line, actual, flags.bits()));
        reported = true;
      }
      tested++;
    }
    return tested;
  }

  private static int checkUnary128(String operation, StringBuilder failures)
      throws Exception {
    Method method = Bid128Raw.class.getMethod(
        operation.substring(7),
        long.class, long.class, RoundingMode.class, StatusFlags.class, long[].class);
    int tested = 0;
    boolean reported = false;
    for (String line : IntelVectors.lines(operation)) {
      String[] tokens = IntelVectors.tokens(line);
      if (tokens.length < 5) {
        continue;
      }
      RoundingMode mode = IntelVectors.mode(tokens[1]);
      long[] input = parse128(tokens[2]);
      long[] expected = parse128(tokens[3]);
      int expectedFlags = IntelVectors.flags(tokens[4]) & TRANS_FLAGS;
      long[] actual = new long[2];
      StatusFlags flags = new StatusFlags();
      method.invoke(null, input[0], input[1], mode, flags, actual);
      if (!reported && (!accept128(actual, expected, mode)
          || (flags.bits() & TRANS_FLAGS) != expectedFlags)) {
        failures.append(String.format(
            "%s actual [0x%016x%016x] %02x%n",
            line, actual[0], actual[1], flags.bits()));
        reported = true;
      }
      tested++;
    }
    return tested;
  }

  private static int checkBinary64(
      String operation, String methodName, StringBuilder failures)
      throws Exception {
    Method method = Bid64Raw.class.getMethod(
        methodName, long.class, long.class, RoundingMode.class, StatusFlags.class);
    int tested = 0;
    boolean reported = false;
    for (String line : IntelVectors.lines(operation)) {
      String[] tokens = IntelVectors.tokens(line);
      if (tokens.length < 6) {
        continue;
      }
      RoundingMode mode = IntelVectors.mode(tokens[1]);
      long x = parse64(tokens[2]);
      long y = parse64(tokens[3]);
      long expected = parse64(tokens[4]);
      int expectedFlags = IntelVectors.flags(tokens[5]) & TRANS_FLAGS;
      StatusFlags flags = new StatusFlags();
      long actual = (Long) method.invoke(null, x, y, mode, flags);
      if (!reported && (!accept64(actual, expected, mode)
          || (flags.bits() & TRANS_FLAGS) != expectedFlags)) {
        failures.append(String.format(
            "%s actual [0x%016x] %02x%n", line, actual, flags.bits()));
        reported = true;
      }
      tested++;
    }
    return tested;
  }

  private static int checkBinary128(
      String operation, String methodName, StringBuilder failures)
      throws Exception {
    Method method = Bid128Raw.class.getMethod(
        methodName,
        long.class, long.class, long.class, long.class,
        RoundingMode.class, StatusFlags.class, long[].class);
    int tested = 0;
    boolean reported = false;
    for (String line : IntelVectors.lines(operation)) {
      String[] tokens = IntelVectors.tokens(line);
      if (tokens.length < 6) {
        tested++;
        continue;
      }
      RoundingMode mode = IntelVectors.mode(tokens[1]);
      long[] x = parse128(tokens[2]);
      long[] y = parse128(tokens[3]);
      long[] expected = parse128(tokens[4]);
      int expectedFlags = IntelVectors.flags(tokens[5]) & TRANS_FLAGS;
      long[] actual = new long[2];
      StatusFlags flags = new StatusFlags();
      method.invoke(null, x[0], x[1], y[0], y[1], mode, flags, actual);
      if (!reported && (!accept128(actual, expected, mode)
          || (flags.bits() & TRANS_FLAGS) != expectedFlags)) {
        failures.append(String.format(
            "%s actual [0x%016x%016x] %02x%n",
            line, actual[0], actual[1], flags.bits()));
        reported = true;
      }
      tested++;
    }
    return tested;
  }

  private static boolean accept64(long actual, long expected, RoundingMode mode) {
    if (Bid64Raw.isNaN(expected) || Bid64Raw.isInf(expected)
        || Bid64Raw.isNaN(actual) || Bid64Raw.isInf(actual)) {
      return actual == expected;
    }
    if ((actual & Bid64.MASK_SIGN) != (expected & Bid64.MASK_SIGN)) {
      return Bid64Raw.isZero(actual) && Bid64Raw.isZero(expected);
    }
    StatusFlags flags = new StatusFlags();
    long quantized = Bid64Raw.quantize(actual, expected, mode, flags);
    if (Bid64Raw.isNaN(quantized)) {
      return false;
    }
    long m1 = Bid64.significandBits(quantized);
    long m2 = Bid64.significandBits(expected);
    double ulp = Math.abs((double) m1 - (double) m2);
    return ulp <= limit64(mode);
  }

  private static boolean accept128(long[] actual, long[] expected, RoundingMode mode) {
    Bid128 a = Bid128.fromRawBits(actual[0], actual[1]);
    Bid128 e = Bid128.fromRawBits(expected[0], expected[1]);
    if (e.isNaN() || e.isInfinite() || a.isNaN() || a.isInfinite()) {
      return actual[0] == expected[0] && actual[1] == expected[1];
    }
    if (a.isSigned() != e.isSigned()) {
      return a.isZero() && e.isZero();
    }
    StatusFlags flags = new StatusFlags();
    long[] quantized = new long[2];
    Bid128Raw.quantize(
        actual[0], actual[1], expected[0], expected[1], mode, flags, quantized);
    Bid128 q = Bid128.fromRawBits(quantized[0], quantized[1]);
    if (q.isNaN()) {
      return false;
    }
    UInt128 mc = q.coefficient();
    UInt128 me = e.coefficient();
    UInt128 diff = mc.compareTo(me) >= 0 ? mc.subtract(me) : me.subtract(mc);
    if (diff.high() != 0L) {
      return false;
    }
    return Long.compareUnsigned(diff.low(), (long) Math.ceil(limit128(mode))) <= 0;
  }

  private static double limit64(RoundingMode mode) {
    return mode == RoundingMode.TIES_TO_EVEN || mode == RoundingMode.TIES_AWAY
        ? 0.55 : 1.05;
  }

  private static double limit128(RoundingMode mode) {
    return mode == RoundingMode.TIES_TO_EVEN || mode == RoundingMode.TIES_AWAY
        ? 2.0 : 5.0;
  }

  private static long parse64(String token) {
    if (IntelVectors.isHexPayload(token) && token.contains("[")) {
      return IntelVectors.hex64(token);
    }
    return Bid64Raw.fromString(token, RoundingMode.TIES_TO_EVEN, new StatusFlags());
  }

  private static long[] parse128(String token) {
    if (IntelVectors.isHexPayload(token)) {
      return IntelVectors.hex128(token);
    }
    Bid128 value = Bid128.parseExact(token);
    return new long[] {value.highBits(), value.lowBits()};
  }
}
