package org.bidfp.binary128;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures packed binary128 arithmetic and every public DPML kernel family.
 *
 * <p>Inputs are prepared outside the measured region. Each invocation includes
 * clearing the explicit IEEE status accumulator used by the public API.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
@Threads(1)
public class Binary128JmhBenchmark {
  private static final RoundingMode ROUNDING = RoundingMode.TIES_TO_EVEN;

  @State(Scope.Thread)
  public static class Operands {
    private static final int SIZE = 1024;
    private static final int MASK = SIZE - 1;

    Binary128[] left;
    Binary128[] right;
    Binary128[] positive;
    Binary128[] greaterThanOne;
    Binary128[] unit;
    Binary128[] angle;
    Binary128[] exponential;
    Binary128[] hyperbolic;
    Binary128[] powerExponent;
    Binary128[] gamma;
    StatusFlags flags;
    int index;

    @Setup
    public void setup() {
      left = new Binary128[SIZE];
      right = new Binary128[SIZE];
      positive = new Binary128[SIZE];
      greaterThanOne = new Binary128[SIZE];
      unit = new Binary128[SIZE];
      angle = new Binary128[SIZE];
      exponential = new Binary128[SIZE];
      hyperbolic = new Binary128[SIZE];
      powerExponent = new Binary128[SIZE];
      gamma = new Binary128[SIZE];
      flags = new StatusFlags();

      Random random = new Random(0x128d_5eedL);
      for (int i = 0; i < SIZE; i++) {
        left[i] = binary128(scaledFinite(random));
        right[i] = binary128(nonzeroScaledFinite(random));
        positive[i] = binary128(0.125 + random.nextDouble() * 15.875);
        greaterThanOne[i] = binary128(1.0 + random.nextDouble() * 15.0);
        unit[i] = binary128(random.nextDouble() * 1.9 - 0.95);
        angle[i] = binary128((random.nextDouble() * 2.0 - 1.0) * 1.0e12);
        exponential[i] = binary128(random.nextDouble() * 20.0 - 10.0);
        hyperbolic[i] = binary128(random.nextDouble() * 10.0 - 5.0);
        powerExponent[i] = binary128(random.nextDouble() * 16.0 - 8.0);
        gamma[i] = binary128(gammaArgument(random, i));
      }
    }

    int nextIndex() {
      flags.clear();
      return index++ & MASK;
    }

    private static Binary128 binary128(double value) {
      return Binary128.fromBinary64(value);
    }

    private static double scaledFinite(Random random) {
      double value = Math.scalb(0.5 + random.nextDouble(), random.nextInt(201) - 100);
      return random.nextBoolean() ? value : -value;
    }

    private static double nonzeroScaledFinite(Random random) {
      double value;
      do {
        value = scaledFinite(random);
      } while (value == 0.0);
      return value;
    }

    private static double gammaArgument(Random random, int index) {
      double magnitude = 0.125 + random.nextDouble() * 11.875;
      return (index & 1) == 0 ? magnitude : -magnitude;
    }
  }

  @Benchmark
  public Binary128 add(Operands data) {
    int i = data.nextIndex();
    return Dpml.add(data.left[i], data.right[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 sub(Operands data) {
    int i = data.nextIndex();
    return Dpml.sub(data.left[i], data.right[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 mul(Operands data) {
    int i = data.nextIndex();
    return Dpml.mul(data.left[i], data.right[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 div(Operands data) {
    int i = data.nextIndex();
    return Dpml.div(data.left[i], data.right[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 sqrt(Operands data) {
    int i = data.nextIndex();
    return Dpml.sqrt(data.positive[i], ROUNDING, data.flags);
  }

  @Benchmark
  public int compare(Operands data) {
    int i = data.nextIndex();
    return data.left[i].compare(data.right[i], data.flags);
  }

  @Benchmark
  public Binary128 exp(Operands data) {
    int i = data.nextIndex();
    return Dpml.exp(data.exponential[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 expm1(Operands data) {
    int i = data.nextIndex();
    return Dpml.expm1(data.exponential[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 exp2(Operands data) {
    int i = data.nextIndex();
    return Dpml.exp2(data.exponential[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 exp10(Operands data) {
    int i = data.nextIndex();
    return Dpml.exp10(data.exponential[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 log(Operands data) {
    int i = data.nextIndex();
    return Dpml.log(data.positive[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 log2(Operands data) {
    int i = data.nextIndex();
    return Dpml.log2(data.positive[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 log10(Operands data) {
    int i = data.nextIndex();
    return Dpml.log10(data.positive[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 log1p(Operands data) {
    int i = data.nextIndex();
    return Dpml.log1p(data.unit[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 pow(Operands data) {
    int i = data.nextIndex();
    return Dpml.pow(data.positive[i], data.powerExponent[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 cbrt(Operands data) {
    int i = data.nextIndex();
    return Dpml.cbrt(data.left[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 sin(Operands data) {
    int i = data.nextIndex();
    return Dpml.sin(data.angle[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 cos(Operands data) {
    int i = data.nextIndex();
    return Dpml.cos(data.angle[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 tan(Operands data) {
    int i = data.nextIndex();
    return Dpml.tan(data.angle[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 asin(Operands data) {
    int i = data.nextIndex();
    return Dpml.asin(data.unit[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 acos(Operands data) {
    int i = data.nextIndex();
    return Dpml.acos(data.unit[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 atan(Operands data) {
    int i = data.nextIndex();
    return Dpml.atan(data.angle[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 atan2(Operands data) {
    int i = data.nextIndex();
    return Dpml.atan2(data.left[i], data.right[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 sinh(Operands data) {
    int i = data.nextIndex();
    return Dpml.sinh(data.hyperbolic[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 cosh(Operands data) {
    int i = data.nextIndex();
    return Dpml.cosh(data.hyperbolic[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 tanh(Operands data) {
    int i = data.nextIndex();
    return Dpml.tanh(data.hyperbolic[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 asinh(Operands data) {
    int i = data.nextIndex();
    return Dpml.asinh(data.angle[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 acosh(Operands data) {
    int i = data.nextIndex();
    return Dpml.acosh(data.greaterThanOne[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 atanh(Operands data) {
    int i = data.nextIndex();
    return Dpml.atanh(data.unit[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 erf(Operands data) {
    int i = data.nextIndex();
    return Dpml.erf(data.hyperbolic[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 erfc(Operands data) {
    int i = data.nextIndex();
    return Dpml.erfc(data.hyperbolic[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 lgamma(Operands data) {
    int i = data.nextIndex();
    return Dpml.lgamma(data.gamma[i], ROUNDING, data.flags);
  }

  @Benchmark
  public Binary128 tgamma(Operands data) {
    int i = data.nextIndex();
    return Dpml.tgamma(data.gamma[i], ROUNDING, data.flags);
  }
}
