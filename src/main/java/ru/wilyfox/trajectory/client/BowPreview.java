package ru.wilyfox.trajectory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import ru.wilyfox.trajectory.config.TrajectoryConfig;
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
  private static Prediction previous;

  private BowPreview() {}

  /** The prediction for a release on the next tick; this is what the server will fire. */
  public static Prediction current() {
    return current;
  }

  /**
   * The prediction one tick earlier, for drawing: frames between two ticks blend from it to {@link
   * #current()} the way vanilla blends the camera, so the line moves with the player instead of
   * jumping once per tick. {@code null} on the first tick of a draw.
   */
  static Prediction previous() {
    return previous;
  }

  /** Runs at the end of every client tick. */
  public static void tick(Minecraft minecraft) {
    Prediction last = current;
    current = compute(minecraft.player, minecraft.level);
    previous = current == null ? null : last;
    if (last != null && current == null) {
      ShotLog.released(last);
    }
  }

  private static Prediction compute(LocalPlayer player, ClientLevel level) {
    if (!TrajectoryConfig.get().enabled
        || player == null
        || level == null
        || player.isPassenger()) {
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
    boolean living =
        path.end() == Trajectory.End.ENTITY
            && level.getEntity(path.entity().id()) instanceof LivingEntity;
    return new Prediction(launch, path, ticksHeld, level.getGameTime(), living);
  }

  /**
   * A predicted shot: the launch, the flight without spread, when it was computed, and whether it
   * ends in a living entity (mobs, players, armor stands).
   */
  public record Prediction(
      BowShot.Launch launch, Trajectory path, int ticksHeld, long gameTime, boolean livingTarget) {}
}
