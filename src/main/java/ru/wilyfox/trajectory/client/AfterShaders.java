package ru.wilyfox.trajectory.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import ru.wilyfox.trajectory.TrajectoryProjected;

/**
 * Drawing after an Iris shader pack has composed the frame. While a pack is active, Iris draws the
 * level with the pack's programs and skips pipelines it has no program for, ours included. So the
 * geometry is recorded during the level's submit phase instead, and replayed with our own pipelines
 * right after the level render returns: onto the finished image, depth-tested against the main
 * depth buffer the pack leaves behind.
 */
public final class AfterShaders {
  private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");
  private static final MethodHandle PACK_IN_USE = resolve("isShaderPackInUse");
  private static final MethodHandle SHADOW_PASS = resolve("isRenderingShadowPass");

  private static final List<Recorded> RECORDED = new ArrayList<>();
  private static Matrix4fc viewRotation;

  private AfterShaders() {}

  /** Whether a shader pack is active now; read every frame, since a pack can be toggled. */
  public static boolean active() {
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
    AfterShaders.viewRotation = new Matrix4f(viewRotation);
    RECORDED.add(new Recorded(type, poseStack.last().copy(), geometry));
  }

  /** Replays what was recorded this frame; called right after the level render. */
  public static void flush() {
    if (RECORDED.isEmpty()) {
      return;
    }
    try {
      RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
      GpuBufferSlice transforms =
          RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(viewRotation));
      for (Recorded r : RECORDED) {
        draw(target, transforms, r);
      }
    } finally {
      RECORDED.clear();
    }
  }

  private static void draw(RenderTarget target, GpuBufferSlice transforms, Recorded r) {
    RenderType type = r.type();
    try (ByteBufferBuilder bytes = new ByteBufferBuilder(type.format().getVertexSize() * 256)) {
      BufferBuilder builder = new BufferBuilder(bytes, type.primitiveTopology(), type.format());
      r.geometry().render(r.pose(), builder);
      try (MeshData mesh = builder.build()) {
        if (mesh == null) {
          return;
        }
        int indexCount = mesh.drawState().indexCount();
        RenderSystem.AutoStorageIndexBuffer indices =
            RenderSystem.getSequentialBuffer(type.primitiveTopology());
        GpuBuffer indexBuffer = indices.getBuffer(indexCount);
        try (GpuBuffer vertices =
            RenderSystem.getDevice()
                .createBuffer(
                    () -> "Trajectory Projected after shaders",
                    GpuBuffer.USAGE_VERTEX,
                    mesh.vertexBuffer())) {
          try (RenderPass pass =
              RenderSystem.getDevice()
                  .createCommandEncoder()
                  .createRenderPass(
                      () -> "Trajectory Projected after shaders",
                      target.getColorTextureView(),
                      Optional.empty(),
                      target.useDepth ? target.getDepthTextureView() : null,
                      OptionalDouble.empty())) {
            pass.setPipeline(type.pipeline());
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertices.slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(indexCount, 1, 0, 0, 0);
          }
        }
      }
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
