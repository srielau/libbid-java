package org.bidfp;

import java.math.BigDecimal;
import java.math.MathContext;
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
 * Compares Java BID arithmetic with BigDecimal on exactly equivalent operands.
 *
 * <p>Each BigDecimal operation uses the MathContext matching the BID format:
 * DECIMAL64 for BID64 and DECIMAL128 for BID128.
 * Operand construction is outside the measured region.
 *
 * <p>BID64 also has raw-bit kernels ({@code *RawBits}) on the same values so
 * JMH can separate object-wrapper allocation from arithmetic cost.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
@Threads(1)
public class BidJmhBenchmark {
  private static final RoundingMode BID_ROUNDING = RoundingMode.TIES_TO_EVEN;

  @State(Scope.Thread)
  public static class Operands {
    private static final int SIZE = 1024;
    private static final int MASK = SIZE - 1;

    @Param({"sameQuantum", "mixedQuantum", "fullPrecision"})
    public String workload;

    Bid64[] left64;
    Bid64[] right64;
    long[] leftBits64;
    long[] rightBits64;
    BigDecimal[] leftDecimal64;
    BigDecimal[] rightDecimal64;
    Bid128[] left128;
    Bid128[] right128;
    BigDecimal[] leftDecimal128;
    BigDecimal[] rightDecimal128;
    StatusFlags flags;
    int index;

    @Setup(Level.Trial)
    public void setup() {
      left64 = new Bid64[SIZE];
      right64 = new Bid64[SIZE];
      leftBits64 = new long[SIZE];
      rightBits64 = new long[SIZE];
      leftDecimal64 = new BigDecimal[SIZE];
      rightDecimal64 = new BigDecimal[SIZE];
      left128 = new Bid128[SIZE];
      right128 = new Bid128[SIZE];
      leftDecimal128 = new BigDecimal[SIZE];
      rightDecimal128 = new BigDecimal[SIZE];
      flags = new StatusFlags();
      Random random = new Random(0x5eed_b1dL ^ workload.hashCode());
      for (int i = 0; i < SIZE; i++) {
        fill64(random, i);
        fill128(random, i);
      }
    }

    int nextIndex() {
      return index++ & MASK;
    }

    private void fill64(Random random, int position) {
      DecimalPair pair = createPair(random, 16);
      left64[position] = Bid64.parseExact(pair.left);
      right64[position] = Bid64.parseExact(pair.right);
      leftBits64[position] = left64[position].toRawBits();
      rightBits64[position] = right64[position].toRawBits();
      leftDecimal64[position] = new BigDecimal(pair.left);
      rightDecimal64[position] = new BigDecimal(pair.right);
    }

    private void fill128(Random random, int position) {
      DecimalPair pair = createPair(random, 34);
      left128[position] = Bid128.parseExact(pair.left);
      right128[position] = Bid128.parseExact(pair.right);
      leftDecimal128[position] = new BigDecimal(pair.left);
      rightDecimal128[position] = new BigDecimal(pair.right);
    }

    private DecimalPair createPair(Random random, int formatDigits) {
      int leftDigits;
      int rightDigits;
      int leftExponent;
      int rightExponent;
      if ("sameQuantum".equals(workload)) {
        leftDigits = Math.max(4, formatDigits - 6);
        rightDigits = leftDigits;
        leftExponent = randomExponent(random, 40);
        rightExponent = leftExponent;
      } else if ("mixedQuantum".equals(workload)) {
        leftDigits = randomDigitsCount(random, 4, formatDigits - 4);
        rightDigits = randomDigitsCount(random, 4, formatDigits - 4);
        leftExponent = randomExponent(random, 80);
        rightExponent = differentExponent(random, leftExponent, 80);
      } else if ("fullPrecision".equals(workload)) {
        leftDigits = formatDigits;
        rightDigits = formatDigits;
        leftExponent = randomExponent(random, 100);
        rightExponent = differentExponent(random, leftExponent, 100);
      } else {
        throw new IllegalArgumentException("Unknown workload: " + workload);
      }
      return new DecimalPair(
          decimalText(random, leftDigits, leftExponent),
          decimalText(random, rightDigits, rightExponent));
    }

    private static int randomDigitsCount(
        Random random, int minimum, int maximum) {
      return minimum + random.nextInt(maximum - minimum + 1);
    }

    private static int randomExponent(Random random, int magnitude) {
      return random.nextInt(2 * magnitude + 1) - magnitude;
    }

    private static int differentExponent(
        Random random, int excluded, int magnitude) {
      int exponent;
      do {
        exponent = randomExponent(random, magnitude);
      } while (exponent == excluded);
      return exponent;
    }

    private static String decimalText(Random random, int digits, int exponent) {
      StringBuilder text = new StringBuilder(digits + 8);
      if (random.nextBoolean()) {
        text.append('-');
      }
      text.append(random.nextInt(9) + 1);
      for (int i = 1; i < digits; i++) {
        text.append(random.nextInt(10));
      }
      return text.append('E').append(exponent).toString();
    }
  }

