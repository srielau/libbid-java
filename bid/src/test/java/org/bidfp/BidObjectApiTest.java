package org.bidfp;

/** Checks that object APIs preserve raw-kernel values and flags. */
public final class BidObjectApiTest {
  private BidObjectApiTest() {
  }

  public static void main(String[] args) {
    testBid64();
    testBid128();
    testCompatStatus();
    System.out.println("BidObjectApiTest: all tests passed");
  }

  private static void testBid64() {
    StatusFlags flags = new StatusFlags();
    Bid64 x = Bid64.parse("12.75", RoundingMode.TIES_TO_EVEN, flags);
    Bid64 y = Bid64.parseExact("2.5");
    check(Bid64.fromLong(Long.MIN_VALUE, RoundingMode.TIES_TO_EVEN, flags).toRawBits()
        == Bid64Raw.fromInt64(Long.MIN_VALUE, RoundingMode.TIES_TO_EVEN, new StatusFlags()),
        "fromLong64");
    StatusFlags integerFlags = new StatusFlags();
    check(x.toLong(RoundingMode.TOWARD_ZERO, integerFlags) == 12L, "long64");
    check(integerFlags.contains(StatusFlags.INEXACT), "longInexact64");
    check(x.toCanonicalString().equals("1275E-2"), "parse64");
    check(x.toDouble(RoundingMode.TIES_TO_EVEN, flags) == 12.75, "double64");
    check(x.toFloat(RoundingMode.TIES_TO_EVEN, flags) == 12.75f, "float64");
    check(x.toBid128(flags).toBid64(RoundingMode.TIES_TO_EVEN, flags)
        .quietEqual(x, new StatusFlags()), "widen64");
    check(x.roundIntegral(RoundingMode.TIES_TO_EVEN, false, flags)
        .quietEqual(Bid64.parseExact("13"), new StatusFlags()), "round64");
    check(x.scaleByPowerOfTen(1, RoundingMode.TIES_TO_EVEN, flags)
        .quietEqual(Bid64.parseExact("127.5"), new StatusFlags()), "scale64");
    check(x.fmod(y, flags).quietEqual(
        Bid64.fromRawBits(Bid64Raw.fmod(
            x.toRawBits(), y.toRawBits(), new StatusFlags())),
        new StatusFlags()), "fmod64");
    check(x.nextAfter(y, flags).toRawBits()
        == Bid64Raw.nextAfter(x.toRawBits(), y.toRawBits(), new StatusFlags()),
        "nextAfter64");
    check(x.minNum(y, flags).quietEqual(y, new StatusFlags()), "min64");
    check(x.maxNum(y, flags).quietEqual(x, new StatusFlags()), "max64");
    check(x.minNumMagnitude(y, flags).quietEqual(y, new StatusFlags()), "minMag64");
    check(x.maxNumMagnitude(y, flags).quietEqual(x, new StatusFlags()), "maxMag64");
    check(x.quantum().toRawBits() == Bid64Raw.quantum(x.toRawBits()), "quantum64");
    check(x.quantumExponent(flags) == Bid64Raw.quantexp(x.toRawBits()), "quantexp64");
    check(x.ilogb(flags) == Bid64Raw.ilogb(x.toRawBits(), new StatusFlags()), "ilogb64");
    check(x.logb(flags).toRawBits()
        == Bid64Raw.logb(x.toRawBits(), new StatusFlags()), "logb64");
    check(Bid64.QUIET_NAN.quietGreaterUnordered(x, flags), "greaterUnordered64");
    check(Bid64.QUIET_NAN.quietLessUnordered(x, flags), "lessUnordered64");
    check(Bid64.QUIET_NAN.quietNotGreater(x, flags), "notGreater64");
    check(Bid64.QUIET_NAN.quietNotLess(x, flags), "notLess64");
  }

