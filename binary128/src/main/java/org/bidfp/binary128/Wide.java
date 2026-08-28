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

/** Unsigned 64/128/256-bit helpers for the DPML 64-bit UX digit path. */
final class Wide {
  private Wide() {
  }

  static long umulh(long x, long y) {
    long result = Math.multiplyHigh(x, y);
    if (x < 0) {
      result += y;
    }
    if (y < 0) {
      result += x;
    }
    return result;
  }

  static int cmp128(long aHi, long aLo, long bHi, long bLo) {
    int high = Long.compareUnsigned(aHi, bHi);
    return high != 0 ? high : Long.compareUnsigned(aLo, bLo);
  }

  static boolean add128(long aHi, long aLo, long bHi, long bLo, long[] out) {
    long lo = aLo + bLo;
    long carry = Long.compareUnsigned(lo, aLo) < 0 ? 1L : 0L;
    long hi = aHi + bHi + carry;
    boolean overflow = Long.compareUnsigned(aHi + bHi, aHi) < 0
        || (carry != 0L && Long.compareUnsigned(hi, aHi + bHi) < 0);
    out[0] = hi;
    out[1] = lo;
    return overflow;
  }

  static void sub128(long aHi, long aLo, long bHi, long bLo, long[] out) {
    long borrow = Long.compareUnsigned(aLo, bLo) < 0 ? 1L : 0L;
    out[0] = aHi - bHi - borrow;
    out[1] = aLo - bLo;
  }

  static void mul128x128(long aHi, long aLo, long bHi, long bLo, long[] out4) {
    long p0 = aLo * bLo;
    long p0h = umulh(aLo, bLo);
    long p1l = aLo * bHi;
    long p1h = umulh(aLo, bHi);
    long p2l = aHi * bLo;
    long p2h = umulh(aHi, bLo);
    long p3l = aHi * bHi;
    long p3h = umulh(aHi, bHi);

    long t1 = p0h;
    long c;
    t1 += p1l;
    c = Long.compareUnsigned(t1, p1l) < 0 ? 1L : 0L;
    long t2 = p1h + c;
    long t3 = Long.compareUnsigned(t2, p1h) < 0 ? 1L : 0L;

    t1 += p2l;
    c = Long.compareUnsigned(t1, p2l) < 0 ? 1L : 0L;
    long t2b = t2;
    t2 += p2h + c;
    t3 += Long.compareUnsigned(t2, t2b) < 0 ? 1L : 0L;

    t2b = t2;
    t2 += p3l;
    t3 += Long.compareUnsigned(t2, t2b) < 0 ? 1L : 0L;
    t3 += p3h;

    out4[0] = t3;
    out4[1] = t2;
    out4[2] = t1;
    out4[3] = p0;
  }

  static void shiftLeft128(long hi, long lo, int n, long[] out) {
    if (n <= 0) {
      out[0] = hi;
      out[1] = lo;
      return;
    }
    if (n >= 128) {
      out[0] = 0L;
      out[1] = 0L;
      return;
    }
    if (n >= 64) {
      out[0] = lo << (n - 64);
      out[1] = 0L;
      return;
    }
    out[0] = (hi << n) | (lo >>> (64 - n));
    out[1] = lo << n;
  }

  static long shiftRight128Sticky(long hi, long lo, int n, long[] out) {
    if (n <= 0) {
      out[0] = hi;
      out[1] = lo;
      return 0L;
    }
    if (n >= 128) {
      out[0] = 0L;
      out[1] = 0L;
      return (hi | lo) == 0L ? 0L : 1L;
    }
    if (n >= 64) {
      int s = n - 64;
      long lost = lo;
      if (s == 0) {
        out[0] = 0L;
        out[1] = hi;
        return lost == 0L ? 0L : 1L;
      }
      lost |= hi & ((1L << s) - 1L);
      out[0] = 0L;
      out[1] = hi >>> s;
      return lost == 0L ? 0L : 1L;
    }
    long lost = lo & ((1L << n) - 1L);
    out[0] = hi >>> n;
    out[1] = (hi << (64 - n)) | (lo >>> n);
    return lost == 0L ? 0L : 1L;
  }

  static BigInteger u128(long hi, long lo) {
    byte[] b = new byte[16];
    for (int i = 0; i < 8; i++) {
      b[i] = (byte) (hi >>> (56 - 8 * i));
      b[8 + i] = (byte) (lo >>> (56 - 8 * i));
    }
    return new BigInteger(1, b);
  }

  static void toU128(BigInteger v, long[] out) {
    byte[] mag = v.toByteArray();
    long hi = 0L;
    long lo = 0L;
    int n = mag.length;
    for (int i = 0; i < n; i++) {
      int bit = mag[n - 1 - i] & 0xff;
      if (i < 8) {
        lo |= ((long) bit) << (8 * i);
      } else if (i < 16) {
        hi |= ((long) bit) << (8 * (i - 8));
      }
    }
    out[0] = hi;
    out[1] = lo;
  }

  /**
   * {@code (a * 2^128) / b} for 128-bit fractions.
   * q[0] is the extra high bit (0 or 1); q[1]:q[2] is the 128-bit body.
   */
  static void divFrac128(
      long aHi, long aLo, long bHi, long bLo, long[] q, long[] rem) {
    BigInteger a = u128(aHi, aLo);
    BigInteger b = u128(bHi, bLo);
    BigInteger[] dr = a.shiftLeft(128).divideAndRemainder(b);
    BigInteger quot = dr[0];
    q[0] = quot.testBit(128) ? 1L : 0L;
    long[] t = new long[2];
    toU128(quot, t);
    q[1] = t[0];
    q[2] = t[1];
    toU128(dr[1], t);
    rem[0] = t[0];
    rem[1] = t[1];
  }
}
