package com.tpaheremod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

/**
 * Auto hit: while on, presses right click (use item) once every X ms (default 300).
 * Turning it on also restarts the last TPAHere queue if none is running.
 */
public final class TpaAutoHit {
    private static boolean enabled = false;
    private static long lastClick = 0;

    private TpaAutoHit() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle(MinecraftClient client) {
        enabled = !enabled;
        lastClick = 0;
        if (client.player != null) {
            client.player.sendMessage(Text.literal(enabled ? "TPAHere: auto hit ON" : "TPAHere: auto hit OFF"), true);
        }
        if (enabled && !TpaQueue.isRunning()) TpaQueue.restartLast(client);
    }

    public static void tick(MinecraftClient client) {
        if (!enabled) return;
        if (client.player == null || client.world == null || client.interactionManager == null) {
            enabled = false;
            return;
        }
        // Don't click while a screen or the confirm window is open.
        if (client.currentScreen != null) return;
        if (client.player.currentScreenHandler != client.player.playerScreenHandler) return;

        long now = System.currentTimeMillis();
        if (now - lastClick < TpaConfig.autoHitDelayMs) return;
        lastClick = now;
        client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
        client.player.swingHand(Hand.MAIN_HAND);
    }
}
