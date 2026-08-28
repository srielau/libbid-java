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
 * Cube root (DPML {@code dpml_ux_cbrt.c}): Halley/Newton iteration on
 * unpacked values.
 */
public final class DpmlCbrt {
  private DpmlCbrt() {
  }

  public static Binary128 cbrt(Binary128 x, RoundingMode mode, StatusFlags st) {
    Unpacked a = UxOps.unpack(x);
    if (a.isNaN()) {
      if (a.signaling) {
        st.raise(StatusFlags.INVALID);
      }
      return Binary128.canonicalNaN(false);
    }
    if (a.isInfinite() || a.isZero()) {
      return x;
    }
    UxOps.normalize(a);
    int sign = a.sign;
    a.sign = 0;
    int exp = a.exponent;
    int q = exp / 3;
    int rem = exp - 3 * q;
    a.exponent = rem == 0 ? 1 : rem;
    Unpacked y = a.copy();
    y.exponent = 1;
    Unpacked tmp = new Unpacked();
    Unpacked y2 = new Unpacked();
    Unpacked three = KernelEval.fromInt(3);
    Unpacked two = KernelEval.fromInt(2);
    for (int i = 0; i < 10; i++) {
      KernelEval.mul(y, y, y2, st);
      KernelEval.div(a, y2, tmp, st);
      KernelEval.mul(y, two, y2, st);
      KernelEval.add(y2, tmp, y2, st);
      KernelEval.div(y2, three, y, st);
    }
    y.exponent += q - (y.exponent > 0 ? 0 : 0);
    y.exponent = y.exponent + q - 1;
    y.sign = sign;
    return UxOps.pack(y, mode, st);
  }
}
