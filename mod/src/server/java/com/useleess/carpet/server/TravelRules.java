package com.useleess.carpet.server;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Two rules about travelling between worlds: {@code creativeNoClipNoPortals} (UCC #7: a player flying in creative is not
 * taken by nether portals, end portals or end gateways) and {@code obsidianPlatformNoRegen} (#15: a player arriving in
 * the End gets no new obsidian platform). A failure switches that rule's change off (Minecraft's own behaviour), logged
 * and told to ops once.
 */
final class TravelRules {
    static final String NO_PORTALS = "creativeNoClipNoPortals", NO_PLATFORM = "obsidianPlatformNoRegen";
    private static final BooleanSupplier noPortals = Rule.named(NO_PORTALS), noPlatform = Rule.named(NO_PLATFORM);
    private static final Set<String> BROKEN = new HashSet<>();

    private TravelRules() {}

    /** True = this entity is a creative player flying while the rule is on, so the portal leaves it alone. */
    static boolean skipsPortal(Entity entity) {
        return guarded(NO_PORTALS, () -> noPortals.getAsBoolean() && entity instanceof EntityPlayer
                && ((EntityPlayer) entity).isCreative() && ((EntityPlayer) entity).capabilities.isFlying);
    }

    /**
     * Teleporter.placeInPortal for an arrival in the End while the rule is on: the entity gets the spot Minecraft's own
     * code gives it, without the obsidian placed under it and the air cleared around it. True = placed.
     */
    static boolean placedWithoutPlatform(Entity entity) {
        return guarded(NO_PLATFORM, () -> {
            if (entity.dimension != 1 || !noPlatform.getAsBoolean()) return false;
            int x = MathHelper.floor(entity.posX), y = MathHelper.floor(entity.posY) - 1, z = MathHelper.floor(entity.posZ);
            entity.setLocationAndAngles(x, y, z, entity.rotationYaw, 0.0F);
            entity.motionX = 0.0D;
            entity.motionY = 0.0D;
            entity.motionZ = 0.0D;
            return true;
        });
    }

    private static boolean guarded(String rule, BooleanSupplier check) {
        if (BROKEN.contains(rule)) return false;
        try {
            return check.getAsBoolean();
        } catch (RuntimeException | LinkageError e) {
            BROKEN.add(rule);
            Guard.LOG.error(ServerText.get("log.travel_rule_failed", rule), e);
            Guard.notice(ServerText.get("travel.rule_failed", rule, String.valueOf(e)));
            return false;
        }
    }
}
