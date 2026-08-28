/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in LICENSE-INTEL
 * are met.
 */
package org.bidfp.binary128;

import java.math.BigInteger;

/**
 * Unpacked binary128 operations ported from Intel DPML {@code dpml_ux_ops.c},
 * {@code dpml_ux_ops_64.c}, and {@code dpml_ux_sqrt.c} (64-bit digit path).
 *
 * <p>{@code PACK} is generalized with the DPML {@code S/K/L/R} bit-vectors so
 * all five IEEE rounding modes are explicit arguments rather than host FPSR.
 */
public final class UxOps {
  private static final int F_EXP_WIDTH = 15;
  private static final int F_EXP_BIAS = 16383;
  private static final int CSHIFT = 64 - F_EXP_WIDTH;
  private static final int MIN_UNBIASED = 1 - F_EXP_BIAS;

  private UxOps() {
  }

  public static Unpacked unpack(Binary128 x) {
    Unpacked u = new Unpacked();
    unpackInto(x, u);
    return u;
  }

  static void unpackInto(Binary128 x, Unpacked u) {
    long high = x.highBits();
    long low = x.lowBits();
    int sign = (high & Binary128.MASK_SIGN) != 0L ? Unpacked.UX_SIGN_BIT : 0;
    int biased = x.biasedExponent();
    long fracHi = high & Binary128.MASK_FRACTION_HIGH;
    if (biased == 0x7fff) {
      if (fracHi == 0L && low == 0L) {
        u.setInf(sign);
        return;
      }
      boolean signaling = (high & 0x0000_8000_0000_0000L) == 0L;
      u.setNaN(signaling);
      u.sign = sign;
      u.fracHi = Unpacked.UX_MSB | (fracHi << F_EXP_WIDTH) | (low >>> CSHIFT);
      u.fracLo = low << F_EXP_WIDTH;
      return;
    }
    if (biased == 0) {
      if (fracHi == 0L && low == 0L) {
        u.setZero(sign);
        return;
      }
      long msd = (fracHi << F_EXP_WIDTH) | (low >>> CSHIFT);
      long lsd = low << F_EXP_WIDTH;
      u.setNorm(sign, MIN_UNBIASED + 1, msd, lsd);
      normalize(u);
      return;
    }
    long msd = Unpacked.UX_MSB | (fracHi << F_EXP_WIDTH) | (low >>> CSHIFT);
    long lsd = low << F_EXP_WIDTH;
    int uxExp = biased - F_EXP_BIAS + 1;
    u.setNorm(sign, uxExp, msd, lsd);
  }

  static int normalize(Unpacked u) {
    if (u.klass != Unpacked.CLASS_NORM) {
      return 0;
    }
    if (u.fracHi < 0) {
      return 0;
    }
    if (u.fracHi == 0L && u.fracLo == 0L) {
      u.setZero(u.sign);
      return 128;
    }
    int shift;
    if (u.fracHi != 0L) {
      shift = Long.numberOfLeadingZeros(u.fracHi);
    } else {
      shift = 64 + Long.numberOfLeadingZeros(u.fracLo);
    }
    long[] t = new long[2];
    Wide.shiftLeft128(u.fracHi, u.fracLo, shift, t);
    u.fracHi = t[0];
    u.fracLo = t[1];
    u.exponent -= shift;
    return shift;
  }

  public static Binary128 pack(Unpacked u, RoundingMode mode, StatusFlags status) {
    if (u.klass == Unpacked.CLASS_NAN) {
      if (u.signaling) {
        status.raise(StatusFlags.INVALID);
      }
      long fractionHigh = (u.fracHi >>> F_EXP_WIDTH)
          & Binary128.MASK_FRACTION_HIGH;
      long fractionLow = (u.fracHi << CSHIFT) | (u.fracLo >>> F_EXP_WIDTH);
      fractionHigh |= Binary128.QUIET_NAN_BIT;
      return Binary128.fromFields(
          u.sign != 0, 0x7fff, fractionHigh, fractionLow);
    }
    if (u.klass == Unpacked.CLASS_INF) {
      return u.sign != 0 ? Binary128.NEGATIVE_INFINITY : Binary128.POSITIVE_INFINITY;
    }
    if (u.klass == Unpacked.CLASS_ZERO) {
      return u.sign != 0 ? Binary128.NEGATIVE_ZERO : Binary128.ZERO;
    }
    BigInteger fraction = Wide.u128(u.fracHi, u.fracLo);
    return IeeeRound.binary128(
        u.sign != 0, fraction, BigInteger.ONE, u.exponent - 128, mode, status);
  }

