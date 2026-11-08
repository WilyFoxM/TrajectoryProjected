package ru.wilyfox.trajectory.dev.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the shot log tell when a real arrow has come to rest. */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
  @Invoker("isInGround")
  boolean trajectory$isInGround();
}
