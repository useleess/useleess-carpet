package com.useleess.carpet.server;

/**
 * Where everything sits in the editor window (a 27-slot chest window; the player's 36 inventory slots follow as
 * slots 27..62 and show the item picker). Clicks and tests speak in {@link Role}s, never in fixed numbers.
 */
final class Layout {
    enum Role { GRID, STATION, OUTPUT, SAVE, CLEAR, ORDER, ALL, SEARCH, EXIT, CATEGORY, PREV, NEXT, PICKER, LIST, BACK, NONE }

    static final int MENU = 27;
    static final int PICKER = 36;
    static final int LIST_PAGE = 18;
    private static final int STATION = 12, OUTPUT = 13, SAVE = 6, CLEAR = 7, ORDER = 8, ALL = 15, SEARCH = 16, EXIT = 17,
            CATEGORY = 24, PREV = 25, NEXT = 26, BACK = 8;

    private Layout() {}

    /** The window slot of a role; {@code n} counts grid cells 0..8, picker cells 0..35 and list entries 0..17. */
    static int slot(Role role, int n) {
        switch (role) {
            case GRID: return n / 3 * 9 + n % 3;
            case STATION: return STATION;
            case OUTPUT: return OUTPUT;
            case SAVE: return SAVE;
            case CLEAR: return CLEAR;
            case ORDER: return ORDER;
            case ALL: return ALL;
            case SEARCH: return SEARCH;
            case EXIT: return EXIT;
            case CATEGORY: return CATEGORY;
            case PREV: return PREV;
            case NEXT: return NEXT;
            case PICKER: return MENU + n;
            case LIST: return n / 6 * 9 + n % 6;
            case BACK: return BACK;
            default: throw new IllegalArgumentException(role.name());
        }
    }

    /** The role of a window slot in the editor. */
    static Role editorRole(int slot) {
        if (slot >= MENU && slot < MENU + PICKER) return Role.PICKER;
        if (slot < 0 || slot >= MENU) return Role.NONE;
        if (slot % 9 < 3) return Role.GRID;
        switch (slot) {
            case STATION: return Role.STATION;
            case OUTPUT: return Role.OUTPUT;
            case SAVE: return Role.SAVE;
            case CLEAR: return Role.CLEAR;
            case ORDER: return Role.ORDER;
            case ALL: return Role.ALL;
            case SEARCH: return Role.SEARCH;
            case EXIT: return Role.EXIT;
            case CATEGORY: return Role.CATEGORY;
            case PREV: return Role.PREV;
            case NEXT: return Role.NEXT;
            default: return Role.NONE;
        }
    }

    /** The role of a window slot in the list of saved recipes. */
    static Role listRole(int slot) {
        if (slot < 0 || slot >= MENU) return Role.NONE;
        if (slot % 9 < 6) return Role.LIST;
        switch (slot) {
            case BACK: return Role.BACK;
            case PREV: return Role.PREV;
            case NEXT: return Role.NEXT;
            default: return Role.NONE;
        }
    }

    static int gridIndex(int slot) {
        return slot / 9 * 3 + slot % 9;
    }

    static int listIndex(int slot) {
        return slot / 9 * 6 + slot % 9;
    }

    static int pickerIndex(int slot) {
        return slot - MENU;
    }
}
