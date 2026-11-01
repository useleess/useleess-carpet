package com.useleess.carpet.server;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IInteractionObject;

/** Opens the anvil window for the search (what {@code EntityPlayerMP.displayGui} wants). */
final class SearchWindow implements IInteractionObject {
    private final EditorSession session;

    SearchWindow(EditorSession session) {
        this.session = session;
    }

    @Override public Container createContainer(InventoryPlayer inventory, EntityPlayer player) {
        return session.createSearchContainer(inventory, player);
    }

    @Override public String getGuiID() {
        return "minecraft:anvil";
    }

    @Override public String getName() {
        return ServerText.get("editor.search_title");
    }

    @Override public boolean hasCustomName() {
        return true;
    }

    @Override public ITextComponent getDisplayName() {
        return new TextComponentString(getName());
    }
}
