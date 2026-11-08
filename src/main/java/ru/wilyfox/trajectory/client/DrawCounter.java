package ru.wilyfox.trajectory.client;

/**
 * How long a bow or trident has really been held, as the server counts it.
 *
 * <p>The client's own count can restart in the middle of a draw. When a new draw begins less than a
 * ping after the last release, the server's now outdated "not using an item" flag reaches the
 * client and stops the draw ({@code LocalPlayer.onSyncedDataUpdated}). The held key then starts it
 * again, once {@code Minecraft.rightClickDelay} (4 ticks) has run out or the server's next flag
 * arrives, and the client counts from zero. The server never stopped, so it fires with more power
 * than the client's count implies.
 *
 * <p>So a stop with the key still held is treated as a gap in the same draw, not as its end. A
 * release always happens with the key up, and that does end it.
 */
final class DrawCounter {
  /** A gap longer than this is not that outdated flag: the draw has really ended. */
  static final int MAX_GAP_TICKS = 40;

  private boolean drawing;
  private boolean inGap;
  private int held;
  private int gap;
  private int slot;

  /**
   * Call once per client tick.
   *
   * @param using whether the client is using a bow or trident now
   * @param clientTicks the client's own count, {@code LivingEntity.getTicksUsingItem}
   * @param keyDown whether the use key is held
   * @param selectedSlot the selected hotbar slot; another slot means another draw
   * @return the ticks held, or 0 when there is no draw
   */
  int update(boolean using, int clientTicks, boolean keyDown, int selectedSlot) {
    if (drawing && selectedSlot != slot) {
      reset();
    }
    slot = selectedSlot;
    if (using) {
      if (drawing) {
        held = Math.max(held + 1, clientTicks);
      } else {
        drawing = true;
        held = clientTicks;
      }
      inGap = false;
      gap = 0;
    } else if (drawing && (inGap || keyDown) && gap < MAX_GAP_TICKS) {
      inGap = true;
      gap++;
      held++;
    } else {
      reset();
    }
    return held;
  }

  /** Whether the client has stopped the draw but the server is taken to be still counting. */
  boolean inGap() {
    return inGap;
  }

  void reset() {
    drawing = false;
    inGap = false;
    held = 0;
    gap = 0;
  }
}
