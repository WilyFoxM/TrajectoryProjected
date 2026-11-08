package ru.wilyfox.trajectory.sim;

import java.util.ArrayList;
import java.util.List;

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

    return fromRotation(shooter, 0.0F, pow * 3.0F);
  }

  /**
   * Relative likelihood of the server counting one tick more, and one tick less, than the client.
   */
  private static final double NEIGHBOUR_TICK_WEIGHT = 0.2;

  /**
   * The launch for the client's count, with the launches a server counting one tick more or less
   * would fire. A neighbour with the same power, as at full draw, or with no shot at all is left
   * out. The weights are a rough figure from one server, where about 4 in 10 partial draws differed
   * by a tick; the split between more and less is taken as even.
   */
  public static List<SpreadZone.Variant> tickVariants(ShooterState shooter, int ticksHeld) {
    List<SpreadZone.Variant> variants = new ArrayList<>();
    Launch nominal = launch(shooter, ticksHeld);
    if (nominal == null) {
      return variants;
    }
    variants.add(new SpreadZone.Variant(nominal, 1.0 - 2.0 * NEIGHBOUR_TICK_WEIGHT));
    for (int ticks : new int[] {ticksHeld - 1, ticksHeld + 1}) {
      Launch neighbour = launch(shooter, ticks);
      if (neighbour != null && neighbour.power() != nominal.power()) {
        variants.add(new SpreadZone.Variant(neighbour, NEIGHBOUR_TICK_WEIGHT));
      }
    }
    return variants;
  }

  /**
   * {@code Projectile.shootFromRotation} by a player, for the projectiles spawned at {@code eyeY -
   * 0.1F} ({@code AbstractArrow} and {@code ThrowableItemProjectile} with an owner): the direction
   * from the rotation with {@code yOffset} added to the pitch of the vertical part only, and the
   * shooter's known movement passed on (without its vertical part on the ground).
   */
  public static Launch fromRotation(ShooterState shooter, float yOffset, float power) {
    Vec3d feet = shooter.position();
    double eyeY = feet.y() + shooter.eyeHeight();
    Vec3d spawn = new Vec3d(feet.x(), eyeY - 0.1F, feet.z());

    float yRot = VanillaMath.serverYaw(shooter.sentYaw());
    float xRot = VanillaMath.serverPitch(shooter.sentPitch());
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
    return new Launch(spawn, direction, power, inherited);
  }
}
