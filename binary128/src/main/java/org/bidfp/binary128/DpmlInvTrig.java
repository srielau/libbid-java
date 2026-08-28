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

/** Inverse trig (DPML {@code dpml_ux_inv_trig.c}). */
public final class DpmlInvTrig {
  private DpmlInvTrig() {
  }

  public static Binary128 atan(Binary128 x, RoundingMode mode, StatusFlags st) {
    Unpacked a = UxOps.unpack(x);
    if (a.isNaN()) {
      if (a.signaling) {
        st.raise(StatusFlags.INVALID);
      }
      return Binary128.canonicalNaN(false);
    }
    if (a.isZero()) {
      return x;
    }
    if (a.isInfinite()) {
      Binary128 p = IeeeConstants.PI_2;
      return a.sign != 0 ? p.negate() : p;
    }
    boolean rec = false;
    Unpacked z = a.copy();
    UxOps.abs(z);
    Unpacked one = UxOps.unpack(Binary128.ONE);
    if (z.exponent > 1) {
      rec = true;
      Unpacked t = new Unpacked();
      KernelEval.div(one, z, t, st);
      z.copyFrom(t);
    }
    Unpacked z2 = new Unpacked();
    KernelEval.mul(z, z, z2, st);
    Unpacked acc = z.copy();
    Unpacked p = z.copy();
    Unpacked tmp = new Unpacked();
    for (int k = 1; k <= 24; k++) {
      KernelEval.mul(p, z2, tmp, st);
      p.copyFrom(tmp);
      KernelEval.div(p, KernelEval.fromInt(2 * k + 1), tmp, st);
      if ((k & 1) != 0) {
        UxOps.negate(tmp);
      }
      KernelEval.add(acc, tmp, p, st);
      acc.copyFrom(p);
      p.copyFrom(tmp);
      if ((k & 1) != 0) {
        UxOps.negate(p);
      }
    }
    if (rec) {
      Unpacked pi2 = UxOps.unpack(IeeeConstants.PI_2);
      KernelEval.sub(pi2, acc, tmp, st);
      acc.copyFrom(tmp);
    }
    acc.sign = a.sign;
    return UxOps.pack(acc, mode, st);
  }

  public static Binary128 asin(Binary128 x, RoundingMode mode, StatusFlags st) {
    if (x.isNaN()) {
      if (x.isSignalingNaN()) {
        st.raise(StatusFlags.INVALID);
      }
      return Binary128.canonicalNaN(false);
    }
    Binary128 x2 = UxOps.mul(x, x, mode, new StatusFlags());
    Binary128 oneM = UxOps.sub(Binary128.ONE, x2, mode, new StatusFlags());
    Binary128 s = UxOps.sqrt(oneM, mode, new StatusFlags());
    return atan(UxOps.div(x, s, mode, new StatusFlags()), mode, st);
  }

  public static Binary128 acos(Binary128 x, RoundingMode mode, StatusFlags st) {
    Binary128 a = asin(x, mode, new StatusFlags());
    return UxOps.sub(IeeeConstants.PI_2, a, mode, st);
  }
}
