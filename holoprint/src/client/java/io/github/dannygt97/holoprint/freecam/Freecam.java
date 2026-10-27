package io.github.dannygt97.holoprint.freecam;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * A detached camera that flies around while the player stays exactly where it is, in whatever
 * game mode it was. The game's camera is pointed at a small invisible entity that exists only on
 * the client; movement keys, the mouse and the controller stick drive that entity instead of the
 * player (see FreecamInput and the mixins), so nothing is sent to the server.
 */
public final class Freecam {
	private static final float LOOK_SPEED = 0.15F;
	private static final double MIN_SPEED = 0.1;
	private static final double MAX_SPEED = 6.0;

	private static Marker camera;
	private static Entity previousCamera;
	/** The player the camera was started for: dying and respawning (or changing dimension) makes the game create another one. */
	private static Entity owner;
	private static double speed = 0.9;
	private static boolean previousSmartCull;
	/** Game tick of the last "you cannot go further" message, so holding a key does not repeat it. */
	private static long lastLimitMessage = Long.MIN_VALUE;
	/** How far in front of the player's eyes the camera appears, so the player is not seen from the inside. */
	private static final double START_DISTANCE = 2.5;

	private Freecam() {}

	public static boolean isActive() {
		return camera != null;
	}

	/** The camera entity while active, otherwise null. */
	public static Entity entity() {
		return camera;
	}

