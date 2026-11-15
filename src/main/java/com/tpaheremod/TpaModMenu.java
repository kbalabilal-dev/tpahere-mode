package com.tpaheremod;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Adds the "Configure" button for this mod in Mod Menu. Only loaded when Mod Menu is installed. */
public class TpaModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new TpaConfigScreen(parent);
    }
}
