package com.tpaheremod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sends /tpahere to several players one after another.
 * For each player: send the command, wait for the confirm window, click the green
 * glass pane (2nd row, 8th item = slot 16), then go to the next player.
 * Players on the skip list, players who left, and players with no confirm window
 * (tpahere off) are skipped and do not count in the progress.
 * The command is set in the settings (default "tpahere"). The queue can be paused and resumed.
 * With "hide window" on, the confirm window is never drawn: the mixin blocks the screen and the
 * click is sent through the window handler instead.
 */
public final class TpaQueue {
    private enum State { IDLE, NEXT, WAITING, DELAY, LOOP_PAUSE }

    // Slot index in the confirm window: 2nd row (slots 9-17), 8th item -> 9 + 7 = 16
    private static final int CONFIRM_SLOT = 16;
    private static final int PANE_TIMEOUT_TICKS = 60;   // window open but pane missing -> skip
    private static final int CLOSE_AFTER_TICKS = 3;     // close leftover window after click

    private static final ArrayDeque<String> queue = new ArrayDeque<>();
    private static List<String> baseNames = List.of();
    private static boolean repeat;
    private static boolean refreshAll;
    private static State state = State.IDLE;
    private static int timer, total, sent, round, pauseTicks;
    private static boolean sawWindow;
    private static boolean paused;
    private static int lastClickedSyncId = -1;
    private static String current = "";
    private static List<String> lastNames = List.of();
    private static boolean lastRepeat, lastRefresh;

    private TpaQueue() {}

    public static boolean isRunning() {
        return state != State.IDLE;
    }

    public static boolean isPaused() {
        return state != State.IDLE && paused;
    }

    /** Pause or resume. A player that is already being sent finishes first, then the queue holds. */
    public static void togglePause(MinecraftClient client) {
        if (state == State.IDLE) return;
        paused = !paused;
        status(client, paused ? "TPAHere: paused" : "TPAHere: resumed");
    }

    /** Used by the mixin: true while the confirm window should not be drawn. */
    public static boolean wantsHiddenWindow() {
        return TpaConfig.hideWindow && (state == State.WAITING || state == State.DELAY);
    }

    /** The chest-style window the server has open for us right now (shown or hidden), or null. */
    private static GenericContainerScreenHandler openContainer(MinecraftClient client) {
        if (client.player == null) return null;
        var h = client.player.currentScreenHandler;
        if (h == client.player.playerScreenHandler) return null;
        return h instanceof GenericContainerScreenHandler g ? g : null;
    }

