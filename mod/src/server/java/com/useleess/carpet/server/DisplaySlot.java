package com.useleess.carpet.server;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** A slot that only shows: nothing can be put in or taken out of it by Minecraft's own click handling. */
final class DisplaySlot extends Slot {
    DisplaySlot(IInventory inventory, int index) {
        super(inventory, index, 0, 0);
    }

    @Override public boolean isItemValid(ItemStack stack) {
        return false;
    }

    @Override public boolean canTakeStack(EntityPlayer player) {
        return false;
    }
}
