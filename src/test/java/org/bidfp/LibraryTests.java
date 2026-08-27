package org.bidfp;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/** Runs the dependency-free {@code main} test classes under JUnit 5. */
class LibraryTests {
  @Test
  void bid64() {
    Bid64Test.main(new String[0]);
  }

  @Test
  void bid64IntelVectors() {
    Bid64IntelVectorTest.main(new String[0]);
  }

  @Test
  void bid64Compare() {
    Bid64CompareTest.main(new String[0]);
  }

  @Test
  void bid64Conversion() {
    Bid64ConversionTest.main(new String[0]);
  }

  @Test
  void bid64Add() throws IOException {
    Bid64AddTest.main(new String[0]);
  }

  @Test
  void bid64Multiply() throws IOException {
    Bid64MultiplyTest.main(new String[0]);
  }

  @Test
  void bid64Divide() throws IOException {
    Bid64DivideTest.main(new String[0]);
  }

  @Test
  void bid64RawKernel() {
    Bid64RawKernelTest.main(new String[0]);
  }

  @Test
  void bid128() {
    Bid128Test.main(new String[0]);
  }

  @Test
  void bid128Add() throws IOException {
    Bid128AddTest.main(new String[0]);
  }

  @Test
  void bid128Multiply() throws IOException {
    Bid128MultiplyTest.main(new String[0]);
  }

  @Test
  void bid128Divide() throws IOException {
    Bid128DivideTest.main(new String[0]);
  }

  @Test
  void uint128() {
    UInt128Test.main(new String[0]);
  }
}
