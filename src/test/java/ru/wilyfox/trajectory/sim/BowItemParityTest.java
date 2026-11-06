package ru.wilyfox.trajectory.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.BowItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Compares the bow draw curve with vanilla {@code BowItem}, which needs registries to load. */
class BowItemParityTest {
  @BeforeAll
  static void bootstrap() {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
  }

  @Test
  void powerMatchesBowItem() {
    for (int ticks = 0; ticks <= 100; ticks++) {
      assertEquals(BowItem.getPowerForTime(ticks), BowShot.powerForTicks(ticks), "ticks " + ticks);
    }
  }
}
