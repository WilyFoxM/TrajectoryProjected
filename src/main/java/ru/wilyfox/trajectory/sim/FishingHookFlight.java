package ru.wilyfox.trajectory.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Flight of a fishing hook, tick by tick, in the order of vanilla 26.2 {@code FishingHook.tick}.
 * Unlike arrows, the hook moves with {@code Entity.move}: its box collides with block shapes axis
 * by axis, and touching the ground or a wall in flight stops it.
 *
 * <ol>
 *   <li>{@code Projectile.tick}: whether the hook has left its owner, for entity hits;
 *   <li>water at the block of its position (from the previous move): on entering it the velocity is
 *       scaled by (0.3, 0.2, 0.3), the hook starts bobbing, and this tick ends;
 *   <li>flying: a ray along the velocity ({@code ProjectileUtil.getHitResultOnMoveVector}); an
 *       entity is hooked, a block sets the speed to the squared distance to it ({@code onHitBlock}
 *       with {@code HitResult.distanceTo}, which is squared);
 *   <li>bobbing: horizontal velocity × 0.9, and a random pull towards the water surface;
 *   <li>gravity 0.03 out of water and off the ground;
 *   <li>{@code move(SELF)} with collisions; in flight, ground or a wall sets the velocity to zero;
 *   <li>velocity × 0.92.
 * </ol>
 *
 * <p>Bobbing is random only vertically, around the water surface, so the end of a cast on water is
 * where the horizontal drift stops, on the surface. The pull uses the average of the random factor.
 *
 * <p>Not modelled: entity colliders such as boats, the world border bounce, cobwebs, block speed
 * factors (honey, soul sand) and bouncy blocks, hooking item entities, a fish biting.
 */
public final class FishingHookFlight {
  /** {@code EntityTypes.FISHING_BOBBER}: {@code sized(0.25F, 0.25F)}. */
  public static final float SIZE = 0.25F;

  public static final double GRAVITY = 0.03;

  public static final double INERTIA = 0.92;

  /** Velocity scale on entering water. */
  private static final double ENTER_WATER_HORIZONTAL = 0.3;

  private static final double ENTER_WATER_VERTICAL = 0.2;

  private static final double BOBBING_DRAG = 0.9;

  /** {@code Mth.equal}: differences below this count as none. */
  private static final double SAME = 1.0E-5F;

  /** {@code Shapes.collide} tolerance. */
  private static final double EPSILON = 1.0E-7;

  /** A bobbing hook slower than this has stopped drifting. */
  private static final double SETTLED_SPEED = 0.003;

  /** Mean of {@code random.nextFloat()} in the bobbing pull. */
  private static final double MEAN_RANDOM = 0.5;

  private FishingHookFlight() {}

  /**
   * Simulates the cast from the spawn state until the hook lies still, floats still, hooks an
   * entity or {@code maxTicks} pass.
   */
  public static Trajectory simulate(Vec3d spawn, Vec3d velocity, WorldView world, int maxTicks) {
    List<Vec3d> points = new ArrayList<>();
    points.add(spawn);
    Vec3d pos = spawn;
    Vec3d vel = velocity;
    boolean onGround = false;
    boolean leftOwner = false;
    boolean bobbing = false;
    Vec3d lastMove = velocity;

    for (int tickCount = 1; tickCount <= maxTicks; tickCount++) {
      if (!leftOwner) {
        leftOwner = ArrowFlight.outsideShooter(world, box(pos), vel);
      }
      int bx = floor(pos.x());
      int by = floor(pos.y());
      int bz = floor(pos.z());
      float water = world.waterHeight(bx, by, bz);
      boolean inWater = water > 0.0F;
      if (!bobbing) {
        if (inWater) {
          vel =
              new Vec3d(
                  vel.x() * ENTER_WATER_HORIZONTAL,
                  vel.y() * ENTER_WATER_VERTICAL,
                  vel.z() * ENTER_WATER_HORIZONTAL);
          bobbing = true;
          points.add(pos);
          continue;
        }
        WorldView.BlockClip blocks = world.clip(pos, pos.add(vel));
        if (blocks.type() == WorldView.BlockClip.Type.UNKNOWN) {
          return new Trajectory(points, Trajectory.End.UNKNOWN, tickCount);
        }
        Vec3d origin = pos;
        Aabb area = box(pos).expandTowards(vel).inflate(1.0);
        List<ArrowFlight.Hit> hits =
            ArrowFlight.entityHits(
                world, origin, blocks.location(), area, ArrowFlight.margin(tickCount), leftOwner);
        if (!hits.isEmpty()) {
          hits.sort(Comparator.comparingDouble(h -> origin.distanceToSqr(h.target().position())));
          ArrowFlight.Hit first = hits.getFirst();
          points.add(first.location());
          return new Trajectory(
              points,
              Trajectory.End.ENTITY,
              tickCount,
              first.target(),
              null,
              first.location(),
              vel);
        }
        if (blocks.type() == WorldView.BlockClip.Type.BLOCK) {
          // HitResult.distanceTo returns the squared distance: close to a block the hook slows down
          // more than the distance, and settles a little short of where the ray met it.
          vel = vel.normalize().scale(blocks.location().distanceToSqr(pos));
        }
      } else {
        double force = pos.y() + vel.y() - by - water;
        if (Math.abs(force) < 0.01) {
          force += Math.signum(force) * 0.1;
        }
        vel =
            new Vec3d(
                vel.x() * BOBBING_DRAG,
                vel.y() - force * MEAN_RANDOM * 0.2,
                vel.z() * BOBBING_DRAG);
      }

      if (!inWater && !onGround) {
        vel = vel.add(0.0, -GRAVITY, 0.0);
      }

      Vec3d delta = vel;
      Vec3d moved = collide(box(pos), delta, world.collisions(box(pos).expandTowards(delta)));
      pos = pos.add(moved);
      boolean horizontal =
          Math.abs(delta.x() - moved.x()) >= SAME || Math.abs(delta.z() - moved.z()) >= SAME;
      if (Math.abs(delta.y()) > 0.0) {
        onGround = delta.y() != moved.y() && delta.y() < 0.0;
      }
      if (!bobbing && (onGround || horizontal)) {
        vel = Vec3d.ZERO;
      }
      vel = vel.scale(INERTIA);
      lastMove = moved.lengthSqr() > 0.0 ? moved : lastMove;
      points.add(pos);

      if (pos.y() < world.minY() - 64.0) {
        return new Trajectory(points, Trajectory.End.VOID, tickCount);
      }
      boolean wetHere = world.waterHeight(floor(pos.x()), floor(pos.y()), floor(pos.z())) > 0.0F;
      if (!bobbing && onGround && !wetHere) {
        return new Trajectory(
            points, Trajectory.End.BLOCK, tickCount, null, Face.UP, pos, lastMove);
      }
      if (bobbing && Math.hypot(vel.x(), vel.z()) < SETTLED_SPEED) {
        Vec3d surface = surface(world, pos);
        if (surface != null) {
          points.add(surface);
          return new Trajectory(
              points, Trajectory.End.FLOATING, tickCount, null, Face.UP, surface, lastMove);
        }
        if (onGround) {
          return new Trajectory(
              points, Trajectory.End.BLOCK, tickCount, null, Face.UP, pos, lastMove);
        }
      }
    }
    return new Trajectory(points, Trajectory.End.MAX_TICKS, maxTicks);
  }

