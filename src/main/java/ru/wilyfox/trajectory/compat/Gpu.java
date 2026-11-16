package ru.wilyfox.trajectory.compat;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
//? if >=26.2 {
import java.util.Optional;
//?} else {
/*import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalInt;
import org.joml.Vector3f;
import org.joml.Vector4f;
*///?}

/** GPU calls whose shape differs between Minecraft versions. Render thread. */
public final class Gpu {
  private static final String LABEL = "Trajectory Projected after level";

  private Gpu() {}

  /** A pipeline builder on {@code snippet} for {@code POSITION_TEX_COLOR} quads. */
  public static RenderPipeline.Builder texColorQuads(RenderPipeline.Snippet snippet) {
    //? if >=26.2 {
    // The snippets we build on already draw quads.
    return RenderPipeline.builder(snippet).withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR);
    //?} else {
    /*return RenderPipeline.builder(snippet)
        .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS);
    *///?}
  }

  /** The main render target, which the level is drawn into. */
  public static RenderTarget mainTarget() {
    //? if >=26.2 {
    return Minecraft.getInstance().gameRenderer.mainRenderTarget();
    //?} else {
    /*return Minecraft.getInstance().getMainRenderTarget();
    *///?}
  }

  /** Writes {@code modelView} into this frame's dynamic transforms, with no tint or offset. */
  public static GpuBufferSlice writeTransform(Matrix4f modelView) {
    //? if >=26.2 {
    return RenderSystem.getDynamicUniforms().writeTransform(modelView);
    //?} else {
    /*return RenderSystem.getDynamicUniforms()
        .writeTransform(modelView, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());
    *///?}
  }

  /**
   * Builds the geometry {@code fill} writes and draws it at once with {@code type}'s pipeline onto
   * {@code target}, depth-tested against its depth buffer.
   */
  public static void drawNow(
      RenderTarget target, GpuBufferSlice transforms, RenderType type, Consumer<BufferBuilder> fill) {
    //? if >=26.2 {
    var topology = type.primitiveTopology();
    //?} else {
    /*var topology = type.mode();
    *///?}
    try (ByteBufferBuilder bytes = new ByteBufferBuilder(type.format().getVertexSize() * 256)) {
      BufferBuilder builder = new BufferBuilder(bytes, topology, type.format());
      fill.accept(builder);
      try (MeshData mesh = builder.build()) {
        if (mesh == null) {
          return;
        }
        int indexCount = mesh.drawState().indexCount();
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(topology);
        GpuBuffer indexBuffer = indices.getBuffer(indexCount);
        try (GpuBuffer vertices =
            RenderSystem.getDevice().createBuffer(() -> LABEL, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer())) {
          //? if >=26.3 {
          /*boolean depth = target.hasDepth();
          *///?} else {
          boolean depth = target.useDepth;
          //?}
          try (RenderPass pass =
              RenderSystem.getDevice()
                  .createCommandEncoder()
                  .createRenderPass(
                      () -> LABEL,
                      target.getColorTextureView(),
                      //? if >=26.2 {
                      Optional.empty(),
                      //?} else {
                      /*OptionalInt.empty(),
                      *///?}
                      depth ? target.getDepthTextureView() : null,
                      OptionalDouble.empty())) {
            //? if >=26.3 {
            /*pass.setPipeline(RenderSystem.getCompiledPipeline(type.pipeline()));
            *///?} else {
            pass.setPipeline(type.pipeline());
            //?}
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setIndexBuffer(indexBuffer, indices.type());
            //? if >=26.2 {
            pass.setVertexBuffer(0, vertices.slice());
            pass.drawIndexed(indexCount, 1, 0, 0, 0);
            //?} else {
            /*pass.setVertexBuffer(0, vertices);
            pass.drawIndexed(0, 0, indexCount, 1);
            *///?}
          }
        }
      }
    }
  }
}
