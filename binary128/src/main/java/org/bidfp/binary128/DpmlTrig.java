/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in LICENSE-INTEL
 * are met.
 */
package org.bidfp.binary128;

import org.bidfp.binary128.tables.IeeeConstants;

/**
 * Radian sin/cos/tan (DPML {@code dpml_ux_trig.c}). Decimal Payne-Hanek
 * moduli from {@code bid64_sin.c} are not used here.
 */
public final class DpmlTrig {
  private DpmlTrig() {
  }

  public static Binary128 sin(Binary128 x, RoundingMode mode, StatusFlags st) {
    Unpacked a = UxOps.unpack(x);
    if (specialTrig(a, st)) {
      return a.isNaN() ? Binary128.canonicalNaN(false) : Binary128.canonicalNaN(false);
    }
    if (a.isInfinite()) {
      st.raise(StatusFlags.INVALID);
      return Binary128.canonicalNaN(false);
    }
    if (a.isZero()) {
      return x;
    }
    int q = reduce(a, st);
    Unpacked s = new Unpacked();
    Unpacked c = new Unpacked();
    kernel(a, s, c, st);
    Unpacked r;
    switch (q & 3) {
      case 0:
        r = s;
        break;
      case 1:
        r = c;
        break;
      case 2:
        UxOps.negate(s);
        r = s;
        break;
      default:
        UxOps.negate(c);
        r = c;
        break;
    }
    return UxOps.pack(r, mode, st);
  }

  public static Binary128 cos(Binary128 x, RoundingMode mode, StatusFlags st) {
    Unpacked a = UxOps.unpack(x);
    if (a.isNaN()) {
      if (a.signaling) {
        st.raise(StatusFlags.INVALID);
      }
      return Binary128.canonicalNaN(false);
    }
    if (a.isInfinite()) {
      st.raise(StatusFlags.INVALID);
      return Binary128.canonicalNaN(false);
    }
    if (a.isZero()) {
      return Binary128.ONE;
    }
    int q = reduce(a, st);
    Unpacked s = new Unpacked();
    Unpacked c = new Unpacked();
    kernel(a, s, c, st);
    Unpacked r;
    switch (q & 3) {
      case 0:
        r = c;
        break;
      case 1:
        UxOps.negate(s);
        r = s;
        break;
      case 2:
        UxOps.negate(c);
        r = c;
        break;
      default:
        r = s;
        break;
    }
    return UxOps.pack(r, mode, st);
  }

  public static Binary128 tan(Binary128 x, RoundingMode mode, StatusFlags st) {
    Binary128 s = sin(x, mode, new StatusFlags());
    Binary128 c = cos(x, mode, new StatusFlags());
    return UxOps.div(s, c, mode, st);
  }

  private static boolean specialTrig(Unpacked a, StatusFlags st) {
    if (a.isNaN()) {
      if (a.signaling) {
        st.raise(StatusFlags.INVALID);
      }
      return true;
    }
    return false;
  }

  private static int reduce(Unpacked a, StatusFlags st) {
    Unpacked twoPi = UxOps.unpack(IeeeConstants.TWO_PI);
    Unpacked qv = new Unpacked();
    KernelEval.div(a, twoPi, qv, st);
    int n = KernelEval.roundToInt(qv);
    Unpacked ni = KernelEval.fromInt(n);
    Unpacked tmp = new Unpacked();
    KernelEval.mul(ni, twoPi, tmp, st);
    Unpacked r = new Unpacked();
    KernelEval.sub(a, tmp, r, st);
    Unpacked pi2 = UxOps.unpack(IeeeConstants.PI_2);
    KernelEval.div(r, pi2, qv, st);
    int q = KernelEval.roundToInt(qv);
    ni = KernelEval.fromInt(q);
    KernelEval.mul(ni, pi2, tmp, st);
    KernelEval.sub(r, tmp, a, st);
    a.copyFrom(a);
    return q;
  }

  private static void kernel(Unpacked z, Unpacked sinOut, Unpacked cosOut, StatusFlags st) {
    Unpacked z2 = new Unpacked();
    KernelEval.mul(z, z, z2, st);
    Unpacked term = KernelEval.fromInt(1);
    Unpacked s = z.copy();
    Unpacked c = KernelEval.fromInt(1);
    Unpacked tmp = new Unpacked();
    Unpacked accs = z.copy();
    Unpacked accc = KernelEval.fromInt(1);
    for (int k = 1; k <= 20; k++) {
      KernelEval.mul(term, z2, tmp, st);
      KernelEval.div(tmp, KernelEval.fromInt(2 * k * (2 * k - 1)), term, st);
      if ((k & 1) != 0) {
        UxOps.negate(term);
      }
      KernelEval.add(accc, term, tmp, st);
      accc.copyFrom(tmp);
    }
    term = z.copy();
    accs.copyFrom(z);
    Unpacked t = z.copy();
    for (int k = 1; k <= 20; k++) {
      KernelEval.mul(t, z2, tmp, st);
      KernelEval.div(tmp, KernelEval.fromInt((2 * k + 1) * (2 * k)), t, st);
      if ((k & 1) != 0) {
        UxOps.negate(t);
      }
      KernelEval.add(accs, t, tmp, st);
      accs.copyFrom(tmp);
      if ((k & 1) != 0) {
        UxOps.negate(t);
      }
    }
    sinOut.copyFrom(accs);
    cosOut.copyFrom(accc);
  }
}
