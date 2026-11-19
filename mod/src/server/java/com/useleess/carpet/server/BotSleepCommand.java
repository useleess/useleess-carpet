package com.useleess.carpet.server;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * {@code /player <bot> autosleep [on|off]}: an action of Carpet's own /player command (#14, was /autosleep in #11): a
 * Carpet bot sleeps through each night by itself; without on / off it tells the state. Every other /player action, and
 * all of them while Carpet's commandPlayer rule is off, stay Carpet's.
 */
final class BotSleepCommand {
    static final String ACTION = "autosleep";
    private static final BooleanSupplier PLAYER_COMMAND = Rule.named("commandPlayer");

    private BotSleepCommand() {}

    /** Carpet's /player, at its start: true = it was our action and is answered. */
    static boolean handles(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2 || !args[1].equals(ACTION) || !PLAYER_COMMAND.getAsBoolean()) return false;
        if (args.length > 3 || args.length == 3 && !args[2].equals("on") && !args[2].equals("off")) {
            throw new WrongUsageException(ServerText.get("botsleep.usage"));
        }
        EntityPlayerMP bot = server.getPlayerList().getPlayerByUsername(args[0]);
        if (!BotSleep.isBot(bot)) {
            tell(sender, "botsleep.not_bot", args[0]);
        } else if (!BotSleep.working()) {
            tell(sender, "botsleep.off_after_error");
        } else if (args.length == 2) {
            tell(sender, BotSleep.on(bot) ? "botsleep.is_on" : "botsleep.is_off", bot.getName());
        } else {
            boolean on = args[2].equals("on");
            try {
                BotSleep.set(bot, on);
            } catch (IOException e) {
                Guard.LOG.error(ServerText.get("log.botsleep_save_failed", bot.getName()), e);
                tell(sender, "botsleep.save_failed", bot.getName());
                return true;
            }
            tell(sender, on ? "botsleep.now_on" : "botsleep.now_off", bot.getName());
            if (on && BotSleep.freeBed(bot) == null) tell(sender, "botsleep.no_bed", bot.getName());
        }
        return true;
    }

    /** Carpet's /player tab list, as it returns: our action among the actions, then on / off after it. */
    static List<String> tabs(List<String> carpets, String[] args) {
        List<String> out = new ArrayList<>(carpets);
        if (args.length == 2) {
            out.addAll(CommandBase.getListOfStringsMatchingLastWord(args, ACTION));
        } else if (args.length == 3 && args[1].equals(ACTION)) {
            out.addAll(CommandBase.getListOfStringsMatchingLastWord(args, "on", "off"));
        }
        return out;
    }

    private static void tell(ICommandSender sender, String key, Object... args) {
        sender.sendMessage(new TextComponentString(ServerText.get(key, args)));
    }
}
