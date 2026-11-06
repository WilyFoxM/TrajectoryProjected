package ru.wilyfox.trajectory;

import net.fabricmc.api.ClientModInitializer;
import ru.wilyfox.trajectory.client.ClientHooks;

/** Client entrypoint. */
public final class TrajectoryProjected implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    ClientHooks.register();
  }
}
