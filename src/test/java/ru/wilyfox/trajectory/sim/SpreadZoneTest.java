package ru.wilyfox.trajectory.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class SpreadZoneTest {
  private static final Vec3d FEET = new Vec3d(0.5, 64.0, 0.5);

  private static BowShot.Launch launch(float pitch) {
    return BowShot.launch(new ShooterState(FEET, 1.62F, 0.0F, pitch, Vec3d.ZERO, true), 20);
  }

  /** The predicted hit on a floor at y = 64, and the arrival direction there. */
  private static Object[] hitOnFloor(BowShot.Launch launch) {
    Trajectory t =
        ArrowFlight.simulate(
            launch.spawn(), launch.nominalVelocity(), TestWorld.floorAt(64.0), 600);
    return new Object[] {t.impact(), t.arrival()};
  }

  @Test
  void everyVanillaShotLandsInsideTheZone() {
    for (float pitch : new float[] {5.0F, 0.0F, -10.0F, -30.0F}) {
      BowShot.Launch launch = launch(pitch);
      Object[] h = hitOnFloor(launch);
      SpreadZone.Zone zone = SpreadZone.compute(launch, (Vec3d) h[0], (Vec3d) h[1], 600);
      assertNotNull(zone, "pitch " + pitch);
      SplittableRandom random = new SplittableRandom(42);
      double s = BowShot.SPREAD_HALF_WIDTH;
      int inside = 0;
      int total = 2000;
      for (int i = 0; i < total; i++) {
        Vec3d offset =
            new Vec3d(
                s * (random.nextDouble() - random.nextDouble()),
                s * (random.nextDouble() - random.nextDouble()),
                s * (random.nextDouble() - random.nextDouble()));
        Vec3d crossing =
            SpreadZone.crossing(
                launch.spawn(), launch.velocity(offset), zone.center(), zone.direction(), 600);
        double[] c = zone.coordinates(crossing);
        if (Math.hypot(c[0], c[1]) <= 1.0 + 1.0e-3) {
          inside++;
        }
      }
      assertEquals(total, inside, "pitch " + pitch);
    }
  }

  @Test
  void theSpreadCornersDefineTheEdge() {
    for (float pitch : new float[] {5.0F, -10.0F, -30.0F}) {
      BowShot.Launch launch = launch(pitch);
      Object[] h = hitOnFloor(launch);
      SpreadZone.Zone zone = SpreadZone.compute(launch, (Vec3d) h[0], (Vec3d) h[1], 600);
      double max = 0.0;
      for (Vec3d corner : SpreadZone.offsets().subList(0, 8)) {
        Vec3d crossing =
            SpreadZone.crossing(
                launch.spawn(), launch.velocity(corner), zone.center(), zone.direction(), 600);
        double[] c = zone.coordinates(crossing);
        double r = Math.hypot(c[0], c[1]);
        assertTrue(r <= 1.0 + 1.0e-9, "corner outside at pitch " + pitch + ": " + r);
        max = Math.max(max, r);
      }
      assertEquals(1.0, max, 1.0e-3, "no corner on the edge at pitch " + pitch);
    }
  }

  @Test
  void centerIsTheHitAndAxesLieInThePlane() {
    BowShot.Launch launch = launch(-10.0F);
    Object[] h = hitOnFloor(launch);
    SpreadZone.Zone zone = SpreadZone.compute(launch, (Vec3d) h[0], (Vec3d) h[1], 600);
    assertEquals(h[0], zone.center());
    assertEquals(0.0, SpreadZone.dot(zone.axisU(), zone.direction()), 1.0e-9);
    assertEquals(0.0, SpreadZone.dot(zone.axisV(), zone.direction()), 1.0e-9);
    assertEquals(0.0, SpreadZone.dot(zone.axisU(), zone.axisV()), 1.0e-9);
    assertEquals(1.0, zone.direction().length(), 1.0e-12);
    assertTrue(zone.coreRatio() > 0.0 && zone.coreRatio() <= 1.0);
  }

  @Test
  void zoneGrowsWithDistance() {
    double near = area(launch(5.0F));
    double far = area(launch(-10.0F));
    assertTrue(far > near * 2.0, "near " + near + " far " + far);
  }

  @Test
  void isDeterministic() {
    BowShot.Launch launch = launch(-10.0F);
    Object[] h = hitOnFloor(launch);
    assertEquals(
        SpreadZone.compute(launch, (Vec3d) h[0], (Vec3d) h[1], 600),
        SpreadZone.compute(launch, (Vec3d) h[0], (Vec3d) h[1], 600));
    assertEquals(8 + SpreadZone.SAMPLES, SpreadZone.offsets().size());
  }

  @Test
  void blendIsStableAgainstFlippedAxes() {
    BowShot.Launch launch = launch(-10.0F);
    Object[] h = hitOnFloor(launch);
    SpreadZone.Zone zone = SpreadZone.compute(launch, (Vec3d) h[0], (Vec3d) h[1], 600);
    SpreadZone.Zone flipped =
        new SpreadZone.Zone(
            zone.center(),
            zone.axisU().scale(-1),
            zone.axisV().scale(-1),
            zone.direction(),
            zone.coreRatio());
    SpreadZone.Zone mid = SpreadZone.Zone.blend(zone, flipped, 0.5);
    assertEquals(zone.axisU().length(), mid.axisU().length(), 1.0e-9);
    assertEquals(zone.axisV().length(), mid.axisV().length(), 1.0e-9);
  }

  private static double area(BowShot.Launch launch) {
    Object[] h = hitOnFloor(launch);
    SpreadZone.Zone zone = SpreadZone.compute(launch, (Vec3d) h[0], (Vec3d) h[1], 600);
    return zone.axisU().length() * zone.axisV().length();
  }
}
