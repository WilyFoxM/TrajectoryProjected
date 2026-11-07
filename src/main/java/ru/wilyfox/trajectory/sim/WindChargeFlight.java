package ru.wilyfox.trajectory.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Flight of a wind charge thrown by a player, in the order of vanilla 26.2 {@code
 * AbstractHurtingProjectile.tick} with the wind charge's constants:
 *
 * <ol>
 *   <li>{@code applyInertia}: acceleration power 0 and inertia 1.0, in air and water alike, so the
 *       velocity never changes; no gravity;
 *   <li>find the hit along the velocity (collider shapes, entity margin as for other projectiles);
 *   <li>move to the hit or by the velocity;
 *   <li>{@code Projectile.tick}: check whether the charge has left its owner (from the next tick
 *       on).
 * </ol>
 *
 * <p>The charge's box hangs 0.15 below its position ({@code AbstractWindCharge.makeBoundingBox}).
 * On a block it bursts 0.25 off the face; above the build limit plus 30 it bursts in the air.
 *
 * <p>Not modelled: wind charges and end crystals, which it passes through, are not told apart from
 * other entities.
 */
public final class WindChargeFlight {
  /** {@code EntityTypes.WIND_CHARGE}: {@code sized(0.3125F, 0.3125F)}. */
  public static final float WIDTH = 0.3125F;

  public static final float HEIGHT = 0.3125F;

  /** {@code AbstractWindCharge.makeBoundingBox}: the box starts this far below the position. */
  public static final float BOX_DROP = 0.15F;

  /** {@code AbstractWindCharge.tick}: it bursts once its block y is above max y plus this. */
  private static final int BURST_ABOVE_MAX = 30;

  private WindChargeFlight() {}

  public static Trajectory simulate(Vec3d spawn, Vec3d velocity, WorldView world, int maxTicks) {
    List<Vec3d> points = new ArrayList<>();
    points.add(spawn);
    Vec3d pos = spawn;
    boolean leftOwner = false;

    for (int tickCount = 1; tickCount <= maxTicks; tickCount++) {
      if (Math.floor(pos.y()) > world.maxY() + BURST_ABOVE_MAX) {
        return new Trajectory(points, Trajectory.End.EXPLODED, tickCount);
      }

      WorldView.BlockClip blocks = world.clip(pos, pos.add(velocity));
      if (blocks.type() == WorldView.BlockClip.Type.UNKNOWN) {
        return new Trajectory(points, Trajectory.End.UNKNOWN, tickCount);
      }

      Vec3d origin = pos;
      Aabb searchArea = box(pos).expandTowards(velocity).inflate(1.0);
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
            points,
            Trajectory.End.ENTITY,
            tickCount,
            first.target(),
            null,
            first.location(),
            velocity);
      }

      if (blocks.type() == WorldView.BlockClip.Type.BLOCK) {
        points.add(blocks.location());
        return new Trajectory(
            points,
            Trajectory.End.BLOCK,
            tickCount,
            null,
            blocks.face(),
            blocks.location(),
            velocity);
      }

      pos = blocks.location();
      points.add(pos);
      if (!leftOwner) {
        leftOwner = ArrowFlight.outsideShooter(world, box(pos), velocity);
      }
      if (pos.y() < world.minY() - 64.0) {
        return new Trajectory(points, Trajectory.End.VOID, tickCount);
      }
    }

    return new Trajectory(points, Trajectory.End.MAX_TICKS, maxTicks);
  }

  /** The charge's box: centered on x and z, from 0.15 below the position. */
  public static Aabb box(Vec3d position) {
    float half = WIDTH / 2.0F;
    return new Aabb(
        position.x() - half,
        position.y() - BOX_DROP,
        position.z() - half,
        position.x() + half,
        position.y() - BOX_DROP + HEIGHT,
        position.z() + half);
  }
}
