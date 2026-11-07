package ru.wilyfox.trajectory.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BowShotTest {
  private static final Vec3d FEET = new Vec3d(10.5, 64.0, -3.25);

  private static ShooterState standing(float yaw, float pitch) {
    return new ShooterState(FEET, 1.62F, yaw, pitch, Vec3d.ZERO, true);
  }

  @Test
  void noArrowAtTwoTicksOrLess() {
    assertNull(BowShot.launch(standing(0.0F, 0.0F), 0));
    assertNull(BowShot.launch(standing(0.0F, 0.0F), 2));
    assertNotNull(BowShot.launch(standing(0.0F, 0.0F), 3));
  }

  @Test
  void fullDrawAtTwentyTicks() {
    Launch launch = BowShot.launch(standing(0.0F, 0.0F), 20);
    assertEquals(3.0F, launch.power());
    assertEquals(1.0F, BowShot.powerForTicks(20));
    assertTrue(BowShot.powerForTicks(19) < 1.0F);
    assertEquals(3.0F, BowShot.launch(standing(0.0F, 0.0F), 400).power());
  }

  @Test
  void spawnsAtEyeHeightMinusFloatTenth() {
    Launch launch = BowShot.launch(standing(0.0F, 0.0F), 20);
    double expectedY = (FEET.y() + 1.62F) - 0.1F;
    assertEquals(new Vec3d(FEET.x(), expectedY, FEET.z()), launch.spawn());
    assertNotEquals(FEET.y() + 1.52, launch.spawn().y(), "float constants are not exact decimals");
  }

  @Test
  void directionFollowsMinecraftAxes() {
    Vec3d south = BowShot.launch(standing(0.0F, 0.0F), 20).direction();
    assertEquals(1.0, south.z(), 1.0E-6);
    Vec3d west = BowShot.launch(standing(90.0F, 0.0F), 20).direction();
    assertEquals(-1.0, west.x(), 1.0E-6);
    Vec3d up = BowShot.launch(standing(0.0F, -90.0F), 20).direction();
    assertEquals(1.0, up.y(), 1.0E-6);
    assertEquals(1.0, south.length(), 1.0E-12);
  }

  @Test
  void yawIsWrappedLikeTheServer() {
    Launch wrapped = BowShot.launch(standing(90.7F, 12.3F), 20);
    Launch unwrapped = BowShot.launch(standing(90.7F + 3600.0F, 12.3F), 20);
    assertEquals(wrapped.direction(), unwrapped.direction());
    assertEquals(-90.0F, VanillaMath.serverPitch(270.0F));
    assertEquals(90.0F, VanillaMath.serverPitch(135.0F));
    assertEquals(-170.0F, VanillaMath.serverYaw(190.0F));
  }

  @Test
  void inheritsMovementWithoutVerticalOnGround() {
    Vec3d moved = new Vec3d(0.2, 0.42, -0.1);
    ShooterState grounded = new ShooterState(FEET, 1.62F, 0.0F, 0.0F, moved, true);
    ShooterState airborne = new ShooterState(FEET, 1.62F, 0.0F, 0.0F, moved, false);
    assertEquals(new Vec3d(0.2, 0.0, -0.1), BowShot.launch(grounded, 20).inherited());
    assertEquals(moved, BowShot.launch(airborne, 20).inherited());

    Launch launch = BowShot.launch(airborne, 20);
    Vec3d expected = launch.direction().scale(3.0F).add(moved);
    assertEquals(expected, launch.nominalVelocity());
  }

  @Test
  void spreadIsAddedBeforeScaling() {
    Launch launch = BowShot.launch(standing(0.0F, 0.0F), 20);
    double s = Launch.SPREAD_HALF_WIDTH;
    Vec3d spread = new Vec3d(s, -s, s);
    Vec3d expected = launch.direction().add(spread).scale(launch.power());
    assertEquals(expected, launch.velocity(spread));
    assertEquals(0.0172275, s);
  }
}
