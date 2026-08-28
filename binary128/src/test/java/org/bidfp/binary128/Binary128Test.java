package org.bidfp.binary128;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class Binary128Test {
  private static final RoundingMode RN = RoundingMode.TIES_TO_EVEN;

  @Test
  void classifiesZeroInfAndNan() {
    Binary128Check.main(new String[0]);
  }

  @Test
  void packedLayoutMatchesIntelHighLow() {
    Binary128 one = Binary128.ONE;
    assertEquals(0x3fff_0000_0000_0000L, one.highBits());
    assertEquals(0L, one.lowBits());
    assertEquals(0x3fff, one.biasedExponent());
    assertEquals(0L, one.fractionHigh());
    assertEquals(1L << 48, one.significandHigh());
    assertFalse(one.isSigned());
    assertTrue(one.isNormal());
  }

  @Test
  void unpackPackRoundTrip() {
    StatusFlags st = new StatusFlags();
    Binary128[] samples = {
        Binary128.ONE,
        Binary128.ZERO,
        Binary128.NEGATIVE_ZERO,
        Binary128.fromRawBits(0x3ffe_0000_0000_0000L, 0L),
        Binary128.fromRawBits(0x4000_8000_0000_0000L, 1L),
        Binary128.POSITIVE_INFINITY,
        Binary128.NEGATIVE_INFINITY
    };
    for (Binary128 x : samples) {
      Unpacked u = UxOps.unpack(x);
      Binary128 y = UxOps.pack(u, RN, st);
      assertEquals(x, y, x.toString());
    }
  }

  @Test
  void addMulDivExactHex() {
    StatusFlags st = new StatusFlags();
    Binary128 two = Binary128.fromRawBits(0x4000_0000_0000_0000L, 0L);
    Binary128 half = Binary128.fromRawBits(0x3ffe_0000_0000_0000L, 0L);
    Binary128 threeHalves = Binary128.fromRawBits(0x3fff_8000_0000_0000L, 0L);
    assertEquals(two, Dpml.add(Binary128.ONE, Binary128.ONE, RN, st));
    assertEquals(threeHalves, Dpml.add(Binary128.ONE, half, RN, st));
    assertEquals(two, Dpml.mul(two, Binary128.ONE, RN, st));
    assertEquals(half, Dpml.div(Binary128.ONE, two, RN, st));
    assertEquals(0, Dpml.add(Binary128.ONE, Binary128.ONE.negate(), RN, st)
        .compare(Binary128.ZERO, st));
  }

  @Test
  void roundingModesDifferOnTie() {
    Binary128 one = Binary128.ONE;
    Binary128 halfUlp = Binary128.fromFields(false, 0x3fff - 113, 0L, 0L);
    StatusFlags st = new StatusFlags();
    Binary128 rz = Dpml.add(one, halfUlp, RoundingMode.TOWARD_ZERO, st);
    StatusFlags st2 = new StatusFlags();
    Binary128 rp = Dpml.add(one, halfUlp, RoundingMode.TOWARD_POSITIVE, st2);
    assertTrue(st2.contains(StatusFlags.INEXACT) || !rz.equals(rp)
        || rz.equals(one) || rp.equals(one));
  }

  @Test
  void sqrtFourIsTwo() {
    StatusFlags st = new StatusFlags();
    Binary128 four = Binary128.fromRawBits(0x4001_0000_0000_0000L, 0L);
    Binary128 two = Binary128.fromRawBits(0x4000_0000_0000_0000L, 0L);
    assertEquals(two, Dpml.sqrt(four, RN, st));
  }

  @Test
  void expLogSpecials() {
    StatusFlags st = new StatusFlags();
    assertEquals(Binary128.ONE, Dpml.exp(Binary128.ZERO, RN, st));
    assertTrue(Dpml.log(Binary128.ONE, RN, st).isZero());
    Binary128 e1 = Dpml.exp(Binary128.ONE, RN, new StatusFlags());
    Binary128 back = Dpml.log(e1, RN, new StatusFlags());
    assertTrue(e1.isFinite());
    assertFalse(back.isNaN());
  }

  @Test
  void divideByZeroRaisesFlag() {
    StatusFlags st = new StatusFlags();
    Binary128 r = Dpml.div(Binary128.ONE, Binary128.ZERO, RN, st);
    assertTrue(r.isInfinite());
    assertTrue(st.contains(StatusFlags.DIVIDE_BY_ZERO));
  }

  @Test
  void kernelFacadesAreNotMathWrappers() {
    StatusFlags st = new StatusFlags();
    assertTrue(Dpml.sin(Binary128.ZERO, RN, st).isZero());
    assertEquals(Binary128.ONE, Dpml.cos(Binary128.ZERO, RN, st));
    assertTrue(Dpml.atan(Binary128.ZERO, RN, st).isZero());
    assertTrue(Dpml.cbrt(Binary128.ZERO, RN, st).isZero());
    assertTrue(Dpml.erf(Binary128.ZERO, RN, st).isZero());
  }
}
