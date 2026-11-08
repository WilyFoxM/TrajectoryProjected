package ru.wilyfox.trajectory.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.wilyfox.trajectory.sim.Aabb;
import ru.wilyfox.trajectory.sim.FaceShadow;
import ru.wilyfox.trajectory.sim.SpreadZone;
import ru.wilyfox.trajectory.sim.Vec3d;

/**
 * The block surfaces a spread zone falls on, like the shadow of a round object cast along the
 * arrow's arrival direction: faces turned towards the arrow, inside the zone's reach, minus the
 * shadows other blocks cast on them along that direction. Gathered once per tick, for every place
 * the frames until the next tick draw the zone: from where it was, to where it is now.
 *
 * <p>Collision shapes are used, as for the arrow itself: grass and flowers neither catch the zone
 * nor shade it.
 */
final class ZoneSurfaces {
  /**
   * How far along the arrival direction faces may lie from the zone's plane, in blocks: at least
   * this, more for wide zones, since on ground the arrow meets at a shallow angle the zone's far
   * edge lies deep behind the plane.
   */
  private static final double MIN_DEPTH = 2.5;

  /**
   * Depth per block of the zone's longer axis, at least: covers surfaces down to about 10 degrees
   * to the arrow; on flat ground the shallower arrival angle is followed instead.
   */
  private static final double DEPTH_PER_RADIUS = 6.0;

  private static final double MAX_DEPTH = 24.0;

  /** How far back along the arrival direction blocks are taken as casting shadows, in blocks. */
  private static final double SHADOW_REACH = 8.0;

  /** Patches are lifted off the block by this much to stay in front of it. */
  private static final double LIFT = 0.002;

  /** How far past its edge an ellipse counts as reaching a face, as a share of the radius. */
  private static final double REACH_STILL = 1.05;

  /** More while moving: an edge between two waypoints lies further out than either ellipse. */
  private static final double REACH_MOVING = 1.2;

  /** The zone is skipped when its reach would cover more blocks than this. */
  private static final int MAX_BLOCKS = 12000;

  /** A lit convex piece of a face, its corners in order, camera-independent. */
  record Patch(List<Vec3d> corners) {}

  private ZoneSurfaces() {}

  /**
   * The surfaces of {@code zone}, and of the way to it from {@code from} if frames blend from there
   * ({@code null} if not). Nothing behind {@code shadowsFrom} casts a shadow: behind the shooter, a
   * wall or the ground is on the straight line back from the hit, but not on the way of the shot.
   */
  static List<Patch> patches(
      ClientLevel level, SpreadZone.Zone zone, SpreadZone.Zone from, Vec3d shadowsFrom) {
    List<SpreadZone.Zone> way = from == null ? List.of(zone) : from.waypoints(zone);
    double reach = from == null ? REACH_STILL : REACH_MOVING;
    Vec3d dir = zone.direction();
    double depth = 0.0;
    for (SpreadZone.Zone at : way) {
      depth = Math.max(depth, depth(at));
    }
    double[] faceMin = new double[3];
    double[] faceMax = new double[3];
    int[] lo = new int[3];
    int[] hi = new int[3];
    // A wide zone at a shallow angle can reach over too many blocks: search less deep rather
    // than drop the zone.
    while (true) {
      bounds(way, depth, faceMin, faceMax, lo, hi);
      long blocks = (long) (hi[0] - lo[0] + 1) * (hi[1] - lo[1] + 1) * (hi[2] - lo[2] + 1);
      if (blocks <= MAX_BLOCKS) {
        break;
      }
      if (depth <= MIN_DEPTH) {
        return List.of();
      }
      depth = Math.max(MIN_DEPTH, depth * 0.5);
    }
    AABB faceRegion =
        new AABB(
            Mth.floor(faceMin[0]) - 1,
            Mth.floor(faceMin[1]) - 1,
            Mth.floor(faceMin[2]) - 1,
            Mth.floor(faceMax[0]) + 2,
            Mth.floor(faceMax[1]) + 2,
            Mth.floor(faceMax[2]) + 2);

    List<Aabb> casters = new ArrayList<>();
    List<AABB> surfaces = new ArrayList<>();
    List<BlockPos> surfacePositions = new ArrayList<>();
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    for (int x = lo[0]; x <= hi[0]; x++) {
      for (int y = lo[1]; y <= hi[1]; y++) {
        for (int z = lo[2]; z <= hi[2]; z++) {
          pos.set(x, y, z);
          BlockState state = level.getBlockState(pos);
          if (state.isAir()) {
            continue;
          }
          VoxelShape shape = state.getCollisionShape(level, pos);
          if (shape.isEmpty()) {
            continue;
          }
          boolean full = Block.isShapeFullBlock(shape);
          boolean surface = faceRegion.contains(x + 0.5, y + 0.5, z + 0.5);
          // A full block whose every side towards the arrow touches another full block casts
          // nothing those neighbours do not already cast, and catches nothing.
          if (full && buried(level, pos, dir)) {
            continue;
          }
          BlockPos at = pos.immutable();
          for (AABB box : shape.toAabbs()) {
            AABB world = box.move(at);
            casters.add(
                new Aabb(world.minX, world.minY, world.minZ, world.maxX, world.maxY, world.maxZ));
            if (surface) {
              surfaces.add(world);
              surfacePositions.add(at);
            }
          }
        }
      }
    }

    List<Patch> patches = new ArrayList<>();
    for (int i = 0; i < surfaces.size(); i++) {
      addPatches(
          level,
          way,
          dir,
          shadowsFrom,
          reach,
          depth,
          surfacePositions.get(i),
          surfaces.get(i),
          casters,
          patches);
    }
    return patches;
  }

