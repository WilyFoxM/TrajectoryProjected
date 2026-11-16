package ru.wilyfox.trajectory.sim.compat;

/** Projectile rules that differ between the Minecraft versions this build targets. */
public final class VanillaRules {
  private VanillaRules() {}

  /**
   * {@code FishingHook} gravity per tick: the literal {@code 0.03} through 26.2; since 26.3 it is
   * {@code getDefaultGravity()}, which returns the float {@code 0.03F} widened to double.
   */
  //? if >=26.3 {
  /*public static final double FISHING_HOOK_GRAVITY = 0.03F;
  *///?} else {
  public static final double FISHING_HOOK_GRAVITY = 0.03;
  //?}

  /**
   * Where an arrow's hit within the entity margin lands ({@code AbstractArrow.findHitEntities}):
   * projected onto the entity's box through 26.2; since 26.3 the arrow passes {@code
   * projectSurfaceHitLocation = false} and the hit stays where the ray enters the inflated box.
   */
  //? if >=26.3 {
  /*public static final boolean ARROW_MARGIN_HIT_ON_SURFACE = false;
  *///?} else {
  public static final boolean ARROW_MARGIN_HIT_ON_SURFACE = true;
  //?}
}
