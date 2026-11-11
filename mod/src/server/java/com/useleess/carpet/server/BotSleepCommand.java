package com.useleess.carpet.server;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** {@code /autosleep <bot> [on|off]}: a Carpet bot sleeps through each night by itself (#11); without on / off it tells the state. */
final class BotSleepCommand extends CommandBase {
    @Override public String getName() {
        return "autosleep";
    }

    @Override public String getUsage(ICommandSender sender) {
        return "/autosleep <bot> [on|off]";
    }

    @Override public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1 || args.length > 2 || args.length == 2 && !args[1].equals("on") && !args[1].equals("off")) {
            throw new WrongUsageException(getUsage(sender));
        }
        EntityPlayerMP bot = server.getPlayerList().getPlayerByUsername(args[0]);
        if (!BotSleep.isBot(bot)) {
            tell(sender, "botsleep.not_bot", args[0]);
        } else if (!BotSleep.working()) {
            tell(sender, "botsleep.off_after_error");
        } else if (args.length == 1) {
            tell(sender, BotSleep.on(bot) ? "botsleep.is_on" : "botsleep.is_off", bot.getName());
        } else {
            boolean on = args[1].equals("on");
            try {
                BotSleep.set(bot, on);
            } catch (IOException e) {
                Guard.LOG.error(ServerText.get("log.botsleep_save_failed", bot.getName()), e);
                tell(sender, "botsleep.save_failed", bot.getName());
                return;
            }
            tell(sender, on ? "botsleep.now_on" : "botsleep.now_off", bot.getName());
            if (on && BotSleep.freeBed(bot) == null) tell(sender, "botsleep.no_bed", bot.getName());
        }
    }

    private static void tell(ICommandSender sender, String key, Object... args) {
        sender.sendMessage(new TextComponentString(ServerText.get(key, args)));
    }

    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos target) {
        if (args.length == 2) return getListOfStringsMatchingLastWord(args, "on", "off");
        if (args.length != 1) return Collections.emptyList();
        List<String> bots = new ArrayList<>();
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) if (BotSleep.isBot(p)) bots.add(p.getName());
        return getListOfStringsMatchingLastWord(args, bots);
    }
}
