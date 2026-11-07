package ru.wilyfox.trajectory.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import ru.wilyfox.trajectory.TrajectoryProjected;
import ru.wilyfox.trajectory.config.TrajectoryConfig;

/** Standalone settings: categories, global search and clipped, accessible controls. */
public final class TrajectoryScreen extends Screen {
  private final Screen parent;
  private final List<SettingsCatalog.Option> options = SettingsCatalog.create();
  private final List<SettingRow> rows = new ArrayList<>();
  private SettingsCatalog.Page page = SettingsCatalog.Page.GENERAL;
  private EditBox search;
  private Button done;
  private String query = "";
  private String saveError;
  private int x, y, panelWidth, panelHeight, sidebarWidth;
  private int contentX, contentY, contentWidth, viewportTop, viewportBottom;
  private int scroll, maxScroll, rowHeight;
  private boolean scrollbarDragging;
  private double scrollbarGrabOffset;
  private final String version =
      FabricLoader.getInstance()
          .getModContainer(TrajectoryProjected.MOD_ID)
          .orElseThrow()
          .getMetadata()
          .getVersion()
          .getFriendlyString();

  public TrajectoryScreen(Screen parent) {
    super(Component.literal(TrajectoryProjected.NAME));
    this.parent = parent;
  }

  @Override
  protected void init() {
    finishDrags();
    scrollbarDragging = false;
    panelWidth = Math.min(600, width - 16);
    panelHeight = Math.min(360, height - 16);
    x = (width - panelWidth) / 2;
    y = (height - panelHeight) / 2;
    sidebarWidth = panelWidth < 430 ? 92 : 112;
    contentX = x + sidebarWidth + 16;
    contentY = y + 40;
    contentWidth = panelWidth - sidebarWidth - 24;
    viewportTop = contentY + 28;
    viewportBottom = y + panelHeight - 40;
    rowHeight = contentWidth - 18 < 225 ? 54 : 36;
    rows.clear();
    int searchWidth = Math.min(210, panelWidth / 2);
    search =
        addRenderableWidget(
            new EditBox(
                font,
                x + panelWidth - searchWidth - 12,
                y + 12,
                searchWidth,
                16,
                Component.translatable(SettingsCatalog.PREFIX + "search")));
    search.setBordered(false);
    search.setTextShadow(false);
    search.setTextColor(SettingsTheme.TEXT);
    search.setMaxLength(80);
    search.setHint(Component.translatable(SettingsCatalog.PREFIX + "search"));
    search.setValue(query);
    search.setResponder(
        value -> {
          query = value;
          scroll = 0;
          rebuildRows();
        });
    int navHeight =
        Math.min(24, Math.max(16, (panelHeight - 90) / SettingsCatalog.Page.values().length - 3));
    for (var category : SettingsCatalog.Page.values()) {
      addRenderableWidget(
          button(
              x + 12,
              contentY + 6 + category.ordinal() * (navHeight + 3),
              sidebarWidth - 8,
              navHeight,
              category.title(),
              () -> {
                finishDrags();
                page = category;
                scroll = 0;
                if (!query.isEmpty()) search.setValue("");
                else rebuildRows();
              },
              () -> query.isBlank() && category == page));
    }
    addRenderableWidget(
        button(
            x + 12,
            y + panelHeight - 28,
            96,
            20,
            Component.translatable(SettingsCatalog.PREFIX + "reset"),
            () -> {
              finishDrags();
              minecraft.gui.setScreen(new ResetConfirmation());
            },
            () -> false));
    done =
        addRenderableWidget(
            button(
                x + panelWidth - 88,
                y + panelHeight - 28,
                76,
                20,
                Component.translatable("gui.done"),
                this::onClose,
                () -> true));
    updateSaveButton();
    rebuildRows();
  }

  private void rebuildRows() {
    finishDrags();
    for (var row : rows) removeWidget(row);
    rows.clear();
    String needle = query.strip().toLowerCase(Locale.ROOT);
    for (var option : options) {
      boolean matches =
          needle.isEmpty()
              ? option.page() == page
              : (option.title().getString()
                      + " "
                      + option.description().getString()
                      + " "
                      + option.page().title().getString())
                  .toLowerCase(Locale.ROOT)
                  .contains(needle);
      if (matches) rows.add(addWidget(new SettingRow(this, option)));
    }
    maxScroll = Math.max(0, rows.size() * (rowHeight + 5) - 5 - (viewportBottom - viewportTop));
    positionRows();
  }

  private void positionRows() {
    scroll = Math.clamp(scroll, 0, maxScroll);
    for (int i = 0; i < rows.size(); i++) {
      var row = rows.get(i);
      row.setSize(contentWidth - 18, rowHeight);
      row.setX(contentX + 8);
      row.setY(viewportTop + i * (rowHeight + 5) - scroll);
    }
  }

