package ru.wilyfox.trajectory.sim;

import java.util.List;

/**
 * The narrow view of the world the simulation needs. A game adapter implements it with vanilla
 * logic; tests implement it with simple geometry.
 */
public interface WorldView {

  /**
   * Block ray from {@code from} to {@code to} with collision shapes and the world border, fluids
   * ignored: vanilla {@code Level.clipIncludingBorder} with {@code ClipContext.Block.COLLIDER} and
   * {@code ClipContext.Fluid.NONE}.
   */
  BlockClip clip(Vec3d from, Vec3d to);

  /**
   * Whether {@code position} lies inside a collision box of the block containing it (start of
   * {@code AbstractArrow.tick}).
   */
  boolean isInsideCollision(Vec3d position);

  /**
   * Whether an entity with this box counts as touching water ({@code
   * EntityFluidInteraction.update}).
   */
  boolean isInWater(Aabb box);

  /**
   * Entities in {@code area} that the arrow could hit: alive, pickable and not excluded by game
   * rules such as player-versus-player settings. Must include the shooter and everything in the
   * shooter's vehicle group, flagged with {@link EntityTarget#shooterGroup()}; the simulation
   * decides when those become hittable.
   */
  List<EntityTarget> entities(Aabb area);

  /** Arrows are removed below {@code minY - 64} ({@code Entity.checkBelowWorld}). */
  double minY();

  /** Result of a block ray. */
  record BlockClip(Type type, Vec3d location, Face face) {
    public static BlockClip miss(Vec3d end) {
      return new BlockClip(Type.MISS, end, null);
    }

    public static BlockClip unknown(Vec3d at) {
      return new BlockClip(Type.UNKNOWN, at, null);
    }

    public static BlockClip block(Vec3d location, Face face) {
      return new BlockClip(Type.BLOCK, location, face);
    }

    /**
     * MISS: nothing on the segment, {@code location} is its end. BLOCK: a collision box or the
     * world border was hit. UNKNOWN: the segment touches unloaded terrain; the simulation stops
     * instead of guessing.
     */
    public enum Type {
      MISS,
      BLOCK,
      UNKNOWN
    }
  }

  /**
   * A hittable entity.
   *
   * @param id stable identifier from the adapter (the entity id)
   * @param box current bounding box
   * @param position entity position, used by vanilla to order several hits
   * @param shooterGroup the shooter or an entity sharing its root vehicle
   */
  record EntityTarget(int id, Aabb box, Vec3d position, boolean shooterGroup) {}
}
