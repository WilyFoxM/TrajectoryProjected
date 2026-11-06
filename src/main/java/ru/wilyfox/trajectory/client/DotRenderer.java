package ru.wilyfox.trajectory.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import ru.wilyfox.trajectory.sim.Trajectory;
import ru.wilyfox.trajectory.sim.Vec3d;

/**
 * Draws the predicted path as evenly spaced round dots: camera-facing quads whose fragment shader
 * cuts an anti-aliased disc. First in-game version; spacing, size and color are expected to change
 * after looking at it in the game.
 */
final class DotRenderer {
  private static final String MOD_ID = "trajectory_projected";

  private static final RenderPipeline PIPELINE =
      RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
          .withLocation(Identifier.fromNamespaceAndPath(MOD_ID, "pipeline/dots"))
          .withVertexShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/dot"))
          .withFragmentShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/dot"))
          .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
          .withCull(false)
          .build();

  private static final RenderType DOTS =
      RenderType.create(
          MOD_ID + "_dots", RenderSetup.builder(PIPELINE).sortOnUpload().createRenderSetup());

  /** {@code f6f7eb} from the palette. */
  private static final int COLOR_RGB = 0xF6F7EB;

  /** Candidate positions along the path; dots are picked from them by on-screen spacing. */
  private static final double STEP = 0.1;

  private static final float DOT_RADIUS = 0.04F;
  private static final float END_RADIUS = 0.11F;

  /** Dot radius on screen, in pixels: far dots never get smaller, near ones never bigger. */
  private static final double MIN_RADIUS_PX = 1.5;

  private static final double MAX_RADIUS_PX = 3.2;

  private static final double MIN_END_RADIUS_PX = 4.5;

  /** Dots fainter than this are not drawn and do not take up space. */
  private static final double MIN_FADE = 0.15;

  /**
   * Center-to-center distance between neighbouring dots on screen, in units of the two radii: 1.0
   * would make them touch. Spacing by screen angle keeps the dots apart where the path runs away
   * from the camera.
   */
  private static final double GAP = 1.6;

  /** Dots closer than this to the camera are hidden; they fade in over the next blocks. */
  private static final double FADE_START = 1.0;

  private static final double FADE_END = 2.5;

  private DotRenderer() {}

  static void collect(LevelRenderContext context) {
    BowPreview.Prediction prediction = BowPreview.current();
    if (prediction == null) {
      return;
    }
    CameraRenderState camera = context.levelState().cameraRenderState;
    Vec3 cam = camera.pos;
    Vector3f up = camera.orientation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
    Vector3f left = camera.orientation.transform(new Vector3f(-1.0F, 0.0F, 0.0F));
    // Pixels per radian near the screen center: projection m11 is 1 / tan(fov / 2).
    double pixelsPerRadian =
        camera.projectionMatrix.m11() * Minecraft.getInstance().getWindow().getHeight() / 2.0;
    double minAngle = MIN_RADIUS_PX / pixelsPerRadian;
    double maxAngle = MAX_RADIUS_PX / pixelsPerRadian;
    double minEndAngle = MIN_END_RADIUS_PX / pixelsPerRadian;
    Trajectory path = prediction.path();
    boolean hit = path.end() == Trajectory.End.BLOCK || path.end() == Trajectory.End.ENTITY;

    PoseStack poseStack = context.poseStack();
    context
        .submitNodeCollector()
        .submitCustomGeometry(
            poseStack,
            DOTS,
            (pose, buffer) -> {
              List<Vec3d> points = path.points();
              double lastX = 0.0;
              double lastY = 0.0;
              double lastZ = 0.0;
              double lastAngle = -1.0;
              double carry = 0.0;
              for (int i = 0; i + 1 < points.size(); i++) {
                Vec3d a = points.get(i);
                Vec3d b = points.get(i + 1);
                double length = Math.sqrt(a.distanceToSqr(b));
                double t = carry;
                for (; t < length; t += STEP) {
                  double f = t / length;
                  double dx = a.x() + (b.x() - a.x()) * f - cam.x;
                  double dy = a.y() + (b.y() - a.y()) * f - cam.y;
                  double dz = a.z() + (b.z() - a.z()) * f - cam.z;
                  double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                  if (fade(distance) < MIN_FADE) {
                    continue;
                  }
                  double angle = Math.clamp(DOT_RADIUS / distance, minAngle, maxAngle);
                  double ux = dx / distance;
                  double uy = dy / distance;
                  double uz = dz / distance;
                  if (lastAngle >= 0.0) {
                    double cx = ux - lastX;
                    double cy = uy - lastY;
                    double cz = uz - lastZ;
                    double chord = Math.sqrt(cx * cx + cy * cy + cz * cz);
                    if (chord < GAP * (angle + lastAngle)) {
                      continue;
                    }
                  }
                  dot(pose, buffer, up, left, dx, dy, dz, distance, angle);
                  lastX = ux;
                  lastY = uy;
                  lastZ = uz;
                  lastAngle = angle;
                }
                carry = t - length;
              }
              if (hit) {
                Vec3d end = path.last();
                double dx = end.x() - cam.x;
                double dy = end.y() - cam.y;
                double dz = end.z() - cam.z;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance >= FADE_START) {
                  double angle = Math.max(END_RADIUS / distance, minEndAngle);
                  dot(pose, buffer, up, left, dx, dy, dz, distance, angle);
                }
              }
            });
  }

  private static double fade(double distance) {
    return Math.clamp((distance - FADE_START) / (FADE_END - FADE_START), 0.0, 1.0);
  }

  /** One camera-facing quad of angular radius {@code angle} at a camera-relative position. */
  private static void dot(
      PoseStack.Pose pose,
      VertexConsumer buffer,
      Vector3f up,
      Vector3f left,
      double dx,
      double dy,
      double dz,
      double distance,
      double angle) {
    double fade = fade(distance);
    if (fade <= 0.0) {
      return;
    }
    float r = (float) (angle * distance);
    float rx = (float) dx;
    float ry = (float) dy;
    float rz = (float) dz;
    int alpha = (int) Math.round(235.0 * fade);
    int color = (alpha << 24) | COLOR_RGB;
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
