package ru.wilyfox.trajectory.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.wilyfox.trajectory.sim.Aabb;
import ru.wilyfox.trajectory.sim.Face;
import ru.wilyfox.trajectory.sim.Vec3d;
import ru.wilyfox.trajectory.sim.WorldView;

/** {@link WorldView} over the client level, using the same vanilla calls an arrow makes. */
final class ClientWorldView implements WorldView {
  private final ClientLevel level;
  private final Player shooter;

  ClientWorldView(ClientLevel level, Player shooter) {
    this.level = level;
    this.shooter = shooter;
  }

  @Override
  public BlockClip clip(Vec3d from, Vec3d to) {
    if (!loaded(from) || !loaded(to)) {
      return BlockClip.unknown(from);
    }

    // CollisionContext.empty() matches an arrow for ordinary blocks; entity-specific shapes
    // (scaffolding, powder snow) may differ.
    BlockHitResult hit =
        level.clipIncludingBorder(
            new ClipContext(
                vanilla(from),
                vanilla(to),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()));
    Vec3d location = own(hit.getLocation());
    if (hit.getType() == HitResult.Type.MISS) {
      return BlockClip.miss(to);
    }
    return BlockClip.block(location, Face.valueOf(hit.getDirection().name()));
  }

  /**
   * {@code ClientLevel.hasChunk} always answers true; the chunk cache returns null for chunks the
   * client has not received.
   */
  private boolean loaded(Vec3d point) {
    return level
        .getChunkSource()
        .hasChunk(
            SectionPos.blockToSectionCoord(Mth.floor(point.x())),
            SectionPos.blockToSectionCoord(Mth.floor(point.z())));
  }

  @Override
  public boolean isInsideCollision(Vec3d position) {
    BlockPos pos = BlockPos.containing(position.x(), position.y(), position.z());
    BlockState state = level.getBlockState(pos);
    if (state.isAir()) {
      return false;
    }
    VoxelShape shape = state.getCollisionShape(level, pos);
    if (shape.isEmpty()) {
      return false;
    }
    Vec3 point = vanilla(position);
    for (AABB box : shape.toAabbs()) {
      if (box.move(pos).contains(point)) {
        return true;
      }
    }
    return false;
  }

  /** {@code EntityFluidInteraction.update} for water, without currents. */
  @Override
  public boolean isInWater(Aabb arrowBox) {
    AABB box = vanilla(arrowBox).deflate(0.001);
    int x0 = Mth.floor(box.minX);
    int y0 = Mth.floor(box.minY);
    int z0 = Mth.floor(box.minZ);
    int x1 = Mth.ceil(box.maxX) - 1;
    int y1 = Mth.ceil(box.maxY) - 1;
    int z1 = Mth.ceil(box.maxZ) - 1;
    double height = 0.0;
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    for (int x = x0; x <= x1; x++) {
      for (int y = y0; y <= y1; y++) {
        for (int z = z0; z <= z1; z++) {
          pos.set(x, y, z);
          FluidState fluid = level.getFluidState(pos);
          if (fluid.isEmpty() || !fluid.is(FluidTags.WATER)) {
            continue;
          }
          double top = y + fluid.getHeight(level, pos);
          if (!(top < box.minY)) {
            height = Math.max(top - arrowBox.minY(), height);
          }
        }
      }
    }
    return height > 0.0;
  }

  @Override
  public List<EntityTarget> entities(Aabb area) {
    Entity shooterRoot = shooter.getRootVehicle();
    List<EntityTarget> targets = new ArrayList<>();
    for (Entity entity :
        level.getEntities((Entity) null, vanilla(area), Entity::canBeHitByProjectile)) {
      // AbstractArrow.canHitEntity: team rules the client knows; the server's PvP switch it
      // doesn't.
      if (entity instanceof Player other && other != shooter && !shooter.canHarmPlayer(other)) {
        continue;
      }
      AABB box = entity.getBoundingBox();
      targets.add(
          new EntityTarget(
              entity.getId(),
              new Aabb(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ),
              own(entity.position()),
              entity.getRootVehicle() == shooterRoot));
    }
    return targets;
  }

  @Override
  public double minY() {
    return level.getMinY();
  }

  static Vec3 vanilla(Vec3d v) {
    return new Vec3(v.x(), v.y(), v.z());
  }

  static Vec3d own(Vec3 v) {
    return new Vec3d(v.x, v.y, v.z);
  }

  private static AABB vanilla(Aabb b) {
    return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
  }
}
