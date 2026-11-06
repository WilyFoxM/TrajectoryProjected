package ru.wilyfox.trajectory.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Optional;
import java.util.SplittableRandom;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Compares the core's math with the vanilla classes themselves. The core does not depend on
 * Minecraft; only this test uses it as the reference.
 */
class VanillaParityTest {
  private final SplittableRandom random = new SplittableRandom(1551);

  @Test
  void sineTableMatchesMth() {
    for (int i = 0; i < 200_000; i++) {
      float degrees = (float) (random.nextDouble(-1080.0, 1080.0));
      double radians = degrees * VanillaMath.DEG_TO_RAD;
      assertEquals(Mth.sin(radians), VanillaMath.sin(radians), "sin " + degrees);
      assertEquals(Mth.cos(radians), VanillaMath.cos(radians), "cos " + degrees);
    }
  }

  @Test
  void wrapDegreesMatchesMth() {
    for (int i = 0; i < 100_000; i++) {
      float angle = (float) random.nextDouble(-100_000.0, 100_000.0);
      assertEquals(Mth.wrapDegrees(angle), VanillaMath.wrapDegrees(angle), "angle " + angle);
    }
  }

  @Test
  void vectorOperationsMatchVec3() {
    for (int i = 0; i < 20_000; i++) {
      Vec3 a = randomVec3(8.0);
      Vec3 b = randomVec3(8.0);
      float f = (float) random.nextDouble(-2.0, 2.0);
      assertSame(a.normalize(), own(a).normalize());
      assertSame(a.add(b), own(a).add(own(b)));
      assertSame(a.subtract(b), own(a).subtract(own(b)));
      assertSame(a.scale(f), own(a).scale(f));
      assertEquals(a.distanceToSqr(b), own(a).distanceToSqr(own(b)));
    }
    assertSame(new Vec3(1.0E-6, 0.0, 0.0).normalize(), new Vec3d(1.0E-6, 0.0, 0.0).normalize());
  }

  @Test
  void boxOperationsMatchAabb() {
    for (int i = 0; i < 50_000; i++) {
      Vec3 p = randomVec3(4.0);
      AABB box =
          new AABB(
              p,
              p.add(
                  random.nextDouble(0.1, 3.0),
                  random.nextDouble(0.1, 3.0),
                  random.nextDouble(0.1, 3.0)));
      Aabb own = own(box);
      Vec3 from = randomVec3(8.0);
      Vec3 to = randomVec3(8.0);
      Optional<Vec3> expected = box.clip(from, to);
      Vec3d actual = own.clip(own(from), own(to));
      if (expected.isEmpty()) {
        assertNull(actual, "clip " + box + " " + from + " " + to);
      } else {
        assertSame(expected.get(), actual);
      }

      Vec3 delta = randomVec3(3.0);
      double amount = random.nextDouble(0.0, 1.5);
      assertBox(
          box.expandTowards(delta).inflate(amount), own.expandTowards(own(delta)).inflate(amount));
      assertEquals(box.contains(from), own.contains(own(from)));
      AABB other = new AABB(from, from.add(1.0, 1.0, 1.0));
      assertEquals(box.intersects(other), own.intersects(own(other)));
    }
  }

  private Vec3 randomVec3(double range) {
    return new Vec3(
        random.nextDouble(-range, range),
        random.nextDouble(-range, range),
        random.nextDouble(-range, range));
  }

  private static Vec3d own(Vec3 v) {
    return new Vec3d(v.x, v.y, v.z);
  }

  private static Aabb own(AABB b) {
    return new Aabb(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
  }

  private static void assertSame(Vec3 expected, Vec3d actual) {
    assertEquals(expected.x, actual.x(), "x");
    assertEquals(expected.y, actual.y(), "y");
    assertEquals(expected.z, actual.z(), "z");
  }

  private static void assertBox(AABB expected, Aabb actual) {
    assertEquals(own(expected), actual);
  }
}
