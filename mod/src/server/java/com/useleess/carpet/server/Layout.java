package com.useleess.carpet.server;

/**
 * Where everything sits in the editor window (#9): a 6-row chest window whose 54 chest slots show the item picker (or
 * the saved recipes); the 36 slots below, where the player's inventory normally is, hold the grid and the buttons
 * (window slots 54..89: three rows, then the hotbar row). Clicks and tests speak in {@link Role}s, never in fixed numbers.
 */
final class Layout {
    enum Role { GRID, STATION, OUTPUT, SAVE, CLEAR, ORDER, ALL, SEARCH, EXIT, CATEGORY, PREV, NEXT, PICKER, LIST, BACK, NONE }

    /** The chest rows: the picker in the editor, the saved recipes in the list. */
    static final int TOP = 54;
    /** The rows below: grid and buttons. */
    static final int BOTTOM = 36;
    static final int PICKER = TOP;
    static final int LIST_PAGE = TOP;
    private static final int SAVE = TOP + 6, CLEAR = TOP + 7, ORDER = TOP + 8, STATION = TOP + 12, OUTPUT = TOP + 13,
            ALL = TOP + 15, SEARCH = TOP + 16, EXIT = TOP + 17, PREV = TOP + 30, CATEGORY = TOP + 31, NEXT = TOP + 32, BACK = CATEGORY;

    private Layout() {}

    /** The window slot of a role; {@code n} counts grid cells 0..8, picker cells 0..53 and list entries 0..53. */
    static int slot(Role role, int n) {
        switch (role) {
            case GRID: return TOP + n / 3 * 9 + n % 3;
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
            case PICKER: return n;
            case LIST: return n;
            case BACK: return BACK;
            default: throw new IllegalArgumentException(role.name());
        }
    }

    /** The role of a window slot in the editor. */
    static Role editorRole(int slot) {
        if (slot < 0 || slot >= TOP + BOTTOM) return Role.NONE;
        if (slot < TOP) return Role.PICKER;
        if (slot < TOP + 27 && (slot - TOP) % 9 < 3) return Role.GRID;
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
        if (slot >= 0 && slot < TOP) return Role.LIST;
        switch (slot) {
            case BACK: return Role.BACK;
            case PREV: return Role.PREV;
            case NEXT: return Role.NEXT;
            default: return Role.NONE;
        }
    }

    static int gridIndex(int slot) {
        return (slot - TOP) / 9 * 3 + (slot - TOP) % 9;
    }
}
