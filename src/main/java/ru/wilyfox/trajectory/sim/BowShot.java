package ru.wilyfox.trajectory.sim;

/**
 * Launch of an arrow from a bow by a player, as vanilla 26.2 computes it on the server.
 *
 * <p>Sources: {@code BowItem.releaseUsing} and {@code getPowerForTime}, {@code
 * AbstractArrow(EntityType, LivingEntity, ...)} for the spawn point, {@code
 * Projectile.shootFromRotation}, {@code shoot} and {@code getMovementToShoot}.
 */
public final class BowShot {
  /** {@code BowItem.releaseUsing}: no arrow below this power. */
  private static final double MIN_POWER = 0.1;

  private BowShot() {}

  /**
   * {@code BowItem.getPowerForTime}: draw power for the number of ticks the bow was held, in [0,
   * 1].
   */
  public static float powerForTicks(int ticksHeld) {
    float pow = ticksHeld / 20.0F;
    pow = (pow * pow + pow * 2.0F) / 3.0F;
    if (pow > 1.0F) {
      pow = 1.0F;
    }
    return pow;
  }

  /**
   * Computes the launch, or returns {@code null} when vanilla would not fire (power below 0.1, that
   * is two ticks or less).
   */
  public static Launch launch(ShooterState shooter, int ticksHeld) {
    float pow = powerForTicks(ticksHeld);
    if (pow < MIN_POWER) {
      return null;
    }

    Vec3d feet = shooter.position();
    double eyeY = feet.y() + shooter.eyeHeight();
    Vec3d spawn = new Vec3d(feet.x(), eyeY - 0.1F, feet.z());

    float yRot = VanillaMath.serverYaw(shooter.sentYaw());
    float xRot = VanillaMath.serverPitch(shooter.sentPitch());
    float yOffset = 0.0F;
    float xd =
        -VanillaMath.sin(yRot * VanillaMath.DEG_TO_RAD)
            * VanillaMath.cos(xRot * VanillaMath.DEG_TO_RAD);
    float yd = -VanillaMath.sin((xRot + yOffset) * VanillaMath.DEG_TO_RAD);
    float zd =
        VanillaMath.cos(yRot * VanillaMath.DEG_TO_RAD)
            * VanillaMath.cos(xRot * VanillaMath.DEG_TO_RAD);
    Vec3d direction = new Vec3d(xd, yd, zd).normalize();

    Vec3d moved = shooter.knownMovement();
    Vec3d inherited = new Vec3d(moved.x(), shooter.onGround() ? 0.0 : moved.y(), moved.z());
    return new Launch(spawn, direction, pow * 3.0F, inherited);
  }
}
