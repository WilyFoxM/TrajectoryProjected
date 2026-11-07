package ru.wilyfox.trajectory.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.trajectory.TrajectoryProjected;
import ru.wilyfox.trajectory.config.TrajectoryConfig;

/**
 * Rebindable keys: open the settings (F7; F8 is taken by DrawLess) and switch the prediction on or
 * off (unbound by default). Presses while another screen is open are discarded, never queued.
 */
public final class SettingsKeyBinding {
  private static final KeyMapping.Category CATEGORY =
      KeyMapping.Category.register(
          Identifier.fromNamespaceAndPath(TrajectoryProjected.MOD_ID, "main"));

  private static final KeyMapping OPEN =
      KeyMappingHelper.registerKeyMapping(
          new KeyMapping(
              "key." + TrajectoryProjected.MOD_ID + ".open_settings",
              InputConstants.Type.KEYSYM,
              GLFW.GLFW_KEY_F7,
              CATEGORY));

  private static final KeyMapping TOGGLE =
      KeyMappingHelper.registerKeyMapping(
          new KeyMapping(
              "key." + TrajectoryProjected.MOD_ID + ".toggle",
              InputConstants.Type.KEYSYM,
              InputConstants.UNKNOWN.getValue(),
              CATEGORY));

  private SettingsKeyBinding() {}

  public static void register() {
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          boolean open = false;
          while (OPEN.consumeClick()) {
            open = true;
          }
          boolean toggle = false;
          while (TOGGLE.consumeClick()) {
            toggle = !toggle;
          }
          if (client.player == null || client.gui.screen() != null) {
            return;
          }
          if (toggle) {
            toggle(client);
          }
          if (open) {
            client.gui.setScreen(new TrajectoryScreen(null));
          }
        });
  }

  private static void toggle(Minecraft client) {
    var config = TrajectoryConfig.get();
    config.enabled = !config.enabled;
    try {
      TrajectoryConfig.save();
    } catch (RuntimeException failure) {
      TrajectoryProjected.LOGGER.error("Could not save settings", failure);
    }
    client.gui.hud.setOverlayMessage(
        Component.translatable(
            SettingsCatalog.PREFIX + (config.enabled ? "toggled.on" : "toggled.off")),
        false);
  }
}
