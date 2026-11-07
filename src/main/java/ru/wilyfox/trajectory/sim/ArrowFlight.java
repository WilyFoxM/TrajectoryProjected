package ru.wilyfox.trajectory.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Flight of an arrow, tick by tick, in the order of vanilla 26.2 {@code AbstractArrow.tick}:
 *
 * <ol>
 *   <li>stop if the position is inside a block's collision box;
 *   <li>in water (state from the previous tick): velocity × 0.6;
 *   <li>move along the velocity captured at the start of the tick, stopping at the first entity or
 *       block ({@code stepMoveAndHit});
 *   <li>out of water: velocity × 0.99;
 *   <li>gravity: velocity.y − 0.05;
 *   <li>{@code baseTick}: update the in-water state from the new position.
 * </ol>
 *
 * <p>Not modelled yet: water currents and bubble columns, piercing, deflection by entities such as
 * the Breeze, world-border bounce beyond stopping at it, portals.
 */
public final class ArrowFlight {
  /** {@code AbstractArrow.getDefaultGravity}. */
  public static final double GRAVITY = 0.05;

  /** {@code AbstractArrow.getAirDrag}. */
  public static final float AIR_DRAG = 0.99F;

  /** {@code AbstractArrow.getWaterInertia} for arrows. */
  public static final float WATER_INERTIA = 0.6F;

  /** {@code EntityTypes.ARROW}: {@code sized(0.5F, 0.5F)}. */
  public static final float WIDTH = 0.5F;

  public static final float HEIGHT = 0.5F;

  /** {@code AbstractArrow.onHitBlock}: the arrow backs off by this much along each axis. */
  private static final float BLOCK_BACKOFF = 0.05F;

  private ArrowFlight() {}

  /**
   * Simulates the flight from a spawn state until it ends or {@code maxTicks} pass.
   *
   * @param spawn position before the first tick
   * @param velocity velocity before the first tick
   */
  public static Trajectory simulate(Vec3d spawn, Vec3d velocity, WorldView world, int maxTicks) {
    List<Vec3d> points = new ArrayList<>();
    points.add(spawn);
    Vec3d pos = spawn;
    Vec3d vel = velocity;
    boolean inWater = false;
    boolean leftOwner = false;

    for (int tickCount = 1; tickCount <= maxTicks; tickCount++) {
      Vec3d movement = vel;
      if (world.isInsideCollision(pos)) {
        return new Trajectory(points, Trajectory.End.STUCK, tickCount);
      }

      Vec3d origin = pos;
      if (inWater) {
        vel = vel.scale(WATER_INERTIA);
      }

      if (!leftOwner) {
        leftOwner = outsideShooter(world, pos, vel);
      }

      WorldView.BlockClip blocks = world.clip(origin, origin.add(movement));
      if (blocks.type() == WorldView.BlockClip.Type.UNKNOWN) {
        return new Trajectory(points, Trajectory.End.UNKNOWN, tickCount);
      }

      Aabb searchArea = box(pos).expandTowards(vel).inflate(1.0);
      List<Hit> hits =
          entityHits(world, origin, blocks.location(), searchArea, margin(tickCount), leftOwner);
      if (!hits.isEmpty()) {
        hits.sort(Comparator.comparingDouble(h -> origin.distanceToSqr(h.target().position())));
        Hit first = hits.getFirst();
        points.add(first.location());
        return new Trajectory(
            points,
            Trajectory.End.ENTITY,
            tickCount,
            first.target(),
            null,
            first.location(),
            movement);
      }

      pos = blocks.location();
      if (blocks.type() == WorldView.BlockClip.Type.BLOCK) {
        Vec3d backoff =
            new Vec3d(Math.signum(vel.x()), Math.signum(vel.y()), Math.signum(vel.z()))
                .scale(BLOCK_BACKOFF);
        points.add(pos.subtract(backoff));
        return new Trajectory(
            points, Trajectory.End.BLOCK, tickCount, null, blocks.face(), pos, movement);
      }

      if (!inWater) {
        vel = vel.scale(AIR_DRAG);
      }
      vel = vel.add(0.0, -GRAVITY, 0.0);

      inWater = world.isInWater(box(pos));
      points.add(pos);
      if (pos.y() < world.minY() - 64.0) {
        return new Trajectory(points, Trajectory.End.VOID, tickCount);
      }
    }

    return new Trajectory(points, Trajectory.End.MAX_TICKS, maxTicks);
  }

  /** Arrow bounding box at a position. */
  public static Aabb box(Vec3d position) {
    return Aabb.ofEntity(position, WIDTH, HEIGHT);
  }

  /**
   * {@code ProjectileUtil.computeMargin}: entity boxes grow from 0 to 0.3 over the first ticks,
   * with {@code tickCount} already incremented for the current tick.
   */
  public static float margin(int tickCount) {
    return Math.max(0.0F, Math.min(0.3F, (tickCount - 2) / 20.0F));
  }

  /**
   * {@code Projectile.isOutsideOwnerCollisionRange}: no member of the shooter's vehicle group
   * touches the arrow box expanded by the velocity and by one block.
   */
  private static boolean outsideShooter(WorldView world, Vec3d pos, Vec3d vel) {
    return outsideShooter(world, box(pos), vel);
  }

  /** {@link #outsideShooter(WorldView, Vec3d, Vec3d)} for a projectile box of any size. */
  static boolean outsideShooter(WorldView world, Aabb projectile, Vec3d vel) {
    Aabb area = projectile.expandTowards(vel).inflate(1.0);
    for (WorldView.EntityTarget target : world.entities(area)) {
      if (target.shooterGroup() && area.intersects(target.box())) {
        return false;
      }
    }
    return true;
  }

  /**
   * {@code ProjectileUtil.getManyEntityHitResult} with {@code includeFromEntity = false}: exact box
   * hits, and hits within the margin that are confirmed by a ray towards the box center.
   */
  static List<Hit> entityHits(
      WorldView world, Vec3d from, Vec3d to, Aabb area, float margin, boolean leftOwner) {
    List<Hit> hits = new ArrayList<>();
    for (WorldView.EntityTarget target : world.entities(area)) {
      if (target.shooterGroup() && !leftOwner) {
        continue;
      }

      Aabb box = target.box();
      Vec3d exact = box.clip(from, to);
      if (exact != null) {
        hits.add(new Hit(target, exact));
        continue;
      }

      if (margin <= 0.0F) {
        continue;
      }

      Vec3d outside = box.inflate(margin).clip(from, to);
      if (outside == null) {
        continue;
      }

      Vec3d towards = box.center();
      WorldView.BlockClip blocked = world.clip(outside, towards);
      if (blocked.type() != WorldView.BlockClip.Type.MISS) {
        towards = blocked.location();
      }

      Vec3d surface = box.clip(outside, towards);
      if (surface != null) {
        hits.add(new Hit(target, surface));
      }
    }
    return hits;
  }

  record Hit(WorldView.EntityTarget target, Vec3d location) {}
}
