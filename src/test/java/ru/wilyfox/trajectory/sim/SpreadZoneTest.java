package ru.wilyfox.trajectory.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class SpreadZoneTest {
  private static final Vec3d FEET = new Vec3d(0.5, 64.0, 0.5);
  private static final Flight ARROW = Flight.arrow(600);

  private static Launch launch(float pitch) {
    return BowShot.launch(new ShooterState(FEET, 1.62F, 0.0F, pitch, Vec3d.ZERO, true), 20);
  }

  /** The predicted hit on a floor at y = 64, and the arrival direction there. */
  private static Object[] hitOnFloor(Launch launch) {
    Trajectory t =
        ArrowFlight.simulate(
            launch.spawn(), launch.nominalVelocity(), TestWorld.floorAt(64.0), 600);
    return new Object[] {t.impact(), t.arrival()};
  }

  @Test
  void everyVanillaShotLandsInsideTheZone() {
    for (float pitch : new float[] {5.0F, 0.0F, -10.0F, -30.0F}) {
      Launch launch = launch(pitch);
      Object[] h = hitOnFloor(launch);
      SpreadZone.Zone zone = SpreadZone.compute(launch, ARROW, (Vec3d) h[0], (Vec3d) h[1]);
      assertNotNull(zone, "pitch " + pitch);
      SplittableRandom random = new SplittableRandom(42);
      double s = Launch.SPREAD_HALF_WIDTH;
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
                launch.spawn(), launch.velocity(offset), ARROW, zone.center(), zone.direction());
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
      Launch launch = launch(pitch);
      Object[] h = hitOnFloor(launch);
      SpreadZone.Zone zone = SpreadZone.compute(launch, ARROW, (Vec3d) h[0], (Vec3d) h[1]);
      double max = 0.0;
      for (Vec3d corner : SpreadZone.offsets().subList(0, 8)) {
        Vec3d crossing =
            SpreadZone.crossing(
                launch.spawn(), launch.velocity(corner), ARROW, zone.center(), zone.direction());
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
    Launch launch = launch(-10.0F);
    Object[] h = hitOnFloor(launch);
    SpreadZone.Zone zone = SpreadZone.compute(launch, ARROW, (Vec3d) h[0], (Vec3d) h[1]);
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
    Launch launch = launch(-10.0F);
    Object[] h = hitOnFloor(launch);
    assertEquals(
        SpreadZone.compute(launch, ARROW, (Vec3d) h[0], (Vec3d) h[1]),
        SpreadZone.compute(launch, ARROW, (Vec3d) h[0], (Vec3d) h[1]));
    assertEquals(8 + SpreadZone.SAMPLES, SpreadZone.offsets().size());
  }

  @Test
  void blendIsStableAgainstFlippedAxes() {
    Launch launch = launch(-10.0F);
    Object[] h = hitOnFloor(launch);
    SpreadZone.Zone zone = SpreadZone.compute(launch, ARROW, (Vec3d) h[0], (Vec3d) h[1]);
    SpreadZone.Zone flipped =
        new SpreadZone.Zone(
            zone.center(),
            zone.axisU().scale(-1),
            zone.axisV().scale(-1),
            zone.direction(),
            zone.coreRatio(),
            zone.wideRatio());
    SpreadZone.Zone mid = SpreadZone.Zone.blend(zone, flipped, 0.5);
    assertEquals(zone.axisU().length(), mid.axisU().length(), 1.0e-9);
    assertEquals(zone.axisV().length(), mid.axisV().length(), 1.0e-9);
  }

  /** The rings hold their shares of shots they were not fitted on. */
  private static SpreadZone.Zone zoneAt(Vec3d center, Vec3d u, Vec3d v) {
    return new SpreadZone.Zone(center, u, v, new Vec3d(0, 0, 1), 0.5, 0.9);
  }

  @Test
  void aZoneGlidesOverSmallMovesAndJumpsOverLargeOnes() {
    Vec3d u = new Vec3d(0.5, 0, 0);
    Vec3d v = new Vec3d(0, 0.4, 0);
    SpreadZone.Zone zone = zoneAt(Vec3d.ZERO, u, v);
    assertTrue(zone.glidesTo(zoneAt(new Vec3d(0.9, 0, 0), u, v)));
    assertFalse(zone.glidesTo(zoneAt(new Vec3d(1.1, 0, 0), u, v)));
    // Reach grows with the radius: far zones move further per tick.
    Vec3d wideU = new Vec3d(2.0, 0, 0);
    Vec3d wideV = new Vec3d(0, 1.8, 0);
    SpreadZone.Zone wide = zoneAt(Vec3d.ZERO, wideU, wideV);
    assertTrue(wide.glidesTo(zoneAt(new Vec3d(3.9, 0, 0), wideU, wideV)));
    assertFalse(wide.glidesTo(zoneAt(new Vec3d(4.1, 0, 0), wideU, wideV)));
  }

  @Test
  void aZoneDoesNotGlideWhenItsAxesSwap() {
    // Blending axes a quarter turn apart would squash the ellipse halfway.
    Vec3d u = new Vec3d(0.5, 0, 0);
    Vec3d v = new Vec3d(0, 0.49, 0);
    SpreadZone.Zone zone = zoneAt(Vec3d.ZERO, u, v);
    assertFalse(zone.glidesTo(zoneAt(Vec3d.ZERO, new Vec3d(0, 0.5, 0), new Vec3d(-0.49, 0, 0))));
    assertTrue(zone.glidesTo(zoneAt(Vec3d.ZERO, u.scale(-1), v)));
  }

  @Test
  void waypointsOverlapAndEndAtBothZones() {
    Vec3d u = new Vec3d(0.4, 0, 0);
    Vec3d v = new Vec3d(0, 0.3, 0);
    Vec3d end = new Vec3d(0.8, 0, 0);
    List<SpreadZone.Zone> way = zoneAt(Vec3d.ZERO, u, v).waypoints(zoneAt(end, u, v));
    assertEquals(0.0, way.getFirst().center().x(), 1.0e-9);
    assertEquals(end.x(), way.getLast().center().x(), 1.0e-9);
    for (int i = 0; i + 1 < way.size(); i++) {
      double gap = way.get(i + 1).center().x() - way.get(i).center().x();
      assertTrue(gap > 0.0 && gap <= 0.15 + 1.0e-9, "gap " + gap);
    }
    // Not moving at all needs only the zone itself.
    assertEquals(2, zoneAt(end, u, v).waypoints(zoneAt(end, u, v)).size());
  }

  @Test
  void ringsHoldHalfAndNinetyPercent() {
    for (float pitch : new float[] {5.0F, -10.0F, -30.0F}) {
      Launch launch = launch(pitch);
      Object[] h = hitOnFloor(launch);
      SpreadZone.Zone zone = SpreadZone.compute(launch, ARROW, (Vec3d) h[0], (Vec3d) h[1]);
      double[] shares = shares(launch, zone, new SplittableRandom(7), 4000);
      assertEquals(0.5, shares[0], 0.05, "half ring at pitch " + pitch);
      assertEquals(0.9, shares[1], 0.04, "wide ring at pitch " + pitch);
      assertTrue(zone.coreRatio() < zone.wideRatio() && zone.wideRatio() <= 1.0);
    }
  }

  @Test
  void aPartialDrawCountedATickDifferentlyStaysInsideTheWidenedZone() {
    ShooterState shooter = new ShooterState(FEET, 1.62F, 0.0F, -10.0F, Vec3d.ZERO, true);
    int ticks = 12;
    Launch nominal = BowShot.launch(shooter, ticks);
    Trajectory t =
        ArrowFlight.simulate(
            nominal.spawn(), nominal.nominalVelocity(), TestWorld.floorAt(64.0), 600);
    SpreadZone.Zone plain = SpreadZone.compute(nominal, ARROW, t.impact(), t.arrival());
    SpreadZone.Zone widened =
        SpreadZone.compute(BowShot.tickVariants(shooter, ticks), ARROW, t.impact(), t.arrival());
    assertTrue(
        widened.axisU().length() * widened.axisV().length()
            > 1.5 * plain.axisU().length() * plain.axisV().length());
    SplittableRandom random = new SplittableRandom(11);
    double s = Launch.SPREAD_HALF_WIDTH;
    for (int neighbour : new int[] {ticks - 1, ticks + 1}) {
      Launch launch = BowShot.launch(shooter, neighbour);
      int inPlain = 0;
      for (int i = 0; i < 500; i++) {
        Vec3d offset =
            new Vec3d(
                s * (random.nextDouble() - random.nextDouble()),
                s * (random.nextDouble() - random.nextDouble()),
                s * (random.nextDouble() - random.nextDouble()));
        Vec3d crossing =
            SpreadZone.crossing(
                launch.spawn(),
                launch.velocity(offset),
                ARROW,
                widened.center(),
                widened.direction());
        double[] c = widened.coordinates(crossing);
        assertTrue(Math.hypot(c[0], c[1]) <= 1.0 + 1.0e-3, "tick " + neighbour);
        double[] p = plain.coordinates(crossing);
        if (Math.hypot(p[0], p[1]) <= 1.0) {
          inPlain++;
        }
      }
      assertTrue(inPlain < 250, "the plain zone should miss most of tick " + neighbour);
    }
  }

  @Test
  void tickVariantsLeaveOutWhatChangesNothing() {
    ShooterState shooter = new ShooterState(FEET, 1.62F, 0.0F, 0.0F, Vec3d.ZERO, true);
    assertEquals(3, BowShot.tickVariants(shooter, 12).size());
    assertEquals(2, BowShot.tickVariants(shooter, 20).size(), "no power above full draw");
    assertEquals(1, BowShot.tickVariants(shooter, 25).size(), "full draw either way");
    assertEquals(2, BowShot.tickVariants(shooter, 3).size(), "no shot one tick earlier");
    assertEquals(0, BowShot.tickVariants(shooter, 2).size(), "no shot at all");
  }

  private static double[] shares(Launch launch, SpreadZone.Zone zone, SplittableRandom r, int n) {
    double s = Launch.SPREAD_HALF_WIDTH;
    int half = 0;
    int wide = 0;
    for (int i = 0; i < n; i++) {
      Vec3d offset =
          new Vec3d(
              s * (r.nextDouble() - r.nextDouble()),
              s * (r.nextDouble() - r.nextDouble()),
              s * (r.nextDouble() - r.nextDouble()));
      Vec3d crossing =
          SpreadZone.crossing(
              launch.spawn(), launch.velocity(offset), ARROW, zone.center(), zone.direction());
      double[] c = zone.coordinates(crossing);
      double radius = Math.hypot(c[0], c[1]);
      if (radius <= zone.coreRatio()) {
        half++;
      }
      if (radius <= zone.wideRatio()) {
        wide++;
      }
    }
    return new double[] {half / (double) n, wide / (double) n};
  }

  private static double area(Launch launch) {
    Object[] h = hitOnFloor(launch);
    SpreadZone.Zone zone = SpreadZone.compute(launch, ARROW, (Vec3d) h[0], (Vec3d) h[1]);
    return zone.axisU().length() * zone.axisV().length();
  }
}
