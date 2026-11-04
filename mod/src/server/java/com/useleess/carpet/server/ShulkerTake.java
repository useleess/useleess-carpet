// Moving items out of a carried shulker box follows Litematica Upgraded's own server part (ShulkerRestock, MIT, Useleess).
package com.useleess.carpet.server;

import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.NonNullList;

import java.util.function.BooleanSupplier;

/**
 * Carpet rule {@code litematicaShulkerRestock}: Litematica Upgraded's printer and Easy Place may ask this server to move
 * an item they need out of a shulker box in the player's inventory (vanilla has no way to do that). Players with LU are
 * told whether the server answers (at join and on every change of the rule). A failure is logged and told to the ops
 * once, then the feature stays off and LU is told so.
 */
final class ShulkerTake {
    static final String RULE = "litematicaShulkerRestock";
    private static final String BOX_TAG = "BlockEntityTag";
    private static final int BOX_SLOTS = 27;

    private ShulkerTake() {}

    /** Moves what fits of the request into the inventory; the rest stays in the box. Returns the number moved. */
    static int take(EntityPlayerMP player, LuProtocol.Take take) {
        if (player.isSpectator() || player.isDead || player.openContainer != player.inventoryContainer) return 0;
        if (take.boxSlot >= player.inventory.mainInventory.size() || take.innerSlot >= BOX_SLOTS || take.count <= 0) return 0;
        ItemStack box = player.inventory.mainInventory.get(take.boxSlot);
        if (box.isEmpty() || box.getCount() != 1 || !(Block.getBlockFromItem(box.getItem()) instanceof BlockShulkerBox)) return 0;
        NonNullList<ItemStack> items = NonNullList.withSize(BOX_SLOTS, ItemStack.EMPTY);
        NBTTagCompound tag = box.getSubCompound(BOX_TAG);
        if (tag != null) ItemStackHelper.loadAllItems(tag, items);
        ItemStack inner = items.get(take.innerSlot);
        if (inner.isEmpty() || Item.getIdFromItem(inner.getItem()) != take.itemId || inner.getMetadata() != take.meta) return 0;
        ItemStack moved = inner.splitStack(Math.min(Math.min(take.count, inner.getCount()), inner.getMaxStackSize()));
        int asked = moved.getCount();
        player.inventory.addItemStackToInventory(moved);
        if (!moved.isEmpty()) {
            if (inner.isEmpty()) items.set(take.innerSlot, moved);
            else inner.grow(moved.getCount());
        }
        int taken = asked - moved.getCount();
        if (taken > 0) {
            store(box, items);
            player.inventoryContainer.detectAndSendChanges();
        }
        return taken;
    }

    /** The contents back the way vanilla drops a box: no item list (and no tag) when it is empty. */
    private static void store(ItemStack box, NonNullList<ItemStack> items) {
        NBTTagCompound tag = box.getOrCreateSubCompound(BOX_TAG);
        tag.removeTag("Items");
        ItemStackHelper.saveAllItems(tag, items, false);
        if (tag.hasNoTags()) {
            box.removeSubCompound(BOX_TAG);
            if (box.getTagCompound() != null && box.getTagCompound().hasNoTags()) box.setTagCompound(null);
        }
    }
}