  @State(Scope.Thread)
  public static class ComparisonOperands {
    private static final int SIZE = 1024;
    private static final int MASK = SIZE - 1;

    Bid64[] orderedLeft64;
    Bid64[] orderedRight64;
    Bid64[] cohortLeft64;
    Bid64[] cohortRight64;
    BigDecimal[] orderedLeftDecimal64;
    BigDecimal[] orderedRightDecimal64;
    BigDecimal[] cohortLeftDecimal64;
    BigDecimal[] cohortRightDecimal64;

    Bid128[] orderedLeft128;
    Bid128[] orderedRight128;
    Bid128[] cohortLeft128;
    Bid128[] cohortRight128;
    BigDecimal[] orderedLeftDecimal128;
    BigDecimal[] orderedRightDecimal128;
    BigDecimal[] cohortLeftDecimal128;
    BigDecimal[] cohortRightDecimal128;

    StatusFlags flags;
    int index;

    @Setup(Level.Trial)
    public void setup() {
      orderedLeft64 = new Bid64[SIZE];
      orderedRight64 = new Bid64[SIZE];
      cohortLeft64 = new Bid64[SIZE];
      cohortRight64 = new Bid64[SIZE];
      orderedLeftDecimal64 = new BigDecimal[SIZE];
      orderedRightDecimal64 = new BigDecimal[SIZE];
      cohortLeftDecimal64 = new BigDecimal[SIZE];
      cohortRightDecimal64 = new BigDecimal[SIZE];

      orderedLeft128 = new Bid128[SIZE];
      orderedRight128 = new Bid128[SIZE];
      cohortLeft128 = new Bid128[SIZE];
      cohortRight128 = new Bid128[SIZE];
      orderedLeftDecimal128 = new BigDecimal[SIZE];
      orderedRightDecimal128 = new BigDecimal[SIZE];
      cohortLeftDecimal128 = new BigDecimal[SIZE];
      cohortRightDecimal128 = new BigDecimal[SIZE];

      flags = new StatusFlags();
      Random random = new Random(0xc0_40_47L);
      for (int i = 0; i < SIZE; i++) {
        fill64(random, i);
        fill128(random, i);
      }
      sanityCheck();
    }

    int nextIndex() {
      return index++ & MASK;
    }

    private void fill64(Random random, int position) {
      DecimalPair ordered = orderedPair(random, 16);
      DecimalPair cohort = cohortPair(random, 16);
      orderedLeft64[position] = Bid64.parseExact(ordered.left);
      orderedRight64[position] = Bid64.parseExact(ordered.right);
      cohortLeft64[position] = Bid64.parseExact(cohort.left);
      cohortRight64[position] = Bid64.parseExact(cohort.right);
      orderedLeftDecimal64[position] = new BigDecimal(ordered.left);
      orderedRightDecimal64[position] = new BigDecimal(ordered.right);
      cohortLeftDecimal64[position] = new BigDecimal(cohort.left);
      cohortRightDecimal64[position] = new BigDecimal(cohort.right);
    }

    private void fill128(Random random, int position) {
      DecimalPair ordered = orderedPair(random, 34);
      DecimalPair cohort = cohortPair(random, 34);
      orderedLeft128[position] = Bid128.parseExact(ordered.left);
      orderedRight128[position] = Bid128.parseExact(ordered.right);
      cohortLeft128[position] = Bid128.parseExact(cohort.left);
      cohortRight128[position] = Bid128.parseExact(cohort.right);
      orderedLeftDecimal128[position] = new BigDecimal(ordered.left);
      orderedRightDecimal128[position] = new BigDecimal(ordered.right);
      cohortLeftDecimal128[position] = new BigDecimal(cohort.left);
      cohortRightDecimal128[position] = new BigDecimal(cohort.right);
    }

    private void sanityCheck() {
      StatusFlags checkFlags = new StatusFlags();
      for (int i = 0; i < SIZE; i++) {
        checkComparison(
            orderedLeft64[i].quietLess(orderedRight64[i], checkFlags),
            orderedLeftDecimal64[i].compareTo(orderedRightDecimal64[i]) < 0);
        checkComparison(
            orderedLeft128[i].quietLess(orderedRight128[i], checkFlags),
            orderedLeftDecimal128[i].compareTo(orderedRightDecimal128[i]) < 0);
        checkComparison(
            cohortLeft64[i].quietEqual(cohortRight64[i], checkFlags),
            cohortLeftDecimal64[i].compareTo(cohortRightDecimal64[i]) == 0);
        checkComparison(
            cohortLeft128[i].quietEqual(cohortRight128[i], checkFlags),
            cohortLeftDecimal128[i].compareTo(cohortRightDecimal128[i]) == 0);
        if (cohortLeft64[i].sameQuantum(cohortRight64[i])
            || cohortLeft128[i].sameQuantum(cohortRight128[i])) {
          throw new IllegalStateException("cohort operands unexpectedly share a quantum");
        }
      }
      if (checkFlags.bits() != 0) {
        throw new IllegalStateException("finite comparisons raised status flags");
      }
    }

