package com.tpaheremod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class TpaHereClient implements ClientModInitializer {
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("tpahere", "main"));
    private static KeyBinding openKey;
    private static KeyBinding cancelKey;
    private static KeyBinding settingsKey;
    private static KeyBinding pauseKey;

    @Override
    public void onInitializeClient() {
        TpaConfig.load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tpahere.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));
        cancelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tpahere.cancel", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tpahere.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        pauseKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tpahere.pause", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.of("tpahere", "queue_hud"), TpaHud::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (TpaQueue.isRunning()) {
                    // While a queue is running, the same key stops it.
                    TpaQueue.cancel(client);
                } else if (client.world != null && client.currentScreen == null) {
                    client.setScreen(new TpaHereScreen());
                }
            }
            while (cancelKey.wasPressed()) {
                TpaQueue.cancel(client);
            }
            while (pauseKey.wasPressed()) {
                TpaQueue.togglePause(client);
            }
            while (settingsKey.wasPressed()) {
                if (client.world != null && client.currentScreen == null) {
                    client.setScreen(new TpaConfigScreen(null));
                }
            }
            TpaQueue.tick(client);
        });
    }
}
