package org.bidfp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Optional second oracle: Intel C {@code readtest} on this repo's vector file.
 * Skipped unless {@code INTEL_RDFP_HOME} points at an Intel RDFP tree.
 */
final class IntelNativeReadtestTest {
  @Test
  @EnabledIfEnvironmentVariable(named = "INTEL_RDFP_HOME", matches = ".+")
  void nativeReadtestReportsNoFail() throws Exception {
    Path vectors = IntelVectors.find();
    Path repo = vectors.getParent().getParent().getParent();
    Path script = repo.resolve("dev/run_intel_readtest.sh");
    ProcessBuilder builder = new ProcessBuilder(script.toString());
    builder.directory(repo.toFile());
    builder.redirectErrorStream(true);
    Process process = builder.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    boolean finished = process.waitFor(30, TimeUnit.MINUTES);
    if (!finished) {
      process.destroyForcibly();
      throw new AssertionError("native readtest timed out");
    }
    if (process.exitValue() != 0 || output.contains("FAIL")) {
      throw new AssertionError("native readtest failed:\n" + output);
    }
  }
}
