package ru.wilyfox.trajectory.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.wilyfox.trajectory.client.AfterLevel;
//? if <26.3 {
import net.minecraft.client.DeltaTracker;
import org.joml.Matrix4fc;
//?}
//? if <26.2 {
/*import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
*///?}

/**
 * Right after the level render, before the hand: water and other translucent layers are drawn and
 * composed by then, as is an Iris shader pack's frame, and what was recorded for {@link AfterLevel}
 * is drawn on top.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererAfterLevelMixin {
  //? if >=26.3 {
  /*@WrapOperation(
      method = "renderLevel",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V"))
  private void trajectory$drawAfterLevel(
      LevelRenderer levelRenderer,
      GraphicsResourceAllocator allocator,
      boolean renderOutline,
      CameraRenderState cameraState,
      GpuBufferSlice terrainFog,
      Vector4f fogColor,
      boolean shouldRenderSky,
      boolean consistentDepthRequired,
      Operation<Void> original) {
    original.call(
        levelRenderer,
        allocator,
        renderOutline,
        cameraState,
        terrainFog,
        fogColor,
        shouldRenderSky,
        consistentDepthRequired);
    AfterLevel.flush();
  }
  *///?} else if >=26.2 {
  @WrapOperation(
      method = "renderLevel",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"))
  private void trajectory$drawAfterLevel(
      LevelRenderer levelRenderer,
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderOutline,
      CameraRenderState cameraState,
      Matrix4fc modelViewMatrix,
      GpuBufferSlice terrainFog,
      Vector4f fogColor,
      boolean shouldCreateFog,
      Operation<Void> original) {
    original.call(
        levelRenderer,
        allocator,
        deltaTracker,
        renderOutline,
        cameraState,
        modelViewMatrix,
        terrainFog,
        fogColor,
        shouldCreateFog);
    AfterLevel.flush();
  }
  //?} else {
  /*@WrapOperation(
      method = "renderLevel",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V"))
  private void trajectory$drawAfterLevel(
      LevelRenderer levelRenderer,
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderOutline,
      CameraRenderState cameraState,
      Matrix4fc modelViewMatrix,
      GpuBufferSlice terrainFog,
      Vector4f fogColor,
      boolean shouldRenderSky,
      ChunkSectionsToRender chunkSections,
      Operation<Void> original) {
    original.call(
        levelRenderer,
        allocator,
        deltaTracker,
        renderOutline,
        cameraState,
        modelViewMatrix,
        terrainFog,
        fogColor,
        shouldRenderSky,
        chunkSections);
    AfterLevel.flush();
  }
  *///?}
}
