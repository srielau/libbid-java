/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in LICENSE-INTEL
 * are met.
 */
package org.bidfp.binary128;

/** Hyperbolic functions via exp (DPML {@code dpml_ux_exp.c} sinh/cosh path). */
public final class DpmlHyper {
  private DpmlHyper() {
  }

  public static Binary128 sinh(Binary128 x, RoundingMode mode, StatusFlags st) {
    Binary128 e = DpmlExp.exp(x, mode, new StatusFlags());
    Binary128 ei = DpmlExp.exp(x.negate(), mode, new StatusFlags());
    Binary128 d = UxOps.sub(e, ei, mode, new StatusFlags());
    return UxOps.mul(d, org.bidfp.binary128.tables.IeeeConstants.HALF, mode, st);
  }

  public static Binary128 cosh(Binary128 x, RoundingMode mode, StatusFlags st) {
    Binary128 e = DpmlExp.exp(x, mode, new StatusFlags());
    Binary128 ei = DpmlExp.exp(x.negate(), mode, new StatusFlags());
    Binary128 s = UxOps.add(e, ei, mode, new StatusFlags());
    return UxOps.mul(s, org.bidfp.binary128.tables.IeeeConstants.HALF, mode, st);
  }

  public static Binary128 tanh(Binary128 x, RoundingMode mode, StatusFlags st) {
    return UxOps.div(sinh(x, mode, new StatusFlags()),
        cosh(x, mode, new StatusFlags()), mode, st);
  }
}
