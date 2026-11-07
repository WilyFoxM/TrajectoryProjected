package ru.wilyfox.trajectory.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FireworkFlightTest {
  private static final Vec3d SPAWN = new Vec3d(0.0, 65.47, 0.0);

  @Test
  void fliesStraightAtConstantSpeed() {
    Vec3d v = new Vec3d(0.3, -0.1, 1.55);
    Trajectory t = FireworkFlight.simulate(SPAWN, v, TestWorld.empty(), 1);
    List<Vec3d> p = t.points();
    for (int i = 1; i < p.size(); i++) {
      assertEquals(SPAWN.add(v.scale(i)).x(), p.get(i).x(), 1.0e-9);
      assertEquals(SPAWN.add(v.scale(i)).y(), p.get(i).y(), 1.0e-9);
      assertEquals(SPAWN.add(v.scale(i)).z(), p.get(i).z(), 1.0e-9);
    }
  }

  /**
   * {@code lifetime = 10 · flightCount + nextInt(6) + nextInt(7)}, explosion once {@code life >
   * lifetime}: the rocket surely makes 10 · flightCount + 1 moves and at most 10 · flightCount +
   * 12.
   */
  @Test
  void explodesWithinItsRandomLifetime() {
    for (int flightCount = 1; flightCount <= 4; flightCount++) {
      Trajectory t =
          FireworkFlight.simulate(SPAWN, new Vec3d(0, 0, 1.6), TestWorld.empty(), flightCount);
      assertEquals(Trajectory.End.EXPLODED, t.end());
      assertEquals(10 * flightCount + 12, t.ticks());
      assertEquals(10 * flightCount + 12 + 1, t.points().size());
      assertEquals(10 * flightCount + 1 + 1, t.certainPoints());
      assertEquals(10 * flightCount + 1, FireworkFlight.firstExplosionTick(flightCount));
      assertEquals(10 * flightCount + 12, FireworkFlight.lastExplosionTick(flightCount));
    }
  }

  @Test
  void stopsAtABlockAndKeepsTheImpact() {
    Vec3d v = new Vec3d(0.0, -1.0, 1.0);
    Trajectory t = FireworkFlight.simulate(SPAWN, v, TestWorld.floorAt(60.0), 3);
    assertEquals(Trajectory.End.BLOCK, t.end());
    assertEquals(60.0, t.impact().y(), 1.0e-9);
    assertEquals(t.impact(), t.last());
    assertEquals(v, t.arrival());
    assertEquals(Face.UP, t.face());
    // Hit before the earliest explosion: every point is certain.
    assertEquals(t.points().size(), t.certainPoints());
  }

  @Test
  void aHitAfterTheEarliestExplosionIsUncertain() {
    // flightCount 1: certain up to 11 moves; the floor is 15 moves away.
    Vec3d v = new Vec3d(0.0, -1.0, 0.0);
    Trajectory t = FireworkFlight.simulate(SPAWN, v, TestWorld.floorAt(SPAWN.y() - 15.5), 1);
    assertEquals(Trajectory.End.BLOCK, t.end());
    assertEquals(12, t.certainPoints());
    assertTrue(t.points().size() > t.certainPoints());
  }

  @Test
  void theZoneOfAStraightFlightGrowsLinearly() {
    Flight rocket = Flight.firework(3);
    Launch launch = new Launch(SPAWN, new Vec3d(0, 0, 1), CrossbowShot.FIREWORK_POWER, Vec3d.ZERO);
    double near = radius(launch, rocket, 8.0);
    double far = radius(launch, rocket, 32.0);
    assertEquals(4.0, far / near, 1.0e-6);
  }

  private static double radius(Launch launch, Flight flight, double distance) {
    Vec3d hit = SPAWN.add(0, 0, distance);
    SpreadZone.Zone zone = SpreadZone.compute(launch, flight, hit, new Vec3d(0, 0, 1));
    return Math.max(zone.axisU().length(), zone.axisV().length());
  }
}
