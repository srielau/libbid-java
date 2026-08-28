/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in LICENSE-INTEL
 * are met.
 */
package org.bidfp.binary128;

/** Inverse hyperbolics (DPML {@code dpml_ux_inv_hyper.c}). */
public final class DpmlInvHyper {
  private DpmlInvHyper() {
  }

  public static Binary128 asinh(Binary128 x, RoundingMode mode, StatusFlags st) {
    Binary128 x2 = UxOps.mul(x, x, mode, new StatusFlags());
    Binary128 s = UxOps.sqrt(UxOps.add(x2, Binary128.ONE, mode, new StatusFlags()),
        mode, new StatusFlags());
    return DpmlLog.log(UxOps.add(x, s, mode, new StatusFlags()), mode, st);
  }

  public static Binary128 acosh(Binary128 x, RoundingMode mode, StatusFlags st) {
    Binary128 x2 = UxOps.mul(x, x, mode, new StatusFlags());
    Binary128 s = UxOps.sqrt(UxOps.sub(x2, Binary128.ONE, mode, new StatusFlags()),
        mode, new StatusFlags());
    return DpmlLog.log(UxOps.add(x, s, mode, new StatusFlags()), mode, st);
  }

  public static Binary128 atanh(Binary128 x, RoundingMode mode, StatusFlags st) {
    Binary128 num = UxOps.add(Binary128.ONE, x, mode, new StatusFlags());
    Binary128 den = UxOps.sub(Binary128.ONE, x, mode, new StatusFlags());
    Binary128 q = UxOps.div(num, den, mode, new StatusFlags());
    Binary128 lg = DpmlLog.log(q, mode, new StatusFlags());
    return UxOps.mul(lg, org.bidfp.binary128.tables.IeeeConstants.HALF, mode, st);
  }
}