  private static Binary128 exactAdd(
      Binary128 x,
      Binary128 y,
      boolean subtract,
      RoundingMode mode,
      StatusFlags status) {
    raiseDenormal(x, y, status);
    if (x.isNaN() || y.isNaN()) {
      return propagateNaN(x, y, status);
    }
    boolean yNegative = y.isSigned() ^ subtract;
    if (x.isInfinite() || y.isInfinite()) {
      if (x.isInfinite() && y.isInfinite() && x.isSigned() != yNegative) {
        status.raise(StatusFlags.INVALID);
        return Binary128.NAN;
      }
      if (x.isInfinite()) {
        return x;
      }
      return yNegative ? Binary128.NEGATIVE_INFINITY : Binary128.POSITIVE_INFINITY;
    }
    if (x.isZero() && y.isZero()) {
      boolean negative = x.isSigned() == yNegative
          ? x.isSigned()
          : mode == RoundingMode.TOWARD_NEGATIVE;
      return negative ? Binary128.NEGATIVE_ZERO : Binary128.ZERO;
    }

    IeeeRound.Finite a = IeeeRound.decode(x);
    IeeeRound.Finite b = IeeeRound.decode(y);
    int commonExponent = Math.min(a.exponent, b.exponent);
    BigInteger left = a.significand.shiftLeft(a.exponent - commonExponent);
    BigInteger right = b.significand.shiftLeft(b.exponent - commonExponent);
    if (a.negative) {
      left = left.negate();
    }
    if (yNegative) {
      right = right.negate();
    }
    BigInteger sum = left.add(right);
    if (sum.signum() == 0) {
      return mode == RoundingMode.TOWARD_NEGATIVE
          ? Binary128.NEGATIVE_ZERO : Binary128.ZERO;
    }
    return IeeeRound.binary128(
        sum.signum() < 0,
        sum.abs(),
        BigInteger.ONE,
        commonExponent,
        mode,
        status);
  }

  public static Binary128 add(
      Binary128 x, Binary128 y, RoundingMode mode, StatusFlags status) {
    return exactAdd(x, y, false, mode, status);
  }

  public static Binary128 sub(
      Binary128 x, Binary128 y, RoundingMode mode, StatusFlags status) {
    return exactAdd(x, y, true, mode, status);
  }

  private static Binary128 addsub(
      Binary128 x, Binary128 y, boolean subtract,
      RoundingMode mode, StatusFlags status) {
    Unpacked a = unpack(x);
    Unpacked b = unpack(y);
    if (subtract) {
      b.sign ^= Unpacked.UX_SIGN_BIT;
    }
    Unpacked r = new Unpacked();
    addsubUnpacked(a, b, r, status);
    return pack(r, mode, status);
  }

