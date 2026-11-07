package ru.wilyfox.trajectory.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Flight of a thrown item (snowball, egg, ender pearl, potion, bottle o' enchanting), tick by tick,
 * in the order of vanilla 26.2 {@code ThrowableProjectile.tick}:
 *
 * <ol>
 *   <li>gravity: velocity.y − g;
 *   <li>in water (state from the previous tick): velocity × 0.8, otherwise × 0.99;
 *   <li>find the hit along the new velocity ({@code ProjectileUtil.getHitResultOnMoveVector}:
 *       collider shapes, then the nearest entity before the block, with the margin of {@code
 *       computeMargin});
 *   <li>move to the hit or by the velocity;
 *   <li>{@code Projectile.tick}: check whether the projectile has left its owner (used from the
 *       next tick on), then {@code baseTick} updates the in-water state.
 * </ol>
 *
 * <p>Gravity and drag come before the move, the reverse of arrows: the first tick moves by {@code
 * (v0 − g) · 0.99}. The hit is exactly where the path meets the target, without a backoff.
 *
 * <p>Not modelled: bubble columns and water currents, deflection by entities such as the Breeze.
 */
public final class ThrowableFlight {
  /** All thrown items are {@code sized(0.25F, 0.25F)}. */
  public static final float WIDTH = 0.25F;

  public static final float HEIGHT = 0.25F;

  /** {@code ThrowableProjectile.getAirDrag}. */
  public static final float AIR_DRAG = 0.99F;

  /** {@code ThrowableProjectile.applyInertia} in water. */
  public static final float WATER_INERTIA = 0.8F;

  private ThrowableFlight() {}

  /**
   * Simulates the flight from a spawn state until it ends or {@code maxTicks} pass.
   *
   * @param gravity {@code getDefaultGravity()} of the thrown item
   */
  public static Trajectory simulate(
      Vec3d spawn, Vec3d velocity, WorldView world, double gravity, int maxTicks) {
    List<Vec3d> points = new ArrayList<>();
    points.add(spawn);
    Vec3d pos = spawn;
    Vec3d vel = velocity;
    boolean inWater = false;
    boolean leftOwner = false;

    for (int tickCount = 1; tickCount <= maxTicks; tickCount++) {
      vel = nextVelocity(vel, gravity, inWater);

      WorldView.BlockClip blocks = world.clip(pos, pos.add(vel));
      if (blocks.type() == WorldView.BlockClip.Type.UNKNOWN) {
        return new Trajectory(points, Trajectory.End.UNKNOWN, tickCount);
      }

      Vec3d origin = pos;
      Aabb searchArea = box(pos).expandTowards(vel).inflate(1.0);
      List<ArrowFlight.Hit> hits =
          ArrowFlight.entityHits(
              world,
              origin,
              blocks.location(),
              searchArea,
              ArrowFlight.margin(tickCount),
              leftOwner);
      if (!hits.isEmpty()) {
        hits.sort(Comparator.comparingDouble(h -> origin.distanceToSqr(h.location())));
        ArrowFlight.Hit first = hits.getFirst();
        points.add(first.location());
        return new Trajectory(
            points, Trajectory.End.ENTITY, tickCount, first.target(), null, first.location(), vel);
      }

      if (blocks.type() == WorldView.BlockClip.Type.BLOCK) {
        points.add(blocks.location());
        return new Trajectory(
            points, Trajectory.End.BLOCK, tickCount, null, blocks.face(), blocks.location(), vel);
      }

      pos = blocks.location();
      points.add(pos);
      if (!leftOwner) {
        leftOwner = ArrowFlight.outsideShooter(world, box(pos), vel);
      }
      inWater = world.isInWater(box(pos));
      if (pos.y() < world.minY() - 64.0) {
        return new Trajectory(points, Trajectory.End.VOID, tickCount);
      }
    }

    return new Trajectory(points, Trajectory.End.MAX_TICKS, maxTicks);
  }

  /** Gravity, then air drag or water inertia. */
  public static Vec3d nextVelocity(Vec3d velocity, double gravity, boolean inWater) {
    return velocity.add(0.0, -gravity, 0.0).scale(inWater ? WATER_INERTIA : AIR_DRAG);
  }

  /** Thrown item bounding box at a position. */
  public static Aabb box(Vec3d position) {
    return Aabb.ofEntity(position, WIDTH, HEIGHT);
  }
}
