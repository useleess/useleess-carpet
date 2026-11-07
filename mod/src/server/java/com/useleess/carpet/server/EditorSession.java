package com.useleess.carpet.server;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.play.server.SPacketOpenWindow;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.network.play.server.SPacketWindowProperty;
import net.minecraft.util.text.TextComponentString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One op's open editor: all state (grid, result, picker category / page / search, loaded recipe) and every click.
 * The window shows this state through two fake inventories (27 menu cells, 36 picker cells), so the real inventory is
 * never part of it; every item shown is a copy that exists only for display.
 */
final class EditorSession {
    enum Mode { EDITOR, LIST, SEARCH }

    private final EntityPlayerMP player;
    private final InventoryBasic menu = new InventoryBasic(ServerText.get("editor.title"), true, Layout.MENU);
    private final InventoryBasic picker = new InventoryBasic("picker", false, Layout.PICKER);
    private Mode mode = Mode.EDITOR;
    private boolean switching, closed, titleIsMessage;
    private Container container;
    private SearchContainer search;

    private final ItemStack[] grid = new ItemStack[9];
    private ItemStack output = ItemStack.EMPTY;
    private ItemStack selected = ItemStack.EMPTY;
    private boolean anyOrder;
    private Station station = Station.CRAFTING;
    private Category category = Category.BUILDING;
    private int pickerPage, listPage;
    private String searchText = "", searchDraft = "", editing, pendingDelete;

