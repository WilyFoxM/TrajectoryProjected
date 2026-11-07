package ru.wilyfox.trajectory.sim;

/**
 * Throws by a player, as vanilla 26.2 performs them on the server: {@code
 * Projectile.spawnProjectileFromRotation} with the item's pitch offset and power, from {@code eyeY
 * - 0.1F}, with the shooter's known movement passed on.
 *
 * <p>Sources: {@code SnowballItem}, {@code EggItem}, {@code EnderpearlItem}, {@code
 * ThrowablePotionItem}, {@code ExperienceBottleItem}; gravity from {@code
 * ThrowableProjectile.getDefaultGravity} and its overrides in {@code AbstractThrownPotion} and
 * {@code ThrownExperienceBottle}.
 *
 * <p>The server throws with the rotation from the use packet, the rotation of the click; with a
 * steady aim it is the last sent one, which is what the prediction uses.
 */
public final class ThrowShot {
  private ThrowShot() {}

  /** What is thrown, with its launch and gravity. */
  public enum Item {
    SNOWBALL(0.0F, 1.5F, 0.03),
    EGG(0.0F, 1.5F, 0.03),
    ENDER_PEARL(0.0F, 1.5F, 0.03),
    POTION(-20.0F, 0.5F, 0.05),
    EXPERIENCE_BOTTLE(-20.0F, 0.7F, 0.07);

    /** Added to the pitch of the vertical part of the direction, in degrees. */
    public final float yOffset;

    public final float power;

    public final double gravity;

    Item(float yOffset, float power, double gravity) {
      this.yOffset = yOffset;
      this.power = power;
      this.gravity = gravity;
    }
  }

  public static Launch launch(ShooterState shooter, Item item) {
    return BowShot.fromRotation(shooter, item.yOffset, item.power);
  }

  public static Flight flight(Item item, int maxTicks) {
    return Flight.throwable(item.gravity, maxTicks);
  }
}
