package ru.wilyfox.trajectory.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ArrowFlightTest {
  private static final Vec3d SPAWN = new Vec3d(0.0, 65.52, 0.0);

  @Test
  void firstTickMovesByTheLaunchVelocity() {
    Vec3d v0 = new Vec3d(0.3, 0.4, 2.9);
    Trajectory t = ArrowFlight.simulate(SPAWN, v0, TestWorld.empty(), 2);
    assertEquals(SPAWN.add(v0), t.points().get(1));
    Vec3d v1 = v0.scale(0.99F).add(0.0, -0.05, 0.0);
    assertEquals(SPAWN.add(v0).add(v1), t.points().get(2));
  }

  @Test
  void dragThenGravityGiveTerminalSpeedOfFive() {
    TestWorld world = TestWorld.empty();
    world.minY = -1.0E9;
    Trajectory t = ArrowFlight.simulate(SPAWN, Vec3d.ZERO, world, 3000);
    List<Vec3d> p = t.points();
    double lastStep = p.get(p.size() - 1).y() - p.get(p.size() - 2).y();
    assertEquals(Trajectory.End.MAX_TICKS, t.end());
    assertEquals(-0.05 / (1.0 - 0.99F), lastStep, 1.0E-6);
  }

  @Test
  void fallsIntoTheVoidBelowMinYMinus64() {
    Trajectory t = ArrowFlight.simulate(SPAWN, Vec3d.ZERO, TestWorld.empty(), 3000);
    assertEquals(Trajectory.End.VOID, t.end());
    assertTrue(t.last().y() < -64.0 - 64.0);
  }

  @Test
  void stopsAtTheTickLimit() {
    Trajectory t = ArrowFlight.simulate(SPAWN, new Vec3d(0, 0, 1), TestWorld.empty(), 5);
    assertEquals(Trajectory.End.MAX_TICKS, t.end());
    assertEquals(6, t.points().size());
  }

  @Test
  void blockHitBacksOffAlongEachAxis() {
    TestWorld world = TestWorld.floorAt(64.0);
    Trajectory t = ArrowFlight.simulate(SPAWN, new Vec3d(0.0, -1.0, 2.0), world, 100);
    assertEquals(Trajectory.End.BLOCK, t.end());
    assertEquals(Face.UP, t.face());
    Vec3d rest = t.last();
    assertEquals(64.0 + 0.05F, rest.y(), 1.0E-12);
    assertEquals(0.0, rest.x());
  }

  @Test
  void waterSlowsFromTheNextTickAndSkipsAirDrag() {
    TestWorld world = TestWorld.empty();
    world.water.add(new Aabb(-10, 0, 0.5, 10, 200, 100));
    Vec3d v0 = new Vec3d(0.0, 0.0, 1.0);
    Trajectory t = ArrowFlight.simulate(SPAWN, v0, world, 3);
    List<Vec3d> p = t.points();
    // Tick 1 starts out of water: full step, then air drag and gravity.
    Vec3d v1 = v0.scale(0.99F).add(0, -0.05, 0);
    assertEquals(SPAWN.add(v0), p.get(1));
    // Tick 2 starts in water: the move still uses the velocity captured before inertia.
    assertEquals(p.get(1).add(v1), p.get(2));
    // Tick 3 moves by v1 * 0.6 with gravity and no air drag.
    Vec3d v2 = v1.scale(0.6F).add(0, -0.05, 0);
    assertEquals(p.get(2).add(v2), p.get(3));
  }

  @Test
  void entityMarginGrowsWithTicks() {
    assertEquals(0.0F, ArrowFlight.margin(1));
    assertEquals(0.0F, ArrowFlight.margin(2));
    assertEquals(0.05F, ArrowFlight.margin(3));
    assertEquals(0.3F, ArrowFlight.margin(8));
    assertEquals(0.3F, ArrowFlight.margin(200));
  }

  @Test
  void hitsAnEntityInThePath() {
    TestWorld world = TestWorld.floorAt(64.0);
    world.entities.add(TestWorld.player(7, new Vec3d(0.0, 64.0, 12.0), false));
    Trajectory t = ArrowFlight.simulate(SPAWN, new Vec3d(0.0, 0.0, 3.0), world, 100);
    assertEquals(Trajectory.End.ENTITY, t.end());
    assertEquals(7, t.entity().id());
    assertEquals(12.0 - 0.3F, t.last().z(), 1.0E-9);
  }

  @Test
  void nearMissIsIgnoredWhileTheMarginIsZero() {
    // The path runs 0.1 beside the box edge and passes it on the first tick, when the margin is 0.
    TestWorld world = TestWorld.empty();
    world.entities.add(TestWorld.player(1, new Vec3d(0.0, 65.0, 2.0), false));
    Trajectory t = ArrowFlight.simulate(new Vec3d(0.4, 66.5, 0.0), new Vec3d(0, 0, 3), world, 12);
    assertEquals(Trajectory.End.MAX_TICKS, t.end());
  }

  @Test
  void nearMissIsCaughtOnceTheMarginHasGrown() {
    // Same offset, but the box is reached around tick 9, when the margin is 0.3.
    TestWorld world = TestWorld.empty();
    world.entities.add(TestWorld.player(1, new Vec3d(0.0, 63.5, 26.0), false));
    Trajectory t = ArrowFlight.simulate(new Vec3d(0.4, 66.5, 0.0), new Vec3d(0, 0, 3), world, 12);
    assertEquals(Trajectory.End.ENTITY, t.end());
    assertTrue(t.ticks() >= 8);
  }

  @Test
  void passesThroughTheShootersMountWhileOverlapping() {
    // A rider shoots steeply down past the horse it sits on: the horse shares the shooter's root
    // vehicle and is ignored until the arrow has left the group's range.
    TestWorld world = TestWorld.floorAt(62.0);
    Aabb horse = new Aabb(-0.7, 63.0, -0.7, 0.7, 64.6, 0.7);
    world.entities.add(new WorldView.EntityTarget(2, horse, new Vec3d(0, 63, 0), true));
    Trajectory t = ArrowFlight.simulate(new Vec3d(0, 65.5, 0), new Vec3d(0, -1.0, 0.5), world, 20);
    assertEquals(Trajectory.End.BLOCK, t.end());

    TestWorld stranger = TestWorld.floorAt(62.0);
    stranger.entities.add(new WorldView.EntityTarget(3, horse, new Vec3d(0, 63, 0), false));
    Trajectory hit =
        ArrowFlight.simulate(new Vec3d(0, 65.5, 0), new Vec3d(0, -1.0, 0.5), stranger, 20);
    assertEquals(Trajectory.End.ENTITY, hit.end());
  }

  @Test
  void canHitTheShooterAfterLeaving() {
    TestWorld world = TestWorld.floorAt(64.0);
    Vec3d feet = new Vec3d(0.0, 64.0, 0.0);
    world.entities.add(TestWorld.player(1, feet, true));
    Trajectory t =
        ArrowFlight.simulate(new Vec3d(0.0, 65.52, 0.0), new Vec3d(0, 1.2, 0), world, 200);
    assertEquals(Trajectory.End.ENTITY, t.end());
    assertEquals(1, t.entity().id());
  }

  @Test
  void stopsAtUnloadedTerrain() {
    TestWorld world = TestWorld.floorAt(64.0);
    world.unloadedBeyondZ = 20.0;
    Trajectory t = ArrowFlight.simulate(SPAWN, new Vec3d(0.0, 0.3, 3.0), world, 100);
    assertEquals(Trajectory.End.UNKNOWN, t.end());
    assertTrue(t.last().z() <= 20.0);
  }

  @Test
  void stopsWhenStartingInsideABlock() {
    TestWorld world = TestWorld.floorAt(64.0);
    Trajectory t = ArrowFlight.simulate(new Vec3d(0, 63.5, 0), new Vec3d(0, 1, 1), world, 10);
    assertEquals(Trajectory.End.STUCK, t.end());
    assertEquals(1, t.points().size());
  }

  @Test
  void fullBowShotOnFlatGround() {
    ShooterState shooter =
        new ShooterState(new Vec3d(0.5, 64.0, 0.5), 1.62F, 0.0F, -10.0F, Vec3d.ZERO, true);
    BowShot.Launch launch = BowShot.launch(shooter, 20);
    Trajectory t =
        ArrowFlight.simulate(
            launch.spawn(), launch.nominalVelocity(), TestWorld.floorAt(64.0), 600);
    assertEquals(Trajectory.End.BLOCK, t.end());
    assertEquals(Face.UP, t.face());
    // Model sanity, not an in-game measurement: a 10 degree full draw lands about 63 blocks out.
    assertEquals(63.2, t.last().z(), 0.5);
    assertEquals(24, t.ticks());
  }

  /**
   * The impact and the final movement come out of the flight itself, so a hit just past a tick
   * boundary still has its true direction; the last two points do not, because of the backoff.
   */
  @Test
  void impactAndArrivalSurviveAShortFinalStep() {
    Vec3d v0 = new Vec3d(0.4, 0.2, 2.9);
    Trajectory free = ArrowFlight.simulate(SPAWN, v0, TestWorld.empty(), 40);
    List<Vec3d> p = free.points();
    int k = 20;
    Vec3d before = p.get(k);
    Vec3d step = p.get(k + 1).subtract(before);
    // A floor 0.02 below the point that starts tick k + 1: the final step is a sliver.
    double floor = before.y() - 0.02;
    Trajectory t = ArrowFlight.simulate(SPAWN, v0, TestWorld.floorAt(floor), 40);

    assertEquals(Trajectory.End.BLOCK, t.end());
    assertEquals(floor, t.impact().y(), 1.0e-9);
    assertEquals(0.0, t.arrival().subtract(step).length(), 1.0e-12);
    Vec3d sliver = t.impact().subtract(before);
    assertTrue(sliver.length() < 0.1, "final step " + sliver.length());
    assertEquals(0.0, angle(sliver, step), 1.0e-6);
    // The rest point is backed off by 0.05 per axis against the motion.
    Vec3d backoff =
        new Vec3d(Math.signum(step.x()), Math.signum(step.y()), Math.signum(step.z())).scale(0.05F);
    assertEquals(t.impact().subtract(backoff), t.last());
    // ... so the direction from the last two points is far off.
    Vec3d fromPoints = t.last().subtract(p.get(k));
    assertTrue(angle(fromPoints, step) > 30.0, "angle " + angle(fromPoints, step));
  }

  @Test
  void flightsWithoutAHitHaveNoImpact() {
    Trajectory t = ArrowFlight.simulate(SPAWN, Vec3d.ZERO, TestWorld.empty(), 3000);
    assertEquals(null, t.impact());
    assertEquals(null, t.arrival());
  }

  private static double angle(Vec3d a, Vec3d b) {
    double c = (a.x() * b.x() + a.y() * b.y() + a.z() * b.z()) / (a.length() * b.length());
    return Math.toDegrees(Math.acos(Math.clamp(c, -1.0, 1.0)));
  }
}
