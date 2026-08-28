package org.bidfp;

import java.io.IOException;

/** Intel readtest.in vectors for BID and DPD encoding conversion. */
public final class BidDpdVectorTest {
  private BidDpdVectorTest() {
  }

  public static void main(String[] args) throws IOException {
    int count64 = test64("bid_to_dpd64", true) + test64("bid_dpd_to_bid64", false);
    int count128 = test128("bid_to_dpd128", true) + test128("bid_dpd_to_bid128", false);
    System.out.printf(
        "BidDpdVectorTest: all tests passed (%d BID64, %d BID128 vectors)%n",
        count64, count128);
  }

  private static int test64(String operation, boolean toDpd) throws IOException {
    int tested = 0;
    for (String line : IntelVectors.lines(operation)) {
      String[] tokens = IntelVectors.tokens(line);
      long input = IntelVectors.hex64(tokens[2]);
      long expected = IntelVectors.hex64(tokens[3]);
      int expectedFlags = IntelVectors.flags(tokens[4]);
      long actual = toDpd ? Bid64Raw.toDpd(input) : Bid64Raw.fromDpd(input);
      if (actual != expected || expectedFlags != 0) {
        throw new AssertionError(String.format(
            "%s actual [0x%016x] 00", line, actual));
      }
      tested++;
    }
    return tested;
  }

  private static int test128(String operation, boolean toDpd) throws IOException {
    int tested = 0;
    for (String line : IntelVectors.lines(operation)) {
      String[] tokens = IntelVectors.tokens(line);
      long[] input = IntelVectors.hex128(tokens[2]);
      long[] expected = IntelVectors.hex128(tokens[3]);
      int expectedFlags = IntelVectors.flags(tokens[4]);
      long[] actual = new long[2];
      if (toDpd) {
        Bid128Raw.toDpd(input[0], input[1], actual);
      } else {
        Bid128Raw.fromDpd(input[0], input[1], actual);
      }
      if (actual[0] != expected[0] || actual[1] != expected[1] || expectedFlags != 0) {
        throw new AssertionError(String.format(
            "%s actual [0x%016x%016x] 00", line, actual[0], actual[1]));
      }
      tested++;
    }
    return tested;
  }
}
