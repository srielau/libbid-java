/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in LICENSE-INTEL
 * are met.
 */
package org.bidfp.binary128;

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
  private static final int F_PRECISION = 113;
  private static final int CSHIFT = 64 - F_EXP_WIDTH;
  private static final int PACK_EXTRA = 128 - F_PRECISION;
  private static final int MIN_UNBIASED = 1 - F_EXP_BIAS;
  private static final int MAX_UNBIASED = F_EXP_BIAS;
  private static final long QNAN_HIGH = 0x7fff_8000_0000_0000L;

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
      long high = QNAN_HIGH | (u.sign != 0 ? Binary128.MASK_SIGN : 0L);
      long payload = u.fracHi << 1 >>> 16;
      if (payload != 0L) {
        high |= payload & Binary128.MASK_FRACTION_HIGH;
      }
      return Binary128.fromRawBits(high, 0L);
    }
    if (u.klass == Unpacked.CLASS_INF) {
      return u.sign != 0 ? Binary128.NEGATIVE_INFINITY : Binary128.POSITIVE_INFINITY;
    }
    if (u.klass == Unpacked.CLASS_ZERO) {
      return u.sign != 0 ? Binary128.NEGATIVE_ZERO : Binary128.ZERO;
    }
    normalize(u);
    if (u.klass == Unpacked.CLASS_ZERO) {
      return u.sign != 0 ? Binary128.NEGATIVE_ZERO : Binary128.ZERO;
    }

    int unbiased = u.exponent - 1;
    long hi = u.fracHi;
    long lo = u.fracLo;
    long sticky = 0L;
    long[] t = new long[2];

    if (unbiased < MIN_UNBIASED) {
      int denormShift = MIN_UNBIASED - unbiased;
      sticky |= Wide.shiftRight128Sticky(hi, lo, denormShift, t);
      hi = t[0];
      lo = t[1];
      unbiased = MIN_UNBIASED - 1;
    }

    int extra = PACK_EXTRA;
    long roundMask = (1L << extra) - 1L;
    long roundBits = lo & roundMask;
    int lsbBit = extra;
    long lBit = (lo >>> lsbBit) & 1L;
    long rBit = extra == 0 ? 0L : (lo >>> (extra - 1)) & 1L;
    long kBit = sticky;
    if (extra >= 2) {
      long belowR = lo & ((1L << (extra - 1)) - 1L);
      if (belowR != 0L) {
        kBit = 1L;
      }
    }
    if (roundBits != 0L && extra == 1) {
      kBit |= sticky;
    }

    int s = u.sign != 0 ? 1 : 0;
    int index = (s << 3) | ((int) kBit << 2) | ((int) lBit << 1) | (int) rBit;
    boolean increment = ((mode.bitVector() >>> index) & 1) != 0;
    boolean inexact = (roundBits | sticky) != 0L;

    if (increment) {
      long addLo = 1L << extra;
      boolean ov = Wide.add128(hi, lo, 0L, addLo, t);
      hi = t[0];
      lo = t[1];
      if (ov) {
        hi = Unpacked.UX_MSB;
        lo = 0L;
        unbiased++;
      } else if ((hi & Unpacked.UX_MSB) == 0L && unbiased >= MIN_UNBIASED) {
        Wide.shiftRight128Sticky(hi, lo, 1, t);
        hi = t[0] | Unpacked.UX_MSB;
        lo = t[1];
        unbiased++;
      }
    }

    if (unbiased > MAX_UNBIASED) {
      status.raise(StatusFlags.OVERFLOW | StatusFlags.INEXACT);
      return overflowResult(u.sign != 0, mode);
    }

    boolean tiny = unbiased < MIN_UNBIASED;
    if (tiny || (unbiased == MIN_UNBIASED - 1)) {
      if ((hi | lo) == 0L) {
        if (inexact) {
          status.raise(StatusFlags.UNDERFLOW | StatusFlags.INEXACT);
        }
        return u.sign != 0 ? Binary128.NEGATIVE_ZERO : Binary128.ZERO;
      }
    }

    lo &= ~roundMask;
    if (inexact) {
      status.raise(StatusFlags.INEXACT);
    }

    int biased;
    if (unbiased < MIN_UNBIASED) {
      if (inexact) {
        status.raise(StatusFlags.UNDERFLOW);
      }
      if ((hi | lo) == 0L) {
        return u.sign != 0 ? Binary128.NEGATIVE_ZERO : Binary128.ZERO;
      }
      biased = 0;
    } else {
      biased = unbiased + F_EXP_BIAS;
    }

    if (biased >= 0x7fff) {
      status.raise(StatusFlags.OVERFLOW | StatusFlags.INEXACT);
      return overflowResult(u.sign != 0, mode);
    }

    long packedHi = (hi >>> F_EXP_WIDTH) & Binary128.MASK_FRACTION_HIGH;
    packedHi |= ((long) biased) << 48;
    if (u.sign != 0) {
      packedHi |= Binary128.MASK_SIGN;
    }
    long packedLo = (hi << CSHIFT) | (lo >>> F_EXP_WIDTH);
    return Binary128.fromRawBits(packedHi, packedLo);
  }

  private static Binary128 overflowResult(boolean negative, RoundingMode mode) {
    boolean toInf;
    switch (mode) {
      case TOWARD_ZERO:
        toInf = false;
        break;
      case TOWARD_NEGATIVE:
        toInf = negative;
        break;
      case TOWARD_POSITIVE:
        toInf = !negative;
        break;
      default:
        toInf = true;
        break;
    }
    if (toInf) {
      return negative ? Binary128.NEGATIVE_INFINITY : Binary128.POSITIVE_INFINITY;
    }
    return negative ? Binary128.NEGATIVE_MAX : Binary128.POSITIVE_MAX;
  }

  public static Binary128 add(
      Binary128 x, Binary128 y, RoundingMode mode, StatusFlags status) {
    return addsub(x, y, false, mode, status);
  }

  public static Binary128 sub(
      Binary128 x, Binary128 y, RoundingMode mode, StatusFlags status) {
    return addsub(x, y, true, mode, status);
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
    Unpacked a = unpack(x);
    Unpacked b = unpack(y);
    Unpacked r = new Unpacked();
    mulUnpacked(a, b, r, status);
    return pack(r, mode, status);
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
    Unpacked a = unpack(x);
    Unpacked b = unpack(y);
    Unpacked r = new Unpacked();
    divUnpacked(a, b, r, status);
    return pack(r, mode, status);
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
    Unpacked a = unpack(x);
    Unpacked r = new Unpacked();
    sqrtUnpacked(a, r, status);
    return pack(r, mode, status);
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
    java.math.BigInteger root = m.shiftLeft(128).sqrt();
    int outExp = exp / 2;
    long[] t = new long[2];
    if (root.bitLength() > 128) {
      root = root.shiftRight(1);
      outExp++;
    }
    Wide.toU128(root, t);
    r.setNorm(0, outExp, t[0], t[1]);
    normalize(r);
  }

  public static int compare(Binary128 x, Binary128 y, StatusFlags status) {
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
