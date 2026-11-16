package ru.wilyfox.trajectory.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ru.wilyfox.trajectory.sim.compat.VanillaRules;

/**
 * {@code ProjectileUtil.getEntityHitResult} with a margin, as throwables, fireworks, the fishing
 * hook and the wind charge use it.
 */
class NearestEntityHitTest {
  // Widths and the margin are floats, widened to double as in vanilla.
  private static final double EPS = 1.0E-6;
  private static final float MARGIN = 0.3F;
  private static final Aabb AREA = new Aabb(-10, -10, -10, 10, 10, 10);

  @Test
  void locationIsWhereTheSegmentEntersTheInflatedBox() {
    TestWorld world = TestWorld.empty();
    world.entities.add(TestWorld.player(1, new Vec3d(0, 0, 0), false));
    Vec3d from = new Vec3d(0, 1, -5);
    Vec3d to = new Vec3d(0, 1, 5);

    ArrowFlight.Hit hit = ArrowFlight.nearestEntityHit(world, from, to, AREA, MARGIN, false);

    assertNotNull(hit);
    assertEquals(1, hit.target().id());
    assertEquals(-0.6, hit.location().z(), EPS);
    assertEquals(0.0, hit.location().x(), EPS);
    assertEquals(1.0, hit.location().y(), EPS);
    // Arrows report the entry into the real box instead.
    ArrowFlight.Hit arrow =
        ArrowFlight.entityHits(
                world, from, to, AREA, MARGIN, false, VanillaRules.ARROW_MARGIN_HIT_ON_SURFACE)
            .getFirst();
    assertEquals(-0.3, arrow.location().z(), EPS);
  }

  @Test
  void marginHitCountsWithoutTheRayToTheCenter() {
    TestWorld world = TestWorld.empty();
    world.entities.add(TestWorld.player(1, new Vec3d(0, 0, 0), false));
    // A wall between the path and the box: the ray from the margin hit to the center meets it.
    world.solids.add(new Aabb(0.35, 0, -1, 0.45, 2, 1));
    Vec3d from = new Vec3d(0.5, 1, -5);
    Vec3d to = new Vec3d(0.5, 1, 5);

    ArrowFlight.Hit hit = ArrowFlight.nearestEntityHit(world, from, to, AREA, MARGIN, false);

    assertNotNull(hit);
    assertEquals(0.5, hit.location().x(), EPS);
    assertEquals(-0.6, hit.location().z(), EPS);
    assertTrue(
        ArrowFlight.entityHits(
                world, from, to, AREA, MARGIN, false, VanillaRules.ARROW_MARGIN_HIT_ON_SURFACE)
            .isEmpty());
  }

  @Test
  void noMarginMeansTheRealBox() {
    TestWorld world = TestWorld.empty();
    world.entities.add(TestWorld.player(1, new Vec3d(0, 0, 0), false));

    assertNull(
        ArrowFlight.nearestEntityHit(
            world, new Vec3d(0.5, 1, -5), new Vec3d(0.5, 1, 5), AREA, 0.0F, false));
  }

  @Test
  void nearestOfTwoOverlappingTargetsWins() {
    TestWorld world = TestWorld.empty();
    // Listed first but entered later: its inflated box starts at z = -0.2.
    world.entities.add(TestWorld.player(1, new Vec3d(0, 0, 0.4), false));
    world.entities.add(TestWorld.player(2, new Vec3d(0, 0, 0), false));

    ArrowFlight.Hit hit =
        ArrowFlight.nearestEntityHit(
            world, new Vec3d(0, 1, -5), new Vec3d(0, 1, 5), AREA, MARGIN, false);

    assertNotNull(hit);
    assertEquals(2, hit.target().id());
    assertEquals(-0.6, hit.location().z(), EPS);
  }

  @Test
  void onATieTheEarlierTargetWins() {
    TestWorld world = TestWorld.empty();
    world.entities.add(TestWorld.player(1, new Vec3d(-0.35, 0, 0), false));
    world.entities.add(TestWorld.player(2, new Vec3d(0.35, 0, 0), false));

    ArrowFlight.Hit hit =
        ArrowFlight.nearestEntityHit(
            world, new Vec3d(0, 1, -5), new Vec3d(0, 1, 5), AREA, MARGIN, false);

    assertNotNull(hit);
    assertEquals(1, hit.target().id());
  }

  @Test
  void shooterGroupIsSkippedUntilTheProjectileLeavesIt() {
    TestWorld world = TestWorld.empty();
    world.entities.add(TestWorld.player(1, new Vec3d(0, 0, 0), true));
    Vec3d from = new Vec3d(0, 1, -5);
    Vec3d to = new Vec3d(0, 1, 5);

    assertNull(ArrowFlight.nearestEntityHit(world, from, to, AREA, MARGIN, false));
    assertNotNull(ArrowFlight.nearestEntityHit(world, from, to, AREA, MARGIN, true));
  }
}
