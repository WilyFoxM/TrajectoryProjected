package ru.wilyfox.trajectory.sim;

/**
 * What the server knows about the shooter at the moment it handles the release packet.
 *
 * <p>All values are the ones the client last <em>sent</em>, not the current camera: the release
 * packet leaves before the movement packet of the same client tick, so the server fires with the
 * rotation and position from the previous tick's packet.
 *
 * @param position feet position from the last movement packet with a position ({@code xLast})
 * @param eyeHeight eye height of the current pose (1.62 standing, 1.27 crouching), already scaled
 * @param sentYaw last sent yaw ({@code yRotLast}), unwrapped as the client keeps it
 * @param sentPitch last sent pitch ({@code xRotLast})
 * @param knownMovement difference between the last two sent positions, zero if the last client tick
 *     sent no position
 * @param onGround on-ground flag of the last movement packet
 */
public record ShooterState(
    Vec3d position,
    float eyeHeight,
    float sentYaw,
    float sentPitch,
    Vec3d knownMovement,
    boolean onGround) {}
