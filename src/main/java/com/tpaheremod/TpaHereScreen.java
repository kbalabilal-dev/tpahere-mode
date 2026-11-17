package com.tpaheremod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TpaHereScreen extends Screen {
    private static final int TOP = 56;
    private static final int ROW_H = 24;
    private static final int RESERVED_SINGLE = 36;
    private static final int RESERVED_MULTI = 84;

    // Remembered between openings of the screen.
    private static boolean autoRepeat = false;

    private TextFieldWidget search;
    private ButtonWidget prev, next, multiBtn, sendBtn, selPageBtn, selAllBtn, autoBtn;
    private final List<ButtonWidget> playerButtons = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private List<String> filtered = List.of();   // players matching the search
    private List<String> pageNames = List.of();  // players shown on this page
    private int page = 0;
    private boolean empty = false;
    private boolean multi = false;

    public TpaHereScreen() {
        super(Text.literal("Tap a player to send /" + TpaConfig.command));
    }

    @Override
    protected void init() {
        playerButtons.clear();

        addDrawableChild(ButtonWidget.builder(Text.literal("Settings"),
                        b -> client.setScreen(new TpaConfigScreen(this)))
                .dimensions(4, 4, 56, 20).build());

        addDrawableChild(ButtonWidget.builder(autoHitLabel(), b -> {
                    TpaAutoHit.toggle(client);
                    b.setMessage(autoHitLabel());
                }).dimensions(width - 94, 4, 90, 20).build());

        String oldSearch = search != null ? search.getText() : "";
        search = new TextFieldWidget(textRenderer, width / 2 - 102, 28, 118, 20, Text.literal("Search"));
        search.setText(oldSearch);
        search.setChangedListener(s -> { page = 0; rebuild(); });
        addDrawableChild(search);

        multiBtn = addDrawableChild(ButtonWidget.builder(multiLabel(), b -> {
                    multi = !multi;
                    if (!multi) selected.clear();
                    b.setMessage(multiLabel());
                    rebuild();
                }).dimensions(width / 2 + 22, 28, 80, 20).build());

        selPageBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Select page"), b -> {
                    List<String> l = selectable(pageNames);
                    if (allSelected(l)) selected.removeAll(l);
                    else selected.addAll(l);
                    rebuild();
                }).dimensions(width / 2 - 102, height - 76, 100, 20).build());

        selAllBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Select all"), b -> {
                    List<String> l = selectable(filtered);
                    if (allSelected(l)) selected.removeAll(l);
                    else selected.addAll(l);
                    rebuild();
                }).dimensions(width / 2 + 2, height - 76, 100, 20).build());

        autoBtn = addDrawableChild(ButtonWidget.builder(autoLabel(), b -> {
                    autoRepeat = !autoRepeat;
                    b.setMessage(autoLabel());
                }).dimensions(width / 2 - 102, height - 52, 72, 20).build());

        sendBtn = addDrawableChild(ButtonWidget.builder(sendLabel(), b -> sendSelected())
                .dimensions(width / 2 - 26, height - 52, 128, 20).build());

        prev = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { page--; rebuild(); })
                .dimensions(width / 2 - 104, height - 28, 44, 20).build());
        next = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { page++; rebuild(); })
                .dimensions(width / 2 + 60, height - 28, 44, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(width / 2 - 50, height - 28, 100, 20).build());

        setInitialFocus(search);
        rebuild();
    }

    private Text multiLabel() {
        return Text.literal(multi ? "Multi: ON" : "Multi: OFF");
    }

    private Text autoHitLabel() {
        return Text.literal(TpaAutoHit.isEnabled() ? "Auto hit: ON" : "Auto hit: OFF");
    }

    private Text autoLabel() {
        return Text.literal(autoRepeat ? "Auto: ON" : "Auto: OFF");
    }

    private Text sendLabel() {
        return Text.literal("Send selected (" + selected.size() + ")");
    }

    private Text nameLabel(String name) {
        if (TpaConfig.isSkipped(name)) return Text.literal(name).formatted(Formatting.RED);
        return selected.contains(name)
                ? Text.literal("\u2714 " + name).formatted(Formatting.GREEN)
                : Text.literal(name);
    }

    /** Players that are not on the skip list. */
    private List<String> selectable(List<String> list) {
        return list.stream().filter(n -> !TpaConfig.isSkipped(n)).toList();
    }

    private boolean allSelected(List<String> list) {
        return !list.isEmpty() && selected.containsAll(list);
    }

    private void rebuild() {
        for (ButtonWidget b : playerButtons) remove(b);
        playerButtons.clear();

        selected.removeIf(TpaConfig::isSkipped);

        filtered = TpaQueue.onlineNames(client, search.getText());
        empty = filtered.isEmpty();
        int reserved = multi ? RESERVED_MULTI : RESERVED_SINGLE;
        int rows = Math.max(1, (height - TOP - reserved) / ROW_H);
        int perPage = rows * 2;
        int maxPage = Math.max(0, (filtered.size() - 1) / perPage);
        page = MathHelper.clamp(page, 0, maxPage);

        int start = page * perPage;
        int end = Math.min(filtered.size(), start + perPage);
        pageNames = new ArrayList<>(filtered.subList(start, end));

        for (int i = start; i < end; i++) {
            String name = filtered.get(i);
            int idx = i - start;
            int col = idx % 2;
            int row = idx / 2;
            ButtonWidget b = ButtonWidget.builder(nameLabel(name), btn -> {
                        if (multi) {
                            if (!selected.remove(name)) selected.add(name);
                            btn.setMessage(nameLabel(name));
                            updateButtons();
                        } else {
                            send(name);
                        }
                    })
                    .dimensions(width / 2 - 102 + col * 104, TOP + row * ROW_H, 100, 20).build();
            // Skip-list players can't be selected for the queue (but a single tap still sends).
            b.active = !(multi && TpaConfig.isSkipped(name));
            playerButtons.add(b);
            addDrawableChild(b);
        }
        prev.active = page > 0;
        next.active = page < maxPage;
        updateButtons();
    }

    private void updateButtons() {
        selPageBtn.visible = multi;
        selAllBtn.visible = multi;
        autoBtn.visible = multi;
        sendBtn.visible = multi;

        List<String> selPage = selectable(pageNames);
        List<String> selAll = selectable(filtered);
        selPageBtn.setMessage(Text.literal(allSelected(selPage) ? "Unselect page" : "Select page"));
        selAllBtn.setMessage(Text.literal(allSelected(selAll) ? "Unselect all" : "Select all"));
        selPageBtn.active = !selPage.isEmpty();
        selAllBtn.active = !selAll.isEmpty();
        sendBtn.setMessage(sendLabel());
        sendBtn.active = !selected.isEmpty();
    }

    private void send(String name) {
        if (client != null && client.player != null) {
            client.player.networkHandler.sendChatCommand(TpaConfig.command + " " + name);
        }
        close();
    }

    private void sendSelected() {
        if (client == null || selected.isEmpty()) return;
        // If every online player (except the skip list) is selected, each round re-reads
        // the online list, so players who join later are included and players who left are dropped.
        List<String> online = TpaQueue.onlineSendable(client);
        boolean everyone = !online.isEmpty() && selected.containsAll(online);
        TpaQueue.start(client, new ArrayList<>(selected), autoRepeat, everyone);
        close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFFFF);
        if (empty) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("No players found"), width / 2, TOP + 10, 0xFFAAAAAA);
        }
    }
}
