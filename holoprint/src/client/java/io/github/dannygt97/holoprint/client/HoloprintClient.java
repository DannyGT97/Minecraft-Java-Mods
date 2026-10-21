package io.github.dannygt97.holoprint.client;

import net.fabricmc.api.ClientModInitializer;

public final class HoloprintClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		io.github.dannygt97.holoprint.hologram.HologramRenderer.init();
		io.github.dannygt97.holoprint.hologram.SelectionRenderer.init();
		io.github.dannygt97.holoprint.placement.Editor.init();
		io.github.dannygt97.holoprint.placement.EasyPlace.init();
		io.github.dannygt97.holoprint.placement.HologramBuilder.init();
		io.github.dannygt97.holoprint.hologram.Verifier.init();
		io.github.dannygt97.holoprint.placement.PlacementStore.init();
		io.github.dannygt97.holoprint.ui.EditorScreen.registerTools();
	}
}
