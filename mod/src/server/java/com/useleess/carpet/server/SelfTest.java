package com.useleess.carpet.server;

import net.minecraft.block.BlockWorkbench;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /uct}: drives the editor of a (fake) player from the console for the in-game tests; registered only when the
 * server runs with {@code -Duseleess.carpet.selftest=1}. Clicks go through the player's real open container by slot
 * ROLE (see {@link Layout}), answers are printed as "UCT ..." lines:
 * <pre>
 * open P | cmd P command... (run as the player, with the player's own permission) | state P | click P ROLE [n] [button] [TYPE] | pick P item | typesearch P text | give P item count
 * inv P | close P | craft P item|- x9  (a real crafting table: result, then taken)
 * fall P blocks cheat  (a client falling that far onto the block under P; cheat = always "on the ground")
 * </pre>
 */
final class SelfTest extends CommandBase {
    static boolean enabled() {
        return System.getProperty("useleess.carpet.selftest") != null;
    }

    @Override public String getName() {
        return "uct";
    }

    @Override public String getUsage(ICommandSender sender) {
        return "/uct <action> <player> ...";
    }

    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = getPlayer(server, sender, args[1]);
        String out;
        switch (args[0]) {
            case "open": Editor.open(player); out = "opened"; break;
            case "cmd": out = "result=" + server.getCommandManager().executeCommand(player, String.join(" ", Arrays.copyOfRange(args, 2, args.length))); break;
            case "state": out = state(player); break;
            case "click": out = click(player, args); break;
            case "pick": out = pick(player, args[2]); break;
            case "typesearch": ((SearchContainer) player.openContainer).updateItemName(String.join(" ", Arrays.copyOfRange(args, 2, args.length))); out = "typed"; break;
            case "give": out = give(player, args); break;
            case "inv": out = inventory(player); break;
            case "close": player.closeScreen(); out = "closed"; break;
            case "craft": out = craft(player, args); break;
            case "fall": out = fall(player, Integer.parseInt(args[2]), Boolean.parseBoolean(args[3])); break;
            default: out = "unknown action";
        }
        sender.sendMessage(new TextComponentString(ServerText.get("selftest.line", args[0], out)));
    }

    private static String show(ItemStack s) {
        return s.isEmpty() ? "-" : Item.REGISTRY.getNameForObject(s.getItem()) + "/" + s.getMetadata() + "x" + s.getCount();
    }

    private static String state(EntityPlayerMP player) {
        EditorSession s = Editor.get(player);
        if (s == null) return "none";
        StringBuilder grid = new StringBuilder();
        for (int i = 0; i < 9; i++) grid.append(show(s.grid(i))).append(i < 8 ? "," : "");
        return "mode=" + s.mode() + " ready=" + s.ready() + " searchready=" + s.searchReady() + " closed=" + s.closed()
                + " category=" + s.category().key + " page=" + s.pickerPage() + " search=[" + s.searchText() + "] editing=" + s.editing()
                + " anvil=[" + s.anvilName() + "] cost=" + s.anvilCost() + " anyorder=" + s.anyOrder() + " title=[" + s.title() + "] grid=" + grid + " output=" + show(s.output())
                + " selected=" + show(s.selected()) + " picker0=" + show(s.pickerStack(0)) + " list0=" + show(s.menuStack(0))
                + " recipes=" + RecipeStore.current.names();
    }

    private static String click(EntityPlayerMP player, String[] a) {
        int n = a.length > 3 ? Integer.parseInt(a[3]) : 0;
        int button = a.length > 4 ? Integer.parseInt(a[4]) : 0;
        ClickType type = a.length > 5 ? ClickType.valueOf(a[5]) : ClickType.PICKUP;
        int slot;
        switch (a[2]) {
            case "OUTSIDE": slot = -999; break;
            case "ANVIL_OUT": slot = 2; break;
            case "SLOT": slot = n; break;
            default: slot = Layout.slot(Layout.Role.valueOf(a[2]), n);
        }
        player.openContainer.slotClick(slot, button, type, player);
        return "slot=" + slot;
    }

    /** Selects an item in the picker by clicking: category button until its category, next page until its page. */
    private static String pick(EntityPlayerMP player, String item) {
        EditorSession s = Editor.get(player);
        ItemStack want = new ItemStack(Item.REGISTRY.getObject(new ResourceLocation(item)));
        Category target = Catalogue.categoryOf(want);
        while (s.searchText().isEmpty() && s.category() != target) player.openContainer.slotClick(Layout.slot(Layout.Role.CATEGORY, 0), 0, ClickType.PICKUP, player);
        List<Catalogue.Entry> list = Catalogue.filter(target, s.searchText());
        for (int i = 0; i < list.size(); i++) {
            if (!ItemCodec.stuff(list.get(i).stack).equals(ItemCodec.stuff(want))) continue;
            while (s.pickerPage() < i / Layout.PICKER) player.openContainer.slotClick(Layout.slot(Layout.Role.NEXT, 0), 0, ClickType.PICKUP, player);
            while (s.pickerPage() > i / Layout.PICKER) player.openContainer.slotClick(Layout.slot(Layout.Role.PREV, 0), 0, ClickType.PICKUP, player);
            player.openContainer.slotClick(Layout.slot(Layout.Role.PICKER, i % Layout.PICKER), 0, ClickType.PICKUP, player);
            return "picked " + show(s.selected());
        }
        return "not in the picker";
    }

    private static String give(EntityPlayerMP player, String[] a) {
        Item item = Item.REGISTRY.getObject(new ResourceLocation(a[2]));
        player.inventory.addItemStackToInventory(new ItemStack(item, Integer.parseInt(a[3])));
        return "given";
    }

    /** Everything the player really holds, sorted by slot, plus the cursor. */
    private static String inventory(EntityPlayerMP player) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            if (!player.inventory.getStackInSlot(i).isEmpty()) parts.add(i + ":" + show(player.inventory.getStackInSlot(i)));
        }
        return parts + " cursor=" + show(player.inventory.getItemStack());
    }

    /**
     * A client's movement reports for a fall of {@code blocks} blocks (one per block) onto the block P stands on, through
     * the real {@code handleFalling}; a cheat claims "on the ground" all the way. Full health first; answers the damage.
     */
    private static String fall(EntityPlayerMP player, int blocks, boolean cheat) {
        player.setHealth(player.getMaxHealth());
        player.hurtResistantTime = 0;
        player.fallDistance = 0;
        double ground = player.posY;
        for (int i = blocks - 1; i >= 0; i--) {
            player.setPosition(player.posX, ground + i, player.posZ);
            player.handleFalling(-1, cheat || i == 0);
        }
        return "damage=" + (player.getMaxHealth() - player.getHealth());
    }

    private static String craft(EntityPlayerMP player, String[] a) {
        player.displayGui(new BlockWorkbench.InterfaceCraftingTable(player.world, BlockPos.ORIGIN));
        Container table = player.openContainer;
        for (int i = 0; i < 9; i++) {
            String name = a[2 + i];
            table.getSlot(1 + i).putStack(name.equals("-") ? ItemStack.EMPTY : new ItemStack(Item.REGISTRY.getObject(new ResourceLocation(name.toLowerCase(Locale.ROOT)))));
        }
        String result = show(table.getSlot(0).getStack());
        String took = show(table.slotClick(0, 0, ClickType.PICKUP, player));
        player.inventory.setItemStack(ItemStack.EMPTY);
        player.closeScreen();
        return "result=" + result + " took=" + took;
    }
}
