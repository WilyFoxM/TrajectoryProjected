package ru.wilyfox.trajectory.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ThrowShotTest {
  private static final Vec3d FEET = new Vec3d(10.5, 64.0, -3.25);
  private static final Vec3d SPAWN = new Vec3d(0.0, 65.52, 0.0);

  private static ShooterState shooter(float yaw, float pitch, Vec3d moved, boolean onGround) {
    return new ShooterState(FEET, 1.62F, yaw, pitch, moved, onGround);
  }

  /** {@code Projectile.shootFromRotation} with vanilla's own math, before spread and movement. */
  private static Vec3d vanillaDirection(float yaw, float pitch, float yOffset) {
    float yRot = Mth.wrapDegrees(yaw);
    float xRot = Mth.clamp(Mth.wrapDegrees(pitch), -90.0F, 90.0F);
    float xd =
        -Mth.sin(yRot * (float) (Math.PI / 180.0)) * Mth.cos(xRot * (float) (Math.PI / 180.0));
    float yd = -Mth.sin((xRot + yOffset) * (float) (Math.PI / 180.0));
    float zd =
        Mth.cos(yRot * (float) (Math.PI / 180.0)) * Mth.cos(xRot * (float) (Math.PI / 180.0));
    Vec3 v = new Vec3(xd, yd, zd).normalize();
    return new Vec3d(v.x, v.y, v.z);
  }

  @Test
  void launchMatchesShootFromRotation() {
    for (ThrowShot.Item item : ThrowShot.Item.values()) {
      for (float pitch : new float[] {0.0F, -35.0F, 20.0F, 89.0F}) {
        Launch launch = ThrowShot.launch(shooter(-47.5F, pitch, Vec3d.ZERO, true), item);
        assertEquals(
            vanillaDirection(-47.5F, pitch, item.yOffset), launch.direction(), item + " " + pitch);
        assertEquals(item.power, launch.power());
        assertEquals(new Vec3d(FEET.x(), FEET.y() + 1.62F - 0.1F, FEET.z()), launch.spawn());
      }
    }
  }

  @Test
  void throwsPassTheShootersMovementOn() {
    Vec3d moved = new Vec3d(0.2, 0.3, -0.1);
    assertEquals(
        new Vec3d(0.2, 0.0, -0.1),
        ThrowShot.launch(shooter(0, 0, moved, true), ThrowShot.Item.ENDER_PEARL).inherited());
    assertEquals(
        moved,
        ThrowShot.launch(shooter(0, 0, moved, false), ThrowShot.Item.ENDER_PEARL).inherited());
  }

  @Test
  void gravityAndDragComeBeforeTheMove() {
    Vec3d v0 = new Vec3d(0.3, 0.4, 1.4);
    Trajectory t = ThrowableFlight.simulate(SPAWN, v0, TestWorld.empty(), 0.03, 3);
    Vec3d first = v0.add(0.0, -0.03, 0.0).scale(0.99F);
    assertEquals(SPAWN.add(first), t.points().get(1));
    Vec3d second = first.add(0.0, -0.03, 0.0).scale(0.99F);
    assertEquals(SPAWN.add(first).add(second), t.points().get(2));
  }

  @Test
  void terminalSpeedsFollowGravity() {
    TestWorld world = TestWorld.empty();
    world.minY = -1.0E9;
    double[][] cases = {{0.03, -2.97}, {0.05, -4.95}, {0.07, -6.93}};
    for (double[] c : cases) {
      Trajectory t = ThrowableFlight.simulate(SPAWN, Vec3d.ZERO, world, c[0], 3000);
      List<Vec3d> p = t.points();
      double step = p.get(p.size() - 1).y() - p.get(p.size() - 2).y();
      assertEquals(c[1], step, 1.0e-5, "gravity " + c[0]);
    }
  }

  @Test
  void waterSlowsFromTheTickAfterEntering() {
    TestWorld world = TestWorld.empty();
    world.water.add(new Aabb(-100, 0, -100, 100, 65.0, 100));
    Vec3d v0 = new Vec3d(0.0, -0.5, 1.0);
    Trajectory t = ThrowableFlight.simulate(new Vec3d(0, 65.3, 0), v0, world, 0.03, 4);
    List<Vec3d> p = t.points();
    Vec3d m1 = v0.add(0, -0.03, 0).scale(0.99F);
    Vec3d m2 = m1.add(0, -0.03, 0).scale(0.8F);
    assertEquals(p.get(0).add(m1), p.get(1));
    assertEquals(p.get(1).add(m2), p.get(2));
  }

  @Test
  void stopsExactlyOnTheBlock() {
    Vec3d v0 = new Vec3d(0.0, -0.4, 1.0);
    Trajectory t = ThrowableFlight.simulate(SPAWN, v0, TestWorld.floorAt(60.0), 0.03, 200);
    assertEquals(Trajectory.End.BLOCK, t.end());
    assertEquals(60.0, t.last().y(), 1.0e-9);
    assertEquals(t.impact(), t.last());
    Vec3d before = t.points().get(t.points().size() - 2);
    Vec3d along = t.impact().subtract(before);
    double cos = SpreadZone.dot(along, t.arrival()) / (along.length() * t.arrival().length());
    assertEquals(1.0, cos, 1.0e-9);
  }

  @Test
  void theOwnerIsIgnoredUntilTheThrowHasLeftIt() {
    // The thrower's box around the spawn: on the first tick the hit search still ignores it.
    TestWorld world = TestWorld.empty();
    world.entities.add(
        new WorldView.EntityTarget(
            1, Aabb.ofEntity(new Vec3d(0, 63.9, 0), 0.6F, 1.8F), new Vec3d(0, 63.9, 0), true));
    Trajectory t = ThrowableFlight.simulate(SPAWN, new Vec3d(0, 0.3, 1.5), world, 0.03, 20);
    assertTrue(t.end() != Trajectory.End.ENTITY);
    assertNull(t.entity());
  }

  @Test
  void aPearlZoneUsesTheThrowOrder() {
    Launch launch = ThrowShot.launch(shooter(0, -20, Vec3d.ZERO, true), ThrowShot.Item.ENDER_PEARL);
    Flight flight = ThrowShot.flight(ThrowShot.Item.ENDER_PEARL, 600);
    Trajectory t =
        flight.simulate(launch.spawn(), launch.nominalVelocity(), TestWorld.floorAt(64.0));
    SpreadZone.Zone zone = SpreadZone.compute(launch, flight, t.impact(), t.arrival());
    // The unspread path crosses the zone's plane at its center.
    Vec3d crossing =
        SpreadZone.crossing(
            launch.spawn(), launch.nominalVelocity(), flight, zone.center(), zone.direction());
    assertEquals(0.0, Math.sqrt(crossing.distanceToSqr(zone.center())), 1.0e-6);
  }
}
