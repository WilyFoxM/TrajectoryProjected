package ru.wilyfox.trajectory.sim;

/**
 * A projectile's state as {@code Projectile.shoot} leaves it, before the server's random spread.
 * The final velocity depends on that spread, which the client cannot know; {@link #velocity(Vec3d)}
 * takes the spread offset explicitly.
 *
 * @param spawn position before the first tick
 * @param direction unit launch direction before spread
 * @param power launch speed
 * @param inherited shooter movement added after the shot (zero for weapons that do not pass it)
 */
public record Launch(Vec3d spawn, Vec3d direction, float power, Vec3d inherited) {
  /**
   * Player weapons pass {@code uncertainty = 1.0F}; {@code Projectile.getMovementToShoot} scales
   * the triangle spread by 0.0172275.
   */
  public static final double SPREAD_HALF_WIDTH = 0.0172275 * 1.0F;

  /**
   * Velocity for a given spread offset. Each axis of {@code spread} must lie within ±{@link
   * #SPREAD_HALF_WIDTH}; vanilla draws it from a triangle distribution centered on zero.
   */
  public Vec3d velocity(Vec3d spread) {
    return direction.add(spread).scale(power).add(inherited);
  }

  /** Velocity without spread: the center of the distribution. */
  public Vec3d nominalVelocity() {
    return velocity(Vec3d.ZERO);
  }
}
