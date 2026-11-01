package com.useleess.carpet.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.SPacketSetSlot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * The op's real inventory (main, armour, offhand and the cursor) while the editor is open:
 * {@code <world>/useleess-carpet/editor/<uuid>.dat}. Written first, then the inventory is emptied; given back exactly
 * on every way out, and a file left behind by a crash is given back at the next join. A file is only deleted after the
 * items are back; if giving them back fails the file stays.
 */
final class Vault {
    static File dir;

    private Vault() {}

    private static File file(UUID id) {
        return new File(dir, id + ".dat");
    }

    static boolean has(UUID id) {
        return file(id).isFile();
    }

    /** Saves and empties the inventory. Nothing is emptied when the file cannot be written or already exists. */
    static void store(EntityPlayerMP player) throws IOException {
        File target = file(player.getUniqueID());
        if (target.exists()) throw new IOException(target + " exists");
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("inventory", player.inventory.writeToNBT(new NBTTagList()));
        root.setTag("cursor", player.inventory.getItemStack().writeToNBT(new NBTTagCompound()));
        Files.createDirectories(dir.toPath());
        File temp = new File(dir, player.getUniqueID() + ".dat.tmp");
        try (OutputStream out = Files.newOutputStream(temp.toPath())) {
            CompressedStreamTools.writeCompressed(root, out);
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
        player.inventory.clear();
        player.inventory.setItemStack(ItemStack.EMPTY);
        resync(player);
    }

    /** Gives the saved items back and deletes the file. Throws (file kept) when it cannot be read. */
    static void restore(EntityPlayerMP player) throws IOException {
        File source = file(player.getUniqueID());
        NBTTagCompound root;
        try (InputStream in = Files.newInputStream(source.toPath())) {
            root = CompressedStreamTools.readCompressed(in);
        }
        player.inventory.readFromNBT(root.getTagList("inventory", 10));
        player.inventory.setItemStack(new ItemStack(root.getCompoundTag("cursor")));
        Files.delete(source.toPath());
        resync(player);
    }

    private static void resync(EntityPlayerMP player) {
        player.inventoryContainer.detectAndSendChanges();
        player.sendContainerToPlayer(player.inventoryContainer);
        player.connection.sendPacket(new SPacketSetSlot(-1, -1, player.inventory.getItemStack()));
    }
}
