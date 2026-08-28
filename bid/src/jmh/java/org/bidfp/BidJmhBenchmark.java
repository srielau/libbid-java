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
 * <p>Both formats also have raw-bit kernels ({@code Bid64Raw} and
 * {@code Bid128Raw}) exercised by the {@code *Raw*} methods on the same values,
 * so JMH can separate object-wrapper allocation from arithmetic cost.
 *
 * <p>The measured BID methods share one {@link StatusFlags} per state and do
 * not clear it. IEEE flags are sticky by design, so this benchmark treats the
 * shared instance as an accumulator: it measures each operation including the
 * cost of merging into already-set flags. The BigDecimal methods do not touch
 * flags, so they are left unchanged.
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
    long[] leftHigh128;
    long[] leftLow128;
    long[] rightHigh128;
    long[] rightLow128;
    BigDecimal[] leftDecimal128;
    BigDecimal[] rightDecimal128;
    long[] result128;
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
      leftHigh128 = new long[SIZE];
      leftLow128 = new long[SIZE];
      rightHigh128 = new long[SIZE];
      rightLow128 = new long[SIZE];
      leftDecimal128 = new BigDecimal[SIZE];
      rightDecimal128 = new BigDecimal[SIZE];
      result128 = new long[2];
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
      leftHigh128[position] = left128[position].highBits();
      leftLow128[position] = left128[position].lowBits();
      rightHigh128[position] = right128[position].highBits();
      rightLow128[position] = right128[position].lowBits();
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

  @State(Scope.Thread)
  public static class ExtendedOperands {
    private static final int SIZE = 1024;
    private static final int MASK = SIZE - 1;

    Bid64[] value64;
    Bid64[] other64;
    Bid64[] addend64;
    Bid64[] powBase64;
    Bid64[] power64;
    long[] valueBits64;
    long[] otherBits64;
    long[] addendBits64;
    long[] powBaseBits64;
    long[] powerBits64;
    BigDecimal[] valueDecimal64;
    BigDecimal[] otherDecimal64;
    BigDecimal[] addendDecimal64;
    BigDecimal[] powBaseDecimal64;
    int[] powers;
    int[] scales;

    Bid128[] value128;
    Bid128[] other128;
    Bid128[] addend128;
    Bid128[] powBase128;
    Bid128[] power128;
    long[] valueHigh128;
    long[] valueLow128;
    long[] otherHigh128;
    long[] otherLow128;
    long[] addendHigh128;
    long[] addendLow128;
    long[] powBaseHigh128;
    long[] powBaseLow128;
    long[] powerHigh128;
    long[] powerLow128;
    BigDecimal[] valueDecimal128;
    BigDecimal[] otherDecimal128;
    BigDecimal[] addendDecimal128;
    BigDecimal[] powBaseDecimal128;

    long[] result128;
    StatusFlags flags;
    int index;

    @Setup(Level.Trial)
    public void setup() {
      value64 = new Bid64[SIZE];
      other64 = new Bid64[SIZE];
      addend64 = new Bid64[SIZE];
      powBase64 = new Bid64[SIZE];
      power64 = new Bid64[SIZE];
      valueBits64 = new long[SIZE];
      otherBits64 = new long[SIZE];
      addendBits64 = new long[SIZE];
      powBaseBits64 = new long[SIZE];
      powerBits64 = new long[SIZE];
      valueDecimal64 = new BigDecimal[SIZE];
      otherDecimal64 = new BigDecimal[SIZE];
      addendDecimal64 = new BigDecimal[SIZE];
      powBaseDecimal64 = new BigDecimal[SIZE];
      powers = new int[SIZE];
      scales = new int[SIZE];

      value128 = new Bid128[SIZE];
      other128 = new Bid128[SIZE];
      addend128 = new Bid128[SIZE];
      powBase128 = new Bid128[SIZE];
      power128 = new Bid128[SIZE];
      valueHigh128 = new long[SIZE];
      valueLow128 = new long[SIZE];
      otherHigh128 = new long[SIZE];
      otherLow128 = new long[SIZE];
      addendHigh128 = new long[SIZE];
      addendLow128 = new long[SIZE];
      powBaseHigh128 = new long[SIZE];
      powBaseLow128 = new long[SIZE];
      powerHigh128 = new long[SIZE];
      powerLow128 = new long[SIZE];
      valueDecimal128 = new BigDecimal[SIZE];
      otherDecimal128 = new BigDecimal[SIZE];
      addendDecimal128 = new BigDecimal[SIZE];
      powBaseDecimal128 = new BigDecimal[SIZE];

      result128 = new long[2];
      flags = new StatusFlags();
      Random random = new Random(0xe17e_4dedL);
      for (int i = 0; i < SIZE; i++) {
        powers[i] = 2 + random.nextInt(4);
        scales[i] = random.nextInt(25) - 12;
        fill64(random, i);
        fill128(random, i);
      }
      sanityCheck();
    }

    int nextIndex() {
      return index++ & MASK;
    }

    private void fill64(Random random, int position) {
      String value = unitDecimalText(random, 16, false);
      String other = unitDecimalText(random, 16, false);
      String addend = unitDecimalText(random, 16, true);
      String powBase = unitDecimalText(random, 3, false);
      value64[position] = Bid64.parseExact(value);
      other64[position] = Bid64.parseExact(other);
      addend64[position] = Bid64.parseExact(addend);
      powBase64[position] = Bid64.parseExact(powBase);
      power64[position] = Bid64.parseExact(Integer.toString(powers[position]));
      valueBits64[position] = value64[position].toRawBits();
      otherBits64[position] = other64[position].toRawBits();
      addendBits64[position] = addend64[position].toRawBits();
      powBaseBits64[position] = powBase64[position].toRawBits();
      powerBits64[position] = power64[position].toRawBits();
      valueDecimal64[position] = new BigDecimal(value);
      otherDecimal64[position] = new BigDecimal(other);
      addendDecimal64[position] = new BigDecimal(addend);
      powBaseDecimal64[position] = new BigDecimal(powBase);
    }

    private void fill128(Random random, int position) {
      String value = unitDecimalText(random, 34, false);
      String other = unitDecimalText(random, 34, false);
      String addend = unitDecimalText(random, 34, true);
      String powBase = unitDecimalText(random, 6, false);
      value128[position] = Bid128.parseExact(value);
      other128[position] = Bid128.parseExact(other);
      addend128[position] = Bid128.parseExact(addend);
      powBase128[position] = Bid128.parseExact(powBase);
      power128[position] = Bid128.parseExact(Integer.toString(powers[position]));
      valueHigh128[position] = value128[position].highBits();
      valueLow128[position] = value128[position].lowBits();
      otherHigh128[position] = other128[position].highBits();
      otherLow128[position] = other128[position].lowBits();
      addendHigh128[position] = addend128[position].highBits();
      addendLow128[position] = addend128[position].lowBits();
      powBaseHigh128[position] = powBase128[position].highBits();
      powBaseLow128[position] = powBase128[position].lowBits();
      powerHigh128[position] = power128[position].highBits();
      powerLow128[position] = power128[position].lowBits();
      valueDecimal128[position] = new BigDecimal(value);
      otherDecimal128[position] = new BigDecimal(other);
      addendDecimal128[position] = new BigDecimal(addend);
      powBaseDecimal128[position] = new BigDecimal(powBase);
    }

    private void sanityCheck() {
      for (int i = 0; i < SIZE; i++) {
        check64(i);
        check128(i);
      }
    }

    private void check64(int i) {
      check64Result(
          value64[i].subtract(other64[i], BID_ROUNDING, new StatusFlags()),
          Bid64Raw.sub(valueBits64[i], otherBits64[i], BID_ROUNDING, new StatusFlags()),
          valueDecimal64[i].subtract(otherDecimal64[i], MathContext.DECIMAL64),
          "subtract");
      check64Result(
          value64[i].sqrt(BID_ROUNDING, new StatusFlags()),
          Bid64Raw.sqrt(valueBits64[i], BID_ROUNDING, new StatusFlags()),
          valueDecimal64[i].sqrt(MathContext.DECIMAL64),
          "sqrt");
      check64Result(
          value64[i].fma(other64[i], addend64[i], BID_ROUNDING, new StatusFlags()),
          Bid64Raw.fma(
              valueBits64[i],
              otherBits64[i],
              addendBits64[i],
              BID_ROUNDING,
              new StatusFlags()),
          decimalFma(
              valueDecimal64[i],
              otherDecimal64[i],
              addendDecimal64[i],
              MathContext.DECIMAL64),
          "fma");
      check64Result(
          value64[i].fmod(other64[i], new StatusFlags()),
          Bid64Raw.fmod(valueBits64[i], otherBits64[i], new StatusFlags()),
          valueDecimal64[i].remainder(otherDecimal64[i]),
          "fmod");
      check64Result(
          powBase64[i].pow(power64[i], BID_ROUNDING, new StatusFlags()),
          Bid64Raw.pow(
              powBaseBits64[i], powerBits64[i], BID_ROUNDING, new StatusFlags()),
          powBaseDecimal64[i].pow(powers[i], MathContext.DECIMAL64),
          "powIntegral");
      check64Result(
          value64[i].roundIntegral(BID_ROUNDING, false, new StatusFlags()),
          Bid64Raw.roundIntegral(
              valueBits64[i], BID_ROUNDING, new StatusFlags(), false),
          valueDecimal64[i].setScale(0, java.math.RoundingMode.HALF_EVEN),
          "roundIntegral");
      check64Result(
          value64[i].scaleByPowerOfTen(scales[i], BID_ROUNDING, new StatusFlags()),
          Bid64Raw.scalbn(
              valueBits64[i], scales[i], BID_ROUNDING, new StatusFlags()),
          valueDecimal64[i].scaleByPowerOfTen(scales[i]),
          "scaleByPowerOfTen");
    }

    private void check128(int i) {
      check128Result(
          value128[i].subtract(other128[i], BID_ROUNDING, new StatusFlags()),
          rawSubtract128(i),
          valueDecimal128[i].subtract(otherDecimal128[i], MathContext.DECIMAL128),
          "subtract");
      check128Result(
          value128[i].sqrt(BID_ROUNDING, new StatusFlags()),
          rawSqrt128(i),
          valueDecimal128[i].sqrt(MathContext.DECIMAL128),
          "sqrt");
      check128Result(
          value128[i].fma(other128[i], addend128[i], BID_ROUNDING, new StatusFlags()),
          rawFma128(i),
          decimalFma(
              valueDecimal128[i],
              otherDecimal128[i],
              addendDecimal128[i],
              MathContext.DECIMAL128),
          "fma");
      check128Result(
          value128[i].fmod(other128[i], new StatusFlags()),
          rawFmod128(i),
          valueDecimal128[i].remainder(otherDecimal128[i]),
          "fmod");
      check128Result(
          powBase128[i].pow(power128[i], BID_ROUNDING, new StatusFlags()),
          rawPow128(i),
          powBaseDecimal128[i].pow(powers[i], MathContext.DECIMAL128),
          "powIntegral");
      check128Result(
          value128[i].roundIntegral(BID_ROUNDING, false, new StatusFlags()),
          rawRound128(i),
          valueDecimal128[i].setScale(0, java.math.RoundingMode.HALF_EVEN),
          "roundIntegral");
      check128Result(
          value128[i].scaleByPowerOfTen(scales[i], BID_ROUNDING, new StatusFlags()),
          rawScale128(i),
          valueDecimal128[i].scaleByPowerOfTen(scales[i]),
          "scaleByPowerOfTen");
    }

    private Bid128 rawSubtract128(int i) {
      return raw128((out) -> Bid128Raw.sub(
          valueHigh128[i],
          valueLow128[i],
          otherHigh128[i],
          otherLow128[i],
          BID_ROUNDING,
          new StatusFlags(),
          out));
    }

    private Bid128 rawSqrt128(int i) {
      return raw128((out) -> Bid128Raw.sqrt(
          valueHigh128[i], valueLow128[i], BID_ROUNDING, new StatusFlags(), out));
    }

    private Bid128 rawFma128(int i) {
      return raw128((out) -> Bid128Raw.fma(
          valueHigh128[i],
          valueLow128[i],
          otherHigh128[i],
          otherLow128[i],
          addendHigh128[i],
          addendLow128[i],
          BID_ROUNDING,
          new StatusFlags(),
          out));
    }

    private Bid128 rawFmod128(int i) {
      return raw128((out) -> Bid128Raw.fmod(
          valueHigh128[i],
          valueLow128[i],
          otherHigh128[i],
          otherLow128[i],
          new StatusFlags(),
          out));
    }

    private Bid128 rawPow128(int i) {
      return raw128((out) -> Bid128Raw.pow(
          powBaseHigh128[i],
          powBaseLow128[i],
          powerHigh128[i],
          powerLow128[i],
          BID_ROUNDING,
          new StatusFlags(),
          out));
    }

    private Bid128 rawRound128(int i) {
      return raw128((out) -> Bid128Raw.roundIntegral(
          valueHigh128[i],
          valueLow128[i],
          BID_ROUNDING,
          new StatusFlags(),
          false,
          out));
    }

    private Bid128 rawScale128(int i) {
      return raw128((out) -> Bid128Raw.scalbn(
          valueHigh128[i],
          valueLow128[i],
          scales[i],
          BID_ROUNDING,
          new StatusFlags(),
          out));
    }

    private static Bid128 raw128(Raw128Operation operation) {
      long[] out = new long[2];
      operation.apply(out);
      return Bid128.fromRawBits(out[0], out[1]);
    }

    private static void check64Result(
        Bid64 object, long raw, BigDecimal decimal, String operation) {
      if (object.toRawBits() != raw) {
        throw new IllegalStateException(operation + " BID64 object/raw mismatch");
      }
      checkDecimal(object.toBigDecimal(), decimal, operation + " BID64");
    }

    private static void check128Result(
        Bid128 object, Bid128 raw, BigDecimal decimal, String operation) {
      if (!object.equals(raw)) {
        throw new IllegalStateException(operation + " BID128 object/raw mismatch");
      }
      checkDecimal(object.toBigDecimal(), decimal, operation + " BID128");
    }

    private static void checkDecimal(
        BigDecimal bid, BigDecimal decimal, String operation) {
      if (bid.compareTo(decimal) != 0) {
        throw new IllegalStateException(operation + " BigDecimal mismatch: "
            + bid + " != " + decimal);
      }
    }

    private static BigDecimal decimalFma(
        BigDecimal x, BigDecimal y, BigDecimal z, MathContext context) {
      return x.multiply(y).add(z).round(context);
    }

    private static String unitDecimalText(Random random, int digits, boolean signed) {
      StringBuilder text = new StringBuilder(digits + 8);
      if (signed && random.nextBoolean()) {
        text.append('-');
      }
      text.append(random.nextInt(9) + 1);
      for (int i = 1; i < digits; i++) {
        text.append(random.nextInt(10));
      }
      return text.append('E').append(-(digits - 1)).toString();
    }
  }

  @FunctionalInterface
  private interface Raw128Operation {
    void apply(long[] out);
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
    blackhole.consume(Bid64Raw.add(
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
    blackhole.consume(Bid64Raw.mul(
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
    blackhole.consume(Bid64Raw.div(
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
  public void bid128RawAdd(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    Bid128Raw.add(
        data.leftHigh128[i],
        data.leftLow128[i],
        data.rightHigh128[i],
        data.rightLow128[i],
        BID_ROUNDING,
        data.flags,
        data.result128);
    blackhole.consume(data.result128[0]);
    blackhole.consume(data.result128[1]);
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
  public void bid128RawMultiply(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    Bid128Raw.mul(
        data.leftHigh128[i],
        data.leftLow128[i],
        data.rightHigh128[i],
        data.rightLow128[i],
        BID_ROUNDING,
        data.flags,
        data.result128);
    blackhole.consume(data.result128[0]);
    blackhole.consume(data.result128[1]);
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
  public void bid128RawDivide(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    Bid128Raw.div(
        data.leftHigh128[i],
        data.leftLow128[i],
        data.rightHigh128[i],
        data.rightLow128[i],
        BID_ROUNDING,
        data.flags,
        data.result128);
    blackhole.consume(data.result128[0]);
    blackhole.consume(data.result128[1]);
  }

  @Benchmark
  public void bigDecimal128Divide(Operands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.leftDecimal128[i].divide(data.rightDecimal128[i], MathContext.DECIMAL128));
  }

  @Benchmark
  public void bid64Subtract(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(data.value64[i].subtract(data.other64[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bid64RawSubtract(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(Bid64Raw.sub(
        data.valueBits64[i], data.otherBits64[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bigDecimal64Subtract(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.valueDecimal64[i].subtract(data.otherDecimal64[i], MathContext.DECIMAL64));
  }

  @Benchmark
  public void bid128Subtract(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.value128[i].subtract(data.other128[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bid128RawSubtract(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    Bid128Raw.sub(
        data.valueHigh128[i],
        data.valueLow128[i],
        data.otherHigh128[i],
        data.otherLow128[i],
        BID_ROUNDING,
        data.flags,
        data.result128);
    consume128(data, blackhole);
  }

  @Benchmark
  public void bigDecimal128Subtract(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.valueDecimal128[i].subtract(data.otherDecimal128[i], MathContext.DECIMAL128));
  }

  @Benchmark
  public void bid64Sqrt(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(data.value64[i].sqrt(BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bid64RawSqrt(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(Bid64Raw.sqrt(data.valueBits64[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bigDecimal64Sqrt(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(data.valueDecimal64[i].sqrt(MathContext.DECIMAL64));
  }

  @Benchmark
  public void bid128Sqrt(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(data.value128[i].sqrt(BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bid128RawSqrt(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    Bid128Raw.sqrt(
        data.valueHigh128[i],
        data.valueLow128[i],
        BID_ROUNDING,
        data.flags,
        data.result128);
    consume128(data, blackhole);
  }

  @Benchmark
  public void bigDecimal128Sqrt(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(data.valueDecimal128[i].sqrt(MathContext.DECIMAL128));
  }

  @Benchmark
  public void bid64Fma(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(data.value64[i].fma(
        data.other64[i], data.addend64[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bid64RawFma(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(Bid64Raw.fma(
        data.valueBits64[i],
        data.otherBits64[i],
        data.addendBits64[i],
        BID_ROUNDING,
        data.flags));
  }

  @Benchmark
  public void bigDecimal64Fma(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(ExtendedOperands.decimalFma(
        data.valueDecimal64[i],
        data.otherDecimal64[i],
        data.addendDecimal64[i],
        MathContext.DECIMAL64));
  }

  @Benchmark
  public void bid128Fma(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(data.value128[i].fma(
        data.other128[i], data.addend128[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bid128RawFma(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    Bid128Raw.fma(
        data.valueHigh128[i],
        data.valueLow128[i],
        data.otherHigh128[i],
        data.otherLow128[i],
        data.addendHigh128[i],
        data.addendLow128[i],
        BID_ROUNDING,
        data.flags,
        data.result128);
    consume128(data, blackhole);
  }

  @Benchmark
  public void bigDecimal128Fma(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(ExtendedOperands.decimalFma(
        data.valueDecimal128[i],
        data.otherDecimal128[i],
        data.addendDecimal128[i],
        MathContext.DECIMAL128));
  }

  @Benchmark
  public void bid64Fmod(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(data.value64[i].fmod(data.other64[i], data.flags));
  }

  @Benchmark
  public void bid64RawFmod(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        Bid64Raw.fmod(data.valueBits64[i], data.otherBits64[i], data.flags));
  }

  @Benchmark
  public void bigDecimal64Fmod(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(data.valueDecimal64[i].remainder(data.otherDecimal64[i]));
  }

  @Benchmark
  public void bid128Fmod(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(data.value128[i].fmod(data.other128[i], data.flags));
  }

  @Benchmark
  public void bid128RawFmod(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    Bid128Raw.fmod(
        data.valueHigh128[i],
        data.valueLow128[i],
        data.otherHigh128[i],
        data.otherLow128[i],
        data.flags,
        data.result128);
    consume128(data, blackhole);
  }

  @Benchmark
  public void bigDecimal128Fmod(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(data.valueDecimal128[i].remainder(data.otherDecimal128[i]));
  }

  @Benchmark
  public void bid64PowIntegral(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.powBase64[i].pow(data.power64[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bid64RawPowIntegral(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(Bid64Raw.pow(
        data.powBaseBits64[i], data.powerBits64[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bigDecimal64PowIntegral(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.powBaseDecimal64[i].pow(data.powers[i], MathContext.DECIMAL64));
  }

  @Benchmark
  public void bid128PowIntegral(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.powBase128[i].pow(data.power128[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bid128RawPowIntegral(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    Bid128Raw.pow(
        data.powBaseHigh128[i],
        data.powBaseLow128[i],
        data.powerHigh128[i],
        data.powerLow128[i],
        BID_ROUNDING,
        data.flags,
        data.result128);
    consume128(data, blackhole);
  }

  @Benchmark
  public void bigDecimal128PowIntegral(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.powBaseDecimal128[i].pow(data.powers[i], MathContext.DECIMAL128));
  }

  @Benchmark
  public void bid64RoundIntegral(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.value64[i].roundIntegral(BID_ROUNDING, false, data.flags));
  }

  @Benchmark
  public void bid64RawRoundIntegral(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(Bid64Raw.roundIntegral(
        data.valueBits64[i], BID_ROUNDING, data.flags, false));
  }

  @Benchmark
  public void bigDecimal64RoundIntegral(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.valueDecimal64[i].setScale(0, java.math.RoundingMode.HALF_EVEN));
  }

  @Benchmark
  public void bid128RoundIntegral(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.value128[i].roundIntegral(BID_ROUNDING, false, data.flags));
  }

  @Benchmark
  public void bid128RawRoundIntegral(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    Bid128Raw.roundIntegral(
        data.valueHigh128[i],
        data.valueLow128[i],
        BID_ROUNDING,
        data.flags,
        false,
        data.result128);
    consume128(data, blackhole);
  }

  @Benchmark
  public void bigDecimal128RoundIntegral(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.valueDecimal128[i].setScale(0, java.math.RoundingMode.HALF_EVEN));
  }

  @Benchmark
  public void bid64ScaleByPowerOfTen(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.value64[i].scaleByPowerOfTen(data.scales[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bid64RawScaleByPowerOfTen(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(Bid64Raw.scalbn(
        data.valueBits64[i], data.scales[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bigDecimal64ScaleByPowerOfTen(
      ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(data.valueDecimal64[i].scaleByPowerOfTen(data.scales[i]));
  }

  @Benchmark
  public void bid128ScaleByPowerOfTen(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(
        data.value128[i].scaleByPowerOfTen(data.scales[i], BID_ROUNDING, data.flags));
  }

  @Benchmark
  public void bid128RawScaleByPowerOfTen(ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    Bid128Raw.scalbn(
        data.valueHigh128[i],
        data.valueLow128[i],
        data.scales[i],
        BID_ROUNDING,
        data.flags,
        data.result128);
    consume128(data, blackhole);
  }

  @Benchmark
  public void bigDecimal128ScaleByPowerOfTen(
      ExtendedOperands data, Blackhole blackhole) {
    int i = data.nextIndex();
    blackhole.consume(data.valueDecimal128[i].scaleByPowerOfTen(data.scales[i]));
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

  private static void consume128(ExtendedOperands data, Blackhole blackhole) {
    blackhole.consume(data.result128[0]);
    blackhole.consume(data.result128[1]);
  }
}
