package com.tpaheremod;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects combat from the action bar (hotbar) message "Combat: Xs".
 * The server re-sends the message about once a second, so we count as "in combat"
 * while the message keeps arriving (or until it shows 0s).
 */
public final class TpaCombat {
    private static final Pattern COMBAT = Pattern.compile("Combat:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final int TIMEOUT_TICKS = 30; // no message for 1.5s -> combat is over

    private static int tickCounter = 0;
    private static int lastSeen = Integer.MIN_VALUE / 2;
    private static int seconds = 0;

    private TpaCombat() {}

    public static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) return;
            Matcher m = COMBAT.matcher(message.getString());
            if (!m.find()) return;
            try {
                seconds = Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return;
            }
            lastSeen = tickCounter;
        });
    }

    public static void tick(MinecraftClient client) {
        tickCounter++;
        if (client.player == null) lastSeen = Integer.MIN_VALUE / 2;
    }

    public static boolean inCombat() {
        return seconds > 0 && tickCounter - lastSeen < TIMEOUT_TICKS;
    }

    public static int secondsLeft() {
        return inCombat() ? seconds : 0;
    }
}
