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

        public Rule(String name, boolean byDefault, String desc, String... categories) {
            this.name = name;
            this.byDefault = byDefault;
            this.desc = desc;
            this.categories = Collections.unmodifiableList(Arrays.asList(categories));
        }
    }

    public static final List<Rule> ALL = Collections.unmodifiableList(Arrays.asList(
            new Rule("endPortalNoTeleportPlayer", false,
                    "Players touching an end portal stay where they are; items, mobs and other entities still go through.",
                    "CREATIVE")));
}
