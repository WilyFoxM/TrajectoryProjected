package ru.wilyfox.trajectory.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;
import org.apache.commons.lang3.mutable.MutableFloat;
import ru.wilyfox.trajectory.config.TrajectoryConfig;
import ru.wilyfox.trajectory.sim.BowShot;
import ru.wilyfox.trajectory.sim.CrossbowShot;
import ru.wilyfox.trajectory.sim.FishingShot;
import ru.wilyfox.trajectory.sim.Flight;
import ru.wilyfox.trajectory.sim.Launch;
import ru.wilyfox.trajectory.sim.ShooterState;
import ru.wilyfox.trajectory.sim.SpreadZone;
import ru.wilyfox.trajectory.sim.ThrowShot;
import ru.wilyfox.trajectory.sim.Trajectory;
import ru.wilyfox.trajectory.sim.TridentShot;
import ru.wilyfox.trajectory.sim.Vec3d;
import ru.wilyfox.trajectory.sim.WindChargeShot;
import ru.wilyfox.trajectory.sim.WorldView;

/**
 * Predicts what the player's weapon would fire, once per client tick: the bow or the trident being
 * readied, a charged crossbow or a thrown item (snowball, egg, ender pearl, splash and lingering
 * potion, bottle o' enchanting, wind charge) held in either hand.
 *
 * <p>The prediction is computed at the end of a client tick, after the movement packet. A bow
 * release or a crossbow click is handled at the start of the next tick and sent before that tick's
 * movement packet, so the server fires with the state used here.
 */
public final class ShotPreview {
  /** Twenty seconds of flight; a full-power arrow lands well within it. */
  private static final int MAX_TICKS = 400;

  /** A cast lands within a few seconds; bobbing settles within a couple more. */
  private static final int HOOK_TICKS = 200;

  private static final DrawCounter DRAW = new DrawCounter();
  private static InteractionHand drawHand = InteractionHand.MAIN_HAND;

  private static Consumer<Prediction> bowRelease;
  private static Prediction current;
  private static Prediction previous;

  private ShotPreview() {}

  /** Sets who hears about a bow prediction ending in a release; for development tools. */
  public static void onBowRelease(Consumer<Prediction> listener) {
    bowRelease = listener;
  }

  /** The prediction for a shot on the next tick; this is what the server will fire. */
  public static Prediction current() {
    return current;
  }

  /**
   * The prediction one tick earlier, for drawing: frames between two ticks blend from it to {@link
   * #current()} the way vanilla blends the camera, so the line moves with the player instead of
   * jumping once per tick. {@code null} on the first tick of a prediction.
   */
  static Prediction previous() {
    return previous;
  }

  /** Runs at the end of every client tick. */
  public static void tick(Minecraft minecraft) {
    Prediction last = current;
    int ticksHeld = followDraw(minecraft, minecraft.player);
    current = withSurfaces(compute(minecraft.player, minecraft.level, ticksHeld), last, minecraft);
    previous = continues(last, current) ? last : null;
    if (bowRelease != null && last != null && last.weapon() == Weapon.BOW && current == null) {
      bowRelease.accept(last);
    }
  }

  /** Whether a prediction goes on from the last one, so frames can blend between the two. */
  private static boolean continues(Prediction last, Prediction next) {
    return last != null
        && next != null
        && last.weapon() == next.weapon()
        && last.shots().size() == next.shots().size();
  }

  /**
   * Finds the surfaces each zone falls on. A zone that glides from where it was last tick needs the
   * surfaces along the way too, or frames before the next tick would draw it on nothing.
   */
  private static Prediction withSurfaces(Prediction next, Prediction last, Minecraft minecraft) {
    if (next == null) {
      return null;
    }
    boolean continues = continues(last, next);
    List<Shot> shots = new ArrayList<>(next.shots().size());
    for (int i = 0; i < next.shots().size(); i++) {
      Shot shot = next.shots().get(i);
      SpreadZone.Zone zone = shot.zone();
      if (zone == null) {
        shots.add(shot);
        continue;
      }
      SpreadZone.Zone before = continues ? last.shots().get(i).zone() : null;
      SpreadZone.Zone from = before != null && before.glidesTo(zone) ? before : null;
      shots.add(
          new Shot(
              shot.flight(),
              shot.launch(),
              shot.path(),
              shot.livingTarget(),
              zone,
              from,
              shot.shadowsFrom(),
              ZoneSurfaces.patches(minecraft.level, zone, from, shot.shadowsFrom())));
    }
    return new Prediction(
        next.weapon(), next.hand(), List.copyOf(shots), next.ticksHeld(), next.gameTime());
  }

