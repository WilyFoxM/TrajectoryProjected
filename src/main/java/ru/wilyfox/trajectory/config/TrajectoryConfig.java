package ru.wilyfox.trajectory.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import ru.wilyfox.trajectory.TrajectoryProjected;
import ru.wilyfox.trajectory.util.AtomicFileWriter;

/**
 * Player settings with bounded values and atomic persistence, stored as {@code
 * trajectory_projected.json}. A field missing from a saved file gets its current default; saved
 * values are never overwritten by a newer default.
 */
public final class TrajectoryConfig {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final String FILE = "trajectory_projected.json";

  public static final int MIN_DOT_SIZE = 50;
  public static final int MAX_DOT_SIZE = 200;
  public static final int MIN_DOT_SPACING = 50;
  public static final int MAX_DOT_SPACING = 250;

  private static TrajectoryConfig instance = new TrajectoryConfig();

  /** Whether any part of the spread zone is to be shown. */
  public boolean showsZone() {
    return spreadZone && (zoneFull || zoneHalfRing || zoneWideRing);
  }

  public int schemaVersion = 1;

  /** Master switch: no prediction, line or crosshair change while off. */
  public boolean enabled = true;

  /** While a fishing rod is held and not cast: where the hook would land. */
  public boolean fishingRod = true;

  /** First person: the line starts beside the eye and bends into the real path. */
  public boolean firstPersonLeadIn = true;

  /** Dot size in percent of the default. */
  public int dotSize = 100;

  /** Distance between dots on screen in percent of the default. */
  public int dotSpacing = 100;

  /** The line dims a little far away. */
  public boolean farDimming = true;

  /** Parts of the path behind blocks are drawn faint and outlined. */
  public boolean showHidden = true;

  /** Where the server's random spread can put the arrow, shown on the blocks around the hit. */
  public boolean spreadZone = true;

  /** The zone reaches its full extent, the edge no vanilla shot crosses. */
  public boolean zoneFull = true;

  /** A ring where about half of the shots land inside. */
  public boolean zoneHalfRing = true;

  /** A ring where about 90 % of the shots land inside. */
  public boolean zoneWideRing = false;

  /** On a server, the zone also holds a partial draw counted a tick more or less. */
  public boolean zoneTickCorrection = false;

  /** A larger dot (or cross) where the arrow will hit. */
  public boolean hitMarker = true;

  /** On a living target the path pulses. */
  public boolean livingPulse = true;

  /** On a living target the hit marker is a cross. */
  public boolean livingCross = true;

  /** On a living target the crosshair turns into a cross. */
  public boolean crosshairTurn = true;

  public static TrajectoryConfig get() {
    return instance;
  }

  public static Path path() {
    return FabricLoader.getInstance().getConfigDir().resolve(FILE);
  }

  public static TrajectoryConfig decode(String text) {
    var json = JsonParser.parseString(text);
    if (!json.isJsonObject()) {
      throw new IllegalArgumentException("Config must be an object");
    }
    var config = GSON.fromJson(json, TrajectoryConfig.class);
    if (config.schemaVersion != 1) {
      throw new IllegalArgumentException("Unsupported schema version " + config.schemaVersion);
    }
    config.sanitize();
    return config;
  }

  public void sanitize() {
    dotSize = Math.clamp(dotSize, MIN_DOT_SIZE, MAX_DOT_SIZE);
    dotSpacing = Math.clamp(dotSpacing, MIN_DOT_SPACING, MAX_DOT_SPACING);
  }

  public static void load() {
    try {
      if (Files.isRegularFile(path())) {
        instance = decode(Files.readString(path()));
      } else {
        save();
      }
    } catch (Exception failure) {
      // Keep the unreadable original; a later explicit settings change may replace it.
      TrajectoryProjected.LOGGER.warn("Could not read {}; using defaults", FILE, failure);
    }
  }

  public static void save() {
    instance.sanitize();
    try {
      AtomicFileWriter.write(path(), writer -> GSON.toJson(instance, writer));
    } catch (Exception failure) {
      throw new IllegalStateException("Could not save " + FILE, failure);
    }
  }

  public static String snapshot() {
    return GSON.toJson(instance);
  }

  public static void restoreSnapshot(String json) {
    instance = decode(json);
  }

  public static void defaults() {
    instance = new TrajectoryConfig();
  }
}
