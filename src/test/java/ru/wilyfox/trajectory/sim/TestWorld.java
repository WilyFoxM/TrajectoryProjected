package ru.wilyfox.trajectory.sim;

import java.util.ArrayList;
import java.util.List;

/** Boxes in empty space: solid blocks, water volumes, entities and an unloaded region. */
final class TestWorld implements WorldView {
  final List<Aabb> solids = new ArrayList<>();
  final List<Aabb> water = new ArrayList<>();
  final List<EntityTarget> entities = new ArrayList<>();
  double unloadedBeyondZ = Double.POSITIVE_INFINITY;
  double minY = -64.0;
  int entityQueries;

  /** A floor whose top surface is at {@code y}. */
  static TestWorld floorAt(double y) {
    TestWorld world = new TestWorld();
    world.solids.add(new Aabb(-1.0E4, y - 1.0, -1.0E4, 1.0E4, y, 1.0E4));
    return world;
  }

  static TestWorld empty() {
    return new TestWorld();
  }

  @Override
  public BlockClip clip(Vec3d from, Vec3d to) {
    if (Math.max(from.z(), to.z()) > unloadedBeyondZ) {
      return BlockClip.unknown(from);
    }
    Aabb.Hit best = null;
    double bestDist = Double.POSITIVE_INFINITY;
    for (Aabb solid : solids) {
      Aabb.Hit hit = solid.clipWithFace(from, to);
      if (hit != null && from.distanceToSqr(hit.location()) < bestDist) {
        best = hit;
        bestDist = from.distanceToSqr(hit.location());
      }
    }
    return best == null ? BlockClip.miss(to) : BlockClip.block(best.location(), best.face());
  }

  @Override
  public boolean isInsideCollision(Vec3d position) {
    for (Aabb solid : solids) {
      if (solid.contains(position)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isInWater(Aabb box) {
    for (Aabb volume : water) {
      if (volume.intersects(box)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public List<Aabb> collisions(Aabb area) {
    List<Aabb> found = new ArrayList<>();
    for (Aabb solid : solids) {
      if (solid.inflate(1.0E-7).intersects(area)) {
        found.add(solid);
      }
    }
    return found;
  }

  /** Water fills a block whose bottom lies in a water volume, up to the volume's top. */
  @Override
  public float waterHeight(int x, int y, int z) {
    for (Aabb volume : water) {
      if (volume.contains(new Vec3d(x + 0.5, y + 0.01, z + 0.5))) {
        return (float) Math.min(1.0, volume.maxY() - y);
      }
    }
    return 0.0F;
  }

  @Override
  public List<EntityTarget> entities(Aabb area) {
    entityQueries++;
    List<EntityTarget> found = new ArrayList<>();
    for (EntityTarget target : entities) {
      if (target.box().intersects(area)) {
        found.add(target);
      }
    }
    return found;
  }

  @Override
  public double minY() {
    return minY;
  }

  /** A player-sized entity standing at the given feet position. */
  static EntityTarget player(int id, Vec3d feet, boolean shooterGroup) {
    return new EntityTarget(id, Aabb.ofEntity(feet, 0.6F, 1.8F), feet, shooterGroup);
  }
}
