package com.useleess.carpet.server;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * The editor's window: the chest layout (27 menu slots, then the player's 36 inventory slots as the picker), every
 * click handled by the {@link EditorSession}. Minecraft's own click handling never runs, so nothing can leave.
 */
final class EditorContainer extends Container {
    private final EditorSession session;

    EditorContainer(EditorSession session, IInventory menu, IInventory picker) {
        this.session = session;
        for (int i = 0; i < Layout.MENU; i++) addSlotToContainer(new DisplaySlot(menu, i));
        for (int i = 0; i < Layout.PICKER; i++) addSlotToContainer(new DisplaySlot(picker, i));
    }

    @Override public ItemStack slotClick(int slotId, int button, ClickType type, EntityPlayer player) {
        session.click(slotId, button, type);
        Slot slot = slotId >= 0 && slotId < inventorySlots.size() ? inventorySlots.get(slotId) : null;
        return slot == null ? ItemStack.EMPTY : slot.getStack().copy();
    }

    @Override public boolean canInteractWith(EntityPlayer player) {
        return !session.closed();
    }

    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return ItemStack.EMPTY;
    }

    @Override public boolean canMergeSlot(ItemStack stack, Slot slot) {
        return false;
    }

    @Override public boolean canDragIntoSlot(Slot slot) {
        return false;
    }

    @Override public void onContainerClosed(EntityPlayer player) {
        session.windowClosed(this);
    }
}
