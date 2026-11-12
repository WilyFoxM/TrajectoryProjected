package ru.wilyfox.trajectory.sim;

import java.util.List;

/**
 * A simulated flight.
 *
 * @param points the spawn position, then the position at the end of every tick; for a hit, the last
 *     point is where the arrow comes to rest
 * @param end why the simulation stopped
 * @param ticks ticks simulated, counting the tick that ended the flight
 * @param entity the entity hit, for {@link End#ENTITY}
 * @param face the block face hit, for {@link End#BLOCK}
 * @param impact where the path meets the block or entity, before vanilla backs a stuck arrow off
 *     it; {@code null} unless the flight ended in a hit
 * @param arrival the movement of the final tick, along which the arrow meets the target; {@code
 *     null} unless the flight ended in a hit
 * @param certainPoints how many leading points the projectile surely reaches; past them the flight
 *     may already have ended at a random moment (a firework's lifetime). All points for flights
 *     without randomness.
 */
public record Trajectory(
    List<Vec3d> points,
    End end,
    int ticks,
    WorldView.EntityTarget entity,
    Face face,
    Vec3d impact,
    Vec3d arrival,
    int certainPoints) {

  public Trajectory {
    points = List.copyOf(points);
    certainPoints = Math.clamp(certainPoints, 0, points.size());
  }

  /** A flight without randomness: every point is certain. */
  public Trajectory(
      List<Vec3d> points,
      End end,
      int ticks,
      WorldView.EntityTarget entity,
      Face face,
      Vec3d impact,
      Vec3d arrival) {
    this(points, end, ticks, entity, face, impact, arrival, points.size());
  }

  /** A flight that ended without a hit. */
  public Trajectory(List<Vec3d> points, End end, int ticks) {
    this(points, end, ticks, null, null, null, null);
  }

  public Vec3d last() {
    return points.getLast();
  }

  /** Why a simulation stopped. */
  public enum End {
    /** Hit a block or the world border. */
    BLOCK,
    /** Hit an entity. */
    ENTITY,
    /** Came to rest floating on water: a fishing hook. */
    FLOATING,
    /** Started a tick inside a collision box. */
    STUCK,
    /** Fell below the world. */
    VOID,
    /** Reached the tick limit while still flying. */
    MAX_TICKS,
    /** A firework's lifetime ran out: it explodes in the air. */
    EXPLODED,
    /** Reached terrain the client has not loaded; nothing beyond this point is known. */
    UNKNOWN
  }
}
