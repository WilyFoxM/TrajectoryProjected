package ru.wilyfox.trajectory.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrajectoryConfigTest {
  @Test
  void missingFieldsGetTheirDefaults() {
    TrajectoryConfig config = TrajectoryConfig.decode("{\"schemaVersion\":1,\"dotSize\":150}");
    assertEquals(150, config.dotSize);
    assertEquals(100, config.dotSpacing);
    assertTrue(config.enabled);
    assertTrue(config.showHidden);
    assertTrue(config.crosshairTurn);
  }

  @Test
  void savedValuesAreKept() {
    TrajectoryConfig config =
        TrajectoryConfig.decode(
            "{\"schemaVersion\":1,\"enabled\":false,\"farDimming\":false,\"livingCross\":false}");
    assertFalse(config.enabled);
    assertFalse(config.farDimming);
    assertFalse(config.livingCross);
  }

  @Test
  void numbersAreClampedToTheirRange() {
    TrajectoryConfig low = TrajectoryConfig.decode("{\"dotSize\":1,\"dotSpacing\":-5}");
    assertEquals(TrajectoryConfig.MIN_DOT_SIZE, low.dotSize);
    assertEquals(TrajectoryConfig.MIN_DOT_SPACING, low.dotSpacing);
    TrajectoryConfig high = TrajectoryConfig.decode("{\"dotSize\":9000,\"dotSpacing\":9000}");
    assertEquals(TrajectoryConfig.MAX_DOT_SIZE, high.dotSize);
    assertEquals(TrajectoryConfig.MAX_DOT_SPACING, high.dotSpacing);
  }

  @Test
  void rejectsOtherSchemasAndNonObjects() {
    assertThrows(
        IllegalArgumentException.class, () -> TrajectoryConfig.decode("{\"schemaVersion\":2}"));
    assertThrows(IllegalArgumentException.class, () -> TrajectoryConfig.decode("[1,2]"));
  }

  @Test
  void snapshotRoundTrips() {
    TrajectoryConfig.defaults();
    TrajectoryConfig.get().dotSize = 135;
    TrajectoryConfig.get().showHidden = false;
    String snapshot = TrajectoryConfig.snapshot();
    TrajectoryConfig.defaults();
    TrajectoryConfig.restoreSnapshot(snapshot);
    assertEquals(135, TrajectoryConfig.get().dotSize);
    assertFalse(TrajectoryConfig.get().showHidden);
    TrajectoryConfig.defaults();
  }

  @Test
  void zoneOptionsKeepTheDefaultLook() {
    TrajectoryConfig config = new TrajectoryConfig();
    assertTrue(config.spreadZone && config.zoneFull && config.zoneHalfRing);
    assertTrue(!config.zoneWideRing && !config.zoneTickCorrection);
    assertTrue(config.showsZone());
    config.zoneFull = false;
    config.zoneHalfRing = false;
    assertTrue(!config.showsZone());
    config.zoneWideRing = true;
    assertTrue(config.showsZone());
    config.spreadZone = false;
    assertTrue(!config.showsZone());
  }
}
