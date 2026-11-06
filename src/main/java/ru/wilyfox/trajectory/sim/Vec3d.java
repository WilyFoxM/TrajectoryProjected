package ru.wilyfox.trajectory.sim;

/**
 * Immutable double vector with the arithmetic of vanilla {@code net.minecraft.world.phys.Vec3}.
 * Operations keep vanilla's evaluation order so results match bit for bit.
 */
public record Vec3d(double x, double y, double z) {
  public static final Vec3d ZERO = new Vec3d(0.0, 0.0, 0.0);

  public Vec3d add(Vec3d other) {
    return new Vec3d(x + other.x, y + other.y, z + other.z);
  }

  public Vec3d add(double dx, double dy, double dz) {
    return new Vec3d(x + dx, y + dy, z + dz);
  }

  public Vec3d subtract(Vec3d other) {
    return new Vec3d(x - other.x, y - other.y, z - other.z);
  }

  public Vec3d scale(double factor) {
    return new Vec3d(x * factor, y * factor, z * factor);
  }

  /** {@code Vec3.normalize}: vectors shorter than {@code 1.0E-5F} become zero. */
  public Vec3d normalize() {
    double dist = Math.sqrt(x * x + y * y + z * z);
    return dist < 1.0E-5F ? ZERO : new Vec3d(x / dist, y / dist, z / dist);
  }

  public double lengthSqr() {
    return x * x + y * y + z * z;
  }

  public double length() {
    return Math.sqrt(x * x + y * y + z * z);
  }

  public double distanceToSqr(Vec3d other) {
    double dx = other.x - x;
    double dy = other.y - y;
    double dz = other.z - z;
    return dx * dx + dy * dy + dz * dz;
  }
}
