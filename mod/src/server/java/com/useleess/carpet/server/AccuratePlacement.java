// What may be set follows Litematica Upgraded's own server part (AccuratePlacement, MIT, Useleess).
package com.useleess.carpet.server;

import com.google.common.base.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockQuartz;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Carpet rule {@code litematicaAccuratePlacement}: the block a click of Litematica Upgraded's printer or Easy Place
 * creates gets the state LU asked for just before that click, as far as a player could give it by placing and clicking
 * (see {@link LuProtocol#settable}), and only if it drops the same items. A vanilla server has no "block placed" event,
 * so the spots with a request are read before each click and the ones whose block changed are the ones it placed.
 */
final class AccuratePlacement {
    static final String RULE = "litematicaAccuratePlacement";
    private static final int STATE_ONLY_TO_CLIENTS = 2;
    private static final Map<UUID, PendingIntents> PENDING = new HashMap<>();
    /** The blocks at the waiting spots before the click being handled (one click at a time, on the server thread). */
    private static final Map<LuProtocol.Intent, Block> BEFORE = new IdentityHashMap<>();
    /** States set this tick: a rail placed next to one re-shapes it, so they are put back after every click of the tick. */
    private static final Map<World, Map<BlockPos, LuProtocol.Intent>> HELD = new HashMap<>();

    private AccuratePlacement() {}

    static void intent(EntityPlayerMP player, LuProtocol.Intent intent) {
        PENDING.computeIfAbsent(player.getUniqueID(), id -> new PendingIntents()).add(intent, System.nanoTime());
    }

    static void forget(EntityPlayerMP player) {
        PENDING.remove(player.getUniqueID());
    }

    static void tick() {
        HELD.clear();
    }

    static void clickStarted(EntityPlayerMP player) {
        BEFORE.clear();
        PendingIntents pending = PENDING.get(player.getUniqueID());
        if (pending == null) return;
        for (LuProtocol.Intent intent : pending.live(System.nanoTime())) {
            BlockPos pos = pos(intent);
            // a client names the spot: reading an unloaded one would load its chunk
            if (player.world.isBlockLoaded(pos)) BEFORE.put(intent, player.world.getBlockState(pos).getBlock());
        }
    }

    static void clickDone(EntityPlayerMP player) {
        World world = player.world;
        Map<BlockPos, LuProtocol.Intent> held = HELD.get(world);
        if (held != null) held.forEach((pos, intent) -> set(world, pos, intent));
        BEFORE.forEach((intent, block) -> {
            BlockPos pos = pos(intent);
            if (world.getBlockState(pos).getBlock() == block) return;
            PENDING.get(player.getUniqueID()).remove(intent);
            if (set(world, pos, intent)) HELD.computeIfAbsent(world, w -> new HashMap<>()).put(pos, intent);
        });
        BEFORE.clear();
    }

    private static BlockPos pos(LuProtocol.Intent intent) {
        return new BlockPos(intent.x, intent.y, intent.z);
    }

    /** Whether the block at the spot was changed to what the request asks for. */
    private static boolean set(World world, BlockPos pos, LuProtocol.Intent intent) {
        IBlockState current = world.getBlockState(pos);
        if (!sameKind(Block.REGISTRY.getObject(new ResourceLocation(intent.block)), current.getBlock())) return false;
        IBlockState next = merge(current, intent.properties);
        if (next == null) return false;
        world.setBlockState(pos, next, STATE_ONLY_TO_CLIENTS);
        if (next.getBlock() instanceof BlockChest) pairChest(world, pos, next);
        return true;
    }

    /** The same block, or its lit / powered twin (an item places the unlit repeater where a schematic has the lit one). */
    private static boolean sameKind(Block wanted, Block placed) {
        return wanted == placed || pair(wanted, placed, Blocks.UNPOWERED_REPEATER, Blocks.POWERED_REPEATER)
                || pair(wanted, placed, Blocks.UNPOWERED_COMPARATOR, Blocks.POWERED_COMPARATOR)
                || pair(wanted, placed, Blocks.REDSTONE_TORCH, Blocks.UNLIT_REDSTONE_TORCH)
                || pair(wanted, placed, Blocks.REDSTONE_LAMP, Blocks.LIT_REDSTONE_LAMP)
                || pair(wanted, placed, Blocks.FURNACE, Blocks.LIT_FURNACE);
    }

    private static boolean pair(Block a, Block b, Block x, Block y) {
        return a == x && b == y || a == y && b == x;
    }

    /** What BlockChest.onBlockPlacedBy does: a chest of the same kind beside it, on the sides its facing allows, turns the same way. */
    private static void pairChest(World world, BlockPos pos, IBlockState chest) {
        EnumFacing facing = chest.getValue(BlockChest.FACING);
        EnumFacing[] sides = facing.getAxis() == EnumFacing.Axis.X ? new EnumFacing[] {EnumFacing.NORTH, EnumFacing.SOUTH}
                : new EnumFacing[] {EnumFacing.WEST, EnumFacing.EAST};
        for (EnumFacing side : sides) {
            BlockPos other = pos.offset(side);
            IBlockState state = world.getBlockState(other);
            if (state.getBlock() != chest.getBlock()) continue;
            if (state.getValue(BlockChest.FACING) != facing) world.setBlockState(other, state.withProperty(BlockChest.FACING, facing), 3);
            return;
        }
    }

    /** The state with the wanted values of the settable properties, or null when nothing changes or it would drop other items. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IBlockState merge(IBlockState current, Map<String, String> wanted) {
        Block block = current.getBlock();
        boolean lever = block instanceof BlockLever, iron = block.getDefaultState().getMaterial() == Material.IRON;
        IBlockState next = current;
        for (IProperty property : current.getPropertyKeys()) {
            boolean quartzAxis = block instanceof BlockQuartz && property == BlockQuartz.VARIANT;
            String text = wanted.get(property.getName());
            if (text == null || !quartzAxis && !LuProtocol.settable(property.getName(), lever, iron)) continue;
            Optional parsed = property.parseValue(text);
            if (!parsed.isPresent()) continue;
            if (quartzAxis && !(pillar(parsed.get()) && pillar(current.getValue(property)))) continue;
            next = next.withProperty(property, (Comparable) parsed.get());
        }
        if (next == current) return null;
        if (block.damageDropped(next) != block.damageDropped(current)) return null;
        return block.getItemDropped(next, new Random(0), 0) == block.getItemDropped(current, new Random(0), 0) ? next : null;
    }

    private static boolean pillar(Object variant) {
        return variant == BlockQuartz.EnumType.LINES_X || variant == BlockQuartz.EnumType.LINES_Y || variant == BlockQuartz.EnumType.LINES_Z;
    }
}
