package ru.wilyfox.trajectory.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrosshairTurnTest {
  @Test
  void easesFromZeroToOneThroughTheMiddle() {
    assertEquals(0.0, CrosshairTurn.easeInOut(0.0));
    assertEquals(0.5, CrosshairTurn.easeInOut(0.5), 1.0e-12);
    assertEquals(1.0, CrosshairTurn.easeInOut(1.0), 1.0e-12);
  }

  @Test
  void neverGoesBackwards() {
    double previous = 0.0;
    for (int i = 1; i <= 1000; i++) {
      double value = CrosshairTurn.easeInOut(i / 1000.0);
      assertTrue(value >= previous, "at " + i);
      previous = value;
    }
  }

  @Test
  void startsAndEndsSlowlyAndIsFastInTheMiddle() {
    double h = 0.01;
    double start = CrosshairTurn.easeInOut(h) / h;
    double middle = (CrosshairTurn.easeInOut(0.5 + h) - CrosshairTurn.easeInOut(0.5 - h)) / (2 * h);
    double end = (1.0 - CrosshairTurn.easeInOut(1.0 - h)) / h;
    assertTrue(start < 0.01, "start slope " + start);
    assertTrue(end < 0.01, "end slope " + end);
    assertTrue(middle > 2.9, "middle slope " + middle);
  }
}
