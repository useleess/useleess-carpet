package com.useleess.carpet.server;

import io.netty.buffer.Unpooled;
import net.minecraft.block.BlockWorkbench;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerBrewingStand;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketConfirmTeleport;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /uct}: drives the editor of a (fake) player from the console for the in-game tests; registered only when the
 * server runs with {@code -Duseleess.carpet.selftest=1}. Clicks go through the player's real open container by slot
 * ROLE (see {@link Layout}), answers are printed as "UCT ..." lines:
 * <pre>
 * open P | cmd P command... (run as the player, with the player's own permission) | state P | click P ROLE [n] [button] [TYPE] | pick P item | typesearch P text | give P item count
 * inv P | close P | craft P item|- x9  (a real crafting table: result, then taken)
 * smelt P item  (a real furnace above P with coal: whether it takes the item, then what comes out)
 * brew P ingredient bottle  (a real brewing stand above P with blaze powder: whether it takes the ingredient, then the bottle)
 * (an item may name its potion type: minecraft:potion@minecraft:water)
 * fall P blocks cheat  (a client falling that far onto the block under P; cheat = always "on the ground")
 * shulker P found asked count  (LU's request, as its client writes it, for item asked from the box slot holding item found)
 * place P x y z item state  (LU's request for that state, then a real click with item on top of the block under x y z; answers the block there)
 * caps P  (the capabilities bytes LU is told)
 * </pre>
 */
final class SelfTest extends CommandBase {
    private static final Set<UUID> TELEPORT_CONFIRMED = new HashSet<>();

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
            case "smelt": out = smelt(player, stack(args[2])); break;
            case "brew": out = brew(player, stack(args[2]), stack(args[3])); break;
            case "fall": out = fall(player, Integer.parseInt(args[2]), Boolean.parseBoolean(args[3])); break;
            case "shulker": out = shulker(player, args); break;
            case "place": out = place(player, args); break;
            case "recipebook": out = recipeBook(player, args[2]); break;
            case "caps": out = Arrays.toString(LuServer.capabilities()); break;
            default: out = "unknown action";
        }
        sender.sendMessage(new TextComponentString(ServerText.get("selftest.line", args[0], out)));
    }

    private static String show(ItemStack s) {
        if (s.isEmpty()) return "-";
        String potion = ItemCodec.stuff(s).potion;
        return Item.REGISTRY.getNameForObject(s.getItem()) + "/" + s.getMetadata() + "x" + s.getCount() + (potion == null ? "" : "~" + potion);
    }

    /** "minecraft:potion@minecraft:water" = that item with that potion type; one of it. */
    private static ItemStack stack(String spec) {
        String[] parts = spec.split("@");
        return ItemCodec.stack(new Stuff(parts[0], 0, parts.length > 1 ? parts[1] : null));
    }

    private static String state(EntityPlayerMP player) {
        EditorSession s = Editor.get(player);
        if (s == null) return "none";
        StringBuilder grid = new StringBuilder();
        for (int i = 0; i < 9; i++) grid.append(show(s.grid(i))).append(i < 8 ? "," : "");
        return "mode=" + s.mode() + " ready=" + s.ready() + " searchready=" + s.searchReady() + " closed=" + s.closed()
                + " category=" + s.category().key + " page=" + s.pickerPage() + " search=[" + s.searchText() + "] editing=" + s.editing()
                + " anvil=[" + s.anvilName() + "] cost=" + s.anvilCost() + " anyorder=" + s.anyOrder() + " station=" + s.station() + " title=[" + s.title() + "] grid=" + grid + " output=" + show(s.output())
                + " selected=" + show(s.selected()) + " picker0=" + show(s.shown(Layout.slot(Layout.Role.PICKER, 0))) + " picker53=" + show(s.shown(Layout.slot(Layout.Role.PICKER, Layout.PICKER - 1)))
                + " list0=" + show(s.shown(Layout.slot(Layout.Role.LIST, 0))) + " window=" + player.openContainer.inventorySlots.size()
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
        ItemStack want = stack(item);
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
     * Litematica Upgraded's shulker request (message 5, laid out the way LU's client writes it) through the real
     * processCustomPayload: count of item a[3] from the box slot that holds item a[2]. Answers how many arrived.
     */
    private static String shulker(EntityPlayerMP player, String[] a) {
        Item found = Item.REGISTRY.getObject(new ResourceLocation(a[2]));
        Item asked = Item.REGISTRY.getObject(new ResourceLocation(a[3]));
        for (int slot = 0; slot < player.inventory.mainInventory.size(); slot++) {
            NBTTagCompound tag = player.inventory.mainInventory.get(slot).getSubCompound("BlockEntityTag");
            if (tag == null) continue;
            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            ItemStackHelper.loadAllItems(tag, items);
            for (int inner = 0; inner < items.size(); inner++) {
                if (items.get(inner).getItem() != found) continue;
                int before = loose(player, asked);
                PacketBuffer request = luMessage();
                request.writeByte(LuProtocol.SHULKER_TAKE).writeByte(slot).writeByte(inner);
                request.writeInt(Item.getIdFromItem(asked)).writeShort(0).writeByte(Integer.parseInt(a[4]));
                player.connection.processCustomPayload(arrived(new CPacketCustomPayload(), request));
                return "moved=" + (loose(player, asked) - before);
            }
        }
        return "no box holds " + a[2];
    }

    /** LU's placement request (message 1, as its client writes it) and a real click, both through the network handler. */
    private static String place(EntityPlayerMP player, String[] a) {
        BlockPos pos = new BlockPos(Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4]));
        confirmTeleports(player);
        player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Item.REGISTRY.getObject(new ResourceLocation(a[5]))));
        byte[] state = a[6].getBytes(StandardCharsets.UTF_8);
        PacketBuffer request = luMessage();
        request.writeByte(LuProtocol.PLACEMENT_INTENT).writeInt(pos.getX()).writeInt(pos.getY()).writeInt(pos.getZ());
        request.writeShort(state.length).writeBytes(state);
        player.connection.processCustomPayload(arrived(new CPacketCustomPayload(), request));
        PacketBuffer click = new PacketBuffer(Unpooled.buffer());
        click.writeBlockPos(pos.down()).writeEnumValue(EnumFacing.UP).writeEnumValue(EnumHand.MAIN_HAND);
        click.writeFloat(0.5f).writeFloat(1).writeFloat(0.5f);
        player.connection.processTryUseItemOnBlock(arrived(new CPacketPlayerTryUseItemOnBlock(), click));
        return "state=" + player.world.getBlockState(pos);
    }

    /**
     * What a real client answers after a teleport: a fake player never does, and the server ignores clicks until then.
     * Wrong numbers do nothing; the right one a second time would crash the handler, so once per player.
     */
    /** #12's crash state: a recipe unlocked for the player while it has no connection; prints whether it is unlocked. */
    private static String recipeBook(EntityPlayerMP player, String recipe) {
        IRecipe r = CraftingManager.getRecipe(new ResourceLocation(recipe));
        NetHandlerPlayServer connection = player.connection;
        player.connection = null;
        try {
            player.unlockRecipes(Collections.singletonList(r));
        } finally {
            player.connection = connection;
        }
        return "unlocked=" + player.getRecipeBook().isUnlocked(r);
    }

    private static void confirmTeleports(EntityPlayerMP player) {
        if (!TELEPORT_CONFIRMED.add(player.getUniqueID())) return;
        for (int id = 0; id < 4096; id++) {
            PacketBuffer confirm = new PacketBuffer(Unpooled.buffer());
            confirm.writeVarInt(id);
            player.connection.processConfirmTeleport(arrived(new CPacketConfirmTeleport(), confirm));
        }
    }

    private static PacketBuffer luMessage() {
        return new PacketBuffer(Unpooled.buffer()).writeString(LuProtocol.CHANNEL);
    }

    /** A packet as it arrives from the network (the packets' filled-in constructors are client-only). */
    private static <P extends Packet<?>> P arrived(P packet, PacketBuffer bytes) {
        try {
            packet.readPacketData(bytes);
            return packet;
        } catch (IOException e) {
            throw new IllegalStateException("unreadable packet", e);
        }
    }

    /** How many of the item lie loose in the main inventory. */
    private static int loose(EntityPlayerMP player, Item item) {
        int n = 0;
        for (ItemStack s : player.inventory.mainInventory) if (s.getItem() == item) n += s.getCount();
        return n;
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

    /** A furnace 3 blocks above P (taken away again): the item and coal in, ticked until something comes out. */
    private static String smelt(EntityPlayerMP player, ItemStack input) {
        BlockPos pos = new BlockPos(player).up(3);
        player.world.setBlockState(pos, Blocks.FURNACE.getDefaultState());
        TileEntityFurnace furnace = (TileEntityFurnace) player.world.getTileEntity(pos);
        boolean takes = !FurnaceRecipes.instance().getSmeltingResult(input).isEmpty();
        furnace.setInventorySlotContents(0, input);
        furnace.setInventorySlotContents(1, new ItemStack(Items.COAL));
        for (int t = 0; t < 600 && furnace.getStackInSlot(2).isEmpty(); t++) furnace.update();
        String out = "takes=" + takes + " result=" + show(furnace.getStackInSlot(2));
        furnace.clear();
        player.world.setBlockToAir(pos);
        return out;
    }

    /** A brewing stand 3 blocks above P (taken away again): ingredient, bottle and blaze powder in, ticked until it brewed. */
    private static String brew(EntityPlayerMP player, ItemStack ingredient, ItemStack bottle) {
        BlockPos pos = new BlockPos(player).up(3);
        player.world.setBlockState(pos, Blocks.BREWING_STAND.getDefaultState());
        TileEntityBrewingStand stand = (TileEntityBrewingStand) player.world.getTileEntity(pos);
        boolean takes = new ContainerBrewingStand(player.inventory, stand).getSlot(3).isItemValid(ingredient);
        stand.setInventorySlotContents(3, ingredient);
        stand.setInventorySlotContents(0, bottle.copy());
        stand.setInventorySlotContents(4, new ItemStack(Items.BLAZE_POWDER));
        for (int t = 0; t < 600 && ItemStack.areItemStacksEqual(stand.getStackInSlot(0), bottle); t++) stand.update();
        String out = "takes=" + takes + " result=" + show(stand.getStackInSlot(0)) + " ingredient_left=" + show(stand.getStackInSlot(3));
        stand.clear();
        player.world.setBlockToAir(pos);
        return out;
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
