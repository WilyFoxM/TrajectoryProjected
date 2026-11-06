package ru.wilyfox.trajectory.client;

import net.minecraft.client.player.LocalPlayer;
import ru.wilyfox.trajectory.mixin.LocalPlayerAccessor;
import ru.wilyfox.trajectory.sim.ShooterState;
import ru.wilyfox.trajectory.sim.Vec3d;

/**
 * The shooter as the server sees it, updated after every {@code LocalPlayer.sendPosition}. Client
 * thread only.
 *
 * <p>The server derives the inherited movement from the difference between the position in a
 * movement packet and the one before it, and resets it to zero when a client tick sends no
 * position. Both cases equal the change of {@code xLast/yLast/zLast} across one {@code
 * sendPosition} call.
 */
public final class SentState {
  private static LocalPlayer player;
  private static Vec3d position;
  private static Vec3d knownMovement = Vec3d.ZERO;
  private static float yaw;
  private static float pitch;
  private static boolean onGround;

  private SentState() {}

  public static void record(LocalPlayer source, double prevX, double prevY, double prevZ) {
    LocalPlayerAccessor sent = (LocalPlayerAccessor) source;
    Vec3d now =
        new Vec3d(sent.trajectory$xLast(), sent.trajectory$yLast(), sent.trajectory$zLast());
    knownMovement = new Vec3d(now.x() - prevX, now.y() - prevY, now.z() - prevZ);
    position = now;
    yaw = sent.trajectory$yRotLast();
    pitch = sent.trajectory$xRotLast();
    onGround = sent.trajectory$lastOnGround();
    player = source;
  }

  /**
   * Shooter state for the given player, or {@code null} when nothing was sent for it yet (after a
   * respawn or world change the player object is new).
   */
  public static ShooterState shooter(LocalPlayer current) {
    if (current != player || position == null) {
      return null;
    }
    return new ShooterState(position, current.getEyeHeight(), yaw, pitch, knownMovement, onGround);
  }
}