  /** Follows the draw every tick, whether or not predictions are on, so a count is never stale. */
  private static int followDraw(Minecraft minecraft, LocalPlayer player) {
    if (player == null) {
      DRAW.reset();
      return 0;
    }
    boolean drawing =
        player.isUsingItem()
            && (player.getUseItem().is(Items.BOW) || player.getUseItem().is(Items.TRIDENT));
    if (drawing) {
      drawHand = player.getUsedItemHand();
    }
    return DRAW.update(
        drawing,
        drawing ? player.getTicksUsingItem() : 0,
        minecraft.options.keyUse.isDown(),
        player.getInventory().getSelectedSlot());
  }

  private static Prediction compute(LocalPlayer player, ClientLevel level, int ticksHeld) {
    if (!TrajectoryConfig.get().enabled
        || player == null
        || level == null
        || player.isPassenger()) {
      return null;
    }
    // Only the vanilla bow and crossbow: other weapons may change the launch.
    if (player.isUsingItem()) {
      ItemStack using = player.getUseItem();
      if (using.is(Items.BOW)) {
        return bow(player, level, player.getUsedItemHand(), ticksHeld);
      }
      if (using.is(Items.TRIDENT)) {
        return trident(player, level, player.getUsedItemHand(), using, ticksHeld);
      }
      return null;
    }
    // Stopped by an outdated flag from the server, which is still counting: the same draw goes on.
    if (DRAW.inGap()) {
      ItemStack held = player.getItemInHand(drawHand);
      if (held.is(Items.BOW)) {
        return bow(player, level, drawHand, ticksHeld);
      }
      if (held.is(Items.TRIDENT)) {
        return trident(player, level, drawHand, held, ticksHeld);
      }
    }
    for (InteractionHand hand : InteractionHand.values()) {
      ItemStack stack = player.getItemInHand(hand);
      if (stack.is(Items.CROSSBOW)) {
        ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
        if (charged != null && !charged.isEmpty()) {
          return crossbow(player, level, hand, stack, charged);
        }
      }
      if (stack.is(Items.WIND_CHARGE)) {
        return player.getCooldowns().isOnCooldown(stack)
            ? null
            : single(
                player,
                level,
                hand,
                Weapon.THROW,
                Flight.windCharge(MAX_TICKS),
                SentState.shooter(player) == null
                    ? null
                    : WindChargeShot.launch(SentState.shooter(player)),
                0);
      }
      if (stack.is(Items.FISHING_ROD)) {
        // With the hook out, a click reels it in instead of casting.
        return player.fishing == null && TrajectoryConfig.get().fishingRod
            ? rod(player, level, hand)
            : null;
      }
      ThrowShot.Item thrown = thrown(stack);
      if (thrown != null) {
        // A throw on cooldown (an ender pearl right after the last) does not happen.
        return player.getCooldowns().isOnCooldown(stack) ? null : toss(player, level, hand, thrown);
      }
    }
    return null;
  }

  /** The vanilla thrown items; {@code null} for anything else. */
  private static ThrowShot.Item thrown(ItemStack stack) {
    if (stack.is(Items.SNOWBALL)) {
      return ThrowShot.Item.SNOWBALL;
    }
    if (stack.is(Items.EGG) || stack.is(Items.BLUE_EGG) || stack.is(Items.BROWN_EGG)) {
      return ThrowShot.Item.EGG;
    }
    if (stack.is(Items.ENDER_PEARL)) {
      return ThrowShot.Item.ENDER_PEARL;
    }
    if (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) {
      return ThrowShot.Item.POTION;
    }
    if (stack.is(Items.EXPERIENCE_BOTTLE)) {
      return ThrowShot.Item.EXPERIENCE_BOTTLE;
    }
    return null;
  }

  /**
   * A trident being readied. Riptide launches the player instead of throwing, and a trident about
   * to break is not thrown: neither is shown.
   */
  private static Prediction trident(
      LocalPlayer player,
      ClientLevel level,
      InteractionHand hand,
      ItemStack trident,
      int ticksHeld) {
    if (trident.nextDamageWillBreak()
        || EnchantmentHelper.getTridentSpinAttackStrength(trident, player) > 0.0F) {
      return null;
    }
    ShooterState shooter = SentState.shooter(player);
    if (shooter == null) {
      return null;
    }
    return single(
        player,
        level,
        hand,
        Weapon.TRIDENT,
        Flight.trident(MAX_TICKS),
        TridentShot.launch(shooter, ticksHeld),
        ticksHeld);
  }

