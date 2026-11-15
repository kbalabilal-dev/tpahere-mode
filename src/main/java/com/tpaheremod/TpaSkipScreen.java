package com.tpaheremod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Tap a name to put it on / take it off the skip list. */
public class TpaSkipScreen extends Screen {
    private static final int TOP = 56;
    private static final int ROW_H = 24;

    private final Screen parent;
    private TextFieldWidget search;
    private ButtonWidget prev, next, addBtn;
    private final List<ButtonWidget> buttons = new ArrayList<>();
    private int page = 0;
    private boolean empty = false;

    public TpaSkipScreen(Screen parent) {
        super(Text.literal("Skip list (red = never sent)"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        buttons.clear();

        search = new TextFieldWidget(textRenderer, width / 2 - 102, 28, 148, 20, Text.literal("Search"));
        search.setChangedListener(s -> { page = 0; rebuild(); });
        addDrawableChild(search);

        addBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Add"), b -> {
                    String t = search.getText().trim();
                    if (valid(t)) {
                        TpaConfig.setSkipped(t, true);
                        TpaConfig.save();
                        search.setText("");
                        rebuild();
                    }
                }).dimensions(width / 2 + 50, 28, 52, 20).build());

        prev = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { page--; rebuild(); })
                .dimensions(width / 2 - 104, height - 28, 44, 20).build());
        next = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { page++; rebuild(); })
                .dimensions(width / 2 + 60, height - 28, 44, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 - 50, height - 28, 100, 20).build());

        setInitialFocus(search);
        rebuild();
    }

    private static boolean valid(String name) {
        return name.matches("[A-Za-z0-9_.]{1,20}");
    }

    private Text label(String name) {
        return TpaConfig.isSkipped(name)
                ? Text.literal(name).formatted(Formatting.RED)
                : Text.literal(name);
    }

    private List<String> entries() {
        TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.addAll(TpaQueue.onlineNames(client, ""));
        set.addAll(TpaConfig.skipList());
        String q = search.getText().toLowerCase();
        return set.stream().filter(n -> n.toLowerCase().contains(q)).toList();
    }

    private void rebuild() {
        for (ButtonWidget b : buttons) remove(b);
        buttons.clear();

        List<String> all = entries();
        empty = all.isEmpty();
        int rows = Math.max(1, (height - TOP - 36) / ROW_H);
        int perPage = rows * 2;
        int maxPage = Math.max(0, (all.size() - 1) / perPage);
        page = MathHelper.clamp(page, 0, maxPage);

        int start = page * perPage;
        int end = Math.min(all.size(), start + perPage);
        for (int i = start; i < end; i++) {
            String name = all.get(i);
            int idx = i - start;
            int col = idx % 2;
            int row = idx / 2;
            ButtonWidget b = ButtonWidget.builder(label(name), btn -> {
                        TpaConfig.setSkipped(name, !TpaConfig.isSkipped(name));
                        TpaConfig.save();
                        btn.setMessage(label(name));
                    })
                    .dimensions(width / 2 - 102 + col * 104, TOP + row * ROW_H, 100, 20).build();
            buttons.add(b);
            addDrawableChild(b);
        }
        prev.active = page > 0;
        next.active = page < maxPage;
        String typed = search.getText().trim();
        addBtn.active = valid(typed) && !TpaConfig.isSkipped(typed);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFFFF);
        if (empty) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Nobody here. Type a name and tap Add."), width / 2, TOP + 10, 0xFFAAAAAA);
        }
    }
}