  private static void testBid128() {
    StatusFlags flags = new StatusFlags();
    Bid128 x = Bid128.parse("12.75", RoundingMode.TIES_TO_EVEN, flags);
    Bid128 y = Bid128.parseExact("2.5");
    check(x.toCanonicalString().equals("1275E-2"), "parse128");
    StatusFlags integerFlags = new StatusFlags();
    check(x.toLong(RoundingMode.TOWARD_ZERO, integerFlags) == 12L, "long128");
    check(integerFlags.contains(StatusFlags.INEXACT), "longInexact128");
    check(x.toDouble(RoundingMode.TIES_TO_EVEN, flags) == 12.75, "double128");
    check(x.toFloat(RoundingMode.TIES_TO_EVEN, flags) == 12.75f, "float128");
    check(x.toBid64(RoundingMode.TIES_TO_EVEN, flags)
        .quietEqual(Bid64.parseExact("12.75"), new StatusFlags()), "narrow128");
    check(Bid128.fromLong(12L, RoundingMode.TIES_TO_EVEN, flags)
        .quietEqual(Bid128.parseExact("12"), new StatusFlags()), "fromLong128");
    check(x.roundIntegral(RoundingMode.TIES_TO_EVEN, false, flags)
        .quietEqual(Bid128.parseExact("13"), new StatusFlags()), "round128");
    check(x.scaleByPowerOfTen(1, RoundingMode.TIES_TO_EVEN, flags)
        .quietEqual(Bid128.parseExact("127.5"), new StatusFlags()), "scale128");
    check(x.fmod(y, flags).quietEqual(rawFmod128(x, y), new StatusFlags()), "fmod128");
    check(x.nextAfter(y, flags).equals(rawNextAfter128(x, y)), "nextAfter128");
    check(x.minNum(y, flags).quietEqual(y, new StatusFlags()), "min128");
    check(x.maxNum(y, flags).quietEqual(x, new StatusFlags()), "max128");
    check(x.minNumMagnitude(y, flags).quietEqual(y, new StatusFlags()), "minMag128");
    check(x.maxNumMagnitude(y, flags).quietEqual(x, new StatusFlags()), "maxMag128");
    check(x.quantum().equals(rawQuantum128(x)), "quantum128");
    check(x.quantumExponent(flags)
        == Bid128Raw.quantexp(x.highBits(), x.lowBits()), "quantexp128");
    check(x.ilogb(flags)
        == Bid128Raw.ilogb(x.highBits(), x.lowBits(), new StatusFlags()), "ilogb128");
    check(x.logb(flags).equals(rawLogb128(x)), "logb128");
    check(Bid128.QUIET_NAN.quietGreaterUnordered(x, flags), "greaterUnordered128");
    check(Bid128.QUIET_NAN.quietLessUnordered(x, flags), "lessUnordered128");
    check(Bid128.QUIET_NAN.quietNotGreater(x, flags), "notGreater128");
    check(Bid128.QUIET_NAN.quietNotLess(x, flags), "notLess128");
  }

  private static void testCompatStatus() {
    long one64 = Bid64.parseExact("1").toRawBits();
    long zero64 = Bid64.POSITIVE_ZERO.toRawBits();
    int[] flags64 = {0};
    DecFloat16Compat.bid64Div(
        one64, zero64, RoundingMode.TIES_TO_EVEN.toIntel(), flags64);
    check((flags64[0] & StatusFlags.DIVIDE_BY_ZERO) != 0, "compatFlags64");

    Bid128 one128 = Bid128.parseExact("1");
    Bid128 zero128 = Bid128.POSITIVE_ZERO;
    long[] result128 = new long[2];
    int[] flags128 = {0};
    DecFloat34Compat.bid128Div(
        one128.highBits(), one128.lowBits(), zero128.highBits(), zero128.lowBits(),
        RoundingMode.TIES_TO_EVEN.toIntel(), result128, flags128);
    check((flags128[0] & StatusFlags.DIVIDE_BY_ZERO) != 0, "compatFlags128");
  }

  private static Bid128 rawFmod128(Bid128 x, Bid128 y) {
    long[] result = new long[2];
    Bid128Raw.fmod(
        x.highBits(), x.lowBits(), y.highBits(), y.lowBits(), new StatusFlags(), result);
    return Bid128.fromRawBits(result[0], result[1]);
  }

  private static Bid128 rawNextAfter128(Bid128 x, Bid128 y) {
    long[] result = new long[2];
    Bid128Raw.nextAfter(
        x.highBits(), x.lowBits(), y.highBits(), y.lowBits(), new StatusFlags(), result);
    return Bid128.fromRawBits(result[0], result[1]);
  }

  private static Bid128 rawQuantum128(Bid128 x) {
    long[] result = new long[2];
    Bid128Raw.quantum(x.highBits(), x.lowBits(), result);
    return Bid128.fromRawBits(result[0], result[1]);
  }

  private static Bid128 rawLogb128(Bid128 x) {
    long[] result = new long[2];
    Bid128Raw.logb(x.highBits(), x.lowBits(), new StatusFlags(), result);
    return Bid128.fromRawBits(result[0], result[1]);
  }

  private static void check(boolean condition, String name) {
    if (!condition) {
      throw new AssertionError(name);
    }
  }
}
