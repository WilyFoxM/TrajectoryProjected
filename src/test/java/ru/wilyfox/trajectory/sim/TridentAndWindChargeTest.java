package ru.wilyfox.trajectory.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class TridentAndWindChargeTest {
  private static final Vec3d FEET = new Vec3d(10.5, 64.0, -3.25);
  private static final Vec3d MOVED = new Vec3d(0.2, 0.3, -0.1);

  private static ShooterState shooter(boolean onGround) {
    return new ShooterState(FEET, 1.62F, 33.0F, -12.0F, MOVED, onGround);
  }

  @Test
  void tridentNeedsTenTicks() {
    assertNull(TridentShot.launch(shooter(true), 9));
    assertNotNull(TridentShot.launch(shooter(true), 10));
  }

  @Test
  void tridentLaunchesLikeABowAtTwoAndAHalf() {
    Launch trident = TridentShot.launch(shooter(false), 25);
    Launch bow = BowShot.launch(shooter(false), 25);
    assertEquals(2.5F, trident.power());
    assertEquals(bow.direction(), trident.direction());
    assertEquals(bow.spawn(), trident.spawn());
    assertEquals(MOVED, trident.inherited());
  }

  @Test
  void tridentHardlySlowsInWater() {
    TestWorld world = TestWorld.empty();
    world.water.add(new Aabb(-100, 0, -100, 100, 65.0, 100));
    Vec3d v0 = new Vec3d(0.0, -0.4, 2.0);
    Vec3d start = new Vec3d(0, 65.2, 0);
    List<Vec3d> trident = ArrowFlight.simulate(start, v0, world, 4, 0.99F).points();
    List<Vec3d> arrow = ArrowFlight.simulate(start, v0, world, 4).points();
    // Tick 1 moves by v0 and ends in water. Tick 2 still moves by the velocity captured at its
    // start; its water inertia (instead of air drag) shapes the velocity of tick 3.
    Vec3d v1 = v0.scale(0.99F).add(0.0, -0.05, 0.0);
    Vec3d third = start.add(v0).add(v1);
    assertEquals(third, trident.get(2));
    assertEquals(third, arrow.get(2));
    assertEquals(third.add(v1.scale(0.99F).add(0.0, -0.05, 0.0)), trident.get(3));
    assertEquals(third.add(v1.scale(0.6F).add(0.0, -0.05, 0.0)), arrow.get(3));
  }

  @Test
  void windChargeSpawnsAtTheEyes() {
    Launch charge = WindChargeShot.launch(shooter(true));
    assertEquals(new Vec3d(FEET.x(), FEET.y() + 1.62F, FEET.z()), charge.spawn());
    assertEquals(BowShot.fromRotation(shooter(true), 0.0F, 1.5F).direction(), charge.direction());
    assertEquals(1.5F, charge.power());
    assertEquals(new Vec3d(0.2, 0.0, -0.1), charge.inherited());
  }

  @Test
  void windChargeFliesStraightThroughWater() {
    TestWorld world = TestWorld.empty();
    world.water.add(new Aabb(-100, 0, -100, 100, 100, 100));
    Vec3d v = new Vec3d(0.3, -0.2, 1.4);
    Vec3d start = new Vec3d(0, 70, 0);
    List<Vec3d> p = WindChargeFlight.simulate(start, v, world, 20).points();
    for (int i = 1; i < p.size(); i++) {
      assertEquals(0.0, Math.sqrt(p.get(i).distanceToSqr(start.add(v.scale(i)))), 1.0e-9);
    }
  }

  @Test
  void windChargeStopsOnTheBlockFace() {
    Vec3d v = new Vec3d(0.0, -0.5, 1.4);
    // Off the exact face: the test world's box clip, unlike the level's, misses a path that ends a
    // tick exactly on a face.
    Trajectory t =
        WindChargeFlight.simulate(new Vec3d(0, 66.3, 0), v, TestWorld.floorAt(64.0), 100);
    assertEquals(Trajectory.End.BLOCK, t.end());
    assertEquals(64.0, t.impact().y(), 1.0e-9);
    assertEquals(v, t.arrival());
  }

  @Test
  void windChargeBoxHangsBelowItsPosition() {
    Aabb box = WindChargeFlight.box(new Vec3d(1, 10, 1));
    assertEquals(10.0 - 0.15F, box.minY(), 1.0e-12);
    assertEquals(10.0 - 0.15F + 0.3125F, box.maxY(), 1.0e-12);
    assertEquals(0.3125F, box.maxX() - box.minX(), 1.0e-6);
  }
}