  /** The face search region and the shadow caster region, in blocks, for a search depth. */
  private static void bounds(
      List<SpreadZone.Zone> way,
      double depth,
      double[] faceMin,
      double[] faceMax,
      int[] lo,
      int[] hi) {
    double[] castMin = new double[3];
    double[] castMax = new double[3];
    for (int i = 0; i < 3; i++) {
      faceMin[i] = castMin[i] = Double.MAX_VALUE;
      faceMax[i] = castMax[i] = -Double.MAX_VALUE;
    }
    for (SpreadZone.Zone zone : way) {
      Vec3d dir = zone.direction();
      for (int su = -1; su <= 1; su += 2) {
        for (int sv = -1; sv <= 1; sv += 2) {
          Vec3d edge = zone.center().add(zone.axisU().scale(su)).add(zone.axisV().scale(sv));
          for (int sd = -1; sd <= 1; sd += 2) {
            Vec3d p = edge.add(dir.scale(sd * depth));
            include(faceMin, faceMax, p);
            include(castMin, castMax, p);
          }
          include(castMin, castMax, edge.add(dir.scale(-SHADOW_REACH)));
        }
      }
    }
    for (int i = 0; i < 3; i++) {
      lo[i] = Mth.floor(castMin[i]) - 1;
      hi[i] = Mth.floor(castMax[i]) + 1;
    }
  }

  private static double depth(SpreadZone.Zone zone) {
    double radius = Math.max(zone.axisU().length(), zone.axisV().length());
    Vec3d d = zone.direction();
    // On flat ground the zone's far edge lies radius * cot(arrival angle) behind its plane.
    double horizontal = Math.hypot(d.x(), d.z());
    double cot = horizontal / Math.max(Math.abs(d.y()), 1.0e-3);
    double perRadius = Math.max(DEPTH_PER_RADIUS, cot + 1.0);
    return Math.clamp(radius * perRadius, MIN_DEPTH, MAX_DEPTH);
  }

  private static boolean buried(ClientLevel level, BlockPos pos, Vec3d dir) {
    for (Direction side : Direction.values()) {
      double facing =
          side.getStepX() * dir.x() + side.getStepY() * dir.y() + side.getStepZ() * dir.z();
      if (facing >= 0.0) {
        continue;
      }
      BlockPos next = pos.relative(side);
      if (!level.getBlockState(next).isCollisionShapeFullBlock(level, next)) {
        return false;
      }
    }
    return true;
  }

  private static void addPatches(
      ClientLevel level,
      List<SpreadZone.Zone> way,
      Vec3d dir,
      Vec3d shadowsFrom,
      double reach,
      double depth,
      BlockPos pos,
      AABB box,
      List<Aabb> casters,
      List<Patch> out) {
    for (Direction side : Direction.values()) {
      double nx = side.getStepX();
      double ny = side.getStepY();
      double nz = side.getStepZ();
      // Only faces turned towards the incoming arrow catch the zone.
      if (nx * dir.x() + ny * dir.y() + nz * dir.z() > -0.05) {
        continue;
      }
      FaceShadow.AxisFace face = face(box, side);
      if (way.stream().noneMatch(zone -> touches(zone, face, reach, depth))) {
        continue;
      }
      if (coveredByNeighbour(level, pos, box, side)) {
        continue;
      }
      Vec3d lift = new Vec3d(nx * LIFT, ny * LIFT, nz * LIFT);
      for (List<Vec3d> piece : FaceShadow.litPieces(face, dir, casters, shadowsFrom)) {
        List<Vec3d> corners = new ArrayList<>(piece.size());
        for (Vec3d corner : piece) {
          corners.add(corner.add(lift));
        }
        out.add(new Patch(List.copyOf(corners)));
      }
    }
  }

