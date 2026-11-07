package ru.wilyfox.trajectory.sim;

/**
 * How a kind of projectile flies: the full simulation against the world, and the free-air step the
 * spread zone flies its sample shots with.
 */
public interface Flight {
  /** Simulates the flight from a spawn state until it ends. */
  Trajectory simulate(Vec3d spawn, Vec3d velocity, WorldView world);

  /** Velocity at the start of the next tick in free air, after moving by {@code velocity}. */
  Vec3d nextVelocity(Vec3d velocity);

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
      public Vec3d nextVelocity(Vec3d velocity) {
        return velocity.scale(ArrowFlight.AIR_DRAG).add(0.0, -ArrowFlight.GRAVITY, 0.0);
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
      public Vec3d nextVelocity(Vec3d velocity) {
        return velocity;
      }

      @Override
      public int maxTicks() {
        return FireworkFlight.lastExplosionTick(flightCount);
      }
    };
  }
}
