/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in LICENSE-INTEL
 * are met.
 */
package org.bidfp.binary128;

/** Shared Horner / integer helpers for DPML kernel families. */
final class KernelEval {
  private KernelEval() {
  }

  static Unpacked fromInt(long n) {
    Unpacked u = new Unpacked();
    if (n == 0L) {
      u.setZero(0);
      return u;
    }
    int sign = n < 0L ? Unpacked.UX_SIGN_BIT : 0;
    long mag = n < 0L ? -n : n;
    u.setNorm(sign, 64, mag, 0L);
    UxOps.normalize(u);
    return u;
  }

  static Unpacked unpack(Binary128 x) {
    return UxOps.unpack(x);
  }

  static Binary128 pack(Unpacked u, RoundingMode mode, StatusFlags status) {
    return UxOps.pack(u, mode, status);
  }

  static void add(Unpacked a, Unpacked b, Unpacked r, StatusFlags st) {
    UxOps.addsubUnpacked(a, b, r, st);
  }

  static void sub(Unpacked a, Unpacked b, Unpacked r, StatusFlags st) {
    Unpacked nb = b.copy();
    UxOps.negate(nb);
    UxOps.addsubUnpacked(a, nb, r, st);
  }

  static void mul(Unpacked a, Unpacked b, Unpacked r, StatusFlags st) {
    UxOps.mulUnpacked(a, b, r, st);
  }

  static void div(Unpacked a, Unpacked b, Unpacked r, StatusFlags st) {
    UxOps.divUnpacked(a, b, r, st);
  }

  /** exp(r) for |r| modest, Horner of Taylor series. */
  static void expSeries(Unpacked r, Unpacked out, StatusFlags st) {
    Unpacked term = fromInt(1);
    Unpacked acc = fromInt(1);
    Unpacked tmp = new Unpacked();
    Unpacked n = new Unpacked();
    for (int k = 1; k <= 28; k++) {
      mul(term, r, tmp, st);
      n.copyFrom(fromInt(k));
      div(tmp, n, term, st);
      add(acc, term, tmp, st);
      acc.copyFrom(tmp);
    }
    out.copyFrom(acc);
  }

  /** log(1+f) for |f| < 0.5, atanh-style series: 2((f/(2+f)) + (f/(2+f))^3/3+...) */
  static void log1pSeries(Unpacked f, Unpacked out, StatusFlags st) {
    Unpacked two = fromInt(2);
    Unpacked den = new Unpacked();
    Unpacked u = new Unpacked();
    Unpacked tmp = new Unpacked();
    add(two, f, den, st);
    div(f, den, u, st);
    Unpacked u2 = new Unpacked();
    mul(u, u, u2, st);
    Unpacked acc = u.copy();
    Unpacked p = u.copy();
    for (int k = 1; k <= 24; k++) {
      mul(p, u2, tmp, st);
      p.copyFrom(tmp);
      div(p, fromInt(2 * k + 1), tmp, st);
      add(acc, tmp, den, st);
      acc.copyFrom(den);
    }
    mul(acc, two, out, st);
  }

  static int roundToInt(Unpacked x) {
    if (x.klass != Unpacked.CLASS_NORM) {
      return 0;
    }
    Unpacked u = x.copy();
    UxOps.normalize(u);
    int shift = 128 - u.exponent;
    if (shift <= 0) {
      return u.sign != 0 ? Integer.MIN_VALUE / 2 : Integer.MAX_VALUE / 2;
    }
    long[] t = new long[2];
    Wide.shiftRight128Sticky(u.fracHi, u.fracLo, shift, t);
    int n = (int) t[1];
    if (u.sign != 0) {
      n = -n;
    }
    return n;
  }
}