  static void addsubUnpacked(Unpacked a, Unpacked b, Unpacked r, StatusFlags status) {
    if (a.klass == Unpacked.CLASS_NAN || b.klass == Unpacked.CLASS_NAN) {
      propagateNaN(a, b, r, status);
      return;
    }
    if (a.klass == Unpacked.CLASS_INF && b.klass == Unpacked.CLASS_INF) {
      if (a.sign != b.sign) {
        status.raise(StatusFlags.INVALID);
        r.setNaN(false);
        return;
      }
      r.setInf(a.sign);
      return;
    }
    if (a.klass == Unpacked.CLASS_INF) {
      r.copyFrom(a);
      return;
    }
    if (b.klass == Unpacked.CLASS_INF) {
      r.copyFrom(b);
      return;
    }
    if (a.klass == Unpacked.CLASS_ZERO && b.klass == Unpacked.CLASS_ZERO) {
      if (a.sign != b.sign) {
        r.setZero(0);
      } else {
        r.setZero(a.sign);
      }
      return;
    }
    if (a.klass == Unpacked.CLASS_ZERO) {
      r.copyFrom(b);
      return;
    }
    if (b.klass == Unpacked.CLASS_ZERO) {
      r.copyFrom(a);
      return;
    }
    normalize(a);
    normalize(b);
    Unpacked x = a;
    Unpacked y = b;
    int sign = x.sign;
    if (x.exponent < y.exponent
        || (x.exponent == y.exponent && Wide.cmp128(x.fracHi, x.fracLo, y.fracHi, y.fracLo) < 0)) {
      x = b;
      y = a;
      sign = x.sign;
    }
    int shift = x.exponent - y.exponent;
    long[] t = new long[2];
    long sticky = Wide.shiftRight128Sticky(y.fracHi, y.fracLo, shift, t);
    long yHi = t[0];
    long yLo = t[1];
    boolean sameSign = a.sign == b.sign;
    if (x == b) {
      sameSign = a.sign == b.sign;
    }
    sameSign = (a.sign ^ b.sign) == 0;
    if (sameSign) {
      boolean ov = Wide.add128(x.fracHi, x.fracLo, yHi, yLo, t);
      r.sign = sign;
      r.klass = Unpacked.CLASS_NORM;
      r.signaling = false;
      if (ov) {
        sticky |= t[1] & 1L;
        r.fracHi = Unpacked.UX_MSB | (t[0] >>> 1);
        r.fracLo = (t[0] << 63) | (t[1] >>> 1);
        r.exponent = x.exponent + 1;
      } else {
        r.fracHi = t[0];
        r.fracLo = t[1];
        r.exponent = x.exponent;
      }
      if (sticky != 0L) {
        r.fracLo |= 1L;
      }
    } else {
      Wide.sub128(x.fracHi, x.fracLo, yHi, yLo, t);
      if (sticky != 0L) {
        long bbit = Long.compareUnsigned(t[1], 1L) < 0 ? 1L : 0L;
        t[1] -= 1L;
        t[0] -= bbit;
      }
      r.sign = sign;
      r.fracHi = t[0];
      r.fracLo = t[1];
      r.exponent = x.exponent;
      r.klass = Unpacked.CLASS_NORM;
      r.signaling = false;
      if (r.fracHi == 0L && r.fracLo == 0L) {
        r.setZero(0);
        return;
      }
      normalize(r);
    }
  }

  public static Binary128 mul(
      Binary128 x, Binary128 y, RoundingMode mode, StatusFlags status) {
    raiseDenormal(x, y, status);
    if (x.isNaN() || y.isNaN()) {
      return propagateNaN(x, y, status);
    }
    boolean negative = x.isSigned() ^ y.isSigned();
    if ((x.isInfinite() && y.isZero()) || (y.isInfinite() && x.isZero())) {
      status.raise(StatusFlags.INVALID);
      return Binary128.NAN;
    }
    if (x.isInfinite() || y.isInfinite()) {
      return negative ? Binary128.NEGATIVE_INFINITY : Binary128.POSITIVE_INFINITY;
    }
    if (x.isZero() || y.isZero()) {
      return negative ? Binary128.NEGATIVE_ZERO : Binary128.ZERO;
    }
    IeeeRound.Finite a = IeeeRound.decode(x);
    IeeeRound.Finite b = IeeeRound.decode(y);
    return IeeeRound.binary128(
        negative,
        a.significand.multiply(b.significand),
        BigInteger.ONE,
        a.exponent + b.exponent,
        mode,
        status);
  }

  static void mulUnpacked(Unpacked a, Unpacked b, Unpacked r, StatusFlags status) {
    if (a.klass == Unpacked.CLASS_NAN || b.klass == Unpacked.CLASS_NAN) {
      propagateNaN(a, b, r, status);
      return;
    }
    int sign = a.sign ^ b.sign;
    boolean aInf = a.klass == Unpacked.CLASS_INF;
    boolean bInf = b.klass == Unpacked.CLASS_INF;
    boolean aZero = a.klass == Unpacked.CLASS_ZERO;
    boolean bZero = b.klass == Unpacked.CLASS_ZERO;
    if ((aInf && bZero) || (bInf && aZero)) {
      status.raise(StatusFlags.INVALID);
      r.setNaN(false);
      return;
    }
    if (aInf || bInf) {
      r.setInf(sign);
      return;
    }
    if (aZero || bZero) {
      r.setZero(sign);
      return;
    }
    normalize(a);
    normalize(b);
    long[] p = new long[4];
    Wide.mul128x128(a.fracHi, a.fracLo, b.fracHi, b.fracLo, p);
    int exp = a.exponent + b.exponent;
    long hi;
    long lo;
    long sticky;
    if ((p[0] & Unpacked.UX_MSB) != 0L) {
      hi = p[0];
      lo = p[1];
      sticky = p[2] | p[3];
    } else {
      hi = (p[0] << 1) | (p[1] >>> 63);
      lo = (p[1] << 1) | (p[2] >>> 63);
      sticky = (p[2] << 1) | p[3];
      exp--;
    }
    if (sticky != 0L) {
      lo |= 1L;
    }
    r.setNorm(sign, exp, hi, lo);
  }