    EditorSession(EntityPlayerMP player) {
        this.player = player;
        Arrays.fill(grid, ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------ state for the tests

    Mode mode() { return mode; }
    boolean closed() { return closed; }
    boolean ready() { return !closed && container instanceof EditorContainer && player.openContainer == container; }
    boolean searchReady() { return !closed && mode == Mode.SEARCH && search != null && player.openContainer == search; }
    ItemStack grid(int i) { return grid[i]; }
    ItemStack output() { return output; }
    ItemStack selected() { return selected; }
    ItemStack menuStack(int slot) { return menu.getStackInSlot(slot); }
    ItemStack pickerStack(int cell) { return picker.getStackInSlot(cell); }
    Category category() { return category; }
    int pickerPage() { return pickerPage; }
    String searchText() { return searchText; }
    String editing() { return editing; }
    boolean anyOrder() { return anyOrder; }
    Station station() { return station; }
    String title() { return menu.getName(); }

    /** Sets the search text as typing it into the anvil's rename field does. */
    void searchTyped(String name) {
        searchDraft = name;
        noRepairCost();
    }

    /** What the search anvil's left item is called (the rename field shows it), "-" outside the search. */
    String anvilName() {
        if (mode != Mode.SEARCH || search == null) return "-";
        ItemStack left = search.getSlot(0).getStack();
        return left.isEmpty() ? "-" : left.getDisplayName();
    }

    int anvilCost() {
        return search == null ? -1 : search.maximumCost;
    }

    // ------------------------------------------------------------------ windows

    /** Opens (or re-opens) the chest window with the editor in it. */
    void showEditor() {
        menu.setCustomName(ServerText.get("editor.title"));
        titleIsMessage = false;
        mode = Mode.EDITOR;
        switching = true;
        try {
            player.displayGUIChest(menu);
            EditorContainer mine = new EditorContainer(this, menu, picker);
            mine.windowId = player.openContainer.windowId;
            player.openContainer = mine;
            container = mine;
            mine.addListener(player);
        } finally {
            switching = false;
        }
        render();
        resync();
    }

    private void openSearch() {
        mode = Mode.SEARCH;
        searchDraft = searchText;
        switching = true;
        try {
            player.displayGui(new SearchWindow(this));
        } finally {
            switching = false;
        }
        search.getSlot(0).putStack(named(new ItemStack(Items.PAPER), searchText, ServerText.get("editor.search_placeholder")));
        search.getSlot(2).putStack(named(new ItemStack(Items.PAPER), ServerText.get("editor.search_apply")));
        resync();
    }

    Container createSearchContainer(InventoryPlayer inventory, EntityPlayer who) {
        search = new SearchContainer(this, inventory, who);
        container = search;
        return search;
    }

    void searchClick(int slot) {
        if (closed || mode != Mode.SEARCH) return;
        if (slot == 2) applySearchAndReturn();
        else resync();
    }

    void searchClosed(SearchContainer closedWindow) {
        if (switching || closed || closedWindow != search || mode != Mode.SEARCH) return;
        applySearchAndReturn();
    }

    private void applySearchAndReturn() {
        searchText = searchDraft.trim();
        pickerPage = 0;
        mode = Mode.EDITOR;
        Hooks.nextTick(() -> {
            if (!closed) showEditor();
        });
    }

    void windowClosed(EditorContainer closedWindow) {
        if (switching || closed || closedWindow != container) return;
        finish();
    }

    /** Ends the editor and gives the inventory back; {@code closeWindow} also closes the window on the client. */
    void end(boolean closeWindow) {
        if (closed) return;
        closed = true;
        if (closeWindow && (player.openContainer == container || player.openContainer == search)) player.closeScreen();
        finish();
    }

    private void finish() {
        closed = true;
        Editor.forget(player);
        try {
            Vault.restore(player);
        } catch (IOException | RuntimeException e) {
            Guard.LOG.error(ServerText.get("log.restore_failed", player.getName()), e);
            Guard.notice(ServerText.get("editor.restore_failed", player.getName(), String.valueOf(e)));
        }
    }

    // ------------------------------------------------------------------ clicks

    void click(int slot, int button, ClickType type) {
        if (closed) return;
        if (titleIsMessage) setTitleText(ServerText.get("editor.title"));
        boolean delete = mode == Mode.LIST && type == ClickType.QUICK_MOVE;
        if ((type == ClickType.PICKUP || delete) && (button == 0 || button == 1)) {
            if (mode == Mode.EDITOR) editorClick(slot, button);
            else if (mode == Mode.LIST) listClick(slot, delete);
        }
        if (closed || mode == Mode.SEARCH) return;
        render();
        resync();
    }

    private void editorClick(int slot, int button) {
        switch (Layout.editorRole(slot)) {
            case PICKER: {
                List<Catalogue.Entry> list = Catalogue.filter(category, searchText);
                int at = pickerPage * Layout.PICKER + Layout.pickerIndex(slot);
                selected = button == 0 && at < list.size() ? list.get(at).stack.copy() : ItemStack.EMPTY;
                break;
            }
            case GRID:
                gridClick(Layout.gridIndex(slot), button);
                break;
            case STATION:
                switchStation(button == 1 ? -1 : 1);
                break;
            case OUTPUT:
                changeOutput(button);
                break;
            case SAVE:
                save();
                break;
            case CLEAR:
                Arrays.fill(grid, ItemStack.EMPTY);
                output = ItemStack.EMPTY;
                editing = null;
                break;
            case ORDER:
                anyOrder = station == Station.CRAFTING && !anyOrder;
                break;
            case ALL:
                mode = Mode.LIST;
                listPage = 0;
                pendingDelete = null;
                break;
            case SEARCH:
                openSearch();
                break;
            case EXIT:
                end(true);
                break;
            case CATEGORY:
                category = category.next(button == 1 ? -1 : 1);
                pickerPage = 0;
                break;
            case PREV:
                pickerPage--;
                break;
            case NEXT:
                pickerPage++;
                break;
            default:
                break;
        }
    }

    /** Left click with an item selected: puts it in the cell; right click: empties it. Cells the station does not use stay empty. */
    private void gridClick(int cell, int button) {
        if (!station.uses(cell)) return;
        if (button == 1) {
            grid[cell] = ItemStack.EMPTY;
        } else if (!selected.isEmpty()) {
            if (station.allows(cell, ItemCodec.stuff(selected).item)) grid[cell] = selected.copy();
            else say(ServerText.get("editor.bottle_only"), "editor.title.bottle_only");
        }
    }

    /** The next (or previous) recipe type: what its cells hold stays, the other cells are emptied; a loaded recipe is let go. */
    private void switchStation(int by) {
        station = station.next(by);
        for (int i = 0; i < 9; i++) {
            if (!grid[i].isEmpty() && !(station.uses(i) && station.allows(i, ItemCodec.stuff(grid[i]).item))) grid[i] = ItemStack.EMPTY;
        }
        if (station != Station.CRAFTING) anyOrder = false;
        editing = null;
    }

    /** Left click with an item selected: sets the result, or one more of it; right click: one less. */
    private void changeOutput(int button) {
        if (button == 1) {
            if (!output.isEmpty()) output.shrink(1);
            if (output.getCount() <= 0) output = ItemStack.EMPTY;
        } else if (!selected.isEmpty()) {
            if (!output.isEmpty() && ItemCodec.stuff(output).equals(ItemCodec.stuff(selected))) {
                output.setCount(Math.min(output.getMaxStackSize(), output.getCount() + 1));
            } else {
                output = selected.copy();
                output.setCount(1);
            }
        }
    }

    private void listClick(int slot, boolean delete) {
        RecipeStore store = RecipeStore.current;
        switch (Layout.listRole(slot)) {
            case LIST: {
                List<String> names = new ArrayList<>(store.names());
                int at = listPage * Layout.LIST_PAGE + Layout.listIndex(slot);
                if (at >= names.size()) break;
                String name = names.get(at);
                if (!delete) {
                    pendingDelete = null;
                    load(store.get(name));
                } else if (name.equals(pendingDelete)) {
                    pendingDelete = null;
                    remove(store, name);
                } else {
                    pendingDelete = name;
                }
                break;
            }
            case BACK:
                mode = Mode.EDITOR;
                break;
            case PREV:
                pendingDelete = null;
                listPage--;
                break;
            case NEXT:
                pendingDelete = null;
                listPage++;
                break;
            default:
                break;
        }
    }

    private void load(RecipeData recipe) {
        try {
            ItemStack result = ItemCodec.stack(recipe.result);
            Stuff[] cells = recipe.toGrid();
            for (int i = 0; i < 9; i++) grid[i] = cells[i] == null ? ItemStack.EMPTY : ItemCodec.stack(cells[i]);
            output = result;
            station = Station.of(recipe.kind);
            anyOrder = recipe.kind == RecipeData.Kind.SHAPELESS;
            editing = recipe.name;
            mode = Mode.EDITOR;
            say(ServerText.get("editor.loaded", recipe.name), "editor.title.loaded", recipe.name);
        } catch (BadRecipe e) {
            say(ServerText.get("editor.load_failed", recipe.name, ServerText.get(e.key, e.args)), "editor.title.load_failed", recipe.name);
        }
    }

    private void remove(RecipeStore store, String name) {
        try {
            store.delete(name);
            if (name.equals(editing)) editing = null;
            say(ServerText.get("editor.deleted", name), "editor.title.deleted", name);
        } catch (IOException e) {
            Guard.LOG.error(ServerText.get("log.recipe_file_failed", name), e);
            say(ServerText.get("editor.delete_failed", name, String.valueOf(e.getMessage())), "editor.title.delete_failed", name);
        }
    }

    private void save() {
        RecipeStore store = RecipeStore.current;
        Stuff[] stuff = new Stuff[9];
        for (int i = 0; i < 9; i++) stuff[i] = ItemCodec.stuff(grid[i]);
        if (output.isEmpty() || !station.complete(stuff)) {
            say(ServerText.get("editor.save_empty"), "editor.title.save_empty");
            return;
        }
        String clash = clashWith(store, stuff);
        if (clash != null) {
            say(ServerText.get("editor.save_conflict", clash), "editor.title.save_conflict");
            return;
        }
        String name = editing != null ? editing : RecipeNames.unique(RecipeNames.of(ItemCodec.stuff(output).item), store.names());
        try {
            store.save(station.recipe(name, stuff, anyOrder, ItemCodec.product(output)));
            editing = name;
            say(ServerText.get("editor.saved", name), "editor.title.saved", name);
        } catch (IOException e) {
            Guard.LOG.error(ServerText.get("log.recipe_file_failed", name), e);
            say(ServerText.get("editor.save_failed", name, String.valueOf(e.getMessage())), "editor.title.save_failed", name);
        }
    }

    /** The name of another recipe (saved or vanilla) that already answers to this grid, or null. */
    private String clashWith(RecipeStore store, Stuff[] stuff) {
        if (station == Station.SMELTING) {
            RecipeData saved = store.findSmelting(stuff[station.cell(0)], editing);
            return saved != null ? saved.name : clashName(StationRecipes.vanillaSmelt(grid[station.cell(0)]));
        }
        if (station == Station.BREWING) {
            RecipeData saved = store.findBrewing(stuff[station.cell(0)], stuff[station.cell(1)], editing);
            return saved != null ? saved.name : clashName(StationRecipes.vanillaBrew(grid[station.cell(0)], grid[station.cell(1)]));
        }
        RecipeData saved = store.find(stuff, 3, 3, editing);
        if (saved != null) return saved.name;
        InventoryCrafting inv = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer who) {
                return true;
            }
        }, 3, 3);
        for (int i = 0; i < 9; i++) inv.setInventorySlotContents(i, grid[i].copy());
        for (IRecipe r : CraftingManager.REGISTRY) {
            if (!(r instanceof CustomRecipes) && r.matches(inv, player.world)) return String.valueOf(CraftingManager.REGISTRY.getNameForObject(r));
        }
        return null;
    }

    /** A vanilla result as a clash name ("minecraft:iron_ingot/0"), null for none. */
    private static String clashName(ItemStack result) {
        return result.isEmpty() ? null : String.valueOf(ItemCodec.stuff(result));
    }

    // ------------------------------------------------------------------ what the window shows

    /** The full text goes to the chat, a short one (lang key {@code titleKey}, at most {@link TitleText#MAX} characters) to the window title. */
    private void say(String text, String titleKey, Object... titleArgs) {
        player.sendMessage(new TextComponentString(text));
        setTitleText(TitleText.fit(ServerText.get(titleKey, titleArgs)));
        titleIsMessage = true;
    }

    private void setTitleText(String text) {
        titleIsMessage = false;
        menu.setCustomName(text);
        if (container instanceof EditorContainer) {
            player.connection.sendPacket(new SPacketOpenWindow(container.windowId, "minecraft:container", menu.getDisplayName(), Layout.MENU));
        }
    }

    private void render() {
        menu.clear();
        picker.clear();
        if (mode == Mode.LIST) renderList();
        else renderEditor();
    }

    private void renderEditor() {
        for (int i = 0; i < 9; i++) {
            menu.setInventorySlotContents(Layout.slot(Layout.Role.GRID, i), station.uses(i) ? grid[i].copy() : named(new ItemStack(Blocks.STAINED_GLASS_PANE, 1, 7), " "));
        }
        button(Layout.Role.STATION, stationIcon(), "editor.button.station", ServerText.get("station." + station.key));
        menu.setInventorySlotContents(Layout.slot(Layout.Role.OUTPUT, 0), output.copy());
        button(Layout.Role.SAVE, Items.EMERALD, "editor.button.save");
        button(Layout.Role.CLEAR, Items.BUCKET, "editor.button.clear");
        if (station == Station.CRAFTING) button(Layout.Role.ORDER, Items.REPEATER, "editor.button.order", ServerText.get(anyOrder ? "editor.on" : "editor.off"));
        button(Layout.Role.ALL, Items.BOOK, "editor.button.all");
        button(Layout.Role.SEARCH, Items.SIGN, "editor.button.search", searchText);
        button(Layout.Role.EXIT, Item.getItemFromBlock(Blocks.BARRIER), "editor.button.exit");
        List<Catalogue.Entry> list = Catalogue.filter(category, searchText);
        int pages = Math.max(1, (list.size() + Layout.PICKER - 1) / Layout.PICKER);
        pickerPage = Math.max(0, Math.min(pickerPage, pages - 1));
        ItemStack cat = named(category.icon(), ServerText.get("editor.button.category", ServerText.get("category." + category.key)),
                ServerText.get("editor.button.category.page", pickerPage + 1, pages),
                searchText.isEmpty() ? "" : ServerText.get("editor.button.category.search", searchText),
                searchText.isEmpty() ? "" : ServerText.get("editor.button.category.search_all"),
                searchText.isEmpty() || !list.isEmpty() ? "" : ServerText.get("editor.button.category.search_none"),
                ServerText.get("editor.button.category.hint"));
        menu.setInventorySlotContents(Layout.slot(Layout.Role.CATEGORY, 0), cat);
        button(Layout.Role.PREV, Items.PAPER, "editor.button.prev");
        button(Layout.Role.NEXT, Items.PAPER, "editor.button.next");
        for (int i = 0; i < Layout.PICKER; i++) {
            int at = pickerPage * Layout.PICKER + i;
            picker.setInventorySlotContents(i, at < list.size() ? list.get(at).stack.copy() : ItemStack.EMPTY);
        }
    }

    private void renderList() {
        List<String> names = new ArrayList<>(RecipeStore.current.names());
        int pages = Math.max(1, (names.size() + Layout.LIST_PAGE - 1) / Layout.LIST_PAGE);
        listPage = Math.max(0, Math.min(listPage, pages - 1));
        for (int i = 0; i < Layout.LIST_PAGE; i++) {
            int at = listPage * Layout.LIST_PAGE + i;
            if (at >= names.size()) break;
            RecipeData r = RecipeStore.current.get(names.get(at));
            ItemStack icon;
            try {
                icon = ItemCodec.stack(r.result);
            } catch (BadRecipe e) {
                icon = new ItemStack(Item.getItemFromBlock(Blocks.BARRIER));
            }
            List<String> lore = new ArrayList<>();
            lore.add(listLine(r));
            for (int row = 0; r.kind == RecipeData.Kind.SHAPED && row < r.height; row++) {
                StringBuilder line = new StringBuilder("§7");
                for (int c = 0; c < r.width; c++) {
                    Stuff s = r.cell(row * r.width + c);
                    line.append(c == 0 ? "" : " | ").append(s == null ? "-" : s.item.substring(s.item.indexOf(':') + 1));
                }
                lore.add(line.toString());
            }
            if (r.name.equals(pendingDelete)) lore.add(ServerText.get("list.confirm_delete"));
            menu.setInventorySlotContents(Layout.slot(Layout.Role.LIST, i), named(icon, ServerText.get("list.entry", r.name), lore.toArray(new String[0])));
        }
        button(Layout.Role.BACK, Items.ARROW, "editor.button.back");
        button(Layout.Role.PREV, Items.PAPER, "editor.button.prev");
        button(Layout.Role.NEXT, Items.PAPER, "editor.button.next");
    }

    private static String listLine(RecipeData r) {
        switch (r.kind) {
            case SHAPED: return ServerText.get("list.shaped", r.width, r.height);
            case SHAPELESS: return ServerText.get("list.shapeless", r.ingredients().size());
            case SMELTING: return ServerText.get("list.smelting", r.cell(0));
            default: return ServerText.get("list.brewing", r.cell(0), r.cell(1));
        }
    }

    private Item stationIcon() {
        switch (station) {
            case SMELTING: return Item.getItemFromBlock(Blocks.FURNACE);
            case BREWING: return Items.BREWING_STAND;
            default: return Item.getItemFromBlock(Blocks.CRAFTING_TABLE);
        }
    }

    private void button(Layout.Role role, Item item, String key, Object... args) {
        String[] lines = ServerText.get(key + ".lore", args).split("\n");
        menu.setInventorySlotContents(Layout.slot(role, 0), named(new ItemStack(item), ServerText.get(key, args), lines));
    }

    private static ItemStack named(ItemStack stack, String name, String... lore) {
        stack.setStackDisplayName(name);
        NBTTagList lines = new NBTTagList();
        for (String l : lore) if (!l.isEmpty()) lines.appendTag(new NBTTagString(l));
        NBTTagCompound display = stack.getOrCreateSubCompound("display");
        display.setTag("Lore", lines);
        return stack;
    }

    /** Sends the whole window and the selected item (shown on the cursor) to the client again. */
    private void resync() {
        container.detectAndSendChanges();
        player.sendAllContents(container, container.getInventory());
        player.connection.sendPacket(new SPacketSetSlot(-1, -1, container == search ? ItemStack.EMPTY : selected.copy()));
        if (container == search) noRepairCost();
    }

    /** The client works out an anvil's level cost itself; property 0 of the window overrides it, so no cost line shows. */
    private void noRepairCost() {
        if (search != null && !closed) player.connection.sendPacket(new SPacketWindowProperty(search.windowId, 0, 0));
    }
}
