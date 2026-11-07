package ru.wilyfox.trajectory.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class CrossbowShotTest {
  private static final Vec3d FEET = new Vec3d(10.5, 64.0, -3.25);

  private static ShooterState standing(float yaw, float pitch) {
    return new ShooterState(FEET, 1.62F, yaw, pitch, new Vec3d(0.2, 0.0, 0.1), true);
  }

  /**
   * {@code CrossbowItem.shootProjectile} without a target, followed by the normalization in {@code
   * Projectile.getMovementToShoot}, written with vanilla's own math classes.
   */
  private static Vec3d vanillaDirection(float yaw, float pitch, float angle) {
    float yRot = Mth.wrapDegrees(yaw);
    float xRot = Mth.clamp(Mth.wrapDegrees(pitch), -90.0F, 90.0F);
    Vec3 up = viewVector(xRot - 90.0F, yRot);
    Quaternionf q =
        new Quaternionf().setAngleAxis(angle * (float) (Math.PI / 180.0), up.x, up.y, up.z);
    Vector3f shot = viewVector(xRot, yRot).toVector3f().rotate(q);
    Vec3 v = new Vec3(shot.x(), shot.y(), shot.z()).normalize();
    return new Vec3d(v.x, v.y, v.z);
  }

  /** {@code Entity.calculateViewVector}. */
  private static Vec3 viewVector(float xRot, float yRot) {
    float realXRot = xRot * (float) (Math.PI / 180.0);
    float realYRot = -yRot * (float) (Math.PI / 180.0);
    float yCos = Mth.cos(realYRot);
    float ySin = Mth.sin(realYRot);
    float xCos = Mth.cos(realXRot);
    float xSin = Mth.sin(realXRot);
    return new Vec3(ySin * xCos, -xSin, yCos * xCos);
  }

  @Test
  void directionMatchesVanillaBitForBit() {
    float[] yaws = {0.0F, 37.5F, -123.25F, 179.9F, 90.7F + 3600.0F};
    float[] pitches = {0.0F, -12.5F, 33.3F, -89.0F, 89.9F};
    float[] angles = {0.0F, -10.0F, 10.0F, 20.0F};
    for (float yaw : yaws) {
      for (float pitch : pitches) {
        for (float angle : angles) {
          Launch launch =
              CrossbowShot.launch(
                  standing(yaw, pitch), CrossbowShot.Charge.ARROW, angle, CrossbowShot.ARROW_POWER);
          assertEquals(
              vanillaDirection(yaw, pitch, angle),
              launch.direction(),
              "yaw " + yaw + " pitch " + pitch + " angle " + angle);
        }
      }
    }
  }

  @Test
  void arrowsSpawnLowerThanTheEyesByATenthRocketsByMore() {
    ShooterState shooter = standing(0.0F, 0.0F);
    double eyeY = FEET.y() + 1.62F;
    Launch arrow = CrossbowShot.launch(shooter, CrossbowShot.Charge.ARROW, 0.0F, 3.15F);
    Launch rocket = CrossbowShot.launch(shooter, CrossbowShot.Charge.FIREWORK, 0.0F, 1.6F);
    assertEquals(new Vec3d(FEET.x(), eyeY - 0.1F, FEET.z()), arrow.spawn());
    assertEquals(new Vec3d(FEET.x(), eyeY - 0.15F, FEET.z()), rocket.spawn());
  }

  @Test
  void theShootersMovementIsNotPassedOn() {
    for (CrossbowShot.Projectile p :
        CrossbowShot.volley(standing(10.0F, 5.0F), List.of(CrossbowShot.Charge.ARROW), 0.0F)) {
      assertEquals(Vec3d.ZERO, p.launch().inherited());
      assertEquals(3.15F, p.launch().power());
    }
  }

  @Test
  void multishotAnglesAlternateAroundTheCenter() {
    List<CrossbowShot.Charge> three =
        List.of(CrossbowShot.Charge.ARROW, CrossbowShot.Charge.ARROW, CrossbowShot.Charge.ARROW);
    assertEquals(
        List.of(0.0F, -10.0F, 10.0F), angles(CrossbowShot.volley(standing(0, 0), three, 10.0F)));

    List<CrossbowShot.Charge> five =
        List.of(
            CrossbowShot.Charge.ARROW,
            CrossbowShot.Charge.ARROW,
            CrossbowShot.Charge.ARROW,
            CrossbowShot.Charge.ARROW,
            CrossbowShot.Charge.ARROW);
    assertEquals(
        List.of(0.0F, -10.0F, 10.0F, -20.0F, 20.0F),
        angles(CrossbowShot.volley(standing(0, 0), five, 20.0F)));

    // An even count, as a datapack can make: no projectile flies straight.
    List<CrossbowShot.Charge> two = List.of(CrossbowShot.Charge.ARROW, CrossbowShot.Charge.ARROW);
    assertEquals(List.of(10.0F, -10.0F), angles(CrossbowShot.volley(standing(0, 0), two, 10.0F)));
  }

  @Test
  void anyRocketSlowsTheWholeVolley() {
    List<CrossbowShot.Projectile> volley =
        CrossbowShot.volley(
            standing(0, 0),
            List.of(
                CrossbowShot.Charge.ARROW, CrossbowShot.Charge.FIREWORK, CrossbowShot.Charge.ARROW),
            10.0F);
    for (CrossbowShot.Projectile p : volley) {
      assertEquals(1.6F, p.launch().power());
    }
  }

  @Test
  void sideArrowsTurnAroundTheUpVectorEvenWhenLookingDown() {
    // Looking down, the turn is in the view's own tilted plane, towards the horizontal right
    // vector: the side arrow keeps cos(10 degrees) of the center's vertical part, not all of it.
    Launch center = CrossbowShot.launch(standing(0, 60), CrossbowShot.Charge.ARROW, 0.0F, 3.15F);
    Launch side = CrossbowShot.launch(standing(0, 60), CrossbowShot.Charge.ARROW, 10.0F, 3.15F);
    assertEquals(
        center.direction().y() * Math.cos(Math.toRadians(10.0)), side.direction().y(), 1.0e-6);
    double cos =
        center.direction().x() * side.direction().x()
            + center.direction().y() * side.direction().y()
            + center.direction().z() * side.direction().z();
    assertEquals(10.0, Math.toDegrees(Math.acos(cos)), 1.0e-3);
  }

  private static List<Float> angles(List<CrossbowShot.Projectile> volley) {
    return volley.stream().map(CrossbowShot.Projectile::angle).toList();
  }
}
