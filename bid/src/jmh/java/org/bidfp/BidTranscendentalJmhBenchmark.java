package org.bidfp;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Measures object and raw BID transcendental entry points on finite, domain-safe operands.
 *
 * <p>The operation is a JMH {@code @Param} dispatched through a {@code switch}.
 * That adds a small, constant dispatch overhead shared by every method and
 * parameter value, so it does not distort comparisons between methods for the
 * same operation. Always filter to one operation and compare {@code bid64Object}
 * against {@code bid64Raw} (or the BID128 pair); the overhead is negligible
 * next to the expensive libm-style kernels being measured.
 *
 * <p>Every measured backend reads and writes {@link StatusFlags}, so each
 * measured method clears the shared instance before the call. This measures a
 * single operation rather than a growing accumulation of sticky flags.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
@Threads(1)
public class BidTranscendentalJmhBenchmark {
  private static final RoundingMode ROUNDING = RoundingMode.TIES_TO_EVEN;
  private static final int EXCEPTION_FLAGS =
      StatusFlags.INVALID
          | StatusFlags.DIVIDE_BY_ZERO
          | StatusFlags.OVERFLOW
          | StatusFlags.UNDERFLOW;

  @State(Scope.Thread)
  public static class Operands {
    private static final int SIZE = 256;
    private static final int MASK = SIZE - 1;

    @Param({
        "exp", "expm1", "exp2", "exp10",
        "log", "log10", "log2", "log1p", "cbrt",
        "sin", "cos", "tan", "asin", "acos", "atan", "atan2",
        "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
        "erf", "erfc", "tgamma", "lgamma", "pow", "hypot"
    })
    public String operation;

    Bid64[] x64;
    Bid64[] y64;
    long[] xBits64;
    long[] yBits64;
    Bid128[] x128;
    Bid128[] y128;
    long[] xHigh128;
    long[] xLow128;
    long[] yHigh128;
    long[] yLow128;
    long[] result128;
    StatusFlags flags;
    int index;

    @Setup(Level.Trial)
    public void setup() {
      x64 = new Bid64[SIZE];
      y64 = new Bid64[SIZE];
      xBits64 = new long[SIZE];
      yBits64 = new long[SIZE];
      x128 = new Bid128[SIZE];
      y128 = new Bid128[SIZE];
      xHigh128 = new long[SIZE];
      xLow128 = new long[SIZE];
      yHigh128 = new long[SIZE];
      yLow128 = new long[SIZE];
      result128 = new long[2];
      flags = new StatusFlags();

      Random random = new Random(0x7a6e_5ce1L ^ operation.hashCode());
      for (int i = 0; i < SIZE; i++) {
        DecimalPair sample64 = sample(random, 16, operation);
        DecimalPair sample128 = sample(random, 34, operation);
        x64[i] = Bid64.parseExact(sample64.x);
        y64[i] = Bid64.parseExact(sample64.y);
        xBits64[i] = x64[i].toRawBits();
        yBits64[i] = y64[i].toRawBits();
        x128[i] = Bid128.parseExact(sample128.x);
        y128[i] = Bid128.parseExact(sample128.y);
        xHigh128[i] = x128[i].highBits();
        xLow128[i] = x128[i].lowBits();
        yHigh128[i] = y128[i].highBits();
        yLow128[i] = y128[i].lowBits();
      }
      sanityCheck();
    }

    int nextIndex() {
      return index++ & MASK;
    }

    private void sanityCheck() {
      long[] raw128 = new long[2];
      for (int i = 0; i < SIZE; i++) {
        StatusFlags objectFlags64 = new StatusFlags();
        StatusFlags rawFlags64 = new StatusFlags();
        Bid64 object64 = applyObject64(operation, x64[i], y64[i], objectFlags64);
        long raw64 = applyRaw64(operation, xBits64[i], yBits64[i], rawFlags64);
        if (object64.toRawBits() != raw64 || objectFlags64.bits() != rawFlags64.bits()) {
          throw new IllegalStateException(operation + " BID64 object/raw mismatch");
        }
        checkOrdinaryResult(operation, "BID64", object64.isFinite(), objectFlags64);

        StatusFlags objectFlags128 = new StatusFlags();
        StatusFlags rawFlags128 = new StatusFlags();
        Bid128 object128 = applyObject128(operation, x128[i], y128[i], objectFlags128);
        applyRaw128(
            operation,
            xHigh128[i],
            xLow128[i],
            yHigh128[i],
            yLow128[i],
            rawFlags128,
            raw128);
        if (object128.highBits() != raw128[0]
            || object128.lowBits() != raw128[1]
            || objectFlags128.bits() != rawFlags128.bits()) {
          throw new IllegalStateException(operation + " BID128 object/raw mismatch");
        }
        checkOrdinaryResult(operation, "BID128", object128.isFinite(), objectFlags128);
      }
    }

