package ru.wilyfox.trajectory.sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The part of an axis-aligned face that a straight line along a direction reaches without passing
 * through other boxes: the face minus the shadows the boxes cast on its plane along that direction.
 *
 * <p>Each box in front of the plane is projected along the direction onto the plane, which gives a
 * convex polygon; the face, a rectangle, is cut by each such polygon in turn. The result is a set
 * of convex pieces that do not overlap. Light has a start: nothing behind it casts a shadow.
 */
public final class FaceShadow {
  private static final double EPSILON = 1.0e-7;

  /** Pieces smaller than this, in square blocks, are dropped. */
  private static final double MIN_AREA = 1.0e-6;

  /** A face stops being cut once it has this many pieces; the rest keep their shadows uncut. */
  private static final int MAX_PIECES = 96;

  private FaceShadow() {}

  /**
   * An axis-aligned face.
   *
   * @param axis the normal axis: 0 for x, 1 for y, 2 for z
   * @param sign +1 if the face looks towards the positive axis, -1 otherwise
   * @param plane the face's coordinate along the normal axis
   * @param minA lower bound along the first other axis (y for x faces, x otherwise)
   * @param maxA upper bound along the first other axis
   * @param minB lower bound along the second other axis (z for x and y faces, y for z faces)
   * @param maxB upper bound along the second other axis
   */
  public record AxisFace(
      int axis, int sign, double plane, double minA, double maxA, double minB, double maxB) {

    int axisA() {
      return axis == 0 ? 1 : 0;
    }

    int axisB() {
      return axis == 2 ? 1 : 2;
    }

    /** The point on the face's plane with in-plane coordinates {@code (a, b)}. */
    Vec3d point(double a, double b) {
      double[] p = new double[3];
      p[axis] = plane;
      p[axisA()] = a;
      p[axisB()] = b;
      return new Vec3d(p[0], p[1], p[2]);
    }
  }

  /**
   * The lit pieces of {@code face} for light travelling along {@code direction}, as convex polygons
   * on the face's plane. Empty if the face looks away from the light or is fully in shadow.
   */
  public static List<List<Vec3d>> litPieces(AxisFace face, Vec3d direction, List<Aabb> boxes) {
    return litPieces(face, direction, boxes, null);
  }

  /**
   * {@link #litPieces(AxisFace, Vec3d, List)} for light starting at {@code start}: what lies behind
   * the plane through it, facing along the direction, casts no shadow. {@code null}: no such limit.
   */
  public static List<List<Vec3d>> litPieces(
      AxisFace face, Vec3d direction, List<Aabb> boxes, Vec3d start) {
    double[] d = {direction.x(), direction.y(), direction.z()};
    int k = face.axis();
    if (d[k] * face.sign() >= -EPSILON) {
      return List.of();
    }
    int ia = face.axisA();
    int ib = face.axisB();

    List<double[][]> pieces = new ArrayList<>();
    pieces.add(
        new double[][] {
          {face.minA(), face.minB()},
          {face.maxA(), face.minB()},
          {face.maxA(), face.maxB()},
          {face.minA(), face.maxB()}
        });
    for (Aabb box : boxes) {
      if (pieces.isEmpty()) {
        break;
      }
      double[][] shadow = shadow(face, d, ia, ib, box, start);
      if (shadow == null) {
        continue;
      }
      List<double[][]> next = new ArrayList<>();
      for (double[][] piece : pieces) {
        subtract(piece, shadow, next);
      }
      if (next.size() > MAX_PIECES) {
        break;
      }
      pieces = next;
    }

    List<List<Vec3d>> out = new ArrayList<>(pieces.size());
    for (double[][] piece : pieces) {
      List<Vec3d> polygon = new ArrayList<>(piece.length);
      for (double[] p : piece) {
        polygon.add(face.point(p[0], p[1]));
      }
      out.add(List.copyOf(polygon));
    }
    return List.copyOf(out);
  }

