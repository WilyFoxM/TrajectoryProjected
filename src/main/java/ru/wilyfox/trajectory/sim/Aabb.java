package ru.wilyfox.trajectory.sim;

/**
 * Axis-aligned box with the semantics of vanilla {@code net.minecraft.world.phys.AABB}: half-open
 * {@link #contains}, {@link #expandTowards}, {@link #inflate} and the ray {@link #clip}.
 */
public record Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
  private static final double EPSILON = 1.0E-7;

  /**
   * Entity box as built by vanilla {@code EntityDimensions.makeBoundingBox}: centered on x/z,
   * standing on y, with the half width computed in {@code float}.
   */
  public static Aabb ofEntity(Vec3d position, float width, float height) {
    float halfWidth = width / 2.0F;
    return new Aabb(
        position.x() - halfWidth,
        position.y(),
        position.z() - halfWidth,
        position.x() + halfWidth,
        position.y() + height,
        position.z() + halfWidth);
  }

  public Aabb expandTowards(Vec3d delta) {
    double nMinX = minX;
    double nMinY = minY;
    double nMinZ = minZ;
    double nMaxX = maxX;
    double nMaxY = maxY;
    double nMaxZ = maxZ;
    if (delta.x() < 0.0) {
      nMinX += delta.x();
    } else if (delta.x() > 0.0) {
      nMaxX += delta.x();
    }

    if (delta.y() < 0.0) {
      nMinY += delta.y();
    } else if (delta.y() > 0.0) {
      nMaxY += delta.y();
    }

    if (delta.z() < 0.0) {
      nMinZ += delta.z();
    } else if (delta.z() > 0.0) {
      nMaxZ += delta.z();
    }

    return new Aabb(nMinX, nMinY, nMinZ, nMaxX, nMaxY, nMaxZ);
  }

  public Aabb inflate(double amount) {
    return new Aabb(
        minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount);
  }

  public boolean contains(Vec3d point) {
    return point.x() >= minX
        && point.x() < maxX
        && point.y() >= minY
        && point.y() < maxY
        && point.z() >= minZ
        && point.z() < maxZ;
  }

  public boolean intersects(Aabb other) {
    return minX < other.maxX
        && maxX > other.minX
        && minY < other.maxY
        && maxY > other.minY
        && minZ < other.maxZ
        && maxZ > other.minZ;
  }

  public Vec3d center() {
    return new Vec3d((minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
  }

  /** Point where the segment from {@code from} to {@code to} enters the box, or {@code null}. */
  public Vec3d clip(Vec3d from, Vec3d to) {
    Hit hit = clipWithFace(from, to);
    return hit == null ? null : hit.location();
  }

  /** Like {@link #clip}, also reporting the face that was entered. */
  public Hit clipWithFace(Vec3d from, Vec3d to) {
    double[] scale = {1.0};
    double dx = to.x() - from.x();
    double dy = to.y() - from.y();
    double dz = to.z() - from.z();
    Face face = null;
    if (dx > EPSILON) {
      face =
          clipPoint(
              scale, face, dx, dy, dz, minX, minY, maxY, minZ, maxZ, Face.WEST, from.x(), from.y(),
              from.z());
    } else if (dx < -EPSILON) {
      face =
          clipPoint(
              scale, face, dx, dy, dz, maxX, minY, maxY, minZ, maxZ, Face.EAST, from.x(), from.y(),
              from.z());
    }

    if (dy > EPSILON) {
      face =
          clipPoint(
              scale, face, dy, dz, dx, minY, minZ, maxZ, minX, maxX, Face.DOWN, from.y(), from.z(),
              from.x());
    } else if (dy < -EPSILON) {
      face =
          clipPoint(
              scale, face, dy, dz, dx, maxY, minZ, maxZ, minX, maxX, Face.UP, from.y(), from.z(),
              from.x());
    }

    if (dz > EPSILON) {
      face =
          clipPoint(
              scale,
              face,
              dz,
              dx,
              dy,
              minZ,
              minX,
              maxX,
              minY,
              maxY,
              Face.NORTH,
              from.z(),
              from.x(),
              from.y());
    } else if (dz < -EPSILON) {
      face =
          clipPoint(
              scale,
              face,
              dz,
              dx,
              dy,
              maxZ,
              minX,
              maxX,
              minY,
              maxY,
              Face.SOUTH,
              from.z(),
              from.x(),
              from.y());
    }

    if (face == null) {
      return null;
    }

    double s = scale[0];
    return new Hit(from.add(s * dx, s * dy, s * dz), face);
  }

  private static Face clipPoint(
      double[] scale,
      Face face,
      double da,
      double db,
      double dc,
      double point,
      double minB,
      double maxB,
      double minC,
      double maxC,
      Face newFace,
      double fromA,
      double fromB,
      double fromC) {
    double s = (point - fromA) / da;
    double pb = fromB + s * db;
    double pc = fromC + s * dc;
    if (0.0 < s
        && s < scale[0]
        && minB - EPSILON < pb
        && pb < maxB + EPSILON
        && minC - EPSILON < pc
        && pc < maxC + EPSILON) {
      scale[0] = s;
      return newFace;
    }
    return face;
  }

  /** Entry point of a ray into a box and the face it crossed. */
  public record Hit(Vec3d location, Face face) {}
}
