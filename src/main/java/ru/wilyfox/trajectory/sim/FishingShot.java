package ru.wilyfox.trajectory.sim;

/**
 * A cast of a fishing rod as vanilla 26.2 performs it on the server, in the {@code FishingHook}
 * constructor: from the eyes, 0.3 ahead along the yaw, with the rotation of the click and without
 * the shooter's movement.
 *
 * <p>Each axis of the direction is scaled by {@code 0.6 / length + triangle(0.5, 0.0103365)}: the
 * random part changes the speed along each axis by about one percent. The nominal launch uses the
 * center, 0.5.
 */
public final class FishingShot {
  /** {@code random.triangle(0.5, 0.0103365)}: the center of the random scale. */
  public static final double SCALE_CENTER = 0.5;

  /** Half width of that triangle distribution. */
  public static final double SCALE_HALF_WIDTH = 0.0103365;

  private FishingShot() {}

  public static Launch launch(ShooterState shooter) {
    float yaw = VanillaMath.serverYaw(shooter.sentYaw());
    float pitch = VanillaMath.serverPitch(shooter.sentPitch());
    float yCos = VanillaMath.cos(-yaw * VanillaMath.DEG_TO_RAD - (float) Math.PI);
    float ySin = VanillaMath.sin(-yaw * VanillaMath.DEG_TO_RAD - (float) Math.PI);
    float xCos = -VanillaMath.cos(-pitch * VanillaMath.DEG_TO_RAD);
    float xSin = VanillaMath.sin(-pitch * VanillaMath.DEG_TO_RAD);
    Vec3d feet = shooter.position();
    Vec3d spawn =
        new Vec3d(feet.x() - ySin * 0.3, feet.y() + shooter.eyeHeight(), feet.z() - yCos * 0.3);
    Vec3d direction = new Vec3d(-ySin, Math.clamp(-(xSin / xCos), -5.0F, 5.0F), -yCos);
    double length = direction.length();
    double scale = 0.6 / length + SCALE_CENTER;
    // As a Launch: the unit direction times the speed along it, nothing inherited.
    return new Launch(spawn, direction.normalize(), (float) (length * scale), Vec3d.ZERO);
  }

  /**
   * The velocity for given random scales per axis, each within {@link #SCALE_CENTER} ± {@link
   * #SCALE_HALF_WIDTH}: {@code raw * (0.6 / length + scale)} per axis, where {@code raw} is the
   * vanilla direction before normalising.
   */
  public static Vec3d velocity(Launch launch, Vec3d scales) {
    // power = length * (0.6 / length + 0.5) = 0.6 + 0.5 * length.
    double length = (launch.power() - 0.6) / SCALE_CENTER;
    Vec3d d = launch.direction();
    return new Vec3d(
        d.x() * (0.6 + length * scales.x()),
        d.y() * (0.6 + length * scales.y()),
        d.z() * (0.6 + length * scales.z()));
  }
}