  /** Area of a planar convex polygon given in 3D. */
  public static double area(List<Vec3d> polygon) {
    double x = 0;
    double y = 0;
    double z = 0;
    for (int i = 0; i < polygon.size(); i++) {
      Vec3d a = polygon.get(i);
      Vec3d b = polygon.get((i + 1) % polygon.size());
      x += a.y() * b.z() - a.z() * b.y();
      y += a.z() * b.x() - a.x() * b.z();
      z += a.x() * b.y() - a.y() * b.x();
    }
    return 0.5 * Math.sqrt(x * x + y * y + z * z);
  }

  /** The convex shadow of the part of {@code box} in front of the plane, or {@code null}. */
  private static double[][] shadow(
      AxisFace face, double[] d, int ia, int ib, Aabb box, Vec3d start) {
    double[] min = {box.minX(), box.minY(), box.minZ()};
    double[] max = {box.maxX(), box.maxY(), box.maxZ()};
    int k = face.axis();
    double c = face.plane();
    if (face.sign() > 0) {
      if (max[k] <= c + EPSILON) {
        return null;
      }
      min[k] = Math.max(min[k], c);
    } else {
      if (min[k] >= c - EPSILON) {
        return null;
      }
      max[k] = Math.min(max[k], c);
    }

    List<double[]> corners = inFrontOfStart(min, max, d, start);
    if (corners.isEmpty()) {
      return null;
    }
    double[][] points = new double[corners.size()][];
    double loA = Double.MAX_VALUE;
    double hiA = -Double.MAX_VALUE;
    double loB = Double.MAX_VALUE;
    double hiB = -Double.MAX_VALUE;
    for (int i = 0; i < points.length; i++) {
      double[] p = corners.get(i);
      double t = (c - p[k]) / d[k];
      double a = p[ia] + d[ia] * t;
      double b = p[ib] + d[ib] * t;
      points[i] = new double[] {a, b};
      loA = Math.min(loA, a);
      hiA = Math.max(hiA, a);
      loB = Math.min(loB, b);
      hiB = Math.max(hiB, b);
    }
    if (hiA <= face.minA() + EPSILON
        || loA >= face.maxA() - EPSILON
        || hiB <= face.minB() + EPSILON
        || loB >= face.maxB() - EPSILON) {
      return null;
    }
    double[][] hull = hull(points);
    return hull.length >= 3 ? hull : null;
  }

  /**
   * The corners of a box in front of the plane through {@code start}, and where its edges cross
   * that plane: their hull is the part of the box that casts a shadow.
   */
  private static List<double[]> inFrontOfStart(
      double[] min, double[] max, double[] d, Vec3d start) {
    double[][] corners = new double[8][];
    double[] along = new double[8];
    for (int i = 0; i < 8; i++) {
      corners[i] =
          new double[] {
            (i & 1) == 0 ? min[0] : max[0],
            (i & 2) == 0 ? min[1] : max[1],
            (i & 4) == 0 ? min[2] : max[2]
          };
      along[i] =
          start == null
              ? 1.0
              : (corners[i][0] - start.x()) * d[0]
                  + (corners[i][1] - start.y()) * d[1]
                  + (corners[i][2] - start.z()) * d[2];
    }
    List<double[]> out = new ArrayList<>(12);
    for (int i = 0; i < 8; i++) {
      if (along[i] >= 0.0) {
        out.add(corners[i]);
      }
      for (int axis = 1; axis < 8; axis <<= 1) {
        int j = i | axis;
        if (j != i && (along[i] >= 0.0) != (along[j] >= 0.0)) {
          double t = along[i] / (along[i] - along[j]);
          out.add(
              new double[] {
                corners[i][0] + (corners[j][0] - corners[i][0]) * t,
                corners[i][1] + (corners[j][1] - corners[i][1]) * t,
                corners[i][2] + (corners[j][2] - corners[i][2]) * t
              });
        }
      }
    }
    return out;
  }