    private static void checkOrdinaryResult(
        String operation, String format, boolean finite, StatusFlags flags) {
      if (!finite || (flags.bits() & EXCEPTION_FLAGS) != 0) {
        throw new IllegalStateException(
            operation + " " + format + " produced a special result or exceptional flag");
      }
    }
  }

  private static final class DecimalPair {
    final String x;
    final String y;

    DecimalPair(String x, String y) {
      this.x = x;
      this.y = y;
    }
  }

  @Benchmark
  public void bid64Object(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    data.flags.clear();
    blackhole.consume(applyObject64(
        data.operation, data.x64[i], data.y64[i], data.flags));
  }

  @Benchmark
  public void bid64Raw(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    data.flags.clear();
    blackhole.consume(applyRaw64(
        data.operation, data.xBits64[i], data.yBits64[i], data.flags));
  }

  @Benchmark
  public void bid128Object(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    data.flags.clear();
    blackhole.consume(applyObject128(
        data.operation, data.x128[i], data.y128[i], data.flags));
  }

  @Benchmark
  public void bid128Raw(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    data.flags.clear();
    applyRaw128(
        data.operation,
        data.xHigh128[i],
        data.xLow128[i],
        data.yHigh128[i],
        data.yLow128[i],
        data.flags,
        data.result128);
    blackhole.consume(data.result128[0]);
    blackhole.consume(data.result128[1]);
  }

  private static Bid64 applyObject64(
      String operation, Bid64 x, Bid64 y, StatusFlags flags) {
    switch (operation) {
      case "exp": return x.exp(ROUNDING, flags);
      case "expm1": return x.expm1(ROUNDING, flags);
      case "exp2": return x.exp2(ROUNDING, flags);
      case "exp10": return x.exp10(ROUNDING, flags);
      case "log": return x.log(ROUNDING, flags);
      case "log10": return x.log10(ROUNDING, flags);
      case "log2": return x.log2(ROUNDING, flags);
      case "log1p": return x.log1p(ROUNDING, flags);
      case "cbrt": return x.cbrt(ROUNDING, flags);
      case "sin": return x.sin(ROUNDING, flags);
      case "cos": return x.cos(ROUNDING, flags);
      case "tan": return x.tan(ROUNDING, flags);
      case "asin": return x.asin(ROUNDING, flags);
      case "acos": return x.acos(ROUNDING, flags);
      case "atan": return x.atan(ROUNDING, flags);
      case "atan2": return y.atan2(x, ROUNDING, flags);
      case "sinh": return x.sinh(ROUNDING, flags);
      case "cosh": return x.cosh(ROUNDING, flags);
      case "tanh": return x.tanh(ROUNDING, flags);
      case "asinh": return x.asinh(ROUNDING, flags);
      case "acosh": return x.acosh(ROUNDING, flags);
      case "atanh": return x.atanh(ROUNDING, flags);
      case "erf": return x.erf(ROUNDING, flags);
      case "erfc": return x.erfc(ROUNDING, flags);
      case "tgamma": return x.tgamma(ROUNDING, flags);
      case "lgamma": return x.lgamma(ROUNDING, flags);
      case "pow": return x.pow(y, ROUNDING, flags);
      case "hypot": return x.hypot(y, ROUNDING, flags);
      default: throw new IllegalArgumentException("Unknown operation: " + operation);
    }
  }