  private static Prediction single(
      LocalPlayer player,
      ClientLevel level,
      InteractionHand hand,
      Weapon weapon,
      Flight flight,
      Launch launch,
      int ticksHeld) {
    if (launch == null) {
      return null;
    }
    Shot shot = shot(player, level, flight, launch);
    return new Prediction(weapon, hand, List.of(shot), ticksHeld, level.getGameTime());
  }

  /**
   * A fishing rod in hand, not cast: where the hook would land. Its random spread moves the landing
   * by a tenth of a block or so, less than the ghost of the hook drawn there, so it has no zone.
   */
  private static Prediction rod(LocalPlayer player, ClientLevel level, InteractionHand hand) {
    ShooterState shooter = SentState.shooter(player);
    if (shooter == null) {
      return null;
    }
    Shot shot =
        shot(player, level, Flight.fishingHook(HOOK_TICKS), FishingShot.launch(shooter), List.of());
    return new Prediction(Weapon.ROD, hand, List.of(shot), 0, level.getGameTime());
  }

  private static Prediction toss(
      LocalPlayer player, ClientLevel level, InteractionHand hand, ThrowShot.Item item) {
    ShooterState shooter = SentState.shooter(player);
    if (shooter == null) {
      return null;
    }
    Shot shot =
        shot(player, level, ThrowShot.flight(item, MAX_TICKS), ThrowShot.launch(shooter, item));
    return new Prediction(Weapon.THROW, hand, List.of(shot), 0, level.getGameTime());
  }

  private static Prediction bow(
      LocalPlayer player, ClientLevel level, InteractionHand hand, int ticksHeld) {
    ShooterState shooter = SentState.shooter(player);
    if (shooter == null) {
      return null;
    }
    Launch launch = BowShot.launch(shooter, ticksHeld);
    if (launch == null) {
      return null;
    }
    // Singleplayer counts the same ticks as the client, so only a remote server needs the variants.
    List<SpreadZone.Variant> variants =
        TrajectoryConfig.get().zoneTickCorrection
                && !Minecraft.getInstance().hasSingleplayerServer()
            ? BowShot.tickVariants(shooter, ticksHeld)
            : List.of(new SpreadZone.Variant(launch, 1.0));
    Shot shot = shot(player, level, Flight.arrow(MAX_TICKS), launch, variants);
    return new Prediction(Weapon.BOW, hand, List.of(shot), ticksHeld, level.getGameTime());
  }

  private static Prediction crossbow(
      LocalPlayer player,
      ClientLevel level,
      InteractionHand hand,
      ItemStack crossbow,
      ChargedProjectiles charged) {
    ShooterState shooter = SentState.shooter(player);
    if (shooter == null) {
      return null;
    }
    List<ItemStack> items = charged.itemCopies();
    List<CrossbowShot.Charge> charges = new ArrayList<>(items.size());
    for (ItemStack item : items) {
      charges.add(
          item.is(Items.FIREWORK_ROCKET)
              ? CrossbowShot.Charge.FIREWORK
              : CrossbowShot.Charge.ARROW);
    }
    Float maxAngle = items.size() > 1 ? projectileSpread(crossbow) : Float.valueOf(0.0F);
    List<CrossbowShot.Projectile> volley =
        CrossbowShot.volley(shooter, charges, maxAngle == null ? 0.0F : maxAngle);

    List<Shot> shots = new ArrayList<>(volley.size());
    for (int i = 0; i < volley.size(); i++) {
      CrossbowShot.Projectile projectile = volley.get(i);
      // An unknown spread leaves only the projectile that flies straight regardless of it.
      if (maxAngle == null && projectile.angle() != 0.0F) {
        continue;
      }
      Flight flight =
          projectile.charge() == CrossbowShot.Charge.FIREWORK
              ? Flight.firework(flightCount(items.get(i)))
              : Flight.arrow(MAX_TICKS);
      shots.add(shot(player, level, flight, projectile.launch()));
    }
    if (shots.isEmpty()) {
      return null;
    }
    return new Prediction(Weapon.CROSSBOW, hand, List.copyOf(shots), 0, level.getGameTime());
  }

  private static Shot shot(LocalPlayer player, ClientLevel level, Flight flight, Launch launch) {
    return shot(player, level, flight, launch, List.of(new SpreadZone.Variant(launch, 1.0)));
  }

