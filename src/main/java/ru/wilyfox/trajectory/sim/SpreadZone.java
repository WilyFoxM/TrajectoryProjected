package ru.wilyfox.trajectory.sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
 * up to the small curvature of the mapping, and {@link Zone#coreRatio()} and {@link
 * Zone#wideRatio()} mark where half and 90 % of the shots cross. Projected along the arrival
 * direction onto the terrain, it is the zone the arrow can land in.
 *
 * <p>Several {@link Variant launches} can be flown together when the launch itself is uncertain, as
 * with a partial draw a server may count a tick differently.
 *
 * <p>Free air is an approximation: water and obstacles on the way are not considered for the
 * spread, only for the predicted path itself. A path that enters water is different, because water
 * brings the projectile almost to a stop and it sinks: free-air shots would cross the plane of its
 * arrival far away. Such shots are flown against the world instead ({@link #computeThroughWater}),
 * and where they scatter too widely there is no zone.
 */
public final class SpreadZone {
  /** Seeded triangle samples in addition to the eight corners and the center. */
  public static final int SAMPLES = 128;

  /** Share of shots inside the core ellipse. */
  public static final double CORE_SHARE = 0.5;

  /** Share of shots inside the wide ellipse. */
  public static final double WIDE_SHARE = 0.9;

  /** Axes never shrink below this, so a zone seen exactly edge-on stays drawable. */
  private static final double MIN_RADIUS = 0.05;

  /**
   * A zone glides to its next position between ticks only over this far, in blocks, or {@link
   * #GLIDE_PER_RADIUS} of its radius if that is more. Beyond it the hit has jumped, as off a block
   * edge, and a zone sliding through the air between would fall on nothing.
   */
  private static final double MIN_GLIDE = 1.0;

  private static final double GLIDE_PER_RADIUS = 2.0;

  /** Below this cosine between axes, blending would squash the ellipse on the way. */
  private static final double MIN_GLIDE_AXIS_COS = 0.7;

  /**
   * Sample shots flown through water, besides the corners. A shot may sink for over a hundred
   * ticks, each a world query, and this runs every tick while the aim is on water.
   */
  private static final int WATER_SAMPLES = 32;

  /**
   * Below this horizontal speed in water, in blocks per tick, a shot only sinks. It would still
   * take a tick and a world query per step of the way down, for no change in where it crosses.
   */
  private static final double SINKING_SPEED = 0.01;

  /**
   * Through water, a zone wider than this has no zone to show: some shots sink nearby and others
   * clear the water altogether, and a circle that size says nothing about where the arrow lands.
   */
  private static final double MAX_WATER_RADIUS = 8.0;

  /** At most this many positions are looked at along a glide. */
  private static final int MAX_WAYPOINTS = 16;

  private static final List<Vec3d> OFFSETS = offsets();

  private SpreadZone() {}

  /**
   * A zone in the plane through {@code center} perpendicular to {@code direction}.
   *
   * @param center the predicted hit, crossing of the shot without spread
   * @param axisU first ellipse axis, its length the outer radius along it
   * @param axisV second axis, perpendicular to the first and to {@code direction}
   * @param direction unit arrival direction of the arrow; the zone is projected along it
   * @param coreRatio radius of the ellipse holding half of the shots relative to the outer one, in
   *     (0, 1]
   * @param wideRatio radius of the ellipse holding 90 % of the shots relative to the outer one, in
   *     (0, 1]
   */
  public record Zone(
      Vec3d center, Vec3d axisU, Vec3d axisV, Vec3d direction, double coreRatio, double wideRatio) {

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

    /** Whether frames between two ticks may blend from this zone to {@code next}. */
    public boolean glidesTo(Zone next) {
      double radius = Math.max(next.axisU.length(), next.axisV.length());
      double reach = Math.max(MIN_GLIDE, GLIDE_PER_RADIUS * radius);
      Vec3d moved = next.center.subtract(center);
      return moved.lengthSqr() <= reach * reach
          && Math.abs(cosine(axisU, next.axisU)) >= MIN_GLIDE_AXIS_COS
          && Math.abs(cosine(axisV, next.axisV)) >= MIN_GLIDE_AXIS_COS;
    }

    /**
     * Positions along the blend from this zone to {@code next}, close enough that the ellipses
     * overlap: everything the frames between two ticks can cover.
     */
    public List<Zone> waypoints(Zone next) {
      double step =
          0.5
              * Math.min(
                  Math.min(axisU.length(), axisV.length()),
                  Math.min(next.axisU.length(), next.axisV.length()));
      double moved = Math.sqrt(next.center.subtract(center).lengthSqr());
      int count = (int) Math.clamp(Math.ceil(moved / step), 1L, (long) MAX_WAYPOINTS);
      List<Zone> out = new ArrayList<>(count + 1);
      for (int i = 0; i <= count; i++) {
        out.add(blend(this, next, (double) i / count));
      }
      return out;
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
          a.coreRatio + (b.coreRatio - a.coreRatio) * t,
          a.wideRatio + (b.wideRatio - a.wideRatio) * t);
    }
  }

  /**
   * One possible launch and how likely it is relative to the others.
   *
   * @param launch the launch
   * @param weight its relative likelihood
   */
  public record Variant(Launch launch, double weight) {}

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
    return compute(List.of(new Variant(launch, 1.0)), flight, hit, arrival);
  }

  /**
   * Fits the zone over several possible launches. {@code hit} and {@code arrival} belong to the
   * path shown, usually the most likely launch. The outer edge holds the corners of every variant;
   * the rings are shares of the shots with the variants' weights.
   */
  public static Zone compute(List<Variant> variants, Flight flight, Vec3d hit, Vec3d arrival) {
    return compute(variants, flight, hit, arrival, null);
  }

  /**
   * {@link #compute(List, Flight, Vec3d, Vec3d)} for a path that enters water: each shot slows down
   * in the water it meets and stops at the first block on its way. Costs a world query per shot and
   * tick, so it is only for such paths. Also {@code null} if the shots scatter too widely to say
   * anything.
   */
  public static Zone computeThroughWater(
      List<Variant> variants, Flight flight, Vec3d hit, Vec3d arrival, WorldView world) {
    return compute(variants, flight, hit, arrival, world);
  }

  private static Zone compute(
      List<Variant> variants, Flight flight, Vec3d hit, Vec3d arrival, WorldView world) {
    Vec3d d = arrival.normalize();
    if (d.lengthSqr() == 0.0) {
      return null;
    }
    Vec3d[] basis = basis(d);
    Vec3d u = basis[0];
    Vec3d v = basis[1];

    // u, v, weight, and whether the point is a shot of the sample rather than a corner.
    List<double[]> points = new ArrayList<>();
    List<Vec3d> offsets = world == null ? offsets() : offsets().subList(0, 8 + WATER_SAMPLES);
    for (Variant variant : variants) {
      Launch launch = variant.launch();
      for (int i = 0; i < offsets.size(); i++) {
        Vec3d velocity = launch.velocity(offsets.get(i));
        Vec3d crossing =
            world == null
                ? crossing(launch.spawn(), velocity, flight, hit, d)
                : crossingInWorld(launch.spawn(), velocity, flight, hit, d, world);
        if (crossing != null) {
          Vec3d rel = crossing.subtract(hit);
          points.add(new double[] {dot(rel, u), dot(rel, v), variant.weight(), i >= 8 ? 1.0 : 0.0});
        }
      }
    }
    if (points.size() < 8) {
      return null;
    }

    // Second moments about the predicted hit, which is where the unspread shot crosses.
    double sxx = 0;
    double sxy = 0;
    double syy = 0;
    double total = 0;
    for (double[] p : points) {
      sxx += p[2] * p[0] * p[0];
      sxy += p[2] * p[0] * p[1];
      syy += p[2] * p[1] * p[1];
      total += p[2];
    }
    sxx /= total;
    sxy /= total;
    syy /= total;
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
    double[][] shots = shotsByRadius(points, m);

    Vec3d e1 = u.scale(c).add(v.scale(sn));
    Vec3d e2 = u.scale(-sn).add(v.scale(c));
    if (world != null && outer * Math.max(r1, r2) > MAX_WATER_RADIUS) {
      return null;
    }
    Vec3d axisU = e1.scale(Math.max(outer * r1, MIN_RADIUS));
    Vec3d axisV = e2.scale(Math.max(outer * r2, MIN_RADIUS));
    return new Zone(
        hit,
        axisU,
        axisV,
        d,
        Math.clamp(quantile(shots, CORE_SHARE) / outer, 0.05, 1.0),
        Math.clamp(quantile(shots, WIDE_SHARE) / outer, 0.05, 1.0));
  }

  /** The sampled shots as radius and weight, by increasing radius. Corners are not shots. */
  private static double[][] shotsByRadius(List<double[]> points, double[] radii) {
    List<double[]> shots = new ArrayList<>();
    for (int i = 0; i < radii.length; i++) {
      if (points.get(i)[3] > 0.0) {
        shots.add(new double[] {radii[i], points.get(i)[2]});
      }
    }
    shots.sort(Comparator.comparingDouble(s -> s[0]));
    return shots.toArray(new double[0][]);
  }

  /** The smallest radius holding {@code share} of the shots' weight. */
  private static double quantile(double[][] shots, double share) {
    double total = 0;
    for (double[] s : shots) {
      total += s[1];
    }
    double held = 0;
    for (double[] s : shots) {
      held += s[1];
      if (held >= share * total) {
        return s[0];
      }
    }
    return shots.length == 0 ? 0.0 : shots[shots.length - 1][0];
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

  /**
   * Flies against the world, in water as water slows the projectile, and returns where the path
   * crosses the plane, or where a block stops it before that.
   */
  static Vec3d crossingInWorld(
      Vec3d spawn, Vec3d velocity, Flight flight, Vec3d point, Vec3d normal, WorldView world) {
    int maxTicks = flight.maxTicks();
    Vec3d pos = spawn;
    Vec3d vel = velocity;
    boolean inWater = false;
    double side = dot(pos.subtract(point), normal);
    for (int tick = 0; tick < maxTicks; tick++) {
      if (inWater && Math.hypot(vel.x(), vel.z()) < SINKING_SPEED) {
        return sunkOnto(pos, side, normal);
      }
      Flight.Step step = flight.step(vel, inWater);
      WorldView.BlockClip clip = world.clip(pos, pos.add(step.movement()));
      if (clip.type() == WorldView.BlockClip.Type.UNKNOWN) {
        return null;
      }
      Vec3d next = clip.location();
      double nextSide = dot(next.subtract(point), normal);
      if (side < 0.0 && nextSide >= 0.0) {
        double f = -side / (nextSide - side);
        return pos.add(next.subtract(pos).scale(f));
      }
      if (clip.type() == WorldView.BlockClip.Type.BLOCK) {
        return next;
      }
      pos = next;
      side = nextSide;
      vel = step.next();
      inWater = world.isInWater(flight.box(pos));
    }
    return null;
  }

  /** Where a shot sinking straight down from {@code pos} meets the plane, or {@code pos}. */
  private static Vec3d sunkOnto(Vec3d pos, double side, Vec3d normal) {
    if (side >= 0.0 || normal.y() >= -1.0e-3) {
      return pos;
    }
    return pos.add(0.0, -side / normal.y(), 0.0);
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

  private static double cosine(Vec3d a, Vec3d b) {
    return dot(a, b) / Math.max(Math.sqrt(a.lengthSqr() * b.lengthSqr()), 1.0e-12);
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
