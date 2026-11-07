package ru.wilyfox.trajectory.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.wilyfox.trajectory.client.AfterShaders;

/**
 * Right after the level render, before the hand: with an Iris shader pack active, the pack has
 * composed the frame by then, and what was recorded for {@link AfterShaders} is drawn on top.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererAfterLevelMixin {
  @WrapOperation(
      method = "renderLevel",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"))
  private void trajectory$drawAfterShaders(
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
    AfterShaders.flush();
  }
}