  /** The water surface above or at a position, or {@code null} out of water. */
  private static Vec3d surface(WorldView world, Vec3d pos) {
    int x = floor(pos.x());
    int z = floor(pos.z());
    int y = floor(pos.y());
    float height = world.waterHeight(x, y, z);
    if (height <= 0.0F) {
      return null;
    }
    // Up through full water to the block the surface is in.
    for (int up = 0; up < 64 && height >= 1.0F && world.waterHeight(x, y + 1, z) > 0.0F; up++) {
      y++;
      height = world.waterHeight(x, y, z);
    }
    return new Vec3d(pos.x(), y + height, pos.z());
  }

  /**
   * {@code Entity.collideWithShapes}: the movement along each axis, Y first, then the larger of X
   * and Z ({@code Direction.axisStepOrder}), each cut by the shapes in the way.
   */
  static Vec3d collide(Aabb box, Vec3d movement, List<Aabb> shapes) {
    if (shapes.isEmpty()) {
      return movement;
    }
    double x = 0.0;
    double y = 0.0;
    double z = 0.0;
    Aabb at = box;
    if (movement.y() != 0.0) {
      y = along(1, at, movement.y(), shapes);
      at = at.move(0.0, y, 0.0);
    }
    boolean zFirst = Math.abs(movement.x()) < Math.abs(movement.z());
    if (zFirst && movement.z() != 0.0) {
      z = along(2, at, movement.z(), shapes);
      at = at.move(0.0, 0.0, z);
    }
    if (movement.x() != 0.0) {
      x = along(0, at, movement.x(), shapes);
      at = at.move(x, 0.0, 0.0);
    }
    if (!zFirst && movement.z() != 0.0) {
      z = along(2, at, movement.z(), shapes);
    }
    return new Vec3d(x, y, z);
  }

  /** {@code Shapes.collide} on one axis: how far the box can move before touching a shape. */
  private static double along(int axis, Aabb box, double distance, List<Aabb> shapes) {
    if (Math.abs(distance) < EPSILON) {
      return 0.0;
    }
    double[] lo = {box.minX(), box.minY(), box.minZ()};
    double[] hi = {box.maxX(), box.maxY(), box.maxZ()};
    for (Aabb shape : shapes) {
      double[] slo = {shape.minX(), shape.minY(), shape.minZ()};
      double[] shi = {shape.maxX(), shape.maxY(), shape.maxZ()};
      boolean overlaps = true;
      for (int other = 0; other < 3 && overlaps; other++) {
        if (other != axis) {
          overlaps = hi[other] - EPSILON > slo[other] && lo[other] + EPSILON < shi[other];
        }
      }
      if (!overlaps) {
        continue;
      }
      if (distance > 0.0 && hi[axis] - EPSILON <= slo[axis]) {
        distance = Math.min(distance, slo[axis] - hi[axis]);
      } else if (distance < 0.0 && lo[axis] + EPSILON >= shi[axis]) {
        distance = Math.max(distance, shi[axis] - lo[axis]);
      }
    }
    return distance;
  }

  /** The hook's box at a position. */
  public static Aabb box(Vec3d position) {
    return Aabb.ofEntity(position, SIZE, SIZE);
  }

  private static int floor(double value) {
    return (int) Math.floor(value);
  }
}
