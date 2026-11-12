package ru.wilyfox.trajectory.sim;

/**
 * How a kind of projectile flies: the full simulation against the world, and the free-air step the
 * spread zone flies its sample shots with.
 */
public interface Flight {
  /** Simulates the flight from a spawn state until it ends. */
  Trajectory simulate(Vec3d spawn, Vec3d velocity, WorldView world);

  /**
   * One tick in free air from a velocity: how far the projectile moves, and its velocity at the
   * start of the next tick. Arrows move first and slow down after; thrown items slow down first.
   */
  Step step(Vec3d velocity);

  /** {@link #step(Vec3d)} for a tick that began in water, for projectiles water slows down. */
  default Step step(Vec3d velocity, boolean inWater) {
    return step(velocity);
  }

  /** The projectile's bounding box at a position. */
  Aabb box(Vec3d position);

  /** A free-air tick: the movement and the velocity it leaves. */
  record Step(Vec3d movement, Vec3d next) {}

  /** Ticks after which the flight is cut off. */
  int maxTicks();

  /** Arrows: {@link ArrowFlight}. */
  static Flight arrow(int maxTicks) {
    return arrow(maxTicks, ArrowFlight.WATER_INERTIA);
  }

  /** The trident: {@link ArrowFlight} with its water inertia. */
  static Flight trident(int maxTicks) {
    return arrow(maxTicks, ArrowFlight.TRIDENT_WATER_INERTIA);
  }

  private static Flight arrow(int maxTicks, float waterInertia) {
    return new Flight() {
      @Override
      public Trajectory simulate(Vec3d spawn, Vec3d velocity, WorldView world) {
        return ArrowFlight.simulate(spawn, velocity, world, maxTicks, waterInertia);
      }

      @Override
      public Step step(Vec3d velocity) {
        return step(velocity, false);
      }

      @Override
      public Step step(Vec3d velocity, boolean inWater) {
        float slowdown = inWater ? waterInertia : ArrowFlight.AIR_DRAG;
        return new Step(velocity, velocity.scale(slowdown).add(0.0, -ArrowFlight.GRAVITY, 0.0));
      }

      @Override
      public Aabb box(Vec3d position) {
        return ArrowFlight.box(position);
      }

      @Override
      public int maxTicks() {
        return maxTicks;
      }
    };
  }

  /** A fishing hook: {@link FishingHookFlight}. */
  static Flight fishingHook(int maxTicks) {
    return new Flight() {
      @Override
      public Trajectory simulate(Vec3d spawn, Vec3d velocity, WorldView world) {
        return FishingHookFlight.simulate(spawn, velocity, world, maxTicks);
      }

      /** Gravity before the move, inertia after it. */
      @Override
      public Step step(Vec3d velocity) {
        Vec3d movement = velocity.add(0.0, -FishingHookFlight.GRAVITY, 0.0);
        return new Step(movement, movement.scale(FishingHookFlight.INERTIA));
      }

      @Override
      public Aabb box(Vec3d position) {
        return FishingHookFlight.box(position);
      }

      @Override
      public int maxTicks() {
        return maxTicks;
      }
    };
  }

  /** A rocket shot at an angle from a crossbow: {@link FireworkFlight}. */
  static Flight firework(int flightCount) {
    return new Flight() {
      @Override
      public Trajectory simulate(Vec3d spawn, Vec3d velocity, WorldView world) {
        return FireworkFlight.simulate(spawn, velocity, world, flightCount);
      }

      @Override
      public Step step(Vec3d velocity) {
        return new Step(velocity, velocity);
      }

      @Override
      public Aabb box(Vec3d position) {
        return FireworkFlight.box(position);
      }

      @Override
      public int maxTicks() {
        return FireworkFlight.lastExplosionTick(flightCount);
      }
    };
  }

  /** A thrown item: {@link ThrowableFlight} with the item's gravity. */
  static Flight throwable(double gravity, int maxTicks) {
    return new Flight() {
      @Override
      public Trajectory simulate(Vec3d spawn, Vec3d velocity, WorldView world) {
        return ThrowableFlight.simulate(spawn, velocity, world, gravity, maxTicks);
      }

      @Override
      public Step step(Vec3d velocity) {
        return step(velocity, false);
      }

      @Override
      public Step step(Vec3d velocity, boolean inWater) {
        Vec3d next = ThrowableFlight.nextVelocity(velocity, gravity, inWater);
        return new Step(next, next);
      }

      @Override
      public Aabb box(Vec3d position) {
        return ThrowableFlight.box(position);
      }

      @Override
      public int maxTicks() {
        return maxTicks;
      }
    };
  }

  /** A wind charge: {@link WindChargeFlight}. */
  static Flight windCharge(int maxTicks) {
    return new Flight() {
      @Override
      public Trajectory simulate(Vec3d spawn, Vec3d velocity, WorldView world) {
        return WindChargeFlight.simulate(spawn, velocity, world, maxTicks);
      }

      @Override
      public Step step(Vec3d velocity) {
        return new Step(velocity, velocity);
      }

      @Override
      public Aabb box(Vec3d position) {
        return WindChargeFlight.box(position);
      }

      @Override
      public int maxTicks() {
        return maxTicks;
      }
    };
  }
}
