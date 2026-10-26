package io.github.dannygt97.holoprint.freecam;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/**
 * Takes the player's place as the input source while the free camera is on. It runs the input the player had (the keyboard's, or the keyboard's and
 * a controller mod's sticks and buttons together), hands the result to the camera and gives the player an empty input, so the player stands still.
 */
final class FreecamInput extends ClientInput {
	private final ClientInput inner;

	FreecamInput(ClientInput inner) {
		this.inner = inner;
	}

	/** The input that was in place before the free camera, which gives the controls back to the player when it ends. */
	ClientInput inner() {
		return inner;
	}

	@Override
	public void tick() {
		// A controller mod toggles sneaking (one press on, another off) for a player on foot, but holds it only while the button is down for a flying one:
		// the camera flies, so the input is read as if the player did, and going down lasts as long as the button is held.
		Abilities abilities = Minecraft.getInstance().player.getAbilities();
		boolean flying = abilities.flying;
		abilities.flying = true;
		try {
			inner.tick();
		} finally {
			abilities.flying = flying;
		}
		// The camera flies with no window open and with the editor's bar open (the cursor is free there on purpose); a window on top of it (a list, the
		// settings, a name being typed) owns the sticks and the keys, and the camera stays where it is.
		var screen = Minecraft.getInstance().gui.screen();
		if (screen == null || screen instanceof io.github.dannygt97.holoprint.ui.EditorScreen editor && !editor.hasPanel()) {
			Input keys = inner.keyPresses;
			int vertical = (keys.jump() ? 1 : 0) - (keys.shift() ? 1 : 0);
			Freecam.move(inner.getMoveVector(), vertical, keys.sprint());
		}
		this.keyPresses = Input.EMPTY;
		this.moveVector = Vec2.ZERO;
	}
}
