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
 */
public record Trajectory(
    List<Vec3d> points, End end, int ticks, WorldView.EntityTarget entity, Face face) {

  public Trajectory {
    points = List.copyOf(points);
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
    /** Started a tick inside a collision box. */
    STUCK,
    /** Fell below the world. */
    VOID,
    /** Reached the tick limit while still flying. */
    MAX_TICKS,
    /** Reached terrain the client has not loaded; nothing beyond this point is known. */
    UNKNOWN
  }
}
