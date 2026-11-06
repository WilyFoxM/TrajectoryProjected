package ru.wilyfox.trajectory.sim;

/**
 * Math helpers that reproduce vanilla {@code net.minecraft.util.Mth} exactly, including the
 * 65536-entry sine table used by projectile launches.
 */
public final class VanillaMath {
  /** {@code (float) (Math.PI / 180.0)}, the constant vanilla multiplies degrees by. */
  public static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

  private static final double TABLE_SCALE = 10430.378350470453;
  private static final float[] SIN = new float[65536];

  static {
    for (int i = 0; i < SIN.length; i++) {
      SIN[i] = (float) Math.sin(i / TABLE_SCALE);
    }
  }

  private VanillaMath() {}

  /** {@code Mth.sin}: table lookup, not {@link Math#sin}. */
  public static float sin(double radians) {
    return SIN[(int) ((long) (radians * TABLE_SCALE) & 65535L)];
  }

  /** {@code Mth.cos}: the sine table shifted by a quarter turn. */
  public static float cos(double radians) {
    return SIN[(int) ((long) (radians * TABLE_SCALE + 16384.0) & 65535L)];
  }

  /** {@code Mth.wrapDegrees(float)}: maps an angle to [-180, 180). */
  public static float wrapDegrees(float angle) {
    float normalized = angle % 360.0F;
    if (normalized >= 180.0F) {
      normalized -= 360.0F;
    }

    if (normalized < -180.0F) {
      normalized += 360.0F;
    }

    return normalized;
  }

  /**
   * Yaw as the server stores it after a movement packet: {@code wrapDegrees} in {@code
   * ServerGamePacketListenerImpl.handleMovePlayer}, then {@code % 360} in {@code
   * Entity.absSnapRotationTo}.
   */
  public static float serverYaw(float sentYaw) {
    return wrapDegrees(sentYaw) % 360.0F;
  }

  /**
   * Pitch as the server stores it: {@code wrapDegrees}, clamped to [-90, 90] by {@code
   * absSnapRotationTo} and again by {@code Entity.setXRot}.
   */
  public static float serverPitch(float sentPitch) {
    float clamped = Math.clamp(wrapDegrees(sentPitch), -90.0F, 90.0F) % 360.0F;
    return Math.clamp(clamped % 360.0F, -90.0F, 90.0F);
  }
}