  public static Binary128 div(
      Binary128 x, Binary128 y, RoundingMode mode, StatusFlags status) {
    raiseDenormal(x, y, status);
    if (x.isNaN() || y.isNaN()) {
      return propagateNaN(x, y, status);
    }
    boolean negative = x.isSigned() ^ y.isSigned();
    if ((x.isInfinite() && y.isInfinite()) || (x.isZero() && y.isZero())) {
      status.raise(StatusFlags.INVALID);
      return Binary128.NAN;
    }
    if (y.isZero()) {
      status.raise(StatusFlags.DIVIDE_BY_ZERO);
      return negative ? Binary128.NEGATIVE_INFINITY : Binary128.POSITIVE_INFINITY;
    }
    if (x.isZero() || y.isInfinite()) {
      return negative ? Binary128.NEGATIVE_ZERO : Binary128.ZERO;
    }
    if (x.isInfinite()) {
      return negative ? Binary128.NEGATIVE_INFINITY : Binary128.POSITIVE_INFINITY;
    }
    IeeeRound.Finite a = IeeeRound.decode(x);
    IeeeRound.Finite b = IeeeRound.decode(y);
    return IeeeRound.binary128(
        negative,
        a.significand,
        b.significand,
        a.exponent - b.exponent,
        mode,
        status);
  }

  static void divUnpacked(Unpacked a, Unpacked b, Unpacked r, StatusFlags status) {
    if (a.klass == Unpacked.CLASS_NAN || b.klass == Unpacked.CLASS_NAN) {
      propagateNaN(a, b, r, status);
      return;
    }
    int sign = a.sign ^ b.sign;
    boolean aInf = a.klass == Unpacked.CLASS_INF;
    boolean bInf = b.klass == Unpacked.CLASS_INF;
    boolean aZero = a.klass == Unpacked.CLASS_ZERO;
    boolean bZero = b.klass == Unpacked.CLASS_ZERO;
    if (aInf && bInf) {
      status.raise(StatusFlags.INVALID);
      r.setNaN(false);
      return;
    }
    if (aZero && bZero) {
      status.raise(StatusFlags.INVALID);
      r.setNaN(false);
      return;
    }
    if (bZero) {
      status.raise(StatusFlags.DIVIDE_BY_ZERO);
      r.setInf(sign);
      return;
    }
    if (aZero) {
      r.setZero(sign);
      return;
    }
    if (aInf) {
      r.setInf(sign);
      return;
    }
    if (bInf) {
      r.setZero(sign);
      return;
    }
    normalize(a);
    normalize(b);
    long[] q = new long[3];
    long[] rem = new long[2];
    Wide.divFrac128(a.fracHi, a.fracLo, b.fracHi, b.fracLo, q, rem);
    int exp = a.exponent - b.exponent;
    long hi;
    long lo;
    if (q[0] != 0L) {
      hi = (q[0] << 63) | (q[1] >>> 1);
      lo = (q[1] << 63) | (q[2] >>> 1);
      exp++;
    } else {
      hi = q[1];
      lo = q[2];
    }
    if ((rem[0] | rem[1]) != 0L) {
      lo |= 1L;
    }
    if ((hi & Unpacked.UX_MSB) == 0L) {
      long[] t = new long[2];
      Wide.shiftLeft128(hi, lo, 1, t);
      hi = t[0];
      lo = t[1];
      exp--;
    }
    r.setNorm(sign, exp, hi, lo);
  }

  public static Binary128 sqrt(Binary128 x, RoundingMode mode, StatusFlags status) {
    raiseDenormal(x, null, status);
    if (x.isNaN()) {
      return quietNaN(x, status);
    }
    if (x.isZero()) {
      return x;
    }
    if (x.isSigned()) {
      status.raise(StatusFlags.INVALID);
      return Binary128.NAN;
    }
    if (x.isInfinite()) {
      return x;
    }
    return IeeeRound.sqrt(x, mode, status);
  }

