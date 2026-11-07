package ru.wilyfox.trajectory.client;

/**
 * Turns the vanilla crosshair from a plus into a cross while the predicted arrow would hit a living
 * entity. The turn runs over {@link #DURATION_MS} in either direction with an ease-in-out curve, so
 * it starts slowly, speeds up and settles; reversing midway continues from the current angle.
 * Client render thread only.
 */
public final class CrosshairTurn {
  private static final double DURATION_MS = 220.0;
  private static final float MAX_DEGREES = 45.0F;

  /** Linear progress of the turn in [0, 1]; the drawn angle eases it. */
  private static double progress;

  private static long lastNanos;

  private CrosshairTurn() {}

  /** Current crosshair rotation in degrees, advanced once per drawn frame. */
  public static float degrees() {
    long now = System.nanoTime();
    double elapsedMs = lastNanos == 0 ? 0.0 : Math.min((now - lastNanos) / 1.0e6, 100.0);
    lastNanos = now;
    BowPreview.Prediction prediction = BowPreview.current();
    boolean living = prediction != null && prediction.livingTarget();
    double step = elapsedMs / DURATION_MS;
    progress = living ? Math.min(1.0, progress + step) : Math.max(0.0, progress - step);
    return (float) (MAX_DEGREES * easeInOut(progress));
  }

  /** Cubic ease-in-out: slow start, fast middle, slow end. */
  static double easeInOut(double t) {
    return t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
  }
}
