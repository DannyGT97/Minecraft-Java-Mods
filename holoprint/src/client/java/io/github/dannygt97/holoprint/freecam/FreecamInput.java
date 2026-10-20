package io.github.dannygt97.holoprint.freecam;

import net.minecraft.client.Options;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/**
 * Takes the player's place as the input source while the free camera is on. It reads the normal
 * keyboard input first (so anything that adds to it, like a controller mod's sticks, is included),
 * hands the result to the camera and gives the player an empty input, so the player stands still.
 */
final class FreecamInput extends KeyboardInput {
	FreecamInput(Options options) {
		super(options);
	}

	@Override
	public void tick() {
		super.tick();
		Input keys = this.keyPresses;
		int vertical = (keys.jump() ? 1 : 0) - (keys.shift() ? 1 : 0);
		Freecam.move(this.moveVector, vertical, keys.sprint());
		this.keyPresses = Input.EMPTY;
		this.moveVector = Vec2.ZERO;
	}
}
