package com.useleess.carpet.server;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

/**
 * The search window: a vanilla anvil whose rename field is the search text. Its two item slots only show a paper;
 * taking the result (or closing the window) hands the text to the {@link EditorSession}. Nothing of the anvil's own
 * logic runs, so no item, level or paper can come out of it.
 */
final class SearchContainer extends ContainerRepair {
    private final EditorSession session;

    SearchContainer(EditorSession session, InventoryPlayer inventory, EntityPlayer player) {
        super(inventory, player.world, BlockPos.ORIGIN, player);
        this.session = session;
    }

    @Override public void updateRepairOutput() {
    }

    @Override public void updateItemName(String name) {
        session.searchTyped(name);
    }

    @Override public ItemStack slotClick(int slotId, int button, ClickType type, EntityPlayer player) {
        session.searchClick(slotId);
        Slot slot = slotId >= 0 && slotId < inventorySlots.size() ? inventorySlots.get(slotId) : null;
        return slot == null ? ItemStack.EMPTY : slot.getStack().copy();
    }

    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return ItemStack.EMPTY;
    }

    @Override public boolean canDragIntoSlot(Slot slot) {
        return false;
    }

    @Override public boolean canMergeSlot(ItemStack stack, Slot slot) {
        return false;
    }

    @Override public boolean canInteractWith(EntityPlayer player) {
        return !session.closed();
    }

    @Override public void onContainerClosed(EntityPlayer player) {
        session.searchClosed(this);
    }
}
