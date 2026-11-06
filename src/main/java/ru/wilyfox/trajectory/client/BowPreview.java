package ru.wilyfox.trajectory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;
import ru.wilyfox.trajectory.sim.ArrowFlight;
import ru.wilyfox.trajectory.sim.BowShot;
import ru.wilyfox.trajectory.sim.ShooterState;
import ru.wilyfox.trajectory.sim.Trajectory;

/**
 * Predicts the arrow of the bow the player is drawing, once per client tick.
 *
 * <p>The prediction is computed at the end of a client tick, after the movement packet. A release
 * is detected at the start of the next tick and sent before that tick's movement packet, so the
 * server fires with exactly the state used here, and with the draw count read here.
 */
public final class BowPreview {
  /** Twenty seconds of flight; a full-power arrow lands well within it. */
  private static final int MAX_TICKS = 400;

  private static Prediction current;

  private BowPreview() {}

  public static Prediction current() {
    return current;
  }

  /** Runs at the end of every client tick. */
  public static void tick(Minecraft minecraft) {
    Prediction previous = current;
    current = compute(minecraft.player, minecraft.level);
    if (previous != null && current == null) {
      ShotLog.released(previous);
    }
  }

  private static Prediction compute(LocalPlayer player, ClientLevel level) {
    if (player == null || level == null || player.isPassenger()) {
      return null;
    }
    // Only the vanilla bow: other bows may change the launch.
    if (!player.isUsingItem() || !player.getUseItem().is(Items.BOW)) {
      return null;
    }
    ShooterState shooter = SentState.shooter(player);
    if (shooter == null) {
      return null;
    }
    int ticksHeld = player.getTicksUsingItem();
    BowShot.Launch launch = BowShot.launch(shooter, ticksHeld);
    if (launch == null) {
      return null;
    }
    Trajectory path =
        ArrowFlight.simulate(
            launch.spawn(),
            launch.nominalVelocity(),
            new ClientWorldView(level, player),
            MAX_TICKS);
    return new Prediction(launch, path, ticksHeld, level.getGameTime());
  }

  /** A predicted shot: the launch, the flight without spread, and when it was computed. */
  public record Prediction(BowShot.Launch launch, Trajectory path, int ticksHeld, long gameTime) {}
}
