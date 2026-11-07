package ru.wilyfox.trajectory.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

/** A keyboard-accessible setting row with an independent value control. */
final class SettingRow extends Button {
  final SettingsCatalog.Option option;
  private final TrajectoryScreen owner;
  private boolean dragging;
  private double dragX;
  private double dragRemainder;
  private Component lastTooltip;

  SettingRow(TrajectoryScreen owner, SettingsCatalog.Option option) {
    super(0, 0, 100, 40, option.title(), ignored -> {}, java.util.function.Supplier::get);
    this.owner = owner;
    this.option = option;
    refresh();
  }

  void refresh() {
    Component unavailable = option.unavailable().get();
    active = unavailable == null;
    setMessage(
        Component.translatable(
            SettingsCatalog.PREFIX + "narration.setting", option.title(), option.value().get()));
    Component hint =
        Component.translatable(
            SettingsCatalog.PREFIX
                + "hint."
                + option.kind().name().toLowerCase(java.util.Locale.ROOT));
    Component tooltip =
        Component.empty()
            .append(getMessage())
            .append("\n")
            .append(option.description())
            .append("\n")
            .append(unavailable == null ? hint : unavailable);
    if (!tooltip.equals(lastTooltip)) {
      lastTooltip = tooltip;
      setTooltip(Tooltip.create(tooltip));
    }
  }

  private boolean stacked() {
    return getWidth() < 225;
  }

  private int valueWidth() {
    return active && option.kind() == SettingsCatalog.Kind.TOGGLE
        ? 34
        : Math.min(116, getWidth() / 2);
  }

  private int valueX() {
    return getRight() - valueWidth() - 8;
  }

  private int valueY() {
    return stacked() ? getBottom() - 20 : getY() + 5;
  }

  @Override
  protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
    refresh();
    var font = Minecraft.getInstance().font;
    boolean highlighted = isMouseOver(mx, my) || isFocused() || dragging;
    SettingsTheme.rounded(
        g,
        getX(),
        getY(),
        getWidth(),
        getHeight(),
        4,
        highlighted ? SettingsTheme.HOVER : SettingsTheme.SURFACE);
    if (isFocused()) g.fill(getX() + 5, getY(), getRight() - 5, getY() + 1, SettingsTheme.FOCUS);
    int labelWidth = stacked() ? getWidth() - 16 : getWidth() - valueWidth() - 28;
    g.text(
        font,
        SettingsTheme.fit(font, option.title().getString(), labelWidth),
        getX() + 8,
        getY() + 6,
        active ? SettingsTheme.TEXT : SettingsTheme.MUTED,
        false);
    String description = (active ? option.description() : option.unavailable().get()).getString();
    g.text(
        font,
        SettingsTheme.fit(font, description, getWidth() - 16),
        getX() + 8,
        getY() + 21,
        SettingsTheme.MUTED,
        false);
    boolean selected = active && option.selected().getAsBoolean();
    SettingsTheme.rounded(
        g,
        valueX(),
        valueY(),
        valueWidth(),
        14,
        3,
        selected ? SettingsTheme.VALUE_ON : SettingsTheme.VALUE_OFF);
    if (selected || dragging)
      g.fill(
          valueX() + 3,
          valueY(),
          valueX() + valueWidth() - 3,
          valueY() + 1,
          dragging ? SettingsTheme.FOCUS : SettingsTheme.ACCENT);
    String value =
        active
            ? option.value().get().getString()
            : Component.translatable(SettingsCatalog.PREFIX + "managed").getString();
    SettingsTheme.centered(
        g,
        font,
        SettingsTheme.fit(font, value, valueWidth() - 6),
        valueX() + valueWidth() / 2,
        valueY() + 3,
        !active
            ? SettingsTheme.DISABLED
            : selected ? SettingsTheme.VALUE_ON_TEXT : SettingsTheme.VALUE_OFF_TEXT);
  }

  @Override
  public boolean isMouseOver(double x, double y) {
    return owner.inViewport(x, y) && super.isMouseOver(x, y);
  }

  @Override
  protected boolean isValidClickButton(MouseButtonInfo button) {
    return button.button() == 0;
  }

  @Override
  public void onPress(InputWithModifiers input) {
    if (!active) return;
    if (option.kind() == SettingsCatalog.Kind.NUMBER && input instanceof MouseButtonEvent mouse) {
      if (mouse.x() < valueX() || mouse.y() < valueY() || mouse.y() >= valueY() + 14) return;
      dragging = true;
      dragX = mouse.x();
      dragRemainder = 0;
      return;
    }
    option.change().accept(input instanceof MouseButtonEvent mouse && mouse.button() == 1 ? -1 : 1);
    owner.saveChanges();
    refresh();
  }

  @Override
  protected void onDrag(MouseButtonEvent event, double dx, double dy) {
    if (!dragging) return;
    // MouseHandler retains the mouse-down modifiers throughout the drag.
    var window = Minecraft.getInstance().getWindow();
    boolean fine =
        InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
            || InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
    dragRemainder += (event.x() - dragX) / (fine ? 12 : 3);
    dragX = event.x();
    int steps = (int) dragRemainder;
    dragRemainder -= steps;
    int next =
        (int)
            Math.clamp(
                (long) option.number().getAsInt() + (long) steps * option.step(),
                option.min(),
                option.max());
    option.setNumber().accept(next);
    if (next == option.min() && dragRemainder < 0 || next == option.max() && dragRemainder > 0)
      dragRemainder = 0;
    refresh();
  }

  @Override
  public void onRelease(MouseButtonEvent event) {
    finishDrag();
  }

  void finishDrag() {
    if (!dragging) return;
    dragging = false;
    owner.saveChanges();
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    if (active && isFocused() && (event.isLeft() || event.isRight())) {
      if (option.kind() != SettingsCatalog.Kind.TOGGLE
          || option.selected().getAsBoolean() != event.isRight()) {
        option.change().accept(event.isLeft() ? -1 : 1);
      }
      owner.saveChanges();
      refresh();
      return true;
    }
    return super.keyPressed(event);
  }
}
