package cn.nukkit.server.block;

import cn.nukkit.api.event.block.BlockRedstoneEvent;
import cn.nukkit.server.Player;
import cn.nukkit.server.item.Item;
import cn.nukkit.server.level.NukkitLevel;
import cn.nukkit.server.math.BlockFace;
import cn.nukkit.server.math.Vector3;

/**
 * @author CreeperFace
 */
public class BlockTripWireHook extends BlockFlowable {

    public BlockTripWireHook() {
        this(0);
    }

    public BlockTripWireHook(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Tripwire Hook";
    }

    @Override
    public int getId() {
        return TRIPWIRE_HOOK;
    }

    public BlockFace getFacing() {
        return BlockFace.fromHorizontalIndex(meta & 0b11);
    }

    @Override
    public int onUpdate(int type) {
        if (type == NukkitLevel.BLOCK_UPDATE_NORMAL) {
            if (!this.getSide(this.getFacing().getOpposite()).isNormalBlock()) {
                this.level.useBreakOn(this);
            }

            return type;
        } else if (type == NukkitLevel.BLOCK_UPDATE_SCHEDULED) {
            this.calculateState(false, true, -1, null);
            return type;
        }

        return 0;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (!this.getSide(face.getOpposite()).isNormalBlock()) {
            return false;
        }

        if (face.getAxis().isHorizontal()) {
            this.setFace(face);
        }

        this.level.setBlock(this, this);

        if (player != null) {
            this.calculateState(false, false, -1, null);
        }
        return true;
    }

    @Override
    public boolean onBreak(Item item) {
        super.onBreak(item);
        boolean attached = isAttached();
        boolean powered = isPowered();

        if (attached || powered) {
            this.calculateState(true, false, -1, null);
        }

        if (powered) {
            this.level.updateAroundRedstone(this, null);
            this.level.updateAroundRedstone(this.getLocation().getSide(getFacing().getOpposite()), null);
        }

        return true;
    }

    public void calculateState(boolean onBreak, boolean updateAround, int pos, Block block) {
        BlockFace facing = getFacing();
        Vector3 v = this.getLocation();
        boolean attached = isAttached();
        boolean powered = isPowered();
        boolean canConnect = !onBreak;
        boolean nextPowered = false;
        int distance = 0;
        Block[] blocks = new Block[42];

        for (int i = 1; i < 42; ++i) {
            Vector3 vector = v.getSide(facing, i);
            Block b = this.level.getBlock(vector);

            if (b instanceof BlockTripWireHook) {
                if (((BlockTripWireHook) b).getFacing() == facing.getOpposite()) {
                    distance = i;
                }
                break;
            }

            if (b.getId() != Block.TRIPWIRE && i != pos) {
                blocks[i] = null;
                canConnect = false;
            } else {
                if (i == pos) {
                    b = block != null ? block : b;
                }

                if (b instanceof BlockTripWire) {
                    boolean disarmed = !((BlockTripWire) b).isDisarmed();
                    boolean wirePowered = ((BlockTripWire) b).isPowered();
                    nextPowered |= disarmed && wirePowered;

                    if (i == pos) {
                        this.level.scheduleUpdate(this, 10);
                        canConnect &= disarmed;
                    }
                }
                blocks[i] = b;
            }
        }

        canConnect = canConnect & distance > 1;
        nextPowered = nextPowered & canConnect;
        BlockTripWireHook hook = new BlockTripWireHook();
        hook.setAttached(canConnect);
        hook.setPowered(nextPowered);


        if (distance > 0) {
            Vector3 vec = v.getSide(facing, distance);
            BlockFace face = facing.getOpposite();
            hook.setFace(face);
            this.level.setBlock(vec, hook, true, false);
            this.level.updateAroundRedstone(vec, null);
            this.level.updateAroundRedstone(vec.getSide(face.getOpposite()), null);
            this.addSound(vec, canConnect, nextPowered, attached, powered);
        }

        this.addSound(v, canConnect, nextPowered, attached, powered);

        if (!onBreak) {
            hook.setFace(facing);
            this.level.setBlock(v, hook, true, false);

            if (updateAround) {
                this.level.updateAroundRedstone(v, null);
                this.level.updateAroundRedstone(v.getSide(facing.getOpposite()), null);
            }
        }

        if (attached != canConnect) {
            for (int i = 1; i < distance; i++) {
                Vector3 vc = v.getSide(facing, i);
                block = blocks[i];

                if (block != null && this.level.getBlockIdAt(vc.getFloorX(), vc.getFloorY(), vc.getFloorZ()) != Block.AIR) {
                    if (canConnect ^ ((block.meta & 0x04) > 0)) {
                        block.meta ^= 0x04;
                    }

                    this.level.setBlock(vc, block, true, false);
                }
            }
        }
    }

    private void addSound(Vector3 pos, boolean canConnect, boolean nextPowered, boolean attached, boolean powered) {
        if (nextPowered && !powered) {
            this.level.addLevelSoundEvent(LevelSoundEventPacket.SOUND_POWER_ON, 1, -1, pos);
            this.level.getServer().getPluginManager().callEvent(new BlockRedstoneEvent(this, 0, 15));
        } else if (!nextPowered && powered) {
            this.level.addLevelSoundEvent(LevelSoundEventPacket.SOUND_POWER_OFF, 1, -1, pos);
            this.level.getServer().getPluginManager().callEvent(new BlockRedstoneEvent(this, 15, 0));
        } else if (canConnect && !attached) {
            this.level.addLevelSoundEvent(LevelSoundEventPacket.SOUND_ATTACH, 1, -1, pos);
        } else if (!canConnect && attached) {
            this.level.addLevelSoundEvent(LevelSoundEventPacket.SOUND_DETACH, 1, -1, pos);
        }
    }

    public boolean isAttached() {
        return (meta & 0x04) > 0;
    }

    public boolean isPowered() {
        return (this.meta & 0x08) > 0;
    }

    public void setPowered(boolean value) {
        if (value ^ this.isPowered()) {
            this.meta ^= 0x08;
        }
    }

    public void setAttached(boolean value) {
        if (value ^ this.isAttached()) {
            this.meta ^= 0x04;
        }
    }

    public void setFace(BlockFace face) {
        this.meta -= this.meta % 4;
        this.meta |= face.getHorizontalIndex();
    }

    @Override
    public boolean isPowerSource() {
        return true;
    }

    @Override
    public int getWeakPower(BlockFace face) {
        return isPowered() ? 15 : 0;
    }

    @Override
    public int getStrongPower(BlockFace side) {
        return !isPowered() ? 0 : getFacing() == side ? 15 : 0;
    }
}