package com.tpaheremod.mixin;

import com.tpaheremod.TpaQueue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops the confirm window from being drawn while the queue is working.
 * The window itself (the "handler") is still open and filled by the server,
 * so TpaQueue can read it and click the green pane without showing anything.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void tpahere$hideConfirmWindow(Screen screen, CallbackInfo ci) {
        if (screen instanceof GenericContainerScreen && TpaQueue.wantsHiddenWindow()) {
            ci.cancel();
        }
    }
}
