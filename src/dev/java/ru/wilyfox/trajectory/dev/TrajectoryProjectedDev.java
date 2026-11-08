package ru.wilyfox.trajectory.dev;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import ru.wilyfox.trajectory.client.ShotPreview;

/** Development add-on: not needed to play, and not part of the release jar. */
public final class TrajectoryProjectedDev implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    ShotPreview.onBowRelease(ShotLog::released);
    ClientTickEvents.END_CLIENT_TICK.register(minecraft -> ShotLog.tick());
    ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> ShotLog.entityLoaded(entity));
  }
}
