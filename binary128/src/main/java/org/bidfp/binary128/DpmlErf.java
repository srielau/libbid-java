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

/** Error function family (DPML {@code dpml_ux_erf.c}). */
public final class DpmlErf {
  private DpmlErf() {
  }

  public static Binary128 erf(Binary128 x, RoundingMode mode, StatusFlags st) {
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
      return a.sign != 0 ? Binary128.ONE.negate() : Binary128.ONE;
    }
    Unpacked z2 = new Unpacked();
    KernelEval.mul(a, a, z2, st);
    Unpacked term = a.copy();
    Unpacked acc = a.copy();
    Unpacked tmp = new Unpacked();
    for (int n = 1; n <= 40; n++) {
      KernelEval.mul(term, z2, tmp, st);
      KernelEval.div(tmp, KernelEval.fromInt(n), term, st);
      Unpacked den = KernelEval.fromInt(2 * n + 1);
      KernelEval.div(term, den, tmp, st);
      if ((n & 1) != 0) {
        UxOps.negate(tmp);
      }
      KernelEval.add(acc, tmp, den, st);
      acc.copyFrom(den);
    }
    Unpacked two = KernelEval.fromInt(2);
    Unpacked pi = UxOps.unpack(IeeeConstants.PI);
    Unpacked sqrtPi = new Unpacked();
    UxOps.sqrtUnpacked(pi, sqrtPi, st);
    KernelEval.mul(acc, two, tmp, st);
    KernelEval.div(tmp, sqrtPi, acc, st);
    acc.sign = a.sign;
    return UxOps.pack(acc, mode, st);
  }

  public static Binary128 erfc(Binary128 x, RoundingMode mode, StatusFlags st) {
    return UxOps.sub(Binary128.ONE, erf(x, mode, new StatusFlags()), mode, st);
  }
}