    private static void checkComparison(boolean bidResult, boolean decimalResult) {
      if (bidResult != decimalResult) {
        throw new IllegalStateException("comparison operand sanity check failed");
      }
    }

    private static DecimalPair orderedPair(Random random, int digits) {
      String left = decimalText(random, digits, random.nextInt(121) - 60);
      String right;
      do {
        right = decimalText(random, digits, random.nextInt(121) - 60);
      } while (new BigDecimal(left).compareTo(new BigDecimal(right)) == 0);
      return random.nextBoolean()
          ? new DecimalPair(left, right)
          : new DecimalPair(right, left);
    }

    private static DecimalPair cohortPair(Random random, int precision) {
      StringBuilder coefficient = new StringBuilder(precision);
      coefficient.append(random.nextInt(9) + 1);
      for (int i = 1; i < precision - 1; i++) {
        coefficient.append(random.nextInt(10));
      }
      String sign = random.nextBoolean() ? "-" : "";
      int exponent = random.nextInt(121) - 60;
      return new DecimalPair(
          sign + coefficient + "E" + exponent,
          sign + coefficient + "0E" + (exponent - 1));
    }

    private static String decimalText(Random random, int digits, int exponent) {
      StringBuilder text = new StringBuilder(digits + 8);
      if (random.nextBoolean()) {
        text.append('-');
      }
      text.append(random.nextInt(9) + 1);
      for (int i = 1; i < digits; i++) {
        text.append(random.nextInt(10));
      }
      return text.append('E').append(exponent).toString();
    }
  }

  private static final class DecimalPair {
    final String left;
    final String right;

    DecimalPair(String left, String right) {
      this.left = left;
      this.right = right;
    }
  }

  @Benchmark
  public void bid64Add(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(data.left64[i].add(data.right64[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bid64RawAdd(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(Bid64Add.addRawBits(
        data.leftBits64[i], data.rightBits64[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bigDecimal64Add(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.leftDecimal64[i].add(data.rightDecimal64[i], MathContext.DECIMAL64));
  }

  @Benchmark
  public void bid64Multiply(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.left64[i].multiply(data.right64[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bid64RawMultiply(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(Bid64Multiply.multiplyRawBits(
        data.leftBits64[i], data.rightBits64[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bigDecimal64Multiply(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.leftDecimal64[i].multiply(data.rightDecimal64[i], MathContext.DECIMAL64));
  }

  @Benchmark
  public void bid64Divide(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(data.left64[i].divide(data.right64[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bid64RawDivide(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(Bid64Divide.divideRawBits(
        data.leftBits64[i], data.rightBits64[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bigDecimal64Divide(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.leftDecimal64[i].divide(data.rightDecimal64[i], MathContext.DECIMAL64));
  }

  @Benchmark
  public void bid128Add(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(data.left128[i].add(data.right128[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bigDecimal128Add(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.leftDecimal128[i].add(data.rightDecimal128[i], MathContext.DECIMAL128));
  }

  @Benchmark
  public void bid128Multiply(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.left128[i].multiply(data.right128[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bigDecimal128Multiply(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.leftDecimal128[i].multiply(data.rightDecimal128[i], MathContext.DECIMAL128));
  }

  @Benchmark
  public void bid128Divide(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(data.left128[i].divide(data.right128[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bigDecimal128Divide(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.leftDecimal128[i].divide(data.rightDecimal128[i], MathContext.DECIMAL128));
  }

  @Benchmark
  public void bid64CompareLess(ComparisonOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.orderedLeft64[i].quietLess(data.orderedRight64[i], data.flags));
  }

  @Benchmark
  public void bigDecimal64CompareLess(ComparisonOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.orderedLeftDecimal64[i].compareTo(data.orderedRightDecimal64[i]) < 0);
  }

  @Benchmark
  public void bid64CohortEqual(ComparisonOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.cohortLeft64[i].quietEqual(data.cohortRight64[i], data.flags));
  }

  @Benchmark
  public void bigDecimal64CohortEqual(ComparisonOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.cohortLeftDecimal64[i].compareTo(data.cohortRightDecimal64[i]) == 0);
  }

  @Benchmark
  public void bid128CompareLess(ComparisonOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.orderedLeft128[i].quietLess(data.orderedRight128[i], data.flags));
  }

  @Benchmark
  public void bigDecimal128CompareLess(ComparisonOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.orderedLeftDecimal128[i].compareTo(data.orderedRightDecimal128[i]) < 0);
  }

  @Benchmark
  public void bid128CohortEqual(ComparisonOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.cohortLeft128[i].quietEqual(data.cohortRight128[i], data.flags));
  }

  @Benchmark
  public void bigDecimal128CohortEqual(ComparisonOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.cohortLeftDecimal128[i].compareTo(data.cohortRightDecimal128[i]) == 0);
  }
}