  boolean inViewport(double mx, double my) {
    return mx >= contentX + 4
        && mx < contentX + contentWidth - 8
        && my >= viewportTop
        && my < viewportBottom;
  }

  private void finishDrags() {
    for (var row : rows) row.finishDrag();
  }

  boolean saveChanges() {
    try {
      TrajectoryConfig.save();
      saveError = null;
      updateSaveButton();
      return true;
    } catch (RuntimeException failure) {
      saveError = Component.translatable(SettingsCatalog.PREFIX + "save_error").getString();
      updateSaveButton();
      TrajectoryProjected.LOGGER.error("Could not save settings", failure);
      return false;
    }
  }

  private void updateSaveButton() {
    if (done == null) return;
    done.setMessage(
        Component.translatable(
            saveError == null ? "gui.done" : SettingsCatalog.PREFIX + "retry_save"));
    done.setTooltip(
        Tooltip.create(
            Component.translatable(
                saveError == null
                    ? SettingsCatalog.PREFIX + "autosave"
                    : SettingsCatalog.PREFIX + "save_error")));
  }

  private Button button(
      int bx, int by, int w, int h, Component label, Runnable action, BooleanSupplier selected) {
    return new Button(
        bx, by, w, h, label, ignored -> action.run(), java.util.function.Supplier::get) {
      @Override
      protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        SettingsTheme.rounded(
            g,
            getX(),
            getY(),
            getWidth(),
            getHeight(),
            4,
            isHoveredOrFocused() ? SettingsTheme.HOVER : SettingsTheme.SURFACE);
        if (selected.getAsBoolean() || isFocused())
          g.fill(getX() + 4, getY(), getRight() - 4, getY() + 1, SettingsTheme.ACCENT);
        g.centeredText(
            font,
            SettingsTheme.fit(font, getMessage().getString(), getWidth() - 8),
            getX() + getWidth() / 2,
            getY() + (getHeight() - font.lineHeight) / 2,
            selected.getAsBoolean() ? SettingsTheme.ACCENT : SettingsTheme.TEXT);
      }
    };
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
    if (minecraft.level == null) extractPanorama(g, delta);
    extractBlurredBackground(g);
    g.fill(0, 0, width, height, SettingsTheme.SCRIM);
    SettingsTheme.rounded(g, x + 2, y + 3, panelWidth, panelHeight, 8, 0x60000000);
    SettingsTheme.rounded(g, x, y, panelWidth, panelHeight, 8, SettingsTheme.PANEL);
    g.fill(x + 8, y, x + panelWidth - 8, y + 1, SettingsTheme.ACCENT);
    g.text(font, TrajectoryProjected.NAME, x + 14, y + 8, SettingsTheme.ACCENT, false);
    g.text(font, version, x + 14, y + 22, SettingsTheme.MUTED, false);
    SettingsTheme.rounded(
        g,
        search.getX() - 5,
        search.getY() - 5,
        search.getWidth() + 10,
        24,
        4,
        SettingsTheme.BACKGROUND);
    if (search.isFocused())
      g.fill(
          search.getX(),
          search.getY() + 18,
          search.getRight(),
          search.getY() + 19,
          SettingsTheme.ACCENT);
    SettingsTheme.rounded(g, x + 8, contentY, sidebarWidth, panelHeight - 76, 5, 0x90060410);
    SettingsTheme.rounded(g, contentX, contentY, contentWidth, panelHeight - 76, 5, 0x70060410);
    Component heading =
        query.isBlank()
            ? page.title()
            : Component.translatable(SettingsCatalog.PREFIX + "results", rows.size());
    g.text(font, heading, contentX + 8, contentY + 9, SettingsTheme.TEXT, false);
    g.enableScissor(contentX + 4, viewportTop, contentX + contentWidth - 8, viewportBottom);
    for (var row : rows) row.extractRenderState(g, mx, my, delta);
    if (rows.isEmpty())
      g.text(
          font,
          Component.translatable(SettingsCatalog.PREFIX + "no_results"),
          contentX + 8,
          viewportTop + 8,
          SettingsTheme.MUTED,
          false);
    g.disableScissor();
    if (maxScroll > 0) {
      int track = viewportBottom - viewportTop;
      int thumb = scrollbarThumbHeight();
      int thumbY = scrollbarThumbY();
      SettingsTheme.rounded(
          g, contentX + contentWidth - 5, viewportTop, 2, track, 1, SettingsTheme.SURFACE);
      SettingsTheme.rounded(
          g, contentX + contentWidth - 6, thumbY, 4, thumb, 2, SettingsTheme.MUTED);
    }
    if (panelWidth > 460 || saveError != null) {
      String footer =
          saveError == null
              ? Component.translatable(SettingsCatalog.PREFIX + "autosave").getString()
              : saveError;
      g.text(
          font,
          SettingsTheme.fit(font, footer, panelWidth - 220),
          x + 116,
          y + panelHeight - 21,
          saveError == null ? SettingsTheme.MUTED : SettingsTheme.ACCENT,
          false);
    }
    super.extractRenderState(g, mx, my, delta);
  }

  @Override
  public boolean mouseScrolled(double mx, double my, double dx, double dy) {
    if (mx >= contentX
        && mx < contentX + contentWidth
        && my >= viewportTop
        && my < viewportBottom) {
      finishDrags();
      scroll -= (int) Math.round(dy * 24);
      positionRows();
      return true;
    }
    return super.mouseScrolled(mx, my, dx, dy);
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    if (event.button() == 0
        && maxScroll > 0
        && event.x() >= contentX + contentWidth - 8
        && event.x() <= contentX + contentWidth
        && event.y() >= viewportTop
        && event.y() < viewportBottom) {
      finishDrags();
      scrollbarDragging = true;
      scrollbarGrabOffset =
          event.y() >= scrollbarThumbY() && event.y() < scrollbarThumbY() + scrollbarThumbHeight()
              ? event.y() - scrollbarThumbY()
              : scrollbarThumbHeight() / 2d;
      dragScrollbar(event.y());
      return true;
    }
    return super.mouseClicked(event, doubleClick);
  }

  private void dragScrollbar(double my) {
    scroll =
        (int)
            Math.round(
                (my - viewportTop - scrollbarGrabOffset)
                    / Math.max(1, viewportBottom - viewportTop - scrollbarThumbHeight())
                    * maxScroll);
    positionRows();
  }

  private int scrollbarThumbHeight() {
    int track = viewportBottom - viewportTop;
    return Math.max(16, track * track / (track + maxScroll));
  }

  private int scrollbarThumbY() {
    return viewportTop
        + (viewportBottom - viewportTop - scrollbarThumbHeight()) * scroll / Math.max(1, maxScroll);
  }

  @Override
  public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
    if (scrollbarDragging) {
      dragScrollbar(event.y());
      return true;
    }
    return super.mouseDragged(event, dx, dy);
  }

  @Override
  public boolean mouseReleased(MouseButtonEvent event) {
    if (event.button() == 0) {
      scrollbarDragging = false;
      finishDrags();
    }
    return super.mouseReleased(event);
  }

  @Override
  protected void changeFocus(ComponentPath path) {
    super.changeFocus(path);
    if (path != null && path.leafComponent() instanceof SettingRow row) {
      if (row.getY() < viewportTop) scroll -= viewportTop - row.getY();
      if (row.getBottom() > viewportBottom) scroll += row.getBottom() - viewportBottom;
      positionRows();
    }
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    if (event.hasControlDown() && event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_F) {
      setFocused(search);
      return true;
    }
    if (event.isEscape() && !query.isEmpty()) {
      search.setValue("");
      return true;
    }
    if (getFocused() != search
        && (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN
            || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP)) {
      finishDrags();
      scroll +=
          (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1)
              * (viewportBottom - viewportTop);
      positionRows();
      return true;
    }
    return super.keyPressed(event);
  }

  @Override
  public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void removed() {
    finishDrags();
  }

  @Override
  public void onClose() {
    finishDrags();
    if (saveChanges()) minecraft.gui.setScreen(parent);
  }

  private final class ResetConfirmation extends Screen {
    ResetConfirmation() {
      super(Component.translatable(SettingsCatalog.PREFIX + "reset.title"));
    }

    @Override
    protected void init() {
      int w = Math.min(320, width - 24), left = (width - w) / 2;
      addRenderableWidget(
          button(
              left + 12,
              height / 2 + 20,
              (w - 30) / 2,
              20,
              Component.translatable("gui.cancel"),
              this::onClose,
              () -> false));
      addRenderableWidget(
          button(
              left + w / 2 + 3,
              height / 2 + 20,
              (w - 30) / 2,
              20,
              Component.translatable(SettingsCatalog.PREFIX + "reset.confirm"),
              () -> {
                TrajectoryConfig.defaults();
                saveChanges();
                onClose();
              },
              () -> true));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
      g.fill(0, 0, width, height, 0xD0060410);
      int w = Math.min(320, width - 24), left = (width - w) / 2;
      SettingsTheme.rounded(g, left, height / 2 - 58, w, 112, 7, SettingsTheme.PANEL);
      g.text(font, title, left + 12, height / 2 - 44, SettingsTheme.ACCENT, false);
      g.textWithWordWrap(
          font,
          Component.translatable(SettingsCatalog.PREFIX + "reset.description"),
          left + 12,
          height / 2 - 25,
          w - 24,
          SettingsTheme.TEXT,
          false);
      super.extractRenderState(g, mx, my, delta);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}

    @Override
    public boolean isPauseScreen() {
      return false;
    }

    @Override
    public void onClose() {
      minecraft.gui.setScreen(TrajectoryScreen.this);
    }
  }
}