    /** Online players (not yourself) whose name contains the query, sorted. */
    public static List<String> onlineNames(MinecraftClient client, String query) {
        ClientPlayNetworkHandler h = client.getNetworkHandler();
        if (h == null || client.player == null) return List.of();
        String self = client.player.getGameProfile().name();
        String q = query.toLowerCase();
        return h.getPlayerList().stream()
                .map(e -> e.getProfile().name())
                .filter(n -> !n.equalsIgnoreCase(self))
                .filter(n -> n.toLowerCase().contains(q))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /** Online players that are not on the skip list. */
    public static List<String> onlineSendable(MinecraftClient client) {
        return onlineNames(client, "").stream().filter(n -> !TpaConfig.isSkipped(n)).toList();
    }

    private static boolean isOnline(MinecraftClient client, String name) {
        ClientPlayNetworkHandler h = client.getNetworkHandler();
        if (h == null) return false;
        for (PlayerListEntry e : h.getPlayerList()) {
            if (e.getProfile().name().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /**
     * @param repeatMode  start again when the queue finishes
     * @param refreshMode every round uses the current list of all online players
     */
    public static void start(MinecraftClient client, List<String> names, boolean repeatMode, boolean refreshMode) {
        if (names.isEmpty()) return;
        lastNames = List.copyOf(names);
        lastRepeat = repeatMode;
        lastRefresh = refreshMode;
        baseNames = List.copyOf(names);
        repeat = repeatMode;
        refreshAll = refreshMode;
        round = 0;
        paused = false;
        lastClickedSyncId = -1;
        beginRound(client);
    }

    /** Starts the last used queue again (used when auto hit is switched on). */
    public static void restartLast(MinecraftClient client) {
        if (lastNames.isEmpty()) return;
        start(client, lastNames, lastRepeat, lastRefresh);
    }

    /** True while the queue is held because "Combat: Xs" is showing. */
    public static boolean combatHold() {
        return TpaConfig.combatPause && TpaCombat.inCombat();
    }

    public static void cancel(MinecraftClient client) {
        if (state == State.IDLE) return;
        queue.clear();
        state = State.IDLE;
        paused = false;
        status(client, "TPAHere: cancelled");
    }

    private static void beginRound(MinecraftClient client) {
        round++;
        queue.clear();
        queue.addAll(refreshAll ? onlineSendable(client) : baseNames);
        total = queue.size();
        sent = 0;
        timer = 0;
        sawWindow = false;
        state = State.NEXT;
    }

    private static void finishRound(MinecraftClient client) {
        if (repeat) {
            pauseTicks = sent > 0 ? TpaConfig.roundPauseTicks : Math.max(TpaConfig.roundPauseTicks, 200);
            timer = 0;
            state = State.LOOP_PAUSE;
            if (TpaConfig.hudPos == 0) {
                status(client, "TPAHere: round " + round + " done, sent " + sent + ". Repeating...");
            }
        } else {
            state = State.IDLE;
            status(client, "TPAHere: done, sent " + sent);
        }
    }

    /**
     * Clicks the confirm pane if this window has one and was not clicked already.
     * Using the window id avoids clicking the same window twice, and lets us also confirm
     * a window that arrives late (after lag) while we are already on the next player.
     */
    private static boolean tryConfirm(MinecraftClient client, GenericContainerScreenHandler h) {
        if (h.syncId == lastClickedSyncId) return false;
        if (h.getRows() * 9 <= CONFIRM_SLOT) return false;
        if (!isGreenPane(h.getSlot(CONFIRM_SLOT).getStack())) return false;
        client.interactionManager.clickSlot(h.syncId, CONFIRM_SLOT, 0, SlotActionType.PICKUP, client.player);
        lastClickedSyncId = h.syncId;
        sent++;
        if (sent > total) total = sent;
        return true;
    }

    private static boolean isGreenPane(ItemStack s) {
        return s.isOf(Items.LIME_STAINED_GLASS_PANE) || s.isOf(Items.GREEN_STAINED_GLASS_PANE);
    }

    private static void status(MinecraftClient client, String msg) {
        if (client.player != null) client.player.sendMessage(Text.literal(msg), true);
    }

    // ---------- HUD info ----------

    private static String secs(int ticks) {
        return String.format(Locale.ROOT, "%.1fs", Math.max(0, ticks) / 20.0);
    }

    public static float progress() {
        return total > 0 ? Math.min(1f, sent / (float) total) : 0f;
    }

    public static List<String> hudLines() {
        List<String> l = new ArrayList<>();
        if (state == State.IDLE) return l;
        boolean hold = combatHold();
        l.add("TPAHere" + (repeat ? "  (round " + round + ")" : "") + (paused ? "  [PAUSED]" : "")
                + (hold ? "  [COMBAT " + TpaCombat.secondsLeft() + "s]" : ""));
        switch (state) {
            case WAITING -> l.add("Sending: " + current);
            case DELAY -> l.add(paused || hold ? "Pausing..." : "Next player in " + secs(TpaConfig.delayTicks - timer));
            case LOOP_PAUSE -> l.add(paused || hold ? "Paused" : "Next round in " + secs(pauseTicks - timer));
            default -> l.add(hold ? "In combat - waiting..." : paused ? "Paused (press the key to resume)" : "Next player...");
        }
        l.add("Sent " + sent + "/" + total + "   Left: " + queue.size());
        return l;
    }

    // ---------- main loop ----------

    public static void tick(MinecraftClient client) {
        if (state == State.IDLE) return;
        if (client.player == null || client.getNetworkHandler() == null || client.interactionManager == null) {
            queue.clear();
            state = State.IDLE;
            return;
        }

        switch (state) {
            case IDLE -> { }
            case NEXT -> {
                if (paused || combatHold()) return; // hold here until resumed / combat is over
                // Skip-list players and players who already left are skipped instantly, not counted.
                String name = queue.poll();
                while (name != null && (TpaConfig.isSkipped(name) || !isOnline(client, name))) {
                    total--;
                    name = queue.poll();
                }
                if (name == null) {
                    finishRound(client);
                    return;
                }
                current = name;
                client.player.networkHandler.sendChatCommand(TpaConfig.command + " " + current);
                state = State.WAITING;
                timer = 0;
                sawWindow = false;
            }
            case WAITING -> {
                timer++;
                GenericContainerScreenHandler window = openContainer(client);
                if (window != null) {
                    sawWindow = true;
                    if (tryConfirm(client, window)) {
                        if (TpaConfig.hudPos == 0) {
                            String prefix = repeat ? "[round " + round + "] " : "";
                            status(client, "TPAHere: " + prefix + current + " (" + sent + "/" + total + ")");
                        }
                        state = State.DELAY;
                        timer = 0;
                        return;
                    }
                } else if (sawWindow) {
                    // The window disappeared before we clicked: you closed it, so stop.
                    cancel(client);
                    return;
                }
                int limit = sawWindow ? PANE_TIMEOUT_TICKS : TpaConfig.windowTimeoutTicks;
                if (timer > limit) {
                    // No confirm window: player offline or tpahere turned off. Skip, don't count.
                    total--;
                    if (openContainer(client) != null) {
                        client.player.closeHandledScreen();
                    }
                    state = State.NEXT;
                }
            }
            case DELAY -> {
                timer++;
                GenericContainerScreenHandler late = openContainer(client);
                if (late != null && tryConfirm(client, late)) {
                    timer = 0; // a late window was confirmed: keep it open a moment, then continue
                }
                int closeAt = Math.min(CLOSE_AFTER_TICKS, Math.max(1, TpaConfig.delayTicks - 1));
                if (timer == closeAt && openContainer(client) != null) {
                    client.player.closeHandledScreen();
                }
                if (timer >= TpaConfig.delayTicks) state = State.NEXT;
            }
            case LOOP_PAUSE -> {
                if (paused || combatHold()) return;
                timer++;
                if (timer >= pauseTicks) beginRound(client);
            }
        }
    }
}
