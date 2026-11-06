package ru.wilyfox.trajectory.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import ru.wilyfox.trajectory.client.SentState;

/**
 * Records what the server will know about the player after this tick's movement packet: the sent
 * position, rotation and on-ground flag, and the movement the server derives from two consecutive
 * sent positions.
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerSendPositionMixin {
  @WrapMethod(method = "sendPosition")
  private void trajectory$recordSentState(Operation<Void> original) {
    LocalPlayerAccessor sent = (LocalPlayerAccessor) this;
    double x = sent.trajectory$xLast();
    double y = sent.trajectory$yLast();
    double z = sent.trajectory$zLast();
    original.call();
    SentState.record((LocalPlayer) (Object) this, x, y, z);
  }
}
