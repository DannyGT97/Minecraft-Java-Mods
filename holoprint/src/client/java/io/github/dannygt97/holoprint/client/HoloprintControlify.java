package io.github.dannygt97.holoprint.client;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint;
import dev.isxander.controlify.api.entrypoint.InitContext;
import dev.isxander.controlify.api.entrypoint.PreInitContext;
import io.github.dannygt97.holoprint.placement.EditorKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holoprint's entry point for Controlify (only called when Controlify is installed). It gives the editor's actions their controller
 * bindings; a failure here is logged and must never keep Controlify from starting.
 */
public final class HoloprintControlify implements ControlifyEntrypoint {
	private static final Logger LOGGER = LoggerFactory.getLogger("holoprint");

	@Override
	public void onControlifyPreInit(PreInitContext context) {
		try {
			EditorKeys.init();
			ControlifyBridge.register(context.bindings());
		} catch (Throwable e) {
			LOGGER.warn("Holoprint could not register its controller bindings", e);
		}
	}

	@Override
	public void onControlifyInit(InitContext context) {}

	@Override
	public void onControllersDiscovered(ControlifyApi controlify) {}
}
