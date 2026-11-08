package ru.wilyfox.trajectory.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class FaceShadowTest {
  /** The top of the block at the origin. */
  private static final FaceShadow.AxisFace TOP = new FaceShadow.AxisFace(1, 1, 1.0, 0, 1, 0, 1);

  private static final Vec3d DOWN = new Vec3d(0, -1, 0);

  @Test
  void nothingBehindTheStartCastsAShadow() {
    List<Aabb> above = List.of(new Aabb(-1, 3, -1, 2, 4, 2));
    assertTrue(FaceShadow.litPieces(TOP, DOWN, above).isEmpty());
    // The start lies between the box and the face: the box is behind the light.
    Vec3d start = new Vec3d(0.5, 2.0, 0.5);
    assertEquals(1.0, totalArea(FaceShadow.litPieces(TOP, DOWN, above, start)), 1.0e-9);
    // The start lies beyond the box: the box is on the way.
    assertTrue(FaceShadow.litPieces(TOP, DOWN, above, new Vec3d(0.5, 5.0, 0.5)).isEmpty());
    // The start lies inside the box: the part in front of it still shades the face.
    assertTrue(FaceShadow.litPieces(TOP, DOWN, above, new Vec3d(0.5, 3.5, 0.5)).isEmpty());
  }

  @Test
  void anOpenFaceIsLitWhole() {
    List<List<Vec3d>> lit = FaceShadow.litPieces(TOP, DOWN, List.of());
    assertEquals(1.0, totalArea(lit), 1.0e-9);
  }

  @Test
  void aFaceTurnedAwayIsNotLit() {
    assertTrue(FaceShadow.litPieces(TOP, new Vec3d(0, 1, 0), List.of()).isEmpty());
    assertTrue(FaceShadow.litPieces(TOP, new Vec3d(1, 0, 0), List.of()).isEmpty());
  }

  @Test
  void aBlockOnTopShadowsItAll() {
    List<Aabb> boxes = List.of(new Aabb(0, 1, 0, 1, 2, 1));
    assertTrue(FaceShadow.litPieces(TOP, DOWN, boxes).isEmpty());
  }

  @Test
  void boxesBehindThePlaneCastNoShadow() {
    // The block itself and its neighbours on the same layer.
    List<Aabb> boxes =
        List.of(
            new Aabb(0, 0, 0, 1, 1, 1), new Aabb(1, 0, 0, 2, 1, 1), new Aabb(-1, -1, 0, 0, 1, 1));
    assertEquals(1.0, totalArea(FaceShadow.litPieces(TOP, DOWN, boxes)), 1.0e-9);
  }

  @Test
  void aSlabOverHalfTheFaceLeavesHalf() {
    List<Aabb> boxes = List.of(new Aabb(0.5, 3, -1, 2, 3.5, 2));
    assertEquals(0.5, totalArea(FaceShadow.litPieces(TOP, DOWN, boxes)), 1.0e-9);
  }

  @Test
  void aPillarBesideTheFaceCastsASlantedShadow() {
    // A pillar east of the face, two blocks tall; light comes down at 45 degrees from the east.
    List<Aabb> boxes = List.of(new Aabb(1, 1, 0, 2, 3, 1));
    Vec3d dir = new Vec3d(-1, -1, 0).normalize();
    // The pillar's shadow reaches two blocks west of it: over the whole face.
    assertTrue(FaceShadow.litPieces(TOP, dir, boxes).isEmpty());
    // Half as tall: the shadow reaches 0.5 into the face.
    List<Aabb> low = List.of(new Aabb(1, 1, 0, 2, 1.5, 1));
    assertEquals(0.5, totalArea(FaceShadow.litPieces(TOP, dir, low)), 1.0e-9);
  }

  /** Random boxes over a face: every sample point is lit exactly when a pierced piece holds it. */
  @Test
  void piecesMatchRayTesting() {
    SplittableRandom random = new SplittableRandom(7);
    int[] faults = {0};
    for (int round = 0; round < 200; round++) {
      Vec3d dir =
          new Vec3d(random.nextDouble(-1, 1), -random.nextDouble(0.2, 1), random.nextDouble(-1, 1))
              .normalize();
      List<Aabb> boxes = new ArrayList<>();
      int count = random.nextInt(1, 7);
      for (int i = 0; i < count; i++) {
        double x = random.nextDouble(-2, 2);
        double y = random.nextDouble(0.5, 3);
        double z = random.nextDouble(-2, 2);
        boxes.add(
            new Aabb(
                x,
                y,
                z,
                x + random.nextDouble(0.1, 1.2),
                y + random.nextDouble(0.1, 1.2),
                z + random.nextDouble(0.1, 1.2)));
      }
      List<List<Vec3d>> lit = FaceShadow.litPieces(TOP, dir, boxes);
      for (List<Vec3d> piece : lit) {
        assertTrue(convexArea2d(piece) > 0, "degenerate piece");
      }
      for (int s = 0; s < 400; s++) {
        Vec3d p = new Vec3d(random.nextDouble(), 1.0, random.nextDouble());
        boolean byRay = lit(p, dir, boxes);
        boolean byPieces = lit.stream().anyMatch(piece -> contains(piece, p));
        if (byRay != byPieces && margin(p, dir, boxes) > 1.0e-6) {
          faults[0]++;
        }
      }
      // Pieces do not overlap: their area never exceeds the face.
      assertTrue(totalArea(lit) <= 1.0 + 1.0e-9);
    }
    assertEquals(0, faults[0]);
  }

  private static double totalArea(List<List<Vec3d>> pieces) {
    return pieces.stream().mapToDouble(FaceShadow::area).sum();
  }

  /** Whether a ray from the point back against the light misses every box. */
  private static boolean lit(Vec3d p, Vec3d dir, List<Aabb> boxes) {
    for (Aabb box : boxes) {
      if (enter(p, dir.scale(-1), box) != null) {
        return false;
      }
    }
    return true;
  }

  /** Slab test: the ray parameter where the ray from p along v first meets the box, or null. */
  private static double[] enter(Vec3d p, Vec3d v, Aabb box) {
    double[] o = {p.x(), p.y(), p.z()};
    double[] d = {v.x(), v.y(), v.z()};
    double[] lo = {box.minX(), box.minY(), box.minZ()};
    double[] hi = {box.maxX(), box.maxY(), box.maxZ()};
    double t0 = 1.0e-9;
    double t1 = Double.MAX_VALUE;
    for (int i = 0; i < 3; i++) {
      if (Math.abs(d[i]) < 1.0e-12) {
        if (o[i] < lo[i] || o[i] > hi[i]) {
          return null;
        }
        continue;
      }
      double a = (lo[i] - o[i]) / d[i];
      double b = (hi[i] - o[i]) / d[i];
      t0 = Math.max(t0, Math.min(a, b));
      t1 = Math.min(t1, Math.max(a, b));
    }
    return t0 < t1 ? new double[] {t0, t1} : null;
  }

  /** How far the ray passes from grazing a box: samples on a shadow edge are not judged. */
  private static double margin(Vec3d p, Vec3d dir, List<Aabb> boxes) {
    double m = Double.MAX_VALUE;
    for (Aabb box : boxes) {
      double[] t = enter(p, dir.scale(-1), box.inflate(1.0e-5));
      double[] u = enter(p, dir.scale(-1), box.inflate(-1.0e-5));
      if ((t == null) != (u == null)) {
        m = 0;
      }
    }
    return m;
  }

  private static boolean contains(List<Vec3d> piece, Vec3d p) {
    double sign = 0;
    for (int i = 0; i < piece.size(); i++) {
      Vec3d a = piece.get(i);
      Vec3d b = piece.get((i + 1) % piece.size());
      double c = (b.x() - a.x()) * (p.z() - a.z()) - (b.z() - a.z()) * (p.x() - a.x());
      if (Math.abs(c) < 1.0e-12) {
        continue;
      }
      if (sign == 0) {
        sign = Math.signum(c);
      } else if (Math.signum(c) != sign) {
        return false;
      }
    }
    return true;
  }

  private static double convexArea2d(List<Vec3d> piece) {
    double s = 0;
    for (int i = 0; i < piece.size(); i++) {
      Vec3d a = piece.get(i);
      Vec3d b = piece.get((i + 1) % piece.size());
      s += a.x() * b.z() - a.z() * b.x();
    }
    return Math.abs(s) * 0.5;
  }
}
