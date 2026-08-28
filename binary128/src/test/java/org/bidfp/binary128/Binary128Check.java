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
    if (!nan.isNaN() || nan.isInfinite() || nan.isSignalingNaN()) {
      throw new AssertionError("nan");
    }
    if (Binary128.ONE.significandHigh() != (1L << 48)) {
      throw new AssertionError("implicit bit");
    }
    if (Binary128.fromFields(false, 0x3fff, 0L, 0L).equals(Binary128.ONE) == false) {
      throw new AssertionError("fromFields");
    }
    StatusFlags st = new StatusFlags();
    Binary128 two = Binary128.ONE.add(Binary128.ONE, RoundingMode.TIES_TO_EVEN, st);
    if (!two.equals(Binary128.fromRawBits(0x4000_0000_0000_0000L, 0L))) {
      throw new AssertionError("1+1=" + two);
    }
    Binary128 half = Binary128.fromRawBits(0x3ffe_0000_0000_0000L, 0L);
    Binary128 prod = Binary128.ONE.multiply(half, RoundingMode.TIES_TO_EVEN, st);
    if (!prod.equals(half)) {
      throw new AssertionError("1*0.5=" + prod);
    }
    Binary128 q = Binary128.ONE.divide(two, RoundingMode.TIES_TO_EVEN, st);
    if (!q.equals(half)) {
      throw new AssertionError("1/2=" + q);
    }
    Binary128 s = Binary128.ONE.sqrt(RoundingMode.TIES_TO_EVEN, st);
    if (!s.equals(Binary128.ONE)) {
      throw new AssertionError("sqrt(1)=" + s);
    }
    Binary128 four = Binary128.fromRawBits(0x4001_0000_0000_0000L, 0L);
    Binary128 sqrt4 = four.sqrt(RoundingMode.TIES_TO_EVEN, st);
    if (!sqrt4.equals(two)) {
      throw new AssertionError("sqrt(4)=" + sqrt4);
    }
    Binary128 e0 = Dpml.exp(Binary128.ZERO, RoundingMode.TIES_TO_EVEN, st);
    if (!e0.equals(Binary128.ONE)) {
      throw new AssertionError("exp(0)=" + e0);
    }
    Binary128 l1 = Dpml.log(Binary128.ONE, RoundingMode.TIES_TO_EVEN, st);
    if (!l1.isZero()) {
      throw new AssertionError("log(1)=" + l1);
    }
    Binary128 roundTrip = Binary128.fromBinary64(1.5);
    if (roundTrip.biasedExponent() != 0x3fff
        || roundTrip.fractionHigh() == 0L) {
      throw new AssertionError("fromBinary64");
    }
    StatusFlags dz = new StatusFlags();
    Binary128 infz = Binary128.ONE.divide(Binary128.ZERO,
        RoundingMode.TIES_TO_EVEN, dz);
    if (!infz.isInfinite() || !dz.contains(StatusFlags.DIVIDE_BY_ZERO)) {
      throw new AssertionError("div0");
    }
    if (UxOps.compare(Binary128.ONE, two, new StatusFlags()) >= 0) {
      throw new AssertionError("compare");
    }
    Unpacked u = UxOps.unpack(Binary128.ONE);
    Binary128 packed = UxOps.pack(u, RoundingMode.TIES_TO_EVEN,
        new StatusFlags());
    if (!packed.equals(Binary128.ONE)) {
      throw new AssertionError("unpack/pack");
    }
    System.out.println("Binary128Check: all tests passed");
  }
}
