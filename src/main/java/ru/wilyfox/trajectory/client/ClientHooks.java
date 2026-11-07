package ru.wilyfox.trajectory.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/** Registers the bow preview with Fabric API events. */
public final class ClientHooks {
  private ClientHooks() {}

  public static void register() {
    ClientTickEvents.END_CLIENT_TICK.register(
        minecraft -> {
          ShotPreview.tick(minecraft);
          ShotLog.tick();
        });
    LevelRenderEvents.COLLECT_SUBMITS.register(DotRenderer::collect);
    if (ShotLog.ENABLED) {
      ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> ShotLog.entityLoaded(entity));
    }
  }
}
