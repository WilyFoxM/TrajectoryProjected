package ru.wilyfox.trajectory.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FishingHookTest {
  private static final Flight HOOK = Flight.fishingHook(400);

  private static ShooterState standing(float yaw, float pitch) {
    return new ShooterState(new Vec3d(0.5, 64.0, 0.5), 1.62F, yaw, pitch, Vec3d.ZERO, true);
  }

  /** The {@code FishingHook} constructor, written out as vanilla has it. */
  private static Vec3d[] vanillaCast(float yaw, float pitch, double scale) {
    float yCos = VanillaMath.cos(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
    float ySin = VanillaMath.sin(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
    float xCos = -VanillaMath.cos(-pitch * (float) (Math.PI / 180.0));
    float xSin = VanillaMath.sin(-pitch * (float) (Math.PI / 180.0));
    Vec3d spawn = new Vec3d(0.5 - ySin * 0.3, 64.0 + 1.62F, 0.5 - yCos * 0.3);
    Vec3d movement = new Vec3d(-ySin, Math.clamp(-(xSin / xCos), -5.0F, 5.0F), -yCos);
    double dist = movement.length();
    double f = 0.6 / dist + scale;
    return new Vec3d[] {spawn, new Vec3d(movement.x() * f, movement.y() * f, movement.z() * f)};
  }

  @Test
  void theCastMatchesTheVanillaConstructor() {
    for (float yaw : new float[] {0.0F, 37.5F, -120.0F, 179.9F}) {
      for (float pitch : new float[] {-89.0F, -45.0F, -10.0F, 0.0F, 30.0F, 89.0F}) {
        Launch launch = FishingShot.launch(standing(yaw, pitch));
        Vec3d[] vanilla = vanillaCast(yaw, pitch, FishingShot.SCALE_CENTER);
        assertEquals(0.0, Math.sqrt(launch.spawn().distanceToSqr(vanilla[0])), 1.0e-12);
        assertEquals(0.0, Math.sqrt(launch.nominalVelocity().distanceToSqr(vanilla[1])), 1.0e-6);
        Vec3d high = vanillaCast(yaw, pitch, 0.51)[1];
        Vec3d scaled = FishingShot.velocity(launch, new Vec3d(0.51, 0.51, 0.51));
        assertEquals(0.0, Math.sqrt(scaled.distanceToSqr(high)), 1.0e-6);
      }
    }
  }

  @Test
  void theHookFallsWithGravityBeforeTheMoveAndInertiaAfter() {
    TestWorld world = TestWorld.floorAt(0.0);
    Vec3d spawn = new Vec3d(0.5, 70.0, 0.5);
    Vec3d v = new Vec3d(0.0, 0.2, 0.5);
    Trajectory path = HOOK.simulate(spawn, v, world);
    List<Vec3d> p = path.points();
    Vec3d first = v.add(0.0, -0.03, 0.0);
    assertEquals(0.0, Math.sqrt(p.get(1).distanceToSqr(spawn.add(first))), 1.0e-12);
    Vec3d second = first.scale(0.92).add(0.0, -0.03, 0.0);
    assertEquals(0.0, Math.sqrt(p.get(2).distanceToSqr(p.get(1).add(second))), 1.0e-12);
  }

  @Test
  void nearABlockTheSpeedBecomesTheSquaredDistance() {
    TestWorld world = TestWorld.floorAt(64.0);
    Trajectory path = HOOK.simulate(new Vec3d(0.5, 64.5, 0.5), new Vec3d(0, -0.9, 0), world);
    List<Vec3d> p = path.points();
    // Ray meets the floor 0.5 below: speed 0.25, then gravity: down 0.28, short of the floor.
    assertEquals(64.22, p.get(1).y(), 1.0e-9);
    // Next: 0.2576 down along the ray meets the floor 0.22 below: speed 0.0484, then gravity.
    assertEquals(64.22 - 0.0484 - 0.03, p.get(2).y(), 1.0e-9);
  }

  @Test
  void aCastOnTheGroundLiesOnTopOfIt() {
    TestWorld world = TestWorld.floorAt(64.0);
    Launch launch = FishingShot.launch(standing(0.0F, -20.0F));
    Trajectory path = HOOK.simulate(launch.spawn(), launch.nominalVelocity(), world);
    assertEquals(Trajectory.End.BLOCK, path.end());
    assertEquals(64.0, path.last().y(), 1.0e-9);
    assertTrue(path.last().z() > 3.0, "lands ahead: " + path.last());
  }

  @Test
  void aWallStopsTheHookAndItDropsDownAlongIt() {
    TestWorld world = TestWorld.floorAt(64.0);
    world.solids.add(new Aabb(-5, 64, 3, 5, 70, 4));
    Launch launch = FishingShot.launch(standing(0.0F, -10.0F));
    Trajectory path = HOOK.simulate(launch.spawn(), launch.nominalVelocity(), world);
    assertEquals(Trajectory.End.BLOCK, path.end());
    assertEquals(64.0, path.last().y(), 1.0e-9);
    assertEquals(3.0 - FishingHookFlight.SIZE / 2.0, path.last().z(), 1.0e-6);
  }

  @Test
  void aCastOnWaterFloatsAtTheSurfaceAfterDrifting() {
    TestWorld world = TestWorld.floorAt(60.0);
    world.solids.add(new Aabb(-50, 60, -50, 50, 64, 2));
    world.water.add(new Aabb(-50, 60, 2, 50, 63.5, 50));
    Launch launch = FishingShot.launch(standing(0.0F, -20.0F));
    Trajectory path = HOOK.simulate(launch.spawn(), launch.nominalVelocity(), world);
    assertEquals(Trajectory.End.FLOATING, path.end());
    assertEquals(63.5, path.last().y(), 1.0e-6);
    // The tick it enters the water it does not move; then it drifts on.
    List<Vec3d> p = path.points();
    int entry = 1;
    while (!p.get(entry).equals(p.get(entry - 1))) {
      entry++;
    }
    assertTrue(
        path.last().z() > p.get(entry).z() + 0.1,
        "drift from " + p.get(entry) + " to " + path.last());
  }

  @Test
  void aMobInTheWayIsHooked() {
    TestWorld world = TestWorld.floorAt(64.0);
    world.entities.add(TestWorld.player(7, new Vec3d(0.5, 64.0, 4.5), false));
    Launch launch = FishingShot.launch(standing(0.0F, 0.0F));
    Trajectory path = HOOK.simulate(launch.spawn(), launch.nominalVelocity(), world);
    assertEquals(Trajectory.End.ENTITY, path.end());
    assertEquals(7, path.entity().id());
  }

  @Test
  void movingIntoACornerSlidesAlongTheFreeAxis() {
    Aabb box = new Aabb(0, 0, 0, 1, 1, 1);
    List<Aabb> wall = List.of(new Aabb(-5, 0, 1.5, 5, 2, 2));
    Vec3d moved = FishingHookFlight.collide(box, new Vec3d(0.4, 0.0, 1.0), wall);
    assertEquals(0.4, moved.x(), 1.0e-12);
    assertEquals(0.5, moved.z(), 1.0e-12);
  }

  @Test
  void theRandomScaleMovesTheLandingLittle() {
    TestWorld world = TestWorld.floorAt(64.0);
    double lo = FishingShot.SCALE_CENTER - FishingShot.SCALE_HALF_WIDTH;
    double hi = FishingShot.SCALE_CENTER + FishingShot.SCALE_HALF_WIDTH;
    for (float pitch : new float[] {-45.0F, -20.0F, 0.0F, 20.0F}) {
      Launch launch = FishingShot.launch(standing(0.0F, pitch));
      Vec3d center = HOOK.simulate(launch.spawn(), launch.nominalVelocity(), world).last();
      double far = 0.0;
      for (int i = 0; i < 8; i++) {
        Vec3d scales =
            new Vec3d((i & 1) == 0 ? lo : hi, (i & 2) == 0 ? lo : hi, (i & 4) == 0 ? lo : hi);
        Vec3d end =
            HOOK.simulate(launch.spawn(), FishingShot.velocity(launch, scales), world).last();
        far = Math.max(far, Math.sqrt(end.distanceToSqr(center)));
      }
      assertTrue(far < 0.6, "spread " + far + " at pitch " + pitch);
    }
  }
}
