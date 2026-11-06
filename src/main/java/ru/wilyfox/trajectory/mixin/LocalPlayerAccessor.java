package ru.wilyfox.trajectory.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The values {@code LocalPlayer.sendPosition} last sent to the server. */
@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
  @Accessor("xLast")
  double trajectory$xLast();

  @Accessor("yLast")
  double trajectory$yLast();

  @Accessor("zLast")
  double trajectory$zLast();

  @Accessor("yRotLast")
  float trajectory$yRotLast();

  @Accessor("xRotLast")
  float trajectory$xRotLast();

  @Accessor("lastOnGround")
  boolean trajectory$lastOnGround();
}
