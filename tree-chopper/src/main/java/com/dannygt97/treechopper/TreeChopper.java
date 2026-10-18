package com.dannygt97.treechopper;

import com.dannygt97.treechopper.tree.FellingHandler;
import com.dannygt97.treechopper.tree.TreeChopperConfig;
import net.fabricmc.api.ModInitializer;

public final class TreeChopper implements ModInitializer {
    @Override
    public void onInitialize() {
		TreeChopperConfig.load();
        FellingHandler.init();
    }
}
