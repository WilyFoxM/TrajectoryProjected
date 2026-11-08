package ru.wilyfox.trajectory.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import ru.wilyfox.trajectory.config.TrajectoryConfig;

/** Every setting of the menu, by page, with its value, change and translation keys. */
final class SettingsCatalog {
  static final String PREFIX = "trajectory_projected.";

  enum Page {
    GENERAL,
    LINE,
    TARGET;

    Component title() {
      return Component.translatable(PREFIX + "page." + name().toLowerCase(Locale.ROOT));
    }
  }

  enum Kind {
    TOGGLE,
    NUMBER
  }

  record Option(
      String id,
      Page page,
      Kind kind,
      Supplier<Component> value,
      IntConsumer change,
      BooleanSupplier selected,
      Supplier<Component> unavailable,
      IntSupplier number,
      IntConsumer setNumber,
      int min,
      int max,
      int step) {
    Component title() {
      return Component.translatable(PREFIX + "setting." + id);
    }

    Component description() {
      return Component.translatable(PREFIX + "setting." + id + ".description");
    }
  }

  private SettingsCatalog() {}

  static List<Option> create() {
    var options = new ArrayList<Option>();
    options.add(toggle("enabled", Page.GENERAL, () -> c().enabled, v -> c().enabled = v));
    options.add(
        toggle(
            "first_person_lead_in",
            Page.GENERAL,
            () -> c().firstPersonLeadIn,
            v -> c().firstPersonLeadIn = v));
    options.add(
        percent(
            "dot_size",
            Page.LINE,
            () -> c().dotSize,
            v -> c().dotSize = v,
            TrajectoryConfig.MIN_DOT_SIZE,
            TrajectoryConfig.MAX_DOT_SIZE,
            5));
    options.add(
        percent(
            "dot_spacing",
            Page.LINE,
            () -> c().dotSpacing,
            v -> c().dotSpacing = v,
            TrajectoryConfig.MIN_DOT_SPACING,
            TrajectoryConfig.MAX_DOT_SPACING,
            10));
    options.add(toggle("far_dimming", Page.LINE, () -> c().farDimming, v -> c().farDimming = v));
    options.add(toggle("show_hidden", Page.LINE, () -> c().showHidden, v -> c().showHidden = v));
    options.add(toggle("spread_zone", Page.TARGET, () -> c().spreadZone, v -> c().spreadZone = v));
    options.add(zoneToggle("zone_full", () -> c().zoneFull, v -> c().zoneFull = v));
    options.add(zoneToggle("zone_half_ring", () -> c().zoneHalfRing, v -> c().zoneHalfRing = v));
    options.add(zoneToggle("zone_wide_ring", () -> c().zoneWideRing, v -> c().zoneWideRing = v));
    options.add(
        zoneToggle(
            "zone_tick_correction", () -> c().zoneTickCorrection, v -> c().zoneTickCorrection = v));
    options.add(toggle("hit_marker", Page.TARGET, () -> c().hitMarker, v -> c().hitMarker = v));
    options.add(
        toggle("living_pulse", Page.TARGET, () -> c().livingPulse, v -> c().livingPulse = v));
    options.add(
        toggle(
            "living_cross",
            Page.TARGET,
            () -> c().livingCross,
            v -> c().livingCross = v,
            () -> c().hitMarker ? null : reason("hit_marker_off")));
    options.add(
        toggle("crosshair_turn", Page.TARGET, () -> c().crosshairTurn, v -> c().crosshairTurn = v));
    return List.copyOf(options);
  }

  static Component onOff(boolean value) {
    return Component.translatable(PREFIX + (value ? "on" : "off"));
  }

  private static Component reason(String id) {
    return Component.translatable(PREFIX + "unavailable." + id);
  }

  private static TrajectoryConfig c() {
    return TrajectoryConfig.get();
  }

  private static Option toggle(String id, Page page, BooleanSupplier get, Consumer<Boolean> set) {
    return toggle(id, page, get, set, () -> null);
  }

  private static Option zoneToggle(String id, BooleanSupplier get, Consumer<Boolean> set) {
    return toggle(
        id, Page.TARGET, get, set, () -> c().spreadZone ? null : reason("spread_zone_off"));
  }

  private static Option toggle(
      String id,
      Page page,
      BooleanSupplier get,
      Consumer<Boolean> set,
      Supplier<Component> unavailable) {
    return new Option(
        id,
        page,
        Kind.TOGGLE,
        () -> onOff(get.getAsBoolean()),
        d -> set.accept(!get.getAsBoolean()),
        get,
        unavailable,
        null,
        null,
        0,
        0,
        0);
  }

  private static Option percent(
      String id, Page page, IntSupplier get, IntConsumer set, int min, int max, int step) {
    return new Option(
        id,
        page,
        Kind.NUMBER,
        () -> Component.translatable(PREFIX + "percent", get.getAsInt()),
        direction -> set.accept(Math.clamp(get.getAsInt() + direction * step, min, max)),
        () -> false,
        () -> null,
        get,
        set,
        min,
        max,
        step);
  }
}
