package ru.wilyfox.trajectory.sim;

import java.util.ArrayList;
import java.util.List;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Launch of the projectiles a charged crossbow fires for a player, as vanilla 26.2 computes it on
 * the server.
 *
 * <p>Sources: {@code CrossbowItem.use}, {@code getShootingPower}, {@code shootProjectile} and
 * {@code createProjectile}; {@code ProjectileWeaponItem.shoot} for the angles of several
 * projectiles; {@code Entity.calculateViewVector} and {@code calculateUpVector}; {@code
 * Projectile.shoot}. Unlike the bow, the crossbow does not pass the shooter's movement on.
 *
 * <p>The direction is {@code getViewVector(1.0F)}, which for a living entity is the head yaw and
 * the pitch. The head yaw follows the body yaw on the server, one server tick behind; the pitch
 * comes from the use packet. Both are the last sent rotation while the aim is held still.
 */
public final class CrossbowShot {
  /** {@code CrossbowItem.ARROW_POWER}. */
  public static final float ARROW_POWER = 3.15F;

  /** {@code CrossbowItem.FIREWORK_POWER}: used for every projectile once any is a rocket. */
  public static final float FIREWORK_POWER = 1.6F;

  private CrossbowShot() {}

  /** What a charged projectile is. Tipped and spectral arrows fly as arrows. */
  public enum Charge {
    ARROW,
    FIREWORK
  }

  /**
   * One projectile of the volley.
   *
   * @param charge what it is
   * @param angle its rotation around the shooter's up vector, in degrees
   * @param launch its launch
   */
  public record Projectile(Charge charge, float angle, Launch launch) {}

  /**
   * The volley in firing order.
   *
   * @param charges the charged projectiles, in the crossbow's order
   * @param maxAngle {@code EnchantmentHelper.processProjectileSpread} of the crossbow (Multishot:
   *     10 degrees), irrelevant for a single projectile
   */
  public static List<Projectile> volley(
      ShooterState shooter, List<Charge> charges, float maxAngle) {
    int n = charges.size();
    float angleStep = n == 1 ? 0.0F : 2.0F * maxAngle / (n - 1);
    float angleOffset = (n - 1) % 2 * angleStep / 2.0F;
    float direction = 1.0F;
    float power = charges.contains(Charge.FIREWORK) ? FIREWORK_POWER : ARROW_POWER;

    List<Projectile> out = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      float angle = angleOffset + direction * ((i + 1) / 2) * angleStep;
      direction = -direction;
      Charge charge = charges.get(i);
      out.add(new Projectile(charge, angle, launch(shooter, charge, angle, power)));
    }
    return List.copyOf(out);
  }

  /** One projectile of a volley, rotated by {@code angle} degrees around the up vector. */
  static Launch launch(ShooterState shooter, Charge charge, float angle, float power) {
    Vec3d feet = shooter.position();
    double eyeY = feet.y() + shooter.eyeHeight();
    Vec3d spawn =
        new Vec3d(feet.x(), charge == Charge.FIREWORK ? eyeY - 0.15F : eyeY - 0.1F, feet.z());

    float yRot = VanillaMath.serverYaw(shooter.sentYaw());
    float xRot = VanillaMath.serverPitch(shooter.sentPitch());
    Vec3d up = viewVector(xRot - 90.0F, yRot);
    Vec3d view = viewVector(xRot, yRot);
    Quaternionf rotation =
        new Quaternionf()
            .setAngleAxis((double) (angle * (float) (Math.PI / 180.0)), up.x(), up.y(), up.z());
    Vector3f shot = new Vector3f((float) view.x(), (float) view.y(), (float) view.z());
    shot.rotate(rotation);
    Vec3d direction = new Vec3d(shot.x(), shot.y(), shot.z()).normalize();
    return new Launch(spawn, direction, power, Vec3d.ZERO);
  }

  /** {@code Entity.calculateViewVector}, with the table sine. */
  static Vec3d viewVector(float xRot, float yRot) {
    float realXRot = xRot * VanillaMath.DEG_TO_RAD;
    float realYRot = -yRot * VanillaMath.DEG_TO_RAD;
    float yCos = VanillaMath.cos(realYRot);
    float ySin = VanillaMath.sin(realYRot);
    float xCos = VanillaMath.cos(realXRot);
    float xSin = VanillaMath.sin(realXRot);
    return new Vec3d(ySin * xCos, -xSin, yCos * xCos);
  }
}