  /** Convex hull, counter-clockwise, by the monotone chain. */
  static double[][] hull(double[][] points) {
    double[][] p = points.clone();
    Arrays.sort(
        p, (u, v) -> u[0] != v[0] ? Double.compare(u[0], v[0]) : Double.compare(u[1], v[1]));
    double[][] h = new double[2 * p.length][];
    int n = 0;
    for (double[] q : p) {
      while (n >= 2 && cross(h[n - 2], h[n - 1], q) <= EPSILON) {
        n--;
      }
      h[n++] = q;
    }
    for (int i = p.length - 2, lower = n + 1; i >= 0; i--) {
      double[] q = p[i];
      while (n >= lower && cross(h[n - 2], h[n - 1], q) <= EPSILON) {
        n--;
      }
      h[n++] = q;
    }
    return Arrays.copyOf(h, Math.max(n - 1, 0));
  }

  /**
   * Adds to {@code out} the convex pieces of {@code piece} outside the convex, counter-clockwise
   * {@code shadow}: for each shadow edge, the part of the remainder on its outer side is a piece,
   * the rest goes on to the next edge.
   */
  private static void subtract(double[][] piece, double[][] shadow, List<double[][]> out) {
    if (!overlaps(piece, shadow)) {
      out.add(piece);
      return;
    }
    double[][] rest = piece;
    for (int i = 0; i < shadow.length && rest.length >= 3; i++) {
      double[] a = shadow[i];
      double[] b = shadow[(i + 1) % shadow.length];
      double[][] outside = clip(rest, a, b, false);
      if (outside.length >= 3 && polygonArea(outside) > MIN_AREA) {
        out.add(outside);
      }
      rest = clip(rest, a, b, true);
    }
  }

  /** Bounding boxes overlap; a cheap test before cutting. */
  private static boolean overlaps(double[][] p, double[][] q) {
    double[] bp = bounds(p);
    double[] bq = bounds(q);
    return bp[0] < bq[1] - EPSILON
        && bq[0] < bp[1] - EPSILON
        && bp[2] < bq[3] - EPSILON
        && bq[2] < bp[3] - EPSILON;
  }

  private static double[] bounds(double[][] p) {
    double[] b = {Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE};
    for (double[] q : p) {
      b[0] = Math.min(b[0], q[0]);
      b[1] = Math.max(b[1], q[0]);
      b[2] = Math.min(b[2], q[1]);
      b[3] = Math.max(b[3], q[1]);
    }
    return b;
  }

  /** Sutherland–Hodgman against the line a→b, keeping its left side or its right side. */
  private static double[][] clip(double[][] polygon, double[] a, double[] b, boolean keepLeft) {
    List<double[]> out = new ArrayList<>(polygon.length + 2);
    for (int i = 0; i < polygon.length; i++) {
      double[] p = polygon[i];
      double[] q = polygon[(i + 1) % polygon.length];
      double sp = side(a, b, p, keepLeft);
      double sq = side(a, b, q, keepLeft);
      if (sp >= 0) {
        out.add(p);
      }
      if ((sp >= 0) != (sq >= 0)) {
        double t = sp / (sp - sq);
        out.add(new double[] {p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t});
      }
    }
    return out.toArray(new double[0][]);
  }

  private static double side(double[] a, double[] b, double[] p, boolean left) {
    double s = cross(a, b, p);
    return left ? s : -s;
  }

  private static double cross(double[] o, double[] a, double[] b) {
    return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
  }

  private static double polygonArea(double[][] p) {
    double s = 0;
    for (int i = 0; i < p.length; i++) {
      double[] a = p[i];
      double[] b = p[(i + 1) % p.length];
      s += a[0] * b[1] - a[1] * b[0];
    }
    return Math.abs(s) * 0.5;
  }
}
