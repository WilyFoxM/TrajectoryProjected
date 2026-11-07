package ru.wilyfox.trajectory;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.wilyfox.trajectory.client.ClientHooks;
import ru.wilyfox.trajectory.config.TrajectoryConfig;
import ru.wilyfox.trajectory.gui.SettingsKeyBinding;

/** Client entrypoint. */
public final class TrajectoryProjected implements ClientModInitializer {
  public static final String MOD_ID = "trajectory_projected";
  public static final String NAME = "Trajectory Projected";
  public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

  @Override
  public void onInitializeClient() {
    TrajectoryConfig.load();
    SettingsKeyBinding.register();
    ClientHooks.register();
  }
}
