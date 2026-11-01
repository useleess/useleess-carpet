package com.useleess.carpet.server;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/** {@code /customrecipe}: opens the editor for an op (permission level 2). */
final class CustomRecipeCommand extends CommandBase {
    @Override public String getName() {
        return "customrecipe";
    }

    @Override public String getUsage(ICommandSender sender) {
        return "/customrecipe";
    }

    @Override public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        Entity entity = sender.getCommandSenderEntity();
        if (!(entity instanceof EntityPlayerMP)) throw new PlayerNotFoundException("commands.generic.player.unspecified");
        EntityPlayerMP player = (EntityPlayerMP) entity;
        if (Guard.off()) {
            player.sendMessage(new TextComponentString(ServerText.get("command.feature_off")));
        } else if (!Rule.on()) {
            player.sendMessage(new TextComponentString(ServerText.get("command.rule_off")));
        } else {
            Guard.run("editor", () -> Editor.open(player));
        }
    }
}
