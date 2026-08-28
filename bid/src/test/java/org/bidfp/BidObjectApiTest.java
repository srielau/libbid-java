package org.bidfp;

import java.lang.reflect.Method;
import java.math.BigDecimal;

/** Checks that object APIs preserve raw-kernel values and flags. */
public final class BidObjectApiTest {
  private static final String[] UNARY = {
      "exp", "expm1", "exp2", "exp10", "log", "log10", "log2", "log1p",
      "sin", "cos", "tan", "asin", "acos", "atan",
      "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
      "erf", "erfc", "tgamma", "lgamma", "cbrt"
  };
  private static final String[] BINARY = {"pow", "hypot", "atan2"};
  private static final RoundingMode[] MODES = {
      RoundingMode.TIES_TO_EVEN, RoundingMode.TOWARD_ZERO
  };

  private BidObjectApiTest() {
  }

  public static void main(String[] args) {
    testBid64();
    testBid128();
    testTranscendentalParity();
    testCompatStatus();
    System.out.println("BidObjectApiTest: all tests passed");
  }

  private static void testBid64() {
    StatusFlags flags = new StatusFlags();
    Bid64 x = Bid64.parse("12.75", RoundingMode.TIES_TO_EVEN, flags);
    Bid64 y = Bid64.parseExact("2.5");
    check(x.compareTo(y) > 0, "compareTo64");
    check(Bid64.NEGATIVE_ZERO.compareTo(Bid64.POSITIVE_ZERO) < 0, "signedZeroOrder64");
    Bid64 zero = Bid64.finite(false, 0, 0);
    Bid64 noncanonicalZero =
        Bid64.fromRawBits(Bid64.finiteRawBits(false, 0, 10_000_000_000_000_000L));
    check(Integer.signum(zero.compareTo(noncanonicalZero))
        == -Integer.signum(noncanonicalZero.compareTo(zero)), "strictOrder64");
    check(Bid64.fromLong(Long.MIN_VALUE, RoundingMode.TIES_TO_EVEN, flags).toRawBits()
        == Bid64Raw.fromInt64(Long.MIN_VALUE, RoundingMode.TIES_TO_EVEN, new StatusFlags()),
        "fromLong64");
    StatusFlags integerFlags = new StatusFlags();
    check(x.toLong(RoundingMode.TOWARD_ZERO, integerFlags) == 12L, "long64");
    check(integerFlags.contains(StatusFlags.INEXACT), "longInexact64");
    check(x.toCanonicalString().equals("1275E-2"), "parse64");
    check(x.toBigDecimal().equals(new BigDecimal("12.75")), "bigDecimal64");
    check(Bid64.fromBigDecimalExact(new BigDecimal("12.750")).toCanonicalString()
        .equals("12750E-3"), "bigDecimalExact64");
    StatusFlags decimalFlags = new StatusFlags();
    Bid64.fromBigDecimal(
        new BigDecimal("1.2345678901234567"),
        RoundingMode.TIES_TO_EVEN,
        decimalFlags);
    check(decimalFlags.contains(StatusFlags.INEXACT), "bigDecimalInexact64");
    boolean rejected = false;
    try {
      Bid64.fromBigDecimalExact(new BigDecimal("1e1000"));
    } catch (ArithmeticException expected) {
      rejected = true;
    }
    check(rejected, "bigDecimalRange64");
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
    check(x.compareTo(y) > 0, "compareTo128");
    check(Bid128.NEGATIVE_ZERO.compareTo(Bid128.POSITIVE_ZERO) < 0, "signedZeroOrder128");
    Bid128 zero = Bid128.rawFinite(false, 0, 0, 0);
    Bid128 noncanonicalZero = Bid128.fromRawBits(0x6000_0000_0000_0000L, 1L);
    check(Integer.signum(zero.compareTo(noncanonicalZero))
        == -Integer.signum(noncanonicalZero.compareTo(zero)), "strictOrder128");
    check(x.toCanonicalString().equals("1275E-2"), "parse128");
    check(x.toBigDecimal().equals(new BigDecimal("12.75")), "bigDecimal128");
    check(Bid128.fromBigDecimalExact(new BigDecimal("12.750")).toCanonicalString()
        .equals("12750E-3"), "bigDecimalExact128");
    StatusFlags decimalFlags = new StatusFlags();
    Bid128.fromBigDecimal(
        new BigDecimal("1.2345678901234567890123456789012345"),
        RoundingMode.TIES_TO_EVEN,
        decimalFlags);
    check(decimalFlags.contains(StatusFlags.INEXACT), "bigDecimalInexact128");
    boolean rejected = false;
    try {
      Bid128.fromBigDecimalExact(new BigDecimal("1e10000"));
    } catch (ArithmeticException expected) {
      rejected = true;
    }
    check(rejected, "bigDecimalRange128");
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

  private static void testTranscendentalParity() {
    Bid64[] samples64 = {
        Bid64.parseExact("0"),
        Bid64.parseExact("0.5"),
        Bid64.parseExact("1"),
        Bid64.parseExact("2"),
        Bid64.parseExact("-0.5"),
        Bid64.parseExact("3"),
        Bid64.POSITIVE_INFINITY,
        Bid64.NEGATIVE_INFINITY,
        Bid64.QUIET_NAN,
        Bid64.SIGNALING_NAN
    };
    Bid128[] samples128 = {
        Bid128.parseExact("0"),
        Bid128.parseExact("0.5"),
        Bid128.parseExact("1"),
        Bid128.parseExact("2"),
        Bid128.parseExact("-0.5"),
        Bid128.parseExact("3"),
        Bid128.POSITIVE_INFINITY,
        Bid128.NEGATIVE_INFINITY,
        Bid128.QUIET_NAN,
        Bid128.SIGNALING_NAN
    };
    check(samples64.length == samples128.length, "sampleCount");
    check(UNARY.length == 25, "unaryCount");
    check(BINARY.length == 3, "binaryCount");

    for (String name : UNARY) {
      for (RoundingMode mode : MODES) {
        for (int i = 0; i < samples64.length; i++) {
          checkUnary64(name, samples64[i], mode);
          checkUnary128(name, samples128[i], mode);
        }
      }
    }
    Bid64[] rhs64 = {
        Bid64.parseExact("0"),
        Bid64.parseExact("1"),
        Bid64.parseExact("3"),
        Bid64.parseExact("-1"),
        Bid64.POSITIVE_INFINITY,
        Bid64.QUIET_NAN
    };
    Bid128[] rhs128 = {
        Bid128.parseExact("0"),
        Bid128.parseExact("1"),
        Bid128.parseExact("3"),
        Bid128.parseExact("-1"),
        Bid128.POSITIVE_INFINITY,
        Bid128.QUIET_NAN
    };
    for (String name : BINARY) {
      for (RoundingMode mode : MODES) {
        for (Bid64 left : samples64) {
          for (Bid64 right : rhs64) {
            checkBinary64(name, left, right, mode);
          }
        }
        for (Bid128 left : samples128) {
          for (Bid128 right : rhs128) {
            checkBinary128(name, left, right, mode);
          }
        }
      }
    }
  }

  private static void checkUnary64(String name, Bid64 x, RoundingMode mode) {
    try {
      Method object = Bid64.class.getMethod(
          name, RoundingMode.class, StatusFlags.class);
      Method raw = Bid64Raw.class.getMethod(
          name, long.class, RoundingMode.class, StatusFlags.class);
      StatusFlags objectFlags = new StatusFlags();
      StatusFlags rawFlags = new StatusFlags();
      Bid64 objectResult = (Bid64) object.invoke(x, mode, objectFlags);
      long rawResult = (Long) raw.invoke(null, x.toRawBits(), mode, rawFlags);
      check(
          objectResult.toRawBits() == rawResult,
          "unary64Bits:" + name + ":" + x);
      check(
          objectFlags.bits() == rawFlags.bits(),
          "unary64Flags:" + name + ":" + x);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("unary64:" + name, e);
    }
  }

  private static void checkUnary128(String name, Bid128 x, RoundingMode mode) {
    try {
      Method object = Bid128.class.getMethod(
          name, RoundingMode.class, StatusFlags.class);
      Method raw = Bid128Raw.class.getMethod(
          name,
          long.class,
          long.class,
          RoundingMode.class,
          StatusFlags.class,
          long[].class);
      StatusFlags objectFlags = new StatusFlags();
      StatusFlags rawFlags = new StatusFlags();
      Bid128 objectResult = (Bid128) object.invoke(x, mode, objectFlags);
      long[] rawBits = new long[2];
      raw.invoke(null, x.highBits(), x.lowBits(), mode, rawFlags, rawBits);
      check(
          objectResult.highBits() == rawBits[0]
              && objectResult.lowBits() == rawBits[1],
          "unary128Bits:" + name + ":" + x);
      check(
          objectFlags.bits() == rawFlags.bits(),
          "unary128Flags:" + name + ":" + x);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("unary128:" + name, e);
    }
  }

  private static void checkBinary64(
      String name, Bid64 left, Bid64 right, RoundingMode mode) {
    try {
      Method object = Bid64.class.getMethod(
          name, Bid64.class, RoundingMode.class, StatusFlags.class);
      Method raw = Bid64Raw.class.getMethod(
          name,
          long.class,
          long.class,
          RoundingMode.class,
          StatusFlags.class);
      StatusFlags objectFlags = new StatusFlags();
      StatusFlags rawFlags = new StatusFlags();
      Bid64 objectResult = (Bid64) object.invoke(left, right, mode, objectFlags);
      long rawResult = (Long) raw.invoke(
          null, left.toRawBits(), right.toRawBits(), mode, rawFlags);
      check(
          objectResult.toRawBits() == rawResult,
          "binary64Bits:" + name + ":" + left + "," + right);
      check(
          objectFlags.bits() == rawFlags.bits(),
          "binary64Flags:" + name + ":" + left + "," + right);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("binary64:" + name, e);
    }
  }

  private static void checkBinary128(
      String name, Bid128 left, Bid128 right, RoundingMode mode) {
    try {
      Method object = Bid128.class.getMethod(
          name, Bid128.class, RoundingMode.class, StatusFlags.class);
      Method raw = Bid128Raw.class.getMethod(
          name,
          long.class,
          long.class,
          long.class,
          long.class,
          RoundingMode.class,
          StatusFlags.class,
          long[].class);
      StatusFlags objectFlags = new StatusFlags();
      StatusFlags rawFlags = new StatusFlags();
      Bid128 objectResult =
          (Bid128) object.invoke(left, right, mode, objectFlags);
      long[] rawBits = new long[2];
      raw.invoke(
          null,
          left.highBits(),
          left.lowBits(),
          right.highBits(),
          right.lowBits(),
          mode,
          rawFlags,
          rawBits);
      check(
          objectResult.highBits() == rawBits[0]
              && objectResult.lowBits() == rawBits[1],
          "binary128Bits:" + name + ":" + left + "," + right);
      check(
          objectFlags.bits() == rawFlags.bits(),
          "binary128Flags:" + name + ":" + left + "," + right);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("binary128:" + name, e);
    }
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
