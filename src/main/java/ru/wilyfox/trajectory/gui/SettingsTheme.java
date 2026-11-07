package ru.wilyfox.trajectory.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Menu surfaces in the project palette: {@code 060410}, {@code 120d31}, {@code 1d1551}, {@code
 * f6f7eb}. There is no accent color; emphasis is the light {@code f6f7eb}, quieter text uses it at
 * lower opacity, and hover is a blend of two palette colors.
 */
final class SettingsTheme {
  static final int BACKGROUND = 0xFF060410;
  static final int PANEL = 0xF2120D31;
  static final int SURFACE = 0xFF1D1551;

  /** {@code 1d1551} blended with 8 % of {@code f6f7eb}: a mix of palette colors, not a new one. */
  static final int HOVER = 0xFF2E275D;

  static final int ACCENT = 0xFFF6F7EB;
  static final int TEXT = 0xFFF6F7EB;
  static final int MUTED = 0xA6F6F7EB;
  static final int DISABLED = 0x66F6F7EB;
  static final int SELECTED = 0x40F6F7EB;
  static final int SCRIM = 0x80060410;

  private SettingsTheme() {}

  static void rounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
    if (w <= 0 || h <= 0) {
      return;
    }
    int r = Math.clamp(radius, 0, Math.min(w, h) / 2);
    for (int i = 0; i < r; i++) {
      int inset = (int) Math.ceil(r - Math.sqrt(r * r - Math.pow(r - i - .5, 2)));
      g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
      g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
    }
    g.fill(x, y + r, x + w, y + h - r, color);
  }

  static String fit(Font font, String text, int width) {
    if (width <= 0) {
      return "";
    }
    if (font.width(text) <= width) {
      return text;
    }
    return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
  }
}