  private static long applyRaw64(
      String operation, long x, long y, StatusFlags flags) {
    switch (operation) {
      case "exp": return Bid64Raw.exp(x, ROUNDING, flags);
      case "expm1": return Bid64Raw.expm1(x, ROUNDING, flags);
      case "exp2": return Bid64Raw.exp2(x, ROUNDING, flags);
      case "exp10": return Bid64Raw.exp10(x, ROUNDING, flags);
      case "log": return Bid64Raw.log(x, ROUNDING, flags);
      case "log10": return Bid64Raw.log10(x, ROUNDING, flags);
      case "log2": return Bid64Raw.log2(x, ROUNDING, flags);
      case "log1p": return Bid64Raw.log1p(x, ROUNDING, flags);
      case "cbrt": return Bid64Raw.cbrt(x, ROUNDING, flags);
      case "sin": return Bid64Raw.sin(x, ROUNDING, flags);
      case "cos": return Bid64Raw.cos(x, ROUNDING, flags);
      case "tan": return Bid64Raw.tan(x, ROUNDING, flags);
      case "asin": return Bid64Raw.asin(x, ROUNDING, flags);
      case "acos": return Bid64Raw.acos(x, ROUNDING, flags);
      case "atan": return Bid64Raw.atan(x, ROUNDING, flags);
      case "atan2": return Bid64Raw.atan2(y, x, ROUNDING, flags);
      case "sinh": return Bid64Raw.sinh(x, ROUNDING, flags);
      case "cosh": return Bid64Raw.cosh(x, ROUNDING, flags);
      case "tanh": return Bid64Raw.tanh(x, ROUNDING, flags);
      case "asinh": return Bid64Raw.asinh(x, ROUNDING, flags);
      case "acosh": return Bid64Raw.acosh(x, ROUNDING, flags);
      case "atanh": return Bid64Raw.atanh(x, ROUNDING, flags);
      case "erf": return Bid64Raw.erf(x, ROUNDING, flags);
      case "erfc": return Bid64Raw.erfc(x, ROUNDING, flags);
      case "tgamma": return Bid64Raw.tgamma(x, ROUNDING, flags);
      case "lgamma": return Bid64Raw.lgamma(x, ROUNDING, flags);
      case "pow": return Bid64Raw.pow(x, y, ROUNDING, flags);
      case "hypot": return Bid64Raw.hypot(x, y, ROUNDING, flags);
      default: throw new IllegalArgumentException("Unknown operation: " + operation);
    }
  }

  private static Bid128 applyObject128(
      String operation, Bid128 x, Bid128 y, StatusFlags flags) {
    switch (operation) {
      case "exp": return x.exp(ROUNDING, flags);
      case "expm1": return x.expm1(ROUNDING, flags);
      case "exp2": return x.exp2(ROUNDING, flags);
      case "exp10": return x.exp10(ROUNDING, flags);
      case "log": return x.log(ROUNDING, flags);
      case "log10": return x.log10(ROUNDING, flags);
      case "log2": return x.log2(ROUNDING, flags);
      case "log1p": return x.log1p(ROUNDING, flags);
      case "cbrt": return x.cbrt(ROUNDING, flags);
      case "sin": return x.sin(ROUNDING, flags);
      case "cos": return x.cos(ROUNDING, flags);
      case "tan": return x.tan(ROUNDING, flags);
      case "asin": return x.asin(ROUNDING, flags);
      case "acos": return x.acos(ROUNDING, flags);
      case "atan": return x.atan(ROUNDING, flags);
      case "atan2": return y.atan2(x, ROUNDING, flags);
      case "sinh": return x.sinh(ROUNDING, flags);
      case "cosh": return x.cosh(ROUNDING, flags);
      case "tanh": return x.tanh(ROUNDING, flags);
      case "asinh": return x.asinh(ROUNDING, flags);
      case "acosh": return x.acosh(ROUNDING, flags);
      case "atanh": return x.atanh(ROUNDING, flags);
      case "erf": return x.erf(ROUNDING, flags);
      case "erfc": return x.erfc(ROUNDING, flags);
      case "tgamma": return x.tgamma(ROUNDING, flags);
      case "lgamma": return x.lgamma(ROUNDING, flags);
      case "pow": return x.pow(y, ROUNDING, flags);
      case "hypot": return x.hypot(y, ROUNDING, flags);
      default: throw new IllegalArgumentException("Unknown operation: " + operation);
    }
  }

