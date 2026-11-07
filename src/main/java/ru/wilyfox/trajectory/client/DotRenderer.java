package ru.wilyfox.trajectory.client;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import ru.wilyfox.trajectory.sim.Aabb;
import ru.wilyfox.trajectory.sim.Face;
import ru.wilyfox.trajectory.sim.Trajectory;
import ru.wilyfox.trajectory.sim.Vec3d;
import ru.wilyfox.trajectory.sim.WorldView;

/**
 * Draws the predicted path as round dots: camera-facing quads whose fragment shader cuts an
 * anti-aliased disc. Dots behind terrain are drawn a second time, fainter and outlined.
 *
 * <p>Dots sit on a fixed lattice along the path, measured from the spawn point, and every lattice
 * point decides for itself whether it is shown, from how densely the path is packed on screen at
 * that point (like mip levels: every 2nd, 4th, 8th point...). A point between two levels fades
 * instead of popping, and moving the camera never shifts the dots along the path.
 */
final class DotRenderer {
  private static final String MOD_ID = "trajectory_projected";

  private static final RenderPipeline VISIBLE =
      RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
          .withLocation(Identifier.fromNamespaceAndPath(MOD_ID, "pipeline/dots"))
          .withVertexShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/dot"))
          .withFragmentShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/dot"))
          .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
          .withCull(false)
          .build();

