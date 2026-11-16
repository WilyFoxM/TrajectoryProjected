package ru.wilyfox.trajectory.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.wilyfox.trajectory.client.CrosshairTurn;
//? if >=26.2 {
import net.minecraft.client.gui.Hud;
//?} else {
/*import net.minecraft.client.gui.Gui;
*///?}

/**
 * Rotates the crosshair sprite around its center while a drawn bow would hit a living entity. Only
 * the crosshair itself turns; the attack indicator below it is drawn by later calls. The crosshair
 * moved from {@code Gui} to {@code Hud} in 26.2.
 */
//? if >=26.2 {
@Mixin(Hud.class)
//?} else {
/*@Mixin(Gui.class)
*///?}
abstract class CrosshairMixin {
  @WrapOperation(
      method = "extractCrosshair",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
              ordinal = 0))
  private void trajectory$turnCrosshair(
      GuiGraphicsExtractor graphics,
      RenderPipeline pipeline,
      Identifier sprite,
      int x,
      int y,
      int width,
      int height,
      Operation<Void> original) {
    float degrees = CrosshairTurn.degrees();
    if (degrees == 0.0F) {
      original.call(graphics, pipeline, sprite, x, y, width, height);
      return;
    }
    float centerX = x + width / 2.0F;
    float centerY = y + height / 2.0F;
    graphics.pose().pushMatrix();
    graphics.pose().translate(centerX, centerY);
    graphics.pose().rotate((float) Math.toRadians(degrees));
    graphics.pose().translate(-centerX, -centerY);
    original.call(graphics, pipeline, sprite, x, y, width, height);
    graphics.pose().popMatrix();
  }
}