  private static void applyRaw128(
      String operation,
      long xh,
      long xl,
      long yh,
      long yl,
      StatusFlags flags,
      long[] out) {
    switch (operation) {
      case "exp": Bid128Raw.exp(xh, xl, ROUNDING, flags, out); return;
      case "expm1": Bid128Raw.expm1(xh, xl, ROUNDING, flags, out); return;
      case "exp2": Bid128Raw.exp2(xh, xl, ROUNDING, flags, out); return;
      case "exp10": Bid128Raw.exp10(xh, xl, ROUNDING, flags, out); return;
      case "log": Bid128Raw.log(xh, xl, ROUNDING, flags, out); return;
      case "log10": Bid128Raw.log10(xh, xl, ROUNDING, flags, out); return;
      case "log2": Bid128Raw.log2(xh, xl, ROUNDING, flags, out); return;
      case "log1p": Bid128Raw.log1p(xh, xl, ROUNDING, flags, out); return;
      case "cbrt": Bid128Raw.cbrt(xh, xl, ROUNDING, flags, out); return;
      case "sin": Bid128Raw.sin(xh, xl, ROUNDING, flags, out); return;
      case "cos": Bid128Raw.cos(xh, xl, ROUNDING, flags, out); return;
      case "tan": Bid128Raw.tan(xh, xl, ROUNDING, flags, out); return;
      case "asin": Bid128Raw.asin(xh, xl, ROUNDING, flags, out); return;
      case "acos": Bid128Raw.acos(xh, xl, ROUNDING, flags, out); return;
      case "atan": Bid128Raw.atan(xh, xl, ROUNDING, flags, out); return;
      case "atan2":
        Bid128Raw.atan2(yh, yl, xh, xl, ROUNDING, flags, out);
        return;
      case "sinh": Bid128Raw.sinh(xh, xl, ROUNDING, flags, out); return;
      case "cosh": Bid128Raw.cosh(xh, xl, ROUNDING, flags, out); return;
      case "tanh": Bid128Raw.tanh(xh, xl, ROUNDING, flags, out); return;
      case "asinh": Bid128Raw.asinh(xh, xl, ROUNDING, flags, out); return;
      case "acosh": Bid128Raw.acosh(xh, xl, ROUNDING, flags, out); return;
      case "atanh": Bid128Raw.atanh(xh, xl, ROUNDING, flags, out); return;
      case "erf": Bid128Raw.erf(xh, xl, ROUNDING, flags, out); return;
      case "erfc": Bid128Raw.erfc(xh, xl, ROUNDING, flags, out); return;
      case "tgamma": Bid128Raw.tgamma(xh, xl, ROUNDING, flags, out); return;
      case "lgamma": Bid128Raw.lgamma(xh, xl, ROUNDING, flags, out); return;
      case "pow":
        Bid128Raw.pow(xh, xl, yh, yl, ROUNDING, flags, out);
        return;
      case "hypot":
        Bid128Raw.hypot(xh, xl, yh, yl, ROUNDING, flags, out);
        return;
      default: throw new IllegalArgumentException("Unknown operation: " + operation);
    }
  }

  private static DecimalPair sample(Random random, int digits, String operation) {
    if (isLog(operation)) {
      return new DecimalPair(decimal(random, digits, random.nextInt(41) - 20, false),
          decimal(random, digits, 0, false));
    }
    if (operation.equals("log1p")
        || operation.equals("asin")
        || operation.equals("acos")
        || operation.equals("atanh")) {
      // Signed values strictly inside (-1, 1). Adjusted exponents from -1 down
      // to -5 keep a leading nonzero digit while spanning several magnitude
      // buckets down to near zero; the magnitude is always below 1, so the
      // arguments never reach the +/-1 endpoints these functions exclude.
      int adjusted = -1 - random.nextInt(5);
      return new DecimalPair(decimal(random, digits, adjusted, true),
          decimal(random, digits, 0, false));
    }
    if (operation.equals("acosh") || operation.equals("tgamma")
        || operation.equals("lgamma")) {
      return new DecimalPair(decimal(random, digits, 0, false),
          decimal(random, digits, 0, false));
    }
    if (operation.equals("exp") || operation.equals("expm1")
        || operation.equals("exp2") || operation.equals("exp10")
        || operation.equals("sinh") || operation.equals("cosh")) {
      return new DecimalPair(decimal(random, digits, random.nextInt(3) - 2, true),
          decimal(random, digits, 0, false));
    }
    if (operation.equals("tanh")) {
      // Bounded, non-saturated domain: adjusted exponents -2 through 0 keep the
      // argument small enough that tanh has not yet flattened to +/-1. asinh is
      // intentionally left in the broad default domain below.
      return new DecimalPair(decimal(random, digits, random.nextInt(3) - 2, true),
          decimal(random, digits, 0, false));
    }
    if (operation.equals("erf") || operation.equals("erfc")) {
      return new DecimalPair(decimal(random, digits, random.nextInt(2) - 1, true),
          decimal(random, digits, 0, false));
    }
    if (operation.equals("pow")) {
      return new DecimalPair(
          decimal(random, digits, random.nextInt(5) - 2, false),
          decimal(random, digits, -1, true));
    }
    int magnitude = random.nextInt(9) - 4;
    return new DecimalPair(
        decimal(random, digits, magnitude, true),
        decimal(random, digits, random.nextInt(9) - 4, true));
  }

  private static boolean isLog(String operation) {
    return operation.equals("log")
        || operation.equals("log10")
        || operation.equals("log2");
  }

  private static String decimal(
      Random random, int digits, int adjustedExponent, boolean signed) {
    StringBuilder text = new StringBuilder(digits + 10);
    if (signed && random.nextBoolean()) {
      text.append('-');
    }
    text.append(random.nextInt(9) + 1);
    for (int i = 1; i < digits; i++) {
      text.append(random.nextInt(10));
    }
    return text.append('E').append(adjustedExponent - digits + 1).toString();
  }
}