	public static void start(Minecraft mc) {
		if (camera != null || mc.player == null || mc.level == null) {
			return;
		}
		camera = new Marker(EntityTypes.MARKER, mc.level);
		Vec3 eye = mc.player.getEyePosition();
		// A little in front of the eyes, stopping short of a wall, so the player can be seen from outside.
		Vec3 look = mc.player.getLookAngle();
		Vec3 ahead = eye.add(look.scale(START_DISTANCE));
		var hit = mc.level.clip(new ClipContext(eye, ahead, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
		if (hit.getType() == HitResult.Type.BLOCK) {
			ahead = hit.getLocation().subtract(look.scale(0.4));
		}
		place(ahead.x, ahead.y, ahead.z, mc.player.getYRot(), mc.player.getXRot());
		previousCamera = mc.getCameraEntity();
		owner = mc.player;
		mc.player.input = new FreecamInput(mc.player.input);
		// The game hides chunks it thinks are not reachable from the camera; a flying camera that goes
		// through terrain would see holes, so that shortcut is off while it flies.
		previousSmartCull = mc.smartCull;
		mc.smartCull = false;
		mc.setCameraEntity(camera);
	}

	/**
	 * True when the player or the world the camera was started in is gone: the player died and came back (a new player, which the game
	 * creates on respawn), or went through a portal. The camera and the saved input belong to the old one, so the editor has to end.
	 */
	public static boolean stale(Minecraft mc) {
		return camera != null && mc.player != null && mc.level != null && (mc.player != owner || camera.level() != mc.level || mc.player.isDeadOrDying());
	}

	/**
	 * Every tick: when a controller mod swaps the player's input for its own (a controller was picked up, or put down), the free camera takes the new
	 * one over, so the sticks keep flying the camera instead of walking the player.
	 */
	public static void keepInput(Minecraft mc) {
		if (camera != null && mc.player != null && !(mc.player.input instanceof FreecamInput)) {
			mc.player.input = new FreecamInput(mc.player.input);
		}
		if (camera != null && mc.player != null) {
			standStill(mc);
		}
	}

	/**
	 * The player stops where it is: with no input left to push it, it would still glide on the speed it had when the camera took over (a creative flight,
	 * a sprint, ice). Falling goes on, since gravity is not the player's doing.
	 */
	private static void standStill(Minecraft mc) {
		var player = mc.player;
		if (player.isFallFlying() || player.isPassenger()) {
			return;
		}
		Vec3 speedNow = player.getDeltaMovement();
		if (speedNow.x != 0.0 || speedNow.z != 0.0) {
			player.setDeltaMovement(0.0, speedNow.y, 0.0);
		}
		player.setSprinting(false);
	}

	/** Gives the view and the controls back to the player. */
	public static void stop(Minecraft mc) {
		if (camera == null) {
			return;
		}
		if (mc.player != null) {
			if (mc.player.input instanceof FreecamInput ours) {
				mc.player.input = ours.inner();
			}
			// The camera that was in place before can be the player of before a death: giving the view back to a dead, removed player
			// leaves the new one frozen (the game only lets the player that is the camera move) and with no hearts.
			boolean usable = previousCamera != null && !previousCamera.isRemoved() && previousCamera.level() == mc.level
				&& (!(previousCamera instanceof net.minecraft.client.player.LocalPlayer) || previousCamera == mc.player);
			mc.setCameraEntity(usable ? previousCamera : mc.player);
		}
		mc.smartCull = previousSmartCull;
		camera = null;
		previousCamera = null;
		owner = null;
	}

	/** Mouse or controller look: turns the camera instead of the player. */
	public static void turn(double yawDelta, double pitchDelta) {
		if (camera == null) {
			return;
		}
		float yaw = camera.getYRot() + (float) yawDelta * LOOK_SPEED;
		float pitch = Mth.clamp(camera.getXRot() + (float) pitchDelta * LOOK_SPEED, -90.0F, 90.0F);
		camera.setYRot(yaw);
		camera.setXRot(pitch);
		// Rotation is applied at once, with no tick lag between the mouse and the picture.
		camera.yRotO = yaw;
		camera.xRotO = pitch;
	}

	/**
	 * One tick of movement. {@code move.x} is the sideways input (positive to the left) and
	 * {@code move.y} the forward one, as in the game's own input; {@code vertical} is +1 up, -1 down.
	 */
	public static void move(Vec2 move, int vertical, boolean fast) {
		if (camera == null) {
			return;
		}
		Vec3 look = camera.getLookAngle();
		double yawRad = Math.toRadians(camera.getYRot());
		Vec3 left = new Vec3(Math.cos(yawRad), 0.0, Math.sin(yawRad));
		double step = speed * (fast ? 2.5 : 1.0);
		Vec3 delta = look.scale(move.y * step).add(left.scale(move.x * step)).add(0.0, vertical * step, 0.0);
		camera.xo = camera.getX();
		camera.yo = camera.getY();
		camera.zo = camera.getZ();
		camera.setPos(camera.getX() + delta.x, camera.getY() + delta.y, camera.getZ() + delta.z);
		keepInLoadedArea(Minecraft.getInstance());
	}

	/**
	 * How far from the player the camera may fly, in blocks. The game only has the chunks around the
	 * player (the server sends nothing else), so a camera that goes beyond half the view distance would
	 * look out over terrain that was never loaded and see empty sky.
	 */
	public static double flightRadius(Minecraft mc) {
		return Math.max(32.0, mc.options.getEffectiveRenderDistance() * 8.0);
	}

	/** Brings the camera back inside the area where the world exists, and says so when it had to. */
	private static void keepInLoadedArea(Minecraft mc) {
		if (camera == null || mc.player == null || mc.level == null) {
			return;
		}
		double radius = flightRadius(mc);
		double dx = camera.getX() - mc.player.getX();
		double dz = camera.getZ() - mc.player.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		double y = Mth.clamp(camera.getY(), mc.level.getMinY() - 8.0, mc.level.getMaxY() + 64.0);
		if (distance <= radius && y == camera.getY()) {
			return;
		}
		double scale = distance > radius ? radius / distance : 1.0;
		camera.setPos(mc.player.getX() + dx * scale, y, mc.player.getZ() + dz * scale);
		long now = mc.level.getGameTime();
		if (now - lastLimitMessage > 60) {
			lastLimitMessage = now;
			mc.gui.hud.setOverlayMessage(Component.translatable("holoprint.message.flight_limit", (int) radius), false);
		}
	}

	/** Mouse wheel: flying speed. */
	public static void changeSpeed(double wheel) {
		speed = Mth.clamp(speed * Math.pow(1.2, wheel), MIN_SPEED, MAX_SPEED);
	}

	public static double speed() {
		return speed;
	}

	public static Vec3 eyePosition() {
		return camera == null ? Vec3.ZERO : camera.position();
	}

	public static Vec3 lookVector() {
		return camera == null ? Vec3.ZERO : camera.getLookAngle();
	}

	/**
	 * Direction of the ray that leaves the camera through a point of the screen. {@code ndcX} and
	 * {@code ndcY} run from -1 to 1 (right and up are positive), {@code fov} is the vertical field of
	 * view in degrees.
	 */
	public static Vec3 rayThrough(double ndcX, double ndcY, double fov, double aspect) {
		if (camera == null) {
			return Vec3.ZERO;
		}
		Vec3 forward = camera.getLookAngle();
		double yawRad = Math.toRadians(camera.getYRot());
		Vec3 left = new Vec3(Math.cos(yawRad), 0.0, Math.sin(yawRad));
		Vec3 up = forward.cross(left);
		double half = Math.tan(Math.toRadians(fov) / 2.0);
		return forward.add(left.scale(-ndcX * half * aspect)).add(up.scale(ndcY * half)).normalize();
	}

	public static float yaw() {
		return camera == null ? 0.0F : camera.getYRot();
	}

	private static void place(double x, double y, double z, float yaw, float pitch) {
		camera.setPos(x, y, z);
		camera.xo = x;
		camera.yo = y;
		camera.zo = z;
		camera.setYRot(yaw);
		camera.setXRot(pitch);
		camera.yRotO = yaw;
		camera.xRotO = pitch;
	}
}
