package ru.wilyfox.trajectory.sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Where the server's random spread can put the arrow around the predicted hit.
 *
 * <p>Vanilla adds an offset of up to {@link Launch#SPREAD_HALF_WIDTH} per axis, from a triangle
 * distribution, to the unit launch direction ({@code Projectile.getMovementToShoot}). A fixed set
 * of offsets — the eight corners of that cube and a seeded triangle sample — is flown in free air
 * up to the plane through the predicted hit that faces the arrow's arrival direction. An ellipse in
 * that plane is fitted to the crossings: it holds every corner, so by convexity every possible shot
 * up to the small curvature of the mapping, and {@link Zone#coreRatio()} marks where most shots
 * cross. Projected along the arrival direction onto the terrain, it is the zone the arrow can land
 * in.
 *
 * <p>Free air is an approximation: water and obstacles on the way are not considered for the
 * spread, only for the predicted path itself.
 */
public final class SpreadZone {
  /** Seeded triangle samples in addition to the eight corners and the center. */
  public static final int SAMPLES = 64;

  /** Share of samples inside the core ellipse. */
  public static final double CORE_SHARE = 0.5;

  /** Axes never shrink below this, so a zone seen exactly edge-on stays drawable. */
  private static final double MIN_RADIUS = 0.05;

  private static final List<Vec3d> OFFSETS = offsets();

  private SpreadZone() {}

  /**
   * A zone in the plane through {@code center} perpendicular to {@code direction}.
   *
   * @param center the predicted hit, crossing of the shot without spread
   * @param axisU first ellipse axis, its length the outer radius along it
   * @param axisV second axis, perpendicular to the first and to {@code direction}
   * @param direction unit arrival direction of the arrow; the zone is projected along it
   * @param coreRatio radius of the core ellipse relative to the outer one, in (0, 1]
   */
  public record Zone(Vec3d center, Vec3d axisU, Vec3d axisV, Vec3d direction, double coreRatio) {

    /**
     * Ellipse coordinates of a world point projected along the direction: the outer edge is at
     * length 1.
     */
    public double[] coordinates(Vec3d point) {
      Vec3d rel = point.subtract(center);
      return new double[] {
        dot(rel, axisU) / axisU.lengthSqr(), dot(rel, axisV) / axisV.lengthSqr()
      };
    }

    /** Blend of two zones; used to move smoothly between ticks. */
    public static Zone blend(Zone a, Zone b, double t) {
      // Axes may flip sign between ticks; align them before blending.
      Vec3d bu = dot(a.axisU, b.axisU) < 0 ? b.axisU.scale(-1) : b.axisU;
      Vec3d bv = dot(a.axisV, b.axisV) < 0 ? b.axisV.scale(-1) : b.axisV;
      return new Zone(
          lerp(a.center, b.center, t),
          lerp(a.axisU, bu, t),
          lerp(a.axisV, bv, t),
          lerp(a.direction, b.direction, t).normalize(),
          a.coreRatio + (b.coreRatio - a.coreRatio) * t);
    }
  }

  /** The spread offsets used: eight corners, then seeded triangle samples. */
  public static List<Vec3d> offsets() {
    if (OFFSETS != null) {
      return OFFSETS;
    }
    double s = Launch.SPREAD_HALF_WIDTH;
    List<Vec3d> out = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      out.add(new Vec3d((i & 1) == 0 ? -s : s, (i & 2) == 0 ? -s : s, (i & 4) == 0 ? -s : s));
    }
    SplittableRandom random = new SplittableRandom(0x5EED_0172_275L);
    for (int i = 0; i < SAMPLES; i++) {
      out.add(
          new Vec3d(
              s * (random.nextDouble() - random.nextDouble()),
              s * (random.nextDouble() - random.nextDouble()),
              s * (random.nextDouble() - random.nextDouble())));
    }
    return List.copyOf(out);
  }

  /**
   * Fits the zone for a launch whose path without spread hits {@code hit}, arriving along {@code
   * arrival}. Returns {@code null} if no shot reaches the plane within the flight's tick limit.
   */
  public static Zone compute(Launch launch, Flight flight, Vec3d hit, Vec3d arrival) {
    Vec3d d = arrival.normalize();
    if (d.lengthSqr() == 0.0) {
      return null;
    }
    Vec3d[] basis = basis(d);
    Vec3d u = basis[0];
    Vec3d v = basis[1];

    List<double[]> points = new ArrayList<>();
    for (Vec3d offset : offsets()) {
      Vec3d crossing = crossing(launch.spawn(), launch.velocity(offset), flight, hit, d);
      if (crossing != null) {
        Vec3d rel = crossing.subtract(hit);
        points.add(new double[] {dot(rel, u), dot(rel, v)});
      }
    }
    if (points.size() < 8) {
      return null;
    }

    // Second moments about the predicted hit, which is where the unspread shot crosses.
    double sxx = 0;
    double sxy = 0;
    double syy = 0;
    for (double[] p : points) {
      sxx += p[0] * p[0];
      sxy += p[0] * p[1];
      syy += p[1] * p[1];
    }
    sxx /= points.size();
    sxy /= points.size();
    syy /= points.size();
    double angle = 0.5 * Math.atan2(2 * sxy, sxx - syy);
    double c = Math.cos(angle);
    double sn = Math.sin(angle);
    double l1 = c * c * sxx + 2 * c * sn * sxy + sn * sn * syy;
    double l2 = sn * sn * sxx - 2 * c * sn * sxy + c * c * syy;
    double r1 = Math.sqrt(Math.max(l1, 1.0e-12));
    double r2 = Math.sqrt(Math.max(l2, 1.0e-12));

    double[] m = new double[points.size()];
    for (int i = 0; i < m.length; i++) {
      double[] p = points.get(i);
      double a = (c * p[0] + sn * p[1]) / r1;
      double b = (-sn * p[0] + c * p[1]) / r2;
      m[i] = Math.sqrt(a * a + b * b);
    }
    double outer = Arrays.stream(m).max().orElse(0.0);
    if (outer <= 0.0) {
      return null;
    }
    double[] sorted = m.clone();
    Arrays.sort(sorted);
    double core = sorted[(int) Math.floor(CORE_SHARE * (sorted.length - 1))];

    Vec3d e1 = u.scale(c).add(v.scale(sn));
    Vec3d e2 = u.scale(-sn).add(v.scale(c));
    Vec3d axisU = e1.scale(Math.max(outer * r1, MIN_RADIUS));
    Vec3d axisV = e2.scale(Math.max(outer * r2, MIN_RADIUS));
    return new Zone(hit, axisU, axisV, d, Math.clamp(core / outer, 0.05, 1.0));
  }

  /**
   * Flies in free air, tick by tick as the flight does, and returns where the path crosses the
   * plane through {@code point} with normal {@code normal}.
   */
  static Vec3d crossing(Vec3d spawn, Vec3d velocity, Flight flight, Vec3d point, Vec3d normal) {
    int maxTicks = flight.maxTicks();
    Vec3d pos = spawn;
    Vec3d vel = velocity;
    double side = dot(pos.subtract(point), normal);
    for (int tick = 0; tick < maxTicks; tick++) {
      Flight.Step step = flight.step(vel);
      Vec3d next = pos.add(step.movement());
      double nextSide = dot(next.subtract(point), normal);
      if (side < 0.0 && nextSide >= 0.0) {
        double f = -side / (nextSide - side);
        return pos.add(next.subtract(pos).scale(f));
      }
      pos = next;
      side = nextSide;
      vel = step.next();
    }
    return null;
  }

  /** Two unit vectors perpendicular to {@code d} and to each other. */
  static Vec3d[] basis(Vec3d d) {
    Vec3d up = Math.abs(d.y()) > 0.99 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
    Vec3d u = cross(d, up).normalize();
    Vec3d v = cross(u, d).normalize();
    return new Vec3d[] {u, v};
  }

  static double dot(Vec3d a, Vec3d b) {
    return a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
  }

  private static Vec3d cross(Vec3d a, Vec3d b) {
    return new Vec3d(
        a.y() * b.z() - a.z() * b.y(),
        a.z() * b.x() - a.x() * b.z(),
        a.x() * b.y() - a.y() * b.x());
  }

  private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
    return a.add(b.subtract(a).scale(t));
  }
}
