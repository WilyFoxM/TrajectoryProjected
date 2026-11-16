package ru.wilyfox.trajectory.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Version seam for the screen, the loading overlay and the overlay message: 26.2 moved them from {@code Minecraft} and
 * {@code Gui} into {@code Gui} and its {@code Hud}. Client thread.
 */
public final class Screens {
  private Screens() {}

  /** The open screen, or {@code null} in game. */
  public static Screen current(Minecraft minecraft) {
    //? if >=26.2 {
    return minecraft.gui.screen();
    //?} else {
    /*return minecraft.screen;
    *///?}
  }

  /** Opens {@code screen}, or closes the open one when {@code null}. */
  public static void open(Minecraft minecraft, Screen screen) {
    //? if >=26.2 {
    minecraft.gui.setScreen(screen);
    //?} else {
    /*minecraft.setScreen(screen);
    *///?}
  }

  /** Shows {@code message} above the hotbar. */
  public static void overlayMessage(Minecraft minecraft, Component message) {
    //? if >=26.2 {
    minecraft.gui.hud.setOverlayMessage(message, false);
    //?} else {
    /*minecraft.gui.setOverlayMessage(message, false);
    *///?}
  }

  /** The loading overlay, or {@code null} when none is shown. */
  public static Overlay loadingOverlay(Minecraft minecraft) {
    //? if >=26.2 {
    return minecraft.gui.overlay();
    //?} else {
    /*return minecraft.getOverlay();
    *///?}
  }
}
