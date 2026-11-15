package com.tpaheremod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;

/** Small progress box shown while a queue is running. */
public final class TpaHud {
    private TpaHud() {}

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (TpaConfig.hudPos == 0 || !TpaQueue.isRunning() || client.player == null) return;

        List<String> lines = TpaQueue.hudLines();
        if (lines.isEmpty()) return;

        TextRenderer tr = client.textRenderer;
        int w = 120;
        for (String s : lines) w = Math.max(w, tr.getWidth(s));

        int sw = context.getScaledWindowWidth();
        int x;
        switch (TpaConfig.hudPos) {
            case 2 -> x = 10;
            case 3 -> x = sw - w - 10;
            default -> x = (sw - w) / 2;
        }
        int y = 10;
        int textH = lines.size() * 11;

        context.fill(x - 4, y - 4, x + w + 4, y + textH + 10, 0x99000000);
        for (int i = 0; i < lines.size(); i++) {
            context.drawTextWithShadow(tr, lines.get(i), x, y + i * 11, 0xFFFFFFFF);
        }
        int by = y + textH + 1;
        context.fill(x, by, x + w, by + 4, 0xFF444444);
        context.fill(x, by, x + (int) (w * TpaQueue.progress()), by + 4, 0xFF55FF55);
    }
}
