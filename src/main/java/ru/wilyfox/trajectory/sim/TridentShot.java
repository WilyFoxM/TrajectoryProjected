package ru.wilyfox.trajectory.sim;

/**
 * A trident thrown by a player, as {@code TridentItem.releaseUsing} does it on the server: once it
 * has been held for at least 10 ticks, {@code spawnProjectileFromRotation} with power 2.5 from
 * {@code eyeY - 0.1F}, passing the shooter's movement on. A trident with Riptide launches the
 * player instead and throws nothing.
 */
public final class TridentShot {
  /** {@code TridentItem.releaseUsing}: no throw below this many ticks of holding. */
  public static final int MIN_TICKS = 10;

  public static final float POWER = 2.5F;

  private TridentShot() {}

  /** The launch, or {@code null} when vanilla would not throw yet. */
  public static Launch launch(ShooterState shooter, int ticksHeld) {
    return ticksHeld < MIN_TICKS ? null : BowShot.fromRotation(shooter, 0.0F, POWER);
  }
}