  private static Shot shot(
      LocalPlayer player,
      ClientLevel level,
      Flight flight,
      Launch launch,
      List<SpreadZone.Variant> variants) {
    WorldView world = new ClientWorldView(level, player);
    Trajectory path = flight.simulate(launch.spawn(), launch.nominalVelocity(), world);
    boolean living =
        path.end() == Trajectory.End.ENTITY
            && level.getEntity(path.entity().id()) instanceof LivingEntity;
    SpreadZone.Zone zone = null;
    Vec3d shadowsFrom = launch.spawn();
    if (TrajectoryConfig.get().showsZone()
        && !variants.isEmpty()
        && path.end() == Trajectory.End.BLOCK
        && path.impact() != null) {
      // The impact and the final movement, not the rest point: vanilla backs a stuck arrow off
      // by 0.05 per axis, which skews a direction taken from the last two points of a short final
      // step.
      Vec3d wet = firstInWater(path, flight, world);
      if (wet == null) {
        zone = SpreadZone.compute(variants, flight, path.impact(), path.arrival());
      } else {
        zone =
            SpreadZone.computeThroughWater(variants, flight, path.impact(), path.arrival(), world);
        // Below the surface the shot bends so much that the line back from the hit says nothing
        // about what it passed above the water.
        shadowsFrom = wet;
      }
    }
    return new Shot(flight, launch, path, living, zone, null, shadowsFrom, List.of());
  }

  /** The first point of a path in water, or {@code null} if it stays out of water. */
  private static Vec3d firstInWater(Trajectory path, Flight flight, WorldView world) {
    for (Vec3d point : path.points()) {
      if (world.isInWater(flight.box(point))) {
        return point;
      }
    }
    return null;
  }

  /** {@code 1 + Fireworks.flightDuration()}, as {@code FireworkRocketEntity} reads it. */
  private static int flightCount(ItemStack rocket) {
    Fireworks fireworks = rocket.get(DataComponents.FIREWORKS);
    return 1 + (fireworks == null ? 0 : fireworks.flightDuration());
  }

  /**
   * {@code EnchantmentHelper.processProjectileSpread} for the crossbow, which vanilla runs on the
   * server only. Effects without conditions are applied as the server would; an effect with a
   * condition could depend on what only the server knows, so the spread is then unknown ({@code
   * null}).
   */
  private static Float projectileSpread(ItemStack crossbow) {
    MutableFloat angle = new MutableFloat(0.0F);
    boolean[] known = {true};
    RandomSource random = RandomSource.create(0L);
    EnchantmentHelper.runIterationOnItem(
        crossbow,
        (enchantment, enchantmentLevel) -> {
          for (ConditionalEffect<EnchantmentValueEffect> effect :
              enchantment.value().getEffects(EnchantmentEffectComponents.PROJECTILE_SPREAD)) {
            if (effect.requirements().isPresent()) {
              known[0] = false;
            } else {
              angle.setValue(effect.effect().process(enchantmentLevel, random, angle.floatValue()));
            }
          }
        });
    return known[0] ? Math.max(0.0F, angle.floatValue()) : null;
  }

  /** The weapon a prediction is for. */
  public enum Weapon {
    BOW,
    CROSSBOW,
    TRIDENT,
    THROW,
    ROD
  }

  /**
   * One predicted projectile: how it flies, its launch, the flight without spread, whether it ends
   * in a living entity (mobs, players, armor stands), and the spread zone with the lit block
   * surfaces it falls on ({@code null} and empty unless the path ends in a block). {@code zoneFrom}
   * is the zone frames blend from until the next tick, which the surfaces also cover; {@code null}
   * if the zone is drawn where it is, because it just appeared or jumped. Nothing behind {@code
   * shadowsFrom} shades the surfaces.
   */
  public record Shot(
      Flight flight,
      Launch launch,
      Trajectory path,
      boolean livingTarget,
      SpreadZone.Zone zone,
      SpreadZone.Zone zoneFrom,
      Vec3d shadowsFrom,
      List<ZoneSurfaces.Patch> zonePatches) {}

  /**
   * A predicted shot of one weapon: its projectiles, the first being the one that flies straight
   * along the view, how long a bow has been drawn, and when it was computed.
   */
  public record Prediction(
      Weapon weapon, InteractionHand hand, List<Shot> shots, int ticksHeld, long gameTime) {

    /** The projectile straight along the view. */
    public Shot primary() {
      return shots.getFirst();
    }

    public Launch launch() {
      return primary().launch();
    }

    public Trajectory path() {
      return primary().path();
    }

    /** Whether any projectile ends in a living entity. */
    public boolean livingTarget() {
      return shots.stream().anyMatch(Shot::livingTarget);
    }

    public SpreadZone.Zone zone() {
      return primary().zone();
    }

    public List<ZoneSurfaces.Patch> zonePatches() {
      return primary().zonePatches();
    }
  }
}
