package ru.wilyfox.trajectory.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DrawCounterTest {
  private static final int SLOT = 3;

  @Test
  void anUninterruptedDrawIsTheClientsCount() {
    DrawCounter counter = new DrawCounter();
    for (int t = 1; t <= 30; t++) {
      assertEquals(t, counter.update(true, t, true, SLOT));
    }
  }

  @Test
  void aStopWithTheKeyHeldDoesNotRestartTheCount() {
    DrawCounter counter = new DrawCounter();
    assertEquals(1, counter.update(true, 1, true, SLOT));
    assertEquals(2, counter.update(true, 2, true, SLOT));
    // The outdated flag stops the client for two ticks.
    assertEquals(3, counter.update(false, 0, true, SLOT));
    assertTrue(counter.inGap());
    assertEquals(4, counter.update(false, 0, true, SLOT));
    // The key starts it again and the client counts from zero; the server never stopped.
    assertEquals(5, counter.update(true, 0, true, SLOT));
    assertFalse(counter.inGap());
    for (int clientTicks = 1; clientTicks <= 6; clientTicks++) {
      assertEquals(5 + clientTicks, counter.update(true, clientTicks, true, SLOT));
    }
  }

  @Test
  void aReleaseEndsTheDrawAndTheNextOneStartsFresh() {
    DrawCounter counter = new DrawCounter();
    for (int t = 1; t <= 11; t++) {
      counter.update(true, t, true, SLOT);
    }
    assertEquals(0, counter.update(false, 0, false, SLOT));
    assertFalse(counter.inGap());
    assertEquals(1, counter.update(true, 1, true, SLOT));
  }

  @Test
  void theKeyGoingUpDuringAGapKeepsCounting() {
    // The client is stopped, so no release has been sent: the server's draw goes on until the
    // client resumes and releases.
    DrawCounter counter = new DrawCounter();
    counter.update(true, 1, true, SLOT);
    assertEquals(2, counter.update(false, 0, true, SLOT));
    assertEquals(3, counter.update(false, 0, false, SLOT));
    assertEquals(4, counter.update(true, 0, false, SLOT));
  }

  @Test
  void aGapThatNeverEndsIsNotADraw() {
    DrawCounter counter = new DrawCounter();
    counter.update(true, 1, true, SLOT);
    for (int i = 0; i < DrawCounter.MAX_GAP_TICKS; i++) {
      counter.update(false, 0, true, SLOT);
    }
    assertTrue(counter.inGap());
    assertEquals(0, counter.update(false, 0, true, SLOT));
    assertFalse(counter.inGap());
    assertEquals(1, counter.update(true, 1, true, SLOT));
  }

  @Test
  void anotherSlotIsAnotherDraw() {
    DrawCounter counter = new DrawCounter();
    for (int t = 1; t <= 5; t++) {
      counter.update(true, t, true, SLOT);
    }
    counter.update(false, 0, true, SLOT);
    assertEquals(1, counter.update(true, 1, true, SLOT + 1));
  }
}
