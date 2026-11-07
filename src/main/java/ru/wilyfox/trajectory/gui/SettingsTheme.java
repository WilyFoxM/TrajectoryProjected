package ru.wilyfox.trajectory.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Menu colors by role, from the project palette ({@code f6f7eb}, {@code ef6351}, {@code 7fb800},
 * {@code 2a2b2a}) and darker or lighter shades of it. Depth as in the DrawLess menu, in shades of
 * 2a2b2a: darker wells and value plates, slightly lighter rows; coral marks the top edge, the
 * selected page and active values; green marks keyboard focus.
 */
final class SettingsTheme {
  static final int SCRIM = 0x80151615;
  static final int SHADOW = 0x60000000;
  static final int PANEL = 0xF22A2B2A;
  static final int PANEL_EDGE = 0xFFEF6351;
  static final int SIDEBAR = 0x90151615;
  static final int CONTENT = 0x70151615;
  static final int SURFACE = 0xFF363736;
  static final int HOVER = 0xFF424341;
  static final int FIELD = 0xFF151615;
  static final int NAV_SELECTED = 0xFF363736;
  static final int NAV_SELECTED_TEXT = 0xFFEF6351;
  static final int VALUE_ON = 0xFF4C2924;
  static final int VALUE_ON_TEXT = 0xFFF18878;
  static final int VALUE_OFF = 0xFF151615;
  static final int VALUE_OFF_TEXT = 0xFFF6F7EB;
  static final int TITLE = 0xFFEF6351;
  static final int TEXT = 0xFFF6F7EB;
  static final int MUTED = 0xFFAFB0A7;
  static final int DISABLED = 0xFF7C7D77;
  static final int FOCUS = 0xFF7FB800;
  static final int SCROLL_TRACK = 0xFF363736;
  static final int SCROLL_THUMB = 0xFFAFB0A7;
  static final int DIALOG_SCRIM = 0xD0151615;
  static final int ACCENT = 0xFFEF6351;

  private SettingsTheme() {}

  static void rounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
    if (w <= 0 || h <= 0 || color >>> 24 == 0) {
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

  /** Centered text without the vanilla drop shadow, which smears dark text on light fills. */
  static void centered(
      GuiGraphicsExtractor g, Font font, String text, int centerX, int y, int color) {
    g.text(font, text, centerX - font.width(text) / 2, y, color, false);
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
