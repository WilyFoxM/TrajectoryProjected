package ru.wilyfox.trajectory.sim;

/**
 * A wind charge thrown by a player, as {@code WindChargeItem.use} does it on the server: {@code
 * spawnProjectileFromRotation} with power 1.5, passing the shooter's movement on, but spawned at
 * eye height ({@code player.getEyePosition().y}), not 0.1 below it.
 */
public final class WindChargeShot {
  public static final float POWER = 1.5F;

  private WindChargeShot() {}

  public static Launch launch(ShooterState shooter) {
    Launch fromHand = BowShot.fromRotation(shooter, 0.0F, POWER);
    Vec3d feet = shooter.position();
    Vec3d spawn = new Vec3d(feet.x(), feet.y() + shooter.eyeHeight(), feet.z());
    return new Launch(spawn, fromHand.direction(), fromHand.power(), fromHand.inherited());
  }
}
