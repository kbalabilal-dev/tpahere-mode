package com.tpaheremod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class TpaConfigScreen extends Screen {
    private static final String[] HUD_NAMES = {"HUD: off", "HUD: top center", "HUD: top left", "HUD: top right"};

    private final Screen parent;
    private TextFieldWidget commandField;
    private ButtonWidget pauseBtn;

    public TpaConfigScreen(Screen parent) {
        super(Text.literal("TPAHere settings"));
        this.parent = parent;
    }

    private static String secs(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }

    private static Text windowLabel() {
        return Text.literal(TpaConfig.hideWindow ? "Window: hidden" : "Window: shown");
    }

    private static Text combatLabel() {
        return Text.literal(TpaConfig.combatPause ? "Combat pause: ON" : "Combat pause: OFF");
    }

    private static Text autoHitLabel() {
        return Text.literal(TpaAutoHit.isEnabled() ? "Auto hit: ON" : "Auto hit: OFF");
    }

    private static Text pauseLabel() {
        return Text.literal(TpaQueue.isPaused() ? "Resume queue" : "Pause queue");
    }

    @Override
    protected void init() {
        int y = 40;
        addStepper(y, "Delay", () -> TpaConfig.delayTicks, v -> TpaConfig.delayTicks = v, 2, 100, 2, 10);
        addStepper(y + 20, "Skip wait", () -> TpaConfig.windowTimeoutTicks, v -> TpaConfig.windowTimeoutTicks = v, 10, 100, 2, 10);
        addStepper(y + 40, "Round pause", () -> TpaConfig.roundPauseTicks, v -> TpaConfig.roundPauseTicks = v, 10, 600, 10, 100);

        addMsStepper(y + 60, "Auto hit", 50, 5000, 50, 100);

        // Command that is sent for every player. Saved while you type.
        commandField = new TextFieldWidget(textRenderer, width / 2 - 100, y + 96, 200, 20, Text.literal("Command"));
        commandField.setMaxLength(40);
        commandField.setText(TpaConfig.command);
        commandField.setChangedListener(s -> {
            TpaConfig.command = TpaConfig.cleanCommand(s);
            TpaConfig.save();
        });
        addDrawableChild(commandField);

        int row1 = y + 120;
        addDrawableChild(ButtonWidget.builder(Text.literal(HUD_NAMES[TpaConfig.hudPos]), b -> {
                    TpaConfig.hudPos = (TpaConfig.hudPos + 1) % HUD_NAMES.length;
                    TpaConfig.save();
                    b.setMessage(Text.literal(HUD_NAMES[TpaConfig.hudPos]));
                }).dimensions(width / 2 - 100, row1, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(windowLabel(), b -> {
                    TpaConfig.hideWindow = !TpaConfig.hideWindow;
                    TpaConfig.save();
                    b.setMessage(windowLabel());
                }).dimensions(width / 2 + 2, row1, 98, 20).build());

        int row2 = row1 + 22;
        addDrawableChild(ButtonWidget.builder(Text.literal("Skip list (" + TpaConfig.skipCount() + ")"),
                        b -> client.setScreen(new TpaSkipScreen(this)))
                .dimensions(width / 2 - 100, row2, 98, 20).build());

        pauseBtn = addDrawableChild(ButtonWidget.builder(pauseLabel(), b -> {
                    TpaQueue.togglePause(client);
                    b.setMessage(pauseLabel());
                }).dimensions(width / 2 + 2, row2, 98, 20).build());
        pauseBtn.active = TpaQueue.isRunning();

        int row3 = row2 + 22;
        addDrawableChild(ButtonWidget.builder(combatLabel(), b -> {
                    TpaConfig.combatPause = !TpaConfig.combatPause;
                    TpaConfig.save();
                    b.setMessage(combatLabel());
                }).dimensions(width / 2 - 100, row3, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(autoHitLabel(), b -> {
                    TpaAutoHit.toggle(client);
                    b.setMessage(autoHitLabel());
                }).dimensions(width / 2 + 2, row3, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 - 50, height - 28, 100, 20).build());
    }

    /** Stepper for the auto hit delay, in milliseconds. */
    private void addMsStepper(int y, String name, int min, int max, int small, int big) {
        int x0 = width / 2 - 134;
        ButtonWidget label = ButtonWidget.builder(
                Text.literal(name + " " + TpaConfig.autoHitDelayMs + "ms"), b -> { })
                .dimensions(x0 + 84, y, 100, 20).build();
        label.active = false;

        int[] steps = {-big, -small, small, big};
        int[] xs = {x0, x0 + 42, x0 + 186, x0 + 228};
        for (int i = 0; i < 4; i++) {
            int d = steps[i];
            String text = (d < 0 ? "-" : "+") + Math.abs(d);
            addDrawableChild(ButtonWidget.builder(Text.literal(text), b -> {
                        TpaConfig.autoHitDelayMs = MathHelper.clamp(TpaConfig.autoHitDelayMs + d, min, max);
                        TpaConfig.save();
                        label.setMessage(Text.literal(name + " " + TpaConfig.autoHitDelayMs + "ms"));
                    }).dimensions(xs[i], y, 40, 20).build());
        }
        addDrawableChild(label);
    }

    private void addStepper(int y, String name, IntSupplier get, IntConsumer set,
                            int min, int max, int small, int big) {
        int x0 = width / 2 - 134;
        ButtonWidget label = ButtonWidget.builder(
                Text.literal(name + " " + secs(get.getAsInt()) + "s"), b -> { })
                .dimensions(x0 + 84, y, 100, 20).build();
        label.active = false;

        int[] steps = {-big, -small, small, big};
        int[] xs = {x0, x0 + 42, x0 + 186, x0 + 228};
        for (int i = 0; i < 4; i++) {
            int d = steps[i];
            String text = (d < 0 ? "-" : "+") + secs(Math.abs(d));
            addDrawableChild(ButtonWidget.builder(Text.literal(text), b -> {
                        set.accept(MathHelper.clamp(get.getAsInt() + d, min, max));
                        TpaConfig.save();
                        label.setMessage(Text.literal(name + " " + secs(get.getAsInt()) + "s"));
                    }).dimensions(xs[i], y, 40, 20).build());
        }
        addDrawableChild(label);
    }

    @Override
    public void close() {
        TpaConfig.save();
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 6, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Delay: pause between players"), width / 2, 18, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Skip wait: how long to wait for the confirm window"), width / 2, 28, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Command (no /) - sends: /" + TpaConfig.command + " <player>"), width / 2, 126, 0xFFAAAAAA);
    }
}
