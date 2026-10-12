package com.dstsoftware.treechopper;

import com.dstsoftware.treechopper.tree.FellingHandler;
import com.dstsoftware.treechopper.tree.TreeChopperConfig;
import net.fabricmc.api.ModInitializer;

public final class TreeChopper implements ModInitializer {
    @Override
    public void onInitialize() {
		TreeChopperConfig.load();
        FellingHandler.init();
    }
}