  /** Passes only where terrain is in front of the dot (the depth buffer is reversed). */
  private static final RenderPipeline HIDDEN =
      RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
          .withLocation(Identifier.fromNamespaceAndPath(MOD_ID, "pipeline/dots_hidden"))
          .withVertexShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/dot"))
          .withFragmentShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/dot_hidden"))
          .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
          .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN, false))
          .withCull(false)
          .build();

  /** The hit marker on a living target: an anti-aliased cross instead of a disc. */
  private static final RenderPipeline CROSS =
      RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
          .withLocation(Identifier.fromNamespaceAndPath(MOD_ID, "pipeline/cross"))
          .withVertexShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/dot"))
          .withFragmentShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/cross"))
          .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
          .withCull(false)
          .build();

  private static final RenderPipeline CROSS_HIDDEN =
      RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
          .withLocation(Identifier.fromNamespaceAndPath(MOD_ID, "pipeline/cross_hidden"))
          .withVertexShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/dot"))
          .withFragmentShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/cross_hidden"))
          .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
          .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN, false))
          .withCull(false)
          .build();

  private static final RenderType DOTS =
      RenderType.create(
          MOD_ID + "_dots", RenderSetup.builder(VISIBLE).sortOnUpload().createRenderSetup());

  private static final RenderType DOTS_HIDDEN =
      RenderType.create(
          MOD_ID + "_dots_hidden", RenderSetup.builder(HIDDEN).sortOnUpload().createRenderSetup());

  private static final RenderType CROSSES =
      RenderType.create(
          MOD_ID + "_cross", RenderSetup.builder(CROSS).sortOnUpload().createRenderSetup());

  private static final RenderType CROSSES_HIDDEN =
      RenderType.create(
          MOD_ID + "_cross_hidden",
          RenderSetup.builder(CROSS_HIDDEN).sortOnUpload().createRenderSetup());

  /** {@code f6f7eb} from the palette. */
  private static final int COLOR_RGB = 0xF6F7EB;

  /** Distance between lattice points along the path, in blocks. */
  private static final double LATTICE = 0.25;

  /** Highest thinning level: at most every 64th lattice point. */
  private static final int MAX_LEVEL = 6;

  /**
   * Required center-to-center distance between shown dots on screen, in dot diameters: 1.0 would
   * make them touch.
   */
  private static final double GAP = 1.6;

  private static final double DOT_RADIUS = 0.04;

  /** Dot radius on screen, in pixels: far dots never get smaller, near ones never bigger. */
  private static final double MIN_RADIUS_PX = 1.5;

  private static final double MAX_RADIUS_PX = 3.2;

  /** The hit marker shrinks with distance but stays this much larger than a dot beside it. */
  private static final double END_RADIUS = 0.13;

  private static final double END_MIN_RADIUS_PX = 2.8;
  private static final double END_MAX_RADIUS_PX = 8.0;
  private static final double END_OVER_DOT = 1.8;
  private static final double LIFT = 1.25;
  private static final double ENTITY_PULL = 0.25;

  /**
   * Submit order for all our geometry. Entity layers use orders from 1 up (a slime's shell, eyes,
   * armor), and an order is drawn after all lower ones; a late order keeps the line and the marker
   * on top of translucent entity layers instead of tinted under them.
   */
  private static final int SUBMIT_ORDER = 64;

  /** Near the camera the line fades in over these distances, so it does not cover the crosshair. */
  private static final double NEAR_FADE_START = 1.0;

  private static final double NEAR_FADE_END = 2.5;

  /** Far away the line dims a little, down to {@link #FAR_ALPHA}, without disappearing. */
  private static final double FAR_FADE_START = 48.0;

  private static final double FAR_FADE_END = 120.0;
  private static final double FAR_ALPHA = 0.45;

  private static final int MAX_ALPHA = 235;

  /**
   * In first person the path leaves the eye almost along the view and collapses on screen. The
   * drawn line therefore starts at the bow hand, this far right (or left for the other hand) and
   * down from the eye, and merges into the real path over {@link #MERGE_DISTANCE} blocks of flight,
   * so the hit point and the far part stay exact. Paths shorter than {@link #HAND_MIN_PATH} blocks
   * are drawn plain; the arc fades in up to {@link #HAND_FULL_PATH}.
   */
  private static final double HAND_SIDE = 0.32;

  private static final double HAND_DOWN = 0.26;
  private static final double MERGE_DISTANCE = 12.0;
  private static final double HAND_MIN_PATH = 12.0;
  private static final double HAND_FULL_PATH = 16.0;

  /** Hidden dots are drawn larger, so their outline is thick enough to read on any surface. */
  private static final double HIDDEN_SCALE = 1.5;

  private static final double HIDDEN_CROSS_SCALE = 1.2;

  /** The cross needs more room than a disc to read as a cross: thin arms, same visual weight. */
  private static final double CROSS_SCALE = 1.35;

  /**
   * On a living target the path breathes: opacity dips by up to {@link #PULSE_ALPHA} and dots grow
   * by up to {@link #PULSE_SIZE}, the cross by up to {@link #PULSE_MARKER_SIZE}, once per {@link
   * #PULSE_PERIOD_MS}. The pulse eases in and out over {@link #PULSE_EASE_MS} when the target
   * changes.
   */
  private static final double PULSE_PERIOD_MS = 1000.0;

  private static final double PULSE_ALPHA = 0.55;
  private static final double PULSE_SIZE = 0.35;
  private static final double PULSE_MARKER_SIZE = 0.2;
  private static final double PULSE_EASE_MS = 180.0;

  private static double pulseStrength;
  private static long lastFrameNanos;

  private DotRenderer() {}

  static void collect(LevelRenderContext context) {
    BowPreview.Prediction current = BowPreview.current();
    if (current == null) {
      return;
    }
    BowPreview.Prediction previous = BowPreview.previous();
    Minecraft minecraft = Minecraft.getInstance();
    float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);

    CameraRenderState camera = context.levelState().cameraRenderState;
    // Pixels per radian near the screen center: projection m11 is 1 / tan(fov / 2).
    double pixelsPerRadian =
        camera.projectionMatrix.m11() * minecraft.getWindow().getHeight() / 2.0;
    Vector3f up = camera.orientation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
    Vector3f left = camera.orientation.transform(new Vector3f(-1.0F, 0.0F, 0.0F));

    List<Vec3d> path =
        previous == null
            ? current.path().points()
            : blend(previous.path().points(), current.path().points(), partial);
    Trajectory.End end = current.path().end();
    boolean hit = end == Trajectory.End.BLOCK || end == Trajectory.End.ENTITY;
    boolean living = current.livingTarget();
    double pulse = pulse(living);
    Vec3d hand = Vec3d.ZERO;
    if (minecraft.options.getCameraType().isFirstPerson()
        && minecraft.getCameraEntity() == minecraft.player
        && minecraft.player != null) {
      HumanoidArm arm =
          minecraft.player.getUsedItemHand() == InteractionHand.MAIN_HAND
              ? minecraft.player.getMainArm()
              : minecraft.player.getMainArm().getOpposite();
      double side = arm == HumanoidArm.RIGHT ? HAND_SIDE : -HAND_SIDE;
      hand =
          new Vec3d(
              -left.x() * side - up.x() * HAND_DOWN,
              -left.y() * side - up.y() * HAND_DOWN,
              -left.z() * side - up.z() * HAND_DOWN);
    }
    Vec3d markerAt = path.isEmpty() ? null : path.getLast();
    if (end == Trajectory.End.ENTITY && markerAt != null) {
      markerAt = facingSide(minecraft, current.path().entity(), markerAt, camera.pos);
    }
    Layout layout =
        layout(
            path, hit, current.path().face(), markerAt, camera.pos, pixelsPerRadian, pulse, hand);
    if (layout.dots().isEmpty() && layout.marker() == null) {
      return;
    }

    PoseStack poseStack = context.poseStack();
    submit(context, poseStack, DOTS, layout.dots(), up, left, 1.0);
    submit(context, poseStack, DOTS_HIDDEN, layout.dots(), up, left, HIDDEN_SCALE);
    if (layout.marker() != null) {
      Dot m = layout.marker();
      List<Dot> marker =
          List.of(
              living
                  ? new Dot(
                      m.dx(),
                      m.dy(),
                      m.dz(),
                      m.radius() * CROSS_SCALE * (1.0 + PULSE_MARKER_SIZE * pulse),
                      m.alpha())
                  : m);
      submit(context, poseStack, living ? CROSSES : DOTS, marker, up, left, 1.0);
      submit(
          context,
          poseStack,
          living ? CROSSES_HIDDEN : DOTS_HIDDEN,
          marker,
          up,
          left,
          living ? HIDDEN_CROSS_SCALE : HIDDEN_SCALE);
    }
  }

  /**
   * The arrow meets an entity's box on the side it flies in from, which from the camera is often
   * the top or the far side, behind the model (and under a slime's shell). The marker goes where
   * the line of sight to that point enters the box instead: the side the player sees.
   */
  private static Vec3d facingSide(
      Minecraft minecraft, WorldView.EntityTarget target, Vec3d hit, Vec3 cam) {
    Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(target.id());
    Aabb box = target.box();
    if (entity != null) {
      AABB b = entity.getBoundingBox();
      box = new Aabb(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }
    Vec3d eye = new Vec3d(cam.x, cam.y, cam.z);
    if (box.contains(eye)) {
      return hit;
    }
    Vec3d entry = box.clip(eye, hit.add(hit.subtract(eye).normalize().scale(0.5)));
    return entry == null ? hit : entry;
  }

  private static void submit(
      LevelRenderContext context,
      PoseStack poseStack,
      RenderType type,
      List<Dot> dots,
      Vector3f up,
      Vector3f left,
      double scale) {
    if (dots.isEmpty()) {
      return;
    }
    context
        .submitNodeCollector()
        .order(SUBMIT_ORDER)
        .submitCustomGeometry(
            poseStack,
            type,
            (pose, buffer) -> dots.forEach(d -> d.emit(pose, buffer, up, left, scale)));
  }

  /**
   * Current pulse phase in [0, 1] (0: rest, 1: dimmest and largest), scaled by how far the pulse
   * has eased in. Smooth in time, so it does not depend on the tick rate.
   */
  private static double pulse(boolean living) {
    long now = System.nanoTime();
    double elapsedMs = lastFrameNanos == 0 ? 0.0 : (now - lastFrameNanos) / 1.0e6;
    lastFrameNanos = now;
    double step = Math.min(1.0, elapsedMs / PULSE_EASE_MS);
    pulseStrength += ((living ? 1.0 : 0.0) - pulseStrength) * step;
    if (pulseStrength < 1.0e-3) {
      return 0.0;
    }
    double phase = (now / 1.0e6 % PULSE_PERIOD_MS) / PULSE_PERIOD_MS;
    return pulseStrength * (0.5 - 0.5 * Math.cos(2.0 * Math.PI * phase));
  }

  /** Point-by-point blend of two per-tick paths; the shorter one repeats its last point. */
  static List<Vec3d> blend(List<Vec3d> from, List<Vec3d> to, double t) {
    int size = Math.max(from.size(), to.size());
    List<Vec3d> out = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      Vec3d a = from.get(Math.min(i, from.size() - 1));
      Vec3d b = to.get(Math.min(i, to.size() - 1));
      out.add(a.add(b.subtract(a).scale(t)));
    }
    return out;
  }

  /** Camera-relative path dots and the hit marker at the path's last point. */
  record Layout(List<Dot> dots, Dot marker) {}

  /**
   * Lays out the dots of a path. {@code pulse} in [0, 1] dims and enlarges the path dots (not the
   * marker); {@code hand} is the offset of the drawn start from the real one (zero outside first
   * person).
   */
  static Layout layout(
      List<Vec3d> path,
      boolean hit,
      Face face,
      Vec3d markerAt,
      Vec3 cam,
      double pixelsPerRadian,
      double pulse,
      Vec3d hand) {
    List<Vec3d> lattice = fromHand(resample(path), hand);
    double minAngle = MIN_RADIUS_PX / pixelsPerRadian;
    double maxAngle = MAX_RADIUS_PX / pixelsPerRadian;
    int n = lattice.size();
    double[][] rel = new double[n][];
    for (int k = 0; k < n; k++) {
      Vec3d p = lattice.get(k);
      double dx = p.x() - cam.x;
      double dy = p.y() - cam.y;
      double dz = p.z() - cam.z;
      double d = Math.max(Math.sqrt(dx * dx + dy * dy + dz * dz), 1.0E-6);
      rel[k] = new double[] {dx, dy, dz, d, dx / d, dy / d, dz / d};
    }

    List<Dot> dots = new ArrayList<>();
    Dot marker = hit && markerAt != null ? marker(markerAt, face, cam, pixelsPerRadian) : null;
    // Path dots under the marker would only show as clipped bits around it.
    double clear = marker == null ? 0.0 : marker.radius() * 2.0;
    Vec3d end = path.isEmpty() ? null : path.getLast();

    for (int k = 0; k < n; k++) {
      double[] p = rel[k];
      double distance = p[3];
      double base = fade(distance);
      if (base <= 0.0) {
        continue;
      }
      if (marker != null && lattice.get(k).distanceToSqr(end) < clear * clear) {
        continue;
      }
      double angle = Math.clamp(DOT_RADIUS / distance, minAngle, maxAngle);
      double[] q = rel[k + 1 < n ? k + 1 : Math.max(k - 1, 0)];
      double sep = Math.sqrt(sq(p[4] - q[4]) + sq(p[5] - q[5]) + sq(p[6] - q[6]));
      double ratio = GAP * 2.0 * angle / Math.max(sep, 1.0e-9);
      double level = Math.clamp(Math.log(ratio) / Math.log(2.0), 0.0, MAX_LEVEL);
      double alpha = base * levelAlpha(k, level) * (1.0 - PULSE_ALPHA * pulse);
      if (alpha > 0.02) {
        dots.add(new Dot(p[0], p[1], p[2], angle * distance * (1.0 + PULSE_SIZE * pulse), alpha));
      }
    }
    return new Layout(dots, marker);
  }

  /**
   * The hit marker: larger than a dot at the same distance, shrinking with distance, and lifted off
   * the hit surface by its radius so the surface does not cut it and send part of it to the hidden
   * pass. {@code face} is {@code null} for an entity hit. {@code null} when it is too close to the
   * camera to show.
   */
  private static Dot marker(Vec3d end, Face face, Vec3 cam, double pixelsPerRadian) {
    double dx = end.x() - cam.x;
    double dy = end.y() - cam.y;
    double dz = end.z() - cam.z;
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    double alpha = fade(distance);
    if (alpha <= 0.0) {
      return null;
    }
    double dotAngle =
        Math.clamp(
            DOT_RADIUS / distance,
            MIN_RADIUS_PX / pixelsPerRadian,
            MAX_RADIUS_PX / pixelsPerRadian);
    double endAngle =
        Math.clamp(
            END_RADIUS / distance,
            END_MIN_RADIUS_PX / pixelsPerRadian,
            END_MAX_RADIUS_PX / pixelsPerRadian);
    double radius = Math.max(endAngle, END_OVER_DOT * dotAngle) * distance;
    // A little more than the radius: at grazing angles far away depth precision runs out.
    double up = radius * LIFT;
    if (face == null) {
      // An entity hit lies on the bounding box, and models often stick out of it (a slime's shell):
      // pull the marker towards the camera instead, at least a quarter block.
      double pull = Math.min(Math.max(up, ENTITY_PULL), distance * 0.5) / distance;
      return new Dot(dx * (1.0 - pull), dy * (1.0 - pull), dz * (1.0 - pull), radius, alpha);
    }
    double[] lift = normal(face);
    return new Dot(dx + lift[0] * up, dy + lift[1] * up, dz + lift[2] * up, radius, alpha);
  }

  /**
   * Visibility of lattice point {@code k} at a fractional thinning level: shown if {@code k} is a
   * multiple of {@code 2^ceil(level)}, fading out if it is only a multiple of {@code
   * 2^floor(level)}.
   */
  static double levelAlpha(int k, double level) {
    int high = (int) Math.ceil(level);
    int low = (int) Math.floor(level);
    if (k % (1 << high) == 0) {
      return 1.0;
    }
    if (k % (1 << low) == 0) {
      return high - level;
    }
    return 0.0;
  }

  /**
   * Moves the start of the lattice to the bow hand and blends it back into the real path: lattice
   * point {@code k} lies {@code k * LATTICE} blocks along the flight.
   */
  static List<Vec3d> fromHand(List<Vec3d> lattice, Vec3d hand) {
    if (hand.lengthSqr() == 0.0 || lattice.isEmpty()) {
      return lattice;
    }
    // Close hits keep the plain path: the arc would not merge before the target. The arc fades in
    // between these path lengths so it does not pop while the aim sweeps over the threshold.
    double length = lattice.size() * LATTICE;
    double strength = Math.clamp((length - HAND_MIN_PATH) / (HAND_FULL_PATH - HAND_MIN_PATH), 0, 1);
    strength = strength * strength * (3.0 - 2.0 * strength);
    if (strength <= 0.0) {
      return lattice;
    }
    double merge = MERGE_DISTANCE;
    List<Vec3d> out = new ArrayList<>(lattice.size());
    for (int k = 0; k < lattice.size(); k++) {
      double t = Math.clamp(k * LATTICE / merge, 0.0, 1.0);
      double weight = strength * (1.0 - t * t * (3.0 - 2.0 * t));
      out.add(weight > 0.0 ? lattice.get(k).add(hand.scale(weight)) : lattice.get(k));
    }
    return out;
  }

  /** Points every {@link #LATTICE} blocks along the path, starting at its first point. */
  static List<Vec3d> resample(List<Vec3d> path) {
    List<Vec3d> out = new ArrayList<>();
    double carry = 0.0;
    for (int i = 0; i + 1 < path.size(); i++) {
      Vec3d a = path.get(i);
      Vec3d b = path.get(i + 1);
      double length = Math.sqrt(a.distanceToSqr(b));
      double t = carry;
      for (; t < length; t += LATTICE) {
        out.add(a.add(b.subtract(a).scale(t / length)));
      }
      carry = t - length;
    }
    return out;
  }

  /** Near fade-in times the gentle far dimming. */
  private static double fade(double distance) {
    double near =
        Math.clamp((distance - NEAR_FADE_START) / (NEAR_FADE_END - NEAR_FADE_START), 0.0, 1.0);
    double far =
        Math.clamp((distance - FAR_FADE_START) / (FAR_FADE_END - FAR_FADE_START), 0.0, 1.0);
    far = far * far * (3.0 - 2.0 * far);
    return near * (1.0 - (1.0 - FAR_ALPHA) * far);
  }

  /** Outward normal of a block face. */
  private static double[] normal(Face face) {
    return switch (face) {
      case DOWN -> new double[] {0.0, -1.0, 0.0};
      case UP -> new double[] {0.0, 1.0, 0.0};
      case NORTH -> new double[] {0.0, 0.0, -1.0};
      case SOUTH -> new double[] {0.0, 0.0, 1.0};
      case WEST -> new double[] {-1.0, 0.0, 0.0};
      case EAST -> new double[] {1.0, 0.0, 0.0};
    };
  }

  private static double sq(double v) {
    return v * v;
  }

  /** One dot: camera-relative center, world radius and opacity. */
  record Dot(double dx, double dy, double dz, double radius, double alpha) {
    void emit(
        PoseStack.Pose pose, VertexConsumer buffer, Vector3f up, Vector3f left, double scale) {
      float r = (float) (radius * scale);
      float rx = (float) dx;
      float ry = (float) dy;
      float rz = (float) dz;
      int color = ((int) Math.round(MAX_ALPHA * alpha) << 24) | COLOR_RGB;
      float ux = up.x() * r;
      float uy = up.y() * r;
      float uz = up.z() * r;
      float lx = left.x() * r;
      float ly = left.y() * r;
      float lz = left.z() * r;
      buffer
          .addVertex(pose, rx + lx + ux, ry + ly + uy, rz + lz + uz)
          .setUv(-1.0F, 1.0F)
          .setColor(color);
      buffer
          .addVertex(pose, rx + lx - ux, ry + ly - uy, rz + lz - uz)
          .setUv(-1.0F, -1.0F)
          .setColor(color);
      buffer
          .addVertex(pose, rx - lx - ux, ry - ly - uy, rz - lz - uz)
          .setUv(1.0F, -1.0F)
          .setColor(color);
      buffer
          .addVertex(pose, rx - lx + ux, ry - ly + uy, rz - lz + uz)
          .setUv(1.0F, 1.0F)
          .setColor(color);
    }
  }
}
