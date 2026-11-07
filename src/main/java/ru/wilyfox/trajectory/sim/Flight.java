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

  /** A free-air tick: the movement and the velocity it leaves. */
  record Step(Vec3d movement, Vec3d next) {}

  /** Ticks after which the flight is cut off. */
  int maxTicks();

  /** Arrows and the trident's relatives: {@link ArrowFlight}. */
  static Flight arrow(int maxTicks) {
    return new Flight() {
      @Override
      public Trajectory simulate(Vec3d spawn, Vec3d velocity, WorldView world) {
        return ArrowFlight.simulate(spawn, velocity, world, maxTicks);
      }

      @Override
      public Step step(Vec3d velocity) {
        return new Step(
            velocity, velocity.scale(ArrowFlight.AIR_DRAG).add(0.0, -ArrowFlight.GRAVITY, 0.0));
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
        Vec3d next = ThrowableFlight.nextVelocity(velocity, gravity, false);
        return new Step(next, next);
      }

      @Override
      public int maxTicks() {
        return maxTicks;
      }
    };
  }
}
