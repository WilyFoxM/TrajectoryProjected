package ru.wilyfox.trajectory.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import ru.wilyfox.trajectory.TrajectoryProjected;
import ru.wilyfox.trajectory.compat.Gpu;

/**
 * Drawing after the level has been rendered, onto the finished image, depth-tested against the main
 * depth buffer. By then water and other translucent layers are drawn, and composed where improved
 * transparency keeps them apart, so they do not tint the line; with an Iris shader pack, the pack
 * has composed the frame too, and it skips pipelines it has no program for, ours included. The
 * geometry is recorded during the level's submit phase and replayed with our own pipelines right
 * after the level render returns.
 */
public final class AfterLevel {
  private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");
  private static final MethodHandle PACK_IN_USE = resolve("isShaderPackInUse");
  private static final MethodHandle SHADOW_PASS = resolve("isRenderingShadowPass");

  private static final List<Recorded> RECORDED = new ArrayList<>();
  private static Matrix4fc viewRotation;

  private AfterLevel() {}

  /** Whether a shader pack is active now; read every frame, since a pack can be toggled. */
  public static boolean shaderPackActive() {
    return call(PACK_IN_USE);
  }

  private static boolean call(MethodHandle handle) {
    if (handle == null) {
      return false;
    }
    try {
      return (boolean) handle.invokeExact();
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new IllegalStateException("Iris state", e);
    }
  }

  /**
   * Whether submits now belong to the pack's shadow pass, which renders the level from the sun:
   * nothing of ours is recorded there.
   */
  public static boolean shadowPass() {
    return call(SHADOW_PASS);
  }

  /** Drops anything left from a frame whose level render did not reach {@link #flush()}. */
  static void beginFrame() {
    RECORDED.clear();
  }

  /** Records geometry to replay after the level render, in submission order. */
  static void record(
      Matrix4fc viewRotation,
      PoseStack poseStack,
      RenderType type,
      SubmitNodeCollector.CustomGeometryRenderer geometry) {
    AfterLevel.viewRotation = new Matrix4f(viewRotation);
    RECORDED.add(new Recorded(type, poseStack.last().copy(), geometry));
  }

  /** Replays what was recorded this frame; called right after the level render. */
  public static void flush() {
    if (RECORDED.isEmpty()) {
      return;
    }
    try {
      RenderTarget target = Gpu.mainTarget();
      GpuBufferSlice transforms = Gpu.writeTransform(new Matrix4f(viewRotation));
      for (Recorded r : RECORDED) {
        Gpu.drawNow(
            target, transforms, r.type(), builder -> r.geometry().render(r.pose(), builder));
      }
    } finally {
      RECORDED.clear();
    }
  }

  private static MethodHandle resolve(String method) {
    if (!IRIS) {
      return null;
    }
    try {
      Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
      Object instance = api.getMethod("getInstance").invoke(null);
      return MethodHandles.publicLookup()
          .findVirtual(api, method, MethodType.methodType(boolean.class))
          .bindTo(instance);
    } catch (ReflectiveOperationException e) {
      TrajectoryProjected.LOGGER.warn("Iris API not found; drawing as without shaders", e);
      return null;
    }
  }

  private record Recorded(
      RenderType type, PoseStack.Pose pose, SubmitNodeCollector.CustomGeometryRenderer geometry) {}
}
