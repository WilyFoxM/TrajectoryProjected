package ru.wilyfox.trajectory.client;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3f;
import ru.wilyfox.trajectory.config.TrajectoryConfig;
import ru.wilyfox.trajectory.sim.Aabb;
import ru.wilyfox.trajectory.sim.Face;
import ru.wilyfox.trajectory.sim.SpreadZone;
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

  /**
   * The lead-in arc from the bow hand is not where the arrow flies, so the depth buffer cannot say
   * whether it is hidden: these pipelines draw it without a depth test, and the style (plain or
   * outlined) is chosen per dot from a ray to the real path point it stands for.
   */
  private static final RenderPipeline OVERLAY =
      RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
          .withLocation(Identifier.fromNamespaceAndPath(MOD_ID, "pipeline/dots_overlay"))
          .withVertexShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/dot"))
          .withFragmentShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/dot"))
          .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
          .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
          .withCull(false)
          .build();

  private static final RenderPipeline OVERLAY_HIDDEN =
      RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
          .withLocation(Identifier.fromNamespaceAndPath(MOD_ID, "pipeline/dots_overlay_hidden"))
          .withVertexShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/dot"))
          .withFragmentShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/dot_hidden"))
          .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
          .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
          .withCull(false)
          .build();

  private static final RenderType DOTS_OVERLAY =
      RenderType.create(
          MOD_ID + "_dots_overlay",
          RenderSetup.builder(OVERLAY).sortOnUpload().createRenderSetup());

  private static final RenderType DOTS_OVERLAY_HIDDEN =
      RenderType.create(
          MOD_ID + "_dots_overlay_hidden",
          RenderSetup.builder(OVERLAY_HIDDEN).sortOnUpload().createRenderSetup());

  /** The spread zone on block faces: a projected decal, depth-tested, never written to depth. */
  private static final RenderPipeline ZONE =
      RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
          .withLocation(Identifier.fromNamespaceAndPath(MOD_ID, "pipeline/zone"))
          .withVertexShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/dot"))
          .withFragmentShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/zone"))
          .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
          .withCull(false)
          .build();

  private static final RenderType ZONES =
      RenderType.create(
          MOD_ID + "_zone", RenderSetup.builder(ZONE).sortOnUpload().createRenderSetup());

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
   * drawn line therefore starts beside the eye, on the side of the hand holding the bow, and merges
   * into the real path over {@link #MERGE_SHARE} of its length (at least {@link #MERGE_DISTANCE}
   * blocks), so the hit point stays exact.
   *
   * <p>The arrow spawns 0.1 below the eye, so on screen the real path itself first rises towards
   * the crosshair and then falls; a start below the view makes the line climb and turn sharply
   * down. The start is therefore slightly above the eye ({@link #HAND_UP}): the line only ever
   * descends, from the side into the fall, like an arc seen a little from the side. Paths shorter
   * than {@link #HAND_MIN_PATH} blocks are drawn plain; the arc fades in up to {@link
   * #HAND_FULL_PATH}.
   */
  private static final double HAND_SIDE = 0.2;

  private static final double HAND_UP = 0.14;
  private static final double MERGE_DISTANCE = 12.0;
  private static final double MERGE_SHARE = 0.6;

  /** Dots moved by less than this share of the hand offset are drawn as the exact path. */
  private static final double ARC_EPSILON = 0.02;

  /** A block this close to the real point does not count as hiding it. */
  private static final double OCCLUSION_SLACK = 0.1;

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

  /** Bits of the zone's vertex color blue channel, read back in {@code zone.fsh}. */
  private static final int ZONE_FULL = 1;

  private static final int ZONE_HALF_RING = 2;
  private static final int ZONE_WIDE_RING = 4;

  /** Opacity at the end of an uncertain tail, relative to the certain path. */
  private static final double UNCERTAIN_ALPHA = 0.2;

  private static double pulseStrength;
  private static long lastFrameNanos;

  private DotRenderer() {}

  static void collect(LevelRenderContext context) {
    if (AfterShaders.shadowPass()) {
      return;
    }
    if (AfterShaders.active()) {
      AfterShaders.beginFrame();
    }
    ShotPreview.Prediction current = ShotPreview.current();
    if (current == null) {
      return;
    }
    ShotPreview.Prediction previous = ShotPreview.previous();
    Minecraft minecraft = Minecraft.getInstance();
    float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);

    CameraRenderState camera = context.levelState().cameraRenderState;
    // Pixels per radian near the screen center: projection m11 is 1 / tan(fov / 2).
    double pixelsPerRadian =
        camera.projectionMatrix.m11() * minecraft.getWindow().getHeight() / 2.0;
    Vector3f up = camera.orientation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
    Vector3f left = camera.orientation.transform(new Vector3f(-1.0F, 0.0F, 0.0F));

    TrajectoryConfig config = TrajectoryConfig.get();
    boolean living = current.livingTarget();
    double pulse = pulse(living && config.livingPulse);
    Vec3d hand = Vec3d.ZERO;
    // The crossbow is held in front of the view: a line from the side looks off, so its path starts
    // at the eyes, as close hits do.
    if (config.firstPersonLeadIn
        && current.weapon() != ShotPreview.Weapon.CROSSBOW
        && minecraft.options.getCameraType().isFirstPerson()
        && minecraft.getCameraEntity() == minecraft.player
        && minecraft.player != null) {
      HumanoidArm arm =
          current.hand() == InteractionHand.MAIN_HAND
              ? minecraft.player.getMainArm()
              : minecraft.player.getMainArm().getOpposite();
      double side = arm == HumanoidArm.RIGHT ? HAND_SIDE : -HAND_SIDE;
      hand =
          new Vec3d(
              -left.x() * side + up.x() * HAND_UP,
              -left.y() * side + up.y() * HAND_UP,
              -left.z() * side + up.z() * HAND_UP);
    }
    Vec3 eye = camera.pos;
    Predicate<Vec3d> occluded = point -> occluded(minecraft, eye, point);

    for (int i = 0; i < current.shots().size(); i++) {
      ShotPreview.Shot shot = current.shots().get(i);
      ShotPreview.Shot before = previous == null ? null : previous.shots().get(i);
      collectShot(
          context,
          minecraft,
          config,
          shot,
          before,
          partial,
          camera,
          pixelsPerRadian,
          up,
          left,
          pulse,
          hand,
          occluded);
    }
  }

  private static void collectShot(
      LevelRenderContext context,
      Minecraft minecraft,
      TrajectoryConfig config,
      ShotPreview.Shot current,
      ShotPreview.Shot previous,
      float partial,
      CameraRenderState camera,
      double pixelsPerRadian,
      Vector3f up,
      Vector3f left,
      double pulse,
      Vec3d hand,
      Predicate<Vec3d> occluded) {
    List<Vec3d> path =
        previous == null
            ? current.path().points()
            : blend(previous.path().points(), current.path().points(), partial);
    Trajectory.End end = current.path().end();
    boolean hit = config.hitMarker && (end == Trajectory.End.BLOCK || end == Trajectory.End.ENTITY);
    boolean cross = current.livingTarget() && config.livingCross;
    Vec3d markerAt = path.isEmpty() ? null : path.getLast();
    if (end == Trajectory.End.ENTITY && markerAt != null) {
      markerAt = facingSide(minecraft, current.path().entity(), markerAt, camera.pos);
    }
    Layout layout =
        layout(
            path,
            certainLength(path, current.path().certainPoints()),
            hit,
            current.path().face(),
            markerAt,
            camera.pos,
            pixelsPerRadian,
            pulse,
            hand,
            occluded);
    if (config.showsZone() && current.zone() != null && !current.zonePatches().isEmpty()) {
      SpreadZone.Zone zone =
          previous != null && previous.zone() != null
              ? SpreadZone.Zone.blend(previous.zone(), current.zone(), partial)
              : current.zone();
      submitZone(context, zone, current.zonePatches(), camera.pos);
    }
    if (layout.dots().isEmpty()
        && layout.overlay().isEmpty()
        && layout.overlayHidden().isEmpty()
        && layout.marker() == null) {
      return;
    }

    PoseStack poseStack = context.poseStack();
    submit(context, poseStack, DOTS, layout.dots(), up, left, 1.0);
    submit(context, poseStack, DOTS_OVERLAY, layout.overlay(), up, left, 1.0);
    if (config.showHidden) {
      submit(context, poseStack, DOTS_HIDDEN, layout.dots(), up, left, HIDDEN_SCALE);
      submit(
          context, poseStack, DOTS_OVERLAY_HIDDEN, layout.overlayHidden(), up, left, HIDDEN_SCALE);
    }
    if (layout.marker() != null) {
      Dot m = layout.marker();
      List<Dot> marker =
          List.of(
              cross
                  ? new Dot(
                      m.dx(),
                      m.dy(),
                      m.dz(),
                      m.radius() * CROSS_SCALE * (1.0 + PULSE_MARKER_SIZE * pulse),
                      m.alpha())
                  : m);
      submit(context, poseStack, cross ? CROSSES : DOTS, marker, up, left, 1.0);
      if (config.showHidden) {
        submit(
            context,
            poseStack,
            cross ? CROSSES_HIDDEN : DOTS_HIDDEN,
            marker,
            up,
            left,
            cross ? HIDDEN_CROSS_SCALE : HIDDEN_SCALE);
      }
    }
  }

  /**
   * Length along the path up to its last certain point; past it the projectile may already have
   * ended (a firework's random lifetime). Infinite when every point is certain.
   */
  static double certainLength(List<Vec3d> path, int certainPoints) {
    if (certainPoints >= path.size()) {
      return Double.POSITIVE_INFINITY;
    }
    double length = 0.0;
    for (int i = 0; i + 1 < certainPoints; i++) {
      length += Math.sqrt(path.get(i).distanceToSqr(path.get(i + 1)));
    }
    return length;
  }

  /** Whether blocks stand between the camera and a world point (block outline shapes). */
  private static boolean occluded(Minecraft minecraft, Vec3 eye, Vec3d point) {
    if (minecraft.level == null) {
      return false;
    }
    Vec3 to = new Vec3(point.x(), point.y(), point.z());
    BlockHitResult hit =
        minecraft.level.clip(
            new ClipContext(
                eye,
                to,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()));
    if (hit.getType() == HitResult.Type.MISS) {
      return false;
    }
    double reach = eye.distanceTo(to) - OCCLUSION_SLACK;
    return hit.getLocation().distanceToSqr(eye) < reach * reach;
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

  /**
   * Draws the zone on its lit patches. Each vertex carries its ellipse coordinates, so the fragment
   * shader finds the edge exactly where the projected ellipse crosses the face. Drawn in an earlier
   * order than the dots, so the path and the marker stay on top.
   */
  private static void submitZone(
      LevelRenderContext context,
      SpreadZone.Zone zone,
      List<ZoneSurfaces.Patch> patches,
      Vec3 cam) {
    Vec3d c = zone.center();
    double distance = Math.sqrt(sq(c.x() - cam.x) + sq(c.y() - cam.y) + sq(c.z() - cam.z));
    double alpha = fade(distance);
    if (alpha <= 0.0) {
      return;
    }
    TrajectoryConfig config = TrajectoryConfig.get();
    int parts =
        (config.zoneFull ? ZONE_FULL : 0)
            | (config.zoneHalfRing ? ZONE_HALF_RING : 0)
            | (config.zoneWideRing ? ZONE_WIDE_RING : 0);
    int color =
        ((int) Math.round(255 * alpha) << 24)
            | ((int) Math.round(255 * zone.coreRatio()) << 16)
            | ((int) Math.round(255 * zone.wideRatio()) << 8)
            | parts;
    emit(
        context,
        SUBMIT_ORDER - 1,
        context.poseStack(),
        ZONES,
        (pose, buffer) -> {
          for (ZoneSurfaces.Patch patch : patches) {
            // A convex polygon as a fan of quads; an odd last one repeats its final corner.
            List<Vec3d> corners = patch.corners();
            for (int i = 1; i + 1 < corners.size(); i += 2) {
              Vec3d last = corners.get(Math.min(i + 2, corners.size() - 1));
              for (Vec3d v :
                  new Vec3d[] {corners.get(0), corners.get(i), corners.get(i + 1), last}) {
                double[] uv = zone.coordinates(v);
                buffer
                    .addVertex(
                        pose,
                        (float) (v.x() - cam.x),
                        (float) (v.y() - cam.y),
                        (float) (v.z() - cam.z))
                    .setUv((float) uv[0], (float) uv[1])
                    .setColor(color);
              }
            }
          }
        });
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
    emit(
        context,
        SUBMIT_ORDER,
        poseStack,
        type,
        (pose, buffer) -> dots.forEach(d -> d.emit(pose, buffer, up, left, scale)));
  }

  /**
   * Hands geometry to the level's submit collector, or, with a shader pack active, to {@link
   * AfterShaders} to draw once the pack has composed the frame.
   */
  private static void emit(
      LevelRenderContext context,
      int order,
      PoseStack poseStack,
      RenderType type,
      SubmitNodeCollector.CustomGeometryRenderer geometry) {
    if (AfterShaders.active()) {
      AfterShaders.record(
          context.levelState().cameraRenderState.viewRotationMatrix, poseStack, type, geometry);
      return;
    }
    context.submitNodeCollector().order(order).submitCustomGeometry(poseStack, type, geometry);
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

  /**
   * Camera-relative dots: the exact path (depth-tested), the lead-in arc from the hand split by
   * whether the real point behind it is visible, and the hit marker at the path's last point.
   */
  record Layout(List<Dot> dots, List<Dot> overlay, List<Dot> overlayHidden, Dot marker) {}

  /**
   * Lays out the dots of a path. Dots past {@code certainLength} blocks fade towards the end, where
   * the projectile may no longer be. {@code pulse} in [0, 1] dims and enlarges the path dots (not
   * the marker); {@code hand} is the offset of the drawn start from the real one (zero outside
   * first person).
   */
  static Layout layout(
      List<Vec3d> path,
      double certainLength,
      boolean hit,
      Face face,
      Vec3d markerAt,
      Vec3 cam,
      double pixelsPerRadian,
      double pulse,
      Vec3d hand,
      Predicate<Vec3d> occluded) {
    List<Vec3d> real = resample(path);
    double[] weights = handWeights(real.size(), hand);
    List<Vec3d> lattice = fromHand(real, weights, hand);
    double size = size();
    double gap = GAP * TrajectoryConfig.get().dotSpacing / 100.0;
    double minAngle = MIN_RADIUS_PX * size / pixelsPerRadian;
    double maxAngle = MAX_RADIUS_PX * size / pixelsPerRadian;
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
    List<Dot> overlay = new ArrayList<>();
    List<Dot> overlayHidden = new ArrayList<>();
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
      double angle = Math.clamp(DOT_RADIUS * size / distance, minAngle, maxAngle);
      double[] q = rel[k + 1 < n ? k + 1 : Math.max(k - 1, 0)];
      double sep = Math.sqrt(sq(p[4] - q[4]) + sq(p[5] - q[5]) + sq(p[6] - q[6]));
      double ratio = gap * 2.0 * angle / Math.max(sep, 1.0e-9);
      double level = Math.clamp(Math.log(ratio) / Math.log(2.0), 0.0, MAX_LEVEL);
      double alpha =
          base
              * levelAlpha(k, level)
              * (1.0 - PULSE_ALPHA * pulse)
              * uncertainty(k * LATTICE, certainLength, (n - 1) * LATTICE);
      if (alpha > 0.02) {
        Dot dot = new Dot(p[0], p[1], p[2], angle * distance * (1.0 + PULSE_SIZE * pulse), alpha);
        if (weights[k] <= ARC_EPSILON) {
          dots.add(dot);
        } else if (occluded.test(real.get(k))) {
          overlayHidden.add(dot);
        } else {
          overlay.add(dot);
        }
      }
    }
    return new Layout(dots, overlay, overlayHidden, marker);
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
    double size = size();
    double dotAngle =
        Math.clamp(
            DOT_RADIUS * size / distance,
            MIN_RADIUS_PX * size / pixelsPerRadian,
            MAX_RADIUS_PX * size / pixelsPerRadian);
    double endAngle =
        Math.clamp(
            END_RADIUS * size / distance,
            END_MIN_RADIUS_PX * size / pixelsPerRadian,
            END_MAX_RADIUS_PX * size / pixelsPerRadian);
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
   * Opacity factor along the uncertain tail of a path: 1 up to {@code certain} blocks, easing down
   * to {@link #UNCERTAIN_ALPHA} at the end.
   */
  static double uncertainty(double along, double certain, double total) {
    if (along <= certain || total <= certain) {
      return 1.0;
    }
    double t = Math.clamp((along - certain) / (total - certain), 0.0, 1.0);
    return 1.0 - (1.0 - UNCERTAIN_ALPHA) * t * t * (3.0 - 2.0 * t);
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
   * How far each lattice point is moved towards the bow hand, from 1 at the start to 0 at the merge
   * point; all zero outside first person and for close hits. Lattice point {@code k} lies {@code k
   * * LATTICE} blocks along the flight.
   */
  static double[] handWeights(int size, Vec3d hand) {
    double[] weights = new double[size];
    if (hand.lengthSqr() == 0.0 || size == 0) {
      return weights;
    }
    // Close hits keep the plain path: the arc would not merge before the target. The arc fades in
    // between these path lengths so it does not pop while the aim sweeps over the threshold.
    double length = size * LATTICE;
    double strength = Math.clamp((length - HAND_MIN_PATH) / (HAND_FULL_PATH - HAND_MIN_PATH), 0, 1);
    strength = strength * strength * (3.0 - 2.0 * strength);
    // Merging over most of the path lets the line join the real path where it already falls on
    // screen, so it bends into the fall instead of meeting it at a corner. Far away the offset is
    // a few pixels at most: it is divided by the distance.
    double merge = Math.max(MERGE_DISTANCE, MERGE_SHARE * length);
    for (int k = 0; k < size && strength > 0.0; k++) {
      double t = Math.clamp(k * LATTICE / merge, 0.0, 1.0);
      weights[k] = strength * (1.0 - t * t * (3.0 - 2.0 * t));
    }
    return weights;
  }

  /** The lattice with each point moved towards the bow hand by its weight. */
  static List<Vec3d> fromHand(List<Vec3d> lattice, double[] weights, Vec3d hand) {
    List<Vec3d> out = new ArrayList<>(lattice.size());
    for (int k = 0; k < lattice.size(); k++) {
      out.add(weights[k] > 0.0 ? lattice.get(k).add(hand.scale(weights[k])) : lattice.get(k));
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
    if (!TrajectoryConfig.get().farDimming) {
      return near;
    }
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

  /** Dot and marker size from the settings, as a factor of the default. */
  private static double size() {
    return TrajectoryConfig.get().dotSize / 100.0;
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
