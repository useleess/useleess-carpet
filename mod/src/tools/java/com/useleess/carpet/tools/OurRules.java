package com.useleess.carpet.tools;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Our own Carpet rules: one line each. {@link RulesMaker} turns every line into a {@code @Rule} field of
 * {@code carpet/CarpetSettings} (so {@code /carpet}, carpet.conf and CarpetSP see it like Carpet's own rules) plus
 * its hook from the per-version data. Descriptions are Carpet's own English text (Carpet has no translations).
 */
public final class OurRules {
    private OurRules() {}

    public static final class Rule {
        public final String name;
        public final boolean byDefault;
        public final String desc;
        /** {@code CarpetSettings$RuleCategory} constants. */
        public final List<String> categories;
        /** False = no hook into Minecraft's code: our own server code reads the rule (see {@link #plain}). */
        public final boolean hooked;

        public Rule(String name, boolean byDefault, String desc, String... categories) {
            this(true, name, byDefault, desc, categories);
        }

        /** A rule with no hook in the per-version data: only the {@code @Rule} field is added. */
        public static Rule plain(String name, boolean byDefault, String desc, String... categories) {
            return new Rule(false, name, byDefault, desc, categories);
        }

        private Rule(boolean hooked, String name, boolean byDefault, String desc, String... categories) {
            this.hooked = hooked;
            this.name = name;
            this.byDefault = byDefault;
            this.desc = desc;
            this.categories = Collections.unmodifiableList(Arrays.asList(categories));
        }
    }

    public static final List<Rule> ALL = Collections.unmodifiableList(Arrays.asList(
            new Rule("endPortalNoTeleportPlayer", false,
                    "Players touching an end portal stay where they are; items, mobs and other entities still go through.",
                    "CREATIVE"),
            Rule.plain("customRecipes", true,
                    "Ops can make their own crafting recipes with /customrecipe (saved per world); off = saved recipes do not match and the command is refused.",
                    "CREATIVE"),
            Rule.plain("redstoneScopeLogger", false,
                    "Redstone Scope records block changes for its tools (sub-tick, waveform, probes, rewind). Off = no recording, no extra work and no Redstone Scope log lines; its commands say it is switched off.",
                    "CREATIVE"),
            Rule.plain("antiNoFall", false,
                    "The server checks itself that a player is standing on something before it ends their fall, so no-fall cheats (a client always saying it is on the ground) still take fall damage.",
                    "SURVIVAL"),
            Rule.plain("litematicaShulkerRestock", false,
                    "Litematica Upgraded's printer and Easy Place may take the items they need out of shulker boxes in the player's inventory (the server moves them; vanilla cannot).",
                    "SURVIVAL")));
}