  private static FaceShadow.AxisFace face(AABB b, Direction side) {
    return switch (side) {
      case DOWN -> new FaceShadow.AxisFace(1, -1, b.minY, b.minX, b.maxX, b.minZ, b.maxZ);
      case UP -> new FaceShadow.AxisFace(1, 1, b.maxY, b.minX, b.maxX, b.minZ, b.maxZ);
      case WEST -> new FaceShadow.AxisFace(0, -1, b.minX, b.minY, b.maxY, b.minZ, b.maxZ);
      case EAST -> new FaceShadow.AxisFace(0, 1, b.maxX, b.minY, b.maxY, b.minZ, b.maxZ);
      case NORTH -> new FaceShadow.AxisFace(2, -1, b.minZ, b.minX, b.maxX, b.minY, b.maxY);
      case SOUTH -> new FaceShadow.AxisFace(2, 1, b.maxZ, b.minX, b.maxX, b.minY, b.maxY);
    };
  }

  /** Whether the face can reach the unit ellipse and lies within the zone's depth. */
  private static boolean touches(
      SpreadZone.Zone zone, FaceShadow.AxisFace face, double reach, double depth) {
    double minU = Double.MAX_VALUE;
    double maxU = -Double.MAX_VALUE;
    double minV = Double.MAX_VALUE;
    double maxV = -Double.MAX_VALUE;
    double minD = Double.MAX_VALUE;
    double maxD = -Double.MAX_VALUE;
    Vec3d dir = zone.direction();
    for (double a : new double[] {face.minA(), face.maxA()}) {
      for (double b : new double[] {face.minB(), face.maxB()}) {
        Vec3d corner = corner(face, a, b);
        double[] c = zone.coordinates(corner);
        minU = Math.min(minU, c[0]);
        maxU = Math.max(maxU, c[0]);
        minV = Math.min(minV, c[1]);
        maxV = Math.max(maxV, c[1]);
        Vec3d rel = corner.subtract(zone.center());
        double d = rel.x() * dir.x() + rel.y() * dir.y() + rel.z() * dir.z();
        minD = Math.min(minD, d);
        maxD = Math.max(maxD, d);
      }
    }
    return maxU >= -reach
        && minU <= reach
        && maxV >= -reach
        && minV <= reach
        && maxD >= -depth
        && minD <= depth;
  }

  private static Vec3d corner(FaceShadow.AxisFace face, double a, double b) {
    return switch (face.axis()) {
      case 0 -> new Vec3d(face.plane(), a, b);
      case 1 -> new Vec3d(a, face.plane(), b);
      default -> new Vec3d(a, b, face.plane());
    };
  }

  /** A face on the block's boundary against a full neighbouring block is never seen. */
  private static boolean coveredByNeighbour(
      ClientLevel level, BlockPos pos, AABB box, Direction side) {
    boolean onBoundary =
        switch (side) {
          case DOWN -> box.minY <= pos.getY() + 1.0e-6;
          case UP -> box.maxY >= pos.getY() + 1 - 1.0e-6;
          case NORTH -> box.minZ <= pos.getZ() + 1.0e-6;
          case SOUTH -> box.maxZ >= pos.getZ() + 1 - 1.0e-6;
          case WEST -> box.minX <= pos.getX() + 1.0e-6;
          case EAST -> box.maxX >= pos.getX() + 1 - 1.0e-6;
        };
    if (!onBoundary) {
      return false;
    }
    BlockPos next = pos.relative(side);
    return level.getBlockState(next).isCollisionShapeFullBlock(level, next);
  }

  private static void include(double[] min, double[] max, Vec3d p) {
    min[0] = Math.min(min[0], p.x());
    min[1] = Math.min(min[1], p.y());
    min[2] = Math.min(min[2], p.z());
    max[0] = Math.max(max[0], p.x());
    max[1] = Math.max(max[1], p.y());
    max[2] = Math.max(max[2], p.z());
  }
}
