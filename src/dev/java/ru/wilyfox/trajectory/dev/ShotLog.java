package ru.wilyfox.trajectory.dev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.wilyfox.trajectory.client.ClientWorldView;
import ru.wilyfox.trajectory.client.ShotPreview;
import ru.wilyfox.trajectory.dev.mixin.AbstractArrowAccessor;
import ru.wilyfox.trajectory.sim.ArrowFlight;
import ru.wilyfox.trajectory.sim.Launch;
import ru.wilyfox.trajectory.sim.Trajectory;
import ru.wilyfox.trajectory.sim.Vec3d;

/**
 * Development log comparing predictions with real arrows, for field measurements. For each own
 * arrow it records the predicted launch, the launch the server sent (velocity quantized by the
 * packet), the spread offset that implies, and where the arrow came to rest compared with two
 * predictions: the nominal one shown to the player, and one re-simulated from the server's launch,
 * which isolates the flight model from the random spread. The rest position is the one the server
 * reports after the arrow stops; where the client's own copy stopped is kept in a separate column.
 *
 * <p>Rows go to {@code trajectory-reports/shots-<time>.csv} in the game directory.
 */
final class ShotLog {
  private static final Logger LOG = LoggerFactory.getLogger("Trajectory Projected");

  /** An arrow should appear within a round trip; two seconds is generous. */
  private static final long MATCH_WINDOW_TICKS = 40;

  private static final int MAX_TRACK_TICKS = 600;

  /**
   * How long to wait after the client's copy of the arrow stops. The copy flies by itself from the
   * launch packet, so at that moment it only repeats the re-simulation; the server's position
   * arrives afterwards, and that is what the row must compare with.
   */
  private static final int SETTLE_TICKS = 40;

  private static ShotPreview.Prediction pending;
  private static final List<Tracked> tracked = new ArrayList<>();
  private static Path file;

  private ShotLog() {}

  static void released(ShotPreview.Prediction prediction) {
    pending = prediction;
  }

  static void entityLoaded(Entity entity) {
    if (pending == null || entity.getType() != EntityTypes.ARROW) {
      return;
    }
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.player == null || ((Arrow) entity).getOwner() != minecraft.player) {
      return;
    }
    if (entity.level().getGameTime() - pending.gameTime() > MATCH_WINDOW_TICKS) {
      pending = null;
      return;
    }
    Vec3d spawn = ClientWorldView.own(entity.position());
    Vec3d velocity = ClientWorldView.own(entity.getDeltaMovement());
    Trajectory fromServer =
        ArrowFlight.simulate(
            spawn, velocity, new ClientWorldView(minecraft.level, minecraft.player), 400);
    tracked.add(new Tracked((Arrow) entity, pending, spawn, velocity, fromServer));
    pending = null;
  }

  static void tick() {
    if (tracked.isEmpty()) {
      return;
    }
    for (Iterator<Tracked> it = tracked.iterator(); it.hasNext(); ) {
      Tracked shot = it.next();
      shot.age++;
      boolean resting = ((AbstractArrowAccessor) shot.arrow).trajectory$isInGround();
      if (resting && shot.firstRest == null) {
        shot.firstRest = ClientWorldView.own(shot.arrow.position());
        shot.settle = SETTLE_TICKS;
      }
      if (shot.arrow.isRemoved()) {
        write(shot, shot.firstRest != null ? "rest_unsettled" : "removed");
        it.remove();
      } else if (shot.firstRest != null) {
        if (--shot.settle <= 0) {
          write(shot, "rest");
          it.remove();
        }
      } else if (shot.age > MAX_TRACK_TICKS) {
        write(shot, "timeout");
        it.remove();
      }
    }
  }

  private static void write(Tracked shot, String outcome) {
    Launch launch = shot.prediction.launch();
    Vec3d actual = ClientWorldView.own(shot.arrow.position());
    Vec3d implied =
        shot.velocity
            .subtract(launch.inherited())
            .scale(1.0 / launch.power())
            .subtract(launch.direction());
    Vec3d nominalEnd = shot.prediction.path().last();
    Vec3d serverEnd = shot.fromServer.last();
    String row =
        String.join(
            ",",
            LocalDateTime.now().toString(),
            Integer.toString(shot.prediction.ticksHeld()),
            outcome,
            shot.prediction.path().end().name(),
            shot.fromServer.end().name(),
            vec(launch.spawn()),
            vec(shot.spawn),
            vec(launch.nominalVelocity()),
            vec(shot.velocity),
            vec(implied),
            num(
                Math.max(
                    Math.abs(implied.x()), Math.max(Math.abs(implied.y()), Math.abs(implied.z())))),
            vec(nominalEnd),
            vec(serverEnd),
            vec(actual),
            num(Math.sqrt(nominalEnd.distanceToSqr(actual))),
            num(Math.sqrt(serverEnd.distanceToSqr(actual))),
            shot.firstRest == null ? ",," : vec(shot.firstRest));
    LOG.info("shot {}", row);
    try {
      if (file == null) {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("trajectory-reports");
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        file = dir.resolve("shots-" + stamp + ".csv");
        Files.writeString(file, HEADER + "\n", StandardCharsets.UTF_8);
      }
      Files.writeString(file, row + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    } catch (IOException e) {
      LOG.warn("Could not write the shot log", e);
    }
  }

  private static final String HEADER =
      String.join(
          ",",
          "time",
          "ticks_held",
          "outcome",
          "predicted_end",
          "resimulated_end",
          "pred_spawn_x,pred_spawn_y,pred_spawn_z",
          "spawn_x,spawn_y,spawn_z",
          "pred_vel_x,pred_vel_y,pred_vel_z",
          "vel_x,vel_y,vel_z",
          "spread_x,spread_y,spread_z",
          "spread_max_abs",
          "pred_end_x,pred_end_y,pred_end_z",
          "resim_end_x,resim_end_y,resim_end_z",
          "rest_x,rest_y,rest_z",
          "pred_error",
          "resim_error",
          "client_rest_x,client_rest_y,client_rest_z");

  private static String vec(Vec3d v) {
    return num(v.x()) + "," + num(v.y()) + "," + num(v.z());
  }

  private static String num(double v) {
    return String.format(Locale.ROOT, "%.6f", v);
  }

  private static final class Tracked {
    final Arrow arrow;
    final ShotPreview.Prediction prediction;
    final Vec3d spawn;
    final Vec3d velocity;
    final Trajectory fromServer;
    int age;
    Vec3d firstRest;
    int settle;

    Tracked(
        Arrow arrow,
        ShotPreview.Prediction prediction,
        Vec3d spawn,
        Vec3d velocity,
        Trajectory fromServer) {
      this.arrow = arrow;
      this.prediction = prediction;
      this.spawn = spawn;
      this.velocity = velocity;
      this.fromServer = fromServer;
    }
  }
}