  static void sqrtUnpacked(Unpacked a, Unpacked r, StatusFlags status) {
    if (a.klass == Unpacked.CLASS_NAN) {
      if (a.signaling) {
        status.raise(StatusFlags.INVALID);
      }
      r.setNaN(false);
      return;
    }
    if (a.klass == Unpacked.CLASS_ZERO) {
      r.copyFrom(a);
      return;
    }
    if (a.sign != 0) {
      status.raise(StatusFlags.INVALID);
      r.setNaN(false);
      return;
    }
    if (a.klass == Unpacked.CLASS_INF) {
      r.setInf(0);
      return;
    }
    normalize(a);
    int exp = a.exponent;
    java.math.BigInteger m = Wide.u128(a.fracHi, a.fracLo);
    if ((exp & 1) != 0) {
      m = m.shiftLeft(1);
      exp--;
    }
    BigInteger radicand = m.shiftLeft(128);
    BigInteger root = radicand.sqrt();
    boolean sticky = !root.multiply(root).equals(radicand);
    int outExp = exp / 2;
    long[] t = new long[2];
    if (root.bitLength() > 128) {
      sticky |= root.testBit(0);
      root = root.shiftRight(1);
      outExp++;
    }
    Wide.toU128(root, t);
    if (sticky) {
      t[1] |= 1L;
    }
    r.setNorm(0, outExp, t[0], t[1]);
    normalize(r);
  }

  public static int compare(Binary128 x, Binary128 y, StatusFlags status) {
    raiseDenormal(x, y, status);
    Unpacked a = unpack(x);
    Unpacked b = unpack(y);
    if (a.klass == Unpacked.CLASS_NAN || b.klass == Unpacked.CLASS_NAN) {
      status.raise(StatusFlags.INVALID);
      return 2;
    }
    if (a.klass == Unpacked.CLASS_ZERO && b.klass == Unpacked.CLASS_ZERO) {
      return 0;
    }
    if (a.sign != b.sign) {
      return a.sign != 0 ? -1 : 1;
    }
    int mag;
    if (a.klass != b.klass) {
      if (a.klass == Unpacked.CLASS_INF) {
        mag = 1;
      } else if (b.klass == Unpacked.CLASS_INF) {
        mag = -1;
      } else if (a.klass == Unpacked.CLASS_ZERO) {
        mag = -1;
      } else {
        mag = 1;
      }
    } else if (a.klass == Unpacked.CLASS_INF) {
      mag = 0;
    } else {
      mag = Integer.compare(a.exponent, b.exponent);
      if (mag == 0) {
        mag = Wide.cmp128(a.fracHi, a.fracLo, b.fracHi, b.fracLo);
      }
    }
    return a.sign != 0 ? -mag : mag;
  }

  static void propagateNaN(Unpacked a, Unpacked b, Unpacked r, StatusFlags status) {
    if (a.isSignalingNaN() || b.isSignalingNaN()) {
      status.raise(StatusFlags.INVALID);
    }
    r.setNaN(false);
  }

  private static Binary128 propagateNaN(
      Binary128 x, Binary128 y, StatusFlags status) {
    Binary128 selected;
    if (x.isSignalingNaN()) {
      selected = x;
    } else if (y.isSignalingNaN()) {
      selected = y;
    } else {
      selected = x.isNaN() ? x : y;
    }
    if (x.isSignalingNaN() || y.isSignalingNaN()) {
      status.raise(StatusFlags.INVALID);
    }
    return Binary128.fromRawBits(
        selected.highBits() | Binary128.QUIET_NAN_BIT, selected.lowBits());
  }

  private static Binary128 quietNaN(Binary128 x, StatusFlags status) {
    if (x.isSignalingNaN()) {
      status.raise(StatusFlags.INVALID);
    }
    return Binary128.fromRawBits(
        x.highBits() | Binary128.QUIET_NAN_BIT, x.lowBits());
  }

  private static void raiseDenormal(
      Binary128 x, Binary128 y, StatusFlags status) {
    if (x.isSubnormal() || (y != null && y.isSubnormal())) {
      status.raise(StatusFlags.DENORMAL);
    }
  }

  static void negate(Unpacked u) {
    if (u.klass == Unpacked.CLASS_NAN) {
      return;
    }
    u.sign ^= Unpacked.UX_SIGN_BIT;
  }

  static void abs(Unpacked u) {
    if (u.klass == Unpacked.CLASS_NAN) {
      return;
    }
    u.sign = 0;
  }
}
