package org.bidfp.binary128;

/** Command-line check used by {@code build.sh} (no JUnit on that classpath). */
public final class Binary128Check {
  private Binary128Check() {
  }

  public static void main(String[] args) {
    Binary128 zero = Binary128.fromRawBits(0L, 0L);
    if (!zero.isZero() || !zero.isFinite()) {
      throw new AssertionError("zero");
    }
    Binary128 inf = Binary128.fromRawBits(0x7fff_0000_0000_0000L, 0L);
    if (!inf.isInfinite() || inf.isNaN()) {
      throw new AssertionError("inf");
    }
    Binary128 nan = Binary128.fromRawBits(0x7fff_8000_0000_0000L, 0L);
    if (!nan.isNaN() || nan.isInfinite()) {
      throw new AssertionError("nan");
    }
    System.out.println("Binary128Check: all tests passed");
  }
}
