package org.bidfp;

import java.io.IOException;

/** Intel readtest.in vectors for fused multiply-add and remainder operations. */
public final class BidFmaRemVectorTest {
  private BidFmaRemVectorTest() {
  }

  public static void main(String[] args) throws IOException {
    int count64 = test64();
    int count128 = test128();
    System.out.printf(
        "BidFmaRemVectorTest: all tests passed (%d BID64, %d BID128 vectors)%n",
        count64, count128);
  }

  private static int test64() throws IOException {
    int tested = 0;
    for (String operation : new String[] {"fma", "rem", "fmod"}) {
      for (String line : IntelVectors.lines("bid64_" + operation)) {
        String[] tokens = IntelVectors.tokens(line);
        boolean ternary = operation.equals("fma");
        int resultIndex = ternary ? 5 : 4;
        long x = operand64(tokens[2]);
        long y = operand64(tokens[3]);
        long z = ternary ? operand64(tokens[4]) : 0L;
        long expected = operand64(tokens[resultIndex]);
        int expectedFlags = IntelVectors.flags(tokens[resultIndex + 1]);
        StatusFlags flags = new StatusFlags();
        long actual;
        if (ternary) {
          actual = Bid64Raw.fma(x, y, z, IntelVectors.mode(tokens[1]), flags);
        } else if (operation.equals("rem")) {
          actual = Bid64Raw.rem(x, y, flags);
        } else {
          actual = Bid64Raw.fmod(x, y, flags);
        }
        if (actual != expected || flags.bits() != expectedFlags) {
          throw new AssertionError(String.format(
              "%s actual [0x%016x] %02x", line, actual, flags.bits()));
        }
        tested++;
      }
    }
    return tested;
  }

  private static int test128() throws IOException {
    int tested = 0;
    for (String operation : new String[] {"fma", "rem", "fmod"}) {
      for (String line : IntelVectors.lines("bid128_" + operation)) {
        String[] tokens = IntelVectors.tokens(line);
        boolean ternary = operation.equals("fma");
        int resultIndex = ternary ? 5 : 4;
        long[] x = operand128(tokens[2]);
        long[] y = operand128(tokens[3]);
        long[] z = ternary ? operand128(tokens[4]) : new long[2];
        long[] expected = operand128(tokens[resultIndex]);
        int expectedFlags = IntelVectors.flags(tokens[resultIndex + 1]);
        StatusFlags flags = new StatusFlags();
        long[] actual = new long[2];
        if (ternary) {
          Bid128Raw.fma(
              x[0], x[1], y[0], y[1], z[0], z[1],
              IntelVectors.mode(tokens[1]), flags, actual);
        } else if (operation.equals("rem")) {
          Bid128Raw.rem(x[0], x[1], y[0], y[1], flags, actual);
        } else {
          Bid128Raw.fmod(x[0], x[1], y[0], y[1], flags, actual);
        }
        if (actual[0] != expected[0] || actual[1] != expected[1]
            || flags.bits() != expectedFlags) {
          throw new AssertionError(String.format(
              "%s actual [0x%016x%016x] %02x",
              line, actual[0], actual[1], flags.bits()));
        }
        tested++;
      }
    }
    return tested;
  }

  private static long operand64(String token) {
    if (IntelVectors.isHexPayload(token)) {
      return token.contains(",") ? IntelVectors.hex128(token)[1] : IntelVectors.hex64(token);
    }
    if (token.equalsIgnoreCase("QNaN")) {
      return Bid64.QUIET_NAN.toRawBits();
    }
    if (isSpecial(token)) {
      return Bid64.parseExact(token).toRawBits();
    }
    return Bid64Raw.fromString(token, RoundingMode.TIES_TO_EVEN, new StatusFlags());
  }

  private static long[] operand128(String token) {
    if (IntelVectors.isHexPayload(token)) {
      return IntelVectors.hex128(token);
    }
    if (token.equalsIgnoreCase("QNaN")) {
      return new long[] {Bid128.QUIET_NAN.highBits(), Bid128.QUIET_NAN.lowBits()};
    }
    if (isSpecial(token)) {
      Bid128 value = Bid128.parseExact(token);
      return new long[] {value.highBits(), value.lowBits()};
    }
    long[] result = new long[2];
    Bid128Raw.fromString(token, RoundingMode.TIES_TO_EVEN, new StatusFlags(), result);
    return result;
  }

  private static boolean isSpecial(String token) {
    String upper = token.toUpperCase();
    return upper.endsWith("NAN") || upper.endsWith("INF") || upper.endsWith("INFINITY");
  }
}
