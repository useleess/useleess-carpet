package com.useleess.carpet.server;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** #9: the picker fills the 6 chest rows, the grid and the buttons sit in the 36 slots below. */
class LayoutTest {
    private static final Set<Layout.Role> BUTTONS = EnumSet.of(Layout.Role.STATION, Layout.Role.OUTPUT, Layout.Role.SAVE,
            Layout.Role.CLEAR, Layout.Role.ORDER, Layout.Role.ALL, Layout.Role.SEARCH, Layout.Role.EXIT, Layout.Role.CATEGORY,
            Layout.Role.PREV, Layout.Role.NEXT);

    private static boolean below(int slot) {
        return slot >= Layout.TOP && slot < Layout.TOP + Layout.BOTTOM;
    }

    @Test void thePickerFillsTheChestRows() {
        assertEquals(54, Layout.TOP);
        for (int n = 0; n < Layout.PICKER; n++) {
            int slot = Layout.slot(Layout.Role.PICKER, n);
            assertTrue(slot < Layout.TOP);
            assertEquals(Layout.Role.PICKER, Layout.editorRole(slot));
        }
    }

    @Test void gridAndButtonsSitBelowEachInItsOwnSlot() {
        Set<Integer> used = new HashSet<>();
        for (int cell = 0; cell < 9; cell++) {
            int slot = Layout.slot(Layout.Role.GRID, cell);
            assertTrue(below(slot), "grid cell " + cell);
            assertEquals(Layout.Role.GRID, Layout.editorRole(slot));
            assertEquals(cell, Layout.gridIndex(slot));
            assertTrue(used.add(slot));
        }
        for (Layout.Role role : BUTTONS) {
            int slot = Layout.slot(role, 0);
            assertTrue(below(slot), role.name());
            assertEquals(role, Layout.editorRole(slot));
            assertTrue(used.add(slot), role + " shares a slot");
        }
    }

    @Test void theListFillsTheChestRowsWithItsButtonsBelow() {
        for (int n = 0; n < Layout.LIST_PAGE; n++) assertEquals(Layout.Role.LIST, Layout.listRole(Layout.slot(Layout.Role.LIST, n)));
        for (Layout.Role role : EnumSet.of(Layout.Role.BACK, Layout.Role.PREV, Layout.Role.NEXT)) {
            assertTrue(below(Layout.slot(role, 0)));
            assertEquals(role, Layout.listRole(Layout.slot(role, 0)));
        }
    }

    @Test void slotsOutsideTheWindowDoNothing() {
        for (int slot : new int[] {-999, -1, Layout.TOP + Layout.BOTTOM}) {
            assertEquals(Layout.Role.NONE, Layout.editorRole(slot));
            assertEquals(Layout.Role.NONE, Layout.listRole(slot));
        }
    }
}
