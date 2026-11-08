package ru.wilyfox.trajectory.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/** Registers the preview with Fabric API events. */
public final class ClientHooks {
  private ClientHooks() {}

  public static void register() {
    ClientTickEvents.END_CLIENT_TICK.register(ShotPreview::tick);
    LevelRenderEvents.COLLECT_SUBMITS.register(DotRenderer::collect);
  }
}
