package ru.wilyfox.trajectory.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Flight of a firework rocket shot at an angle from a crossbow, in the order of vanilla 26.2 {@code
 * FireworkRocketEntity.tick} with {@code isShotAtAngle()}:
 *
 * <ol>
 *   <li>no acceleration and no gravity: the velocity never changes;
 *   <li>find the hit along the velocity ({@code ProjectileUtil.getHitResultOnMoveVector}, collider
 *       shapes, entity margin as for arrows);
 *   <li>move; a hit entity or a block ends the flight;
 *   <li>{@code life} grows by one; the rocket explodes once {@code life > lifetime}.
 * </ol>
 *
 * <p>The lifetime is random: {@code 10 · flightCount + nextInt(6) + nextInt(7)}, so the rocket
 * explodes in the air somewhere between tick {@code 10 · flightCount + 1} and {@code 10 ·
 * flightCount + 12}. Points past the earliest explosion are marked uncertain in the trajectory.
 *
 * <p>Simplified: on a block the flight ends at the impact. Vanilla explodes there only if the
 * rocket has explosions; one without them slides along the surface ({@code move}) until its
 * lifetime runs out, which is not followed.
 */
public final class FireworkFlight {
  /** {@code EntityTypes.FIREWORK_ROCKET}: {@code sized(0.25F, 0.25F)}. */
  public static final float WIDTH = 0.25F;

  public static final float HEIGHT = 0.25F;

  private FireworkFlight() {}

  /** The first tick the rocket may explode in the air: lifetime {@code 10 · flightCount}. */
  public static int firstExplosionTick(int flightCount) {
    return 10 * flightCount + 1;
  }

  /** The last tick the rocket may explode in the air: lifetime {@code 10 · flightCount + 11}. */
  public static int lastExplosionTick(int flightCount) {
    return 10 * flightCount + 12;
  }

  /**
   * Simulates the flight.
   *
   * @param flightCount {@code 1 + Fireworks.flightDuration()} of the rocket item
   */
  public static Trajectory simulate(Vec3d spawn, Vec3d velocity, WorldView world, int flightCount) {
    int last = lastExplosionTick(flightCount);
    int certain = firstExplosionTick(flightCount) + 1;
    List<Vec3d> points = new ArrayList<>();
    points.add(spawn);
    Vec3d pos = spawn;
    boolean leftOwner = false;

    for (int tickCount = 1; tickCount <= last; tickCount++) {
      if (!leftOwner) {
        leftOwner = ArrowFlight.outsideShooter(world, box(pos), velocity);
      }

      Vec3d to = pos.add(velocity);
      WorldView.BlockClip blocks = world.clip(pos, to);
      if (blocks.type() == WorldView.BlockClip.Type.UNKNOWN) {
        return new Trajectory(
            points, Trajectory.End.UNKNOWN, tickCount, null, null, null, null, points.size());
      }

      Aabb searchArea = box(pos).expandTowards(velocity).inflate(1.0);
      Vec3d origin = pos;
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
            velocity,
            Math.min(certain, points.size()));
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
            velocity,
            Math.min(certain, points.size()));
      }

      pos = to;
      points.add(pos);
      if (pos.y() < world.minY() - 64.0) {
        return new Trajectory(
            points,
            Trajectory.End.VOID,
            tickCount,
            null,
            null,
            null,
            null,
            Math.min(certain, points.size()));
      }
    }

    return new Trajectory(points, Trajectory.End.EXPLODED, last, null, null, null, null, certain);
  }

  /** Rocket bounding box at a position. */
  public static Aabb box(Vec3d position) {
    return Aabb.ofEntity(position, WIDTH, HEIGHT);
  }
}
