package com.dstsoftware.dynamiclights.client.light;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dstsoftware.dynamiclights.client.config.DynamicLightsConfig;
import com.dstsoftware.dynamiclights.client.config.DynamicLightsConfig.DynamicLightMode;
import com.dstsoftware.dynamiclights.client.config.DynamicLightsConfig.GuardianLaserMode;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.squid.GlowSquid;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

// Purely client-side dynamic lights: every source below lights its surroundings without placing a
// block or talking to the server. Any entity holding or being a light-emitting item, any entity on
// fire, creepers mid-swell, glow squids, blazes, fireballs, magma cubes, spectral arrows, allays,
// a warden's sonic boom, primed TNT and a guardian's attack beam (the one source that is a line
// instead of a point).
//
// LightCoordsUtilMixin (terrain) and EntityRendererMixin (entities) call getDynamicLightLevel to
// blend these sources into vanilla's own lightmap computation. The terrain contribution is BAKED
// into each chunk section's mesh when it is rebuilt, so any change to a source (moved to another
// block, luminance changed, gone) has to mark both its old and its new sections dirty - that
// bookkeeping is the LAST_STATE map.
//
// The scan runs once per rendered frame (LevelExtractionEvents.END_EXTRACTION, before terrain
// rebuilds are processed) with each entity's interpolated position, instead of once per game tick:
// at more than 20 fps a tick-rate scan lets a source's position go stale for up to 50 ms, which
// makes the light visibly step instead of follow the source smoothly.
//
// Every category is gated by DynamicLightsConfig. A disabled category contributes nothing, and
// disabling one that had active sources darkens them again: its entries are missing from the
// current scan, which the cleanup loop treats as "source vanished" and marks dirty.
public final class DynamicLights {
	// A light source for one frame: world-space center, brightness 0-15, reach in blocks and an
	// optional second endpoint (null for a point source; present for a LINE source - a guardian's
	// beam - whose "range" is the lateral falloff distance from the nearest point of the
	// pos-lineEnd segment, not a radius around pos).
	private record Source(Vec3 pos, float luminance, float range, @Nullable Vec3 lineEnd) {
	}

	// What a source looked like last frame, reduced to what affects baked terrain light: its block
	// position (and the far endpoint's for line sources), its luminance and how many sections around
	// it need rebuilding when it changes (depends on the source's reach).
	private record Fingerprint(BlockPos pos, @Nullable BlockPos secondaryPos, int luminance, int sectionRadius) {
	}

	private static final int ON_FIRE_LUMINANCE = 15;
	private static final int GLOW_SQUID_LUMINANCE = 12;
	private static final int GLOW_SQUID_DARK_LUMINANCE = 5;
	private static final int CREEPER_SWELL_MAX_LUMINANCE = 10;
	// Hand-picked, not derived from a vanilla light-emission value (none of these emit real light
	// in vanilla).
	private static final int BLAZE_LUMINANCE = 10;
	private static final int FIREBALL_LUMINANCE = 14;
	private static final int MAGMA_CUBE_LUMINANCE = 11;
	private static final int SPECTRAL_ARROW_LUMINANCE = 8;
	private static final int ALLAY_LUMINANCE = 8;
	private static final int WARDEN_SONIC_BOOM_LUMINANCE = 15;
	private static final long WARDEN_SONIC_BOOM_DURATION_MS = 500;
	// Vanilla starts the warden's sonic boom animation state at the very beginning of the attack
	// (the roar/charge-up), but the boom itself - particles, sound, damage - only happens
	// TICKS_BEFORE_PLAYING_SOUND (34 ticks) later. The flash is delayed by the same offset so it
	// lines up with the actual boom.
	private static final long WARDEN_SONIC_BOOM_DELAY_MS = 34 * 50L;
	// Brightest point of a primed TNT's fuse glow (FANCY ramps up to it as the fuse nears zero,
	// SIMPLE sits at it constantly).
	private static final int TNT_LUMINANCE = 12;
	private static final float GUARDIAN_LASER_LUMINANCE = 12.0F;
	// Lateral falloff distance from a guardian beam's line, not a radius from either endpoint.
	private static final float GUARDIAN_LASER_RANGE = 6.0F;

	private static volatile List<Source> activeSources = List.of();

	private static final Map<Integer, Fingerprint> LAST_STATE = new HashMap<>();

	private DynamicLights() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(DynamicLights::tick);
		LevelExtractionEvents.END_EXTRACTION.register(DynamicLights::onFrame);
	}

	// The only job of the tick hook: clear all state when a world is unloaded (disconnect, back to
	// the title screen), since END_EXTRACTION never fires outside an actively rendered world.
	private static void tick(final Minecraft client) {
		if (client.level == null) {
			activeSources = List.of();
			LAST_STATE.clear();
		}
	}

	private static void onFrame(final LevelExtractionContext context) {
		ClientLevel level = context.level();
		float partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(true);

		List<Source> sources = new ArrayList<>();
		Map<Integer, Fingerprint> current = new HashMap<>();
		DynamicLightsConfig config = DynamicLightsConfig.get();
		Entity localPlayer = Minecraft.getInstance().player;
		// With both gates off nothing can contribute, so skip the whole scan.
		boolean scanEntities = config.playerDynamicLightEnabled || config.entitiesDynamicLightEnabled;
		for (Entity entity : scanEntities ? level.entitiesForRendering() : List.<Entity>of()) {
			int luminance = entityLuminance(entity, config, entity == localPlayer, partialTick);
			Source guardianSource = guardianSource(entity, config, partialTick);

			if (luminance <= 0 && guardianSource == null) {
				continue;
			}

			Vec3 center = entity.getEyePosition(partialTick);
			float maxRange = 0.0F;
			if (luminance > 0) {
				// Vanilla-like reach: a level-14 torch lights up to 14 blocks away, fading linearly.
				sources.add(new Source(center, luminance, luminance, null));
				maxRange = luminance;
			}
			BlockPos secondaryPos = null;
			if (guardianSource != null) {
				sources.add(guardianSource);
				maxRange = Math.max(maxRange, guardianSource.range());
				// A guardian source degraded to a point (GuardianLaserMode.FAST) has no line end.
				if (guardianSource.lineEnd() != null) {
					secondaryPos = BlockPos.containing(guardianSource.lineEnd());
				}
			}

			int sectionRadius = Math.max(1, (int) Math.ceil(maxRange / 16.0));
			int fingerprint = luminance + (guardianSource != null ? 10000 : 0);
			current.put(entity.getId(), new Fingerprint(BlockPos.containing(center), secondaryPos, fingerprint, sectionRadius));
		}

		// Publish the new source list BEFORE requesting any rebuilds, so a rebuild kicked off this
		// frame already sees the up-to-date light data.
		activeSources = sources;

		for (Map.Entry<Integer, Fingerprint> entry : current.entrySet()) {
			Fingerprint last = LAST_STATE.get(entry.getKey());
			if (last == null || !last.equals(entry.getValue())) {
				if (last != null) {
					markSectionsDirty(level, last.pos(), last.secondaryPos(), last.sectionRadius());
				}
				markSectionsDirty(level, entry.getValue().pos(), entry.getValue().secondaryPos(), entry.getValue().sectionRadius());
			}
		}
		for (Map.Entry<Integer, Fingerprint> entry : LAST_STATE.entrySet()) {
			if (!current.containsKey(entry.getKey())) {
				markSectionsDirty(level, entry.getValue().pos(), entry.getValue().secondaryPos(), entry.getValue().sectionRadius());
			}
		}

		LAST_STATE.clear();
		LAST_STATE.putAll(current);
	}

	// A guardian's attack beam as a LINE source, lighting whatever it passes near - including the
	// entity being attacked. Only active once the beam is locked on (hasActiveAttackTarget), which
	// matches the visible beam rather than mere aggro.
	//
	// FAST degrades the beam to a single POINT at its midpoint: a line source's dirty region is the
	// whole bounding box between guardian and target, which keeps changing as either moves and
	// forces a section rebuild almost every frame during a fight - the most expensive source of
	// the lot. A point only ever dirties one small, fixed-size region, at the cost of the light no
	// longer following the whole beam.
	private static @Nullable Source guardianSource(final Entity entity, final DynamicLightsConfig config, final float partialTick) {
		// A guardian is never the local player, so only the entities gate applies.
		if (!config.entitiesDynamicLightEnabled || config.guardianLaserMode == GuardianLaserMode.OFF || !(entity instanceof Guardian guardian)
			|| !guardian.hasActiveAttackTarget()) {
			return null;
		}
		LivingEntity target = guardian.getActiveAttackTarget();
		if (target == null) {
			return null;
		}
		Vec3 origin = guardian.getEyePosition(partialTick);
		Vec3 targetPos = target.getEyePosition(partialTick);
		if (config.guardianLaserMode == GuardianLaserMode.FAST) {
			return new Source(origin.add(targetPos).scale(0.5), GUARDIAN_LASER_LUMINANCE, GUARDIAN_LASER_RANGE, null);
		}
		return new Source(origin, GUARDIAN_LASER_LUMINANCE, GUARDIAN_LASER_RANGE, targetPos);
	}

	// isLocalPlayer picks which of the two top-level gates applies - independent of which settings
	// page a category's own switch lives on. partialTick is only needed for the warden's sonic boom
	// (a real-time duration window), but keeps every timing input of this scan consistent.
	private static int entityLuminance(final Entity entity, final DynamicLightsConfig config, final boolean isLocalPlayer, final float partialTick) {
		if (!(isLocalPlayer ? config.playerDynamicLightEnabled : config.entitiesDynamicLightEnabled)) {
			return 0;
		}
		int luminance = 0;
		if (config.entitiesOnFire && entity.isOnFire()) {
			luminance = ON_FIRE_LUMINANCE;
		}
		if (config.droppedAndHeldItems) {
			if (entity instanceof ItemEntity itemEntity) {
				luminance = Math.max(luminance, itemLuminance(itemEntity.getItem()));
			}
			if (entity instanceof LivingEntity living) {
				luminance = Math.max(luminance, itemLuminance(living.getMainHandItem()));
				luminance = Math.max(luminance, itemLuminance(living.getOffhandItem()));
			}
		}
		if (config.glowSquids && entity instanceof GlowSquid squid) {
			luminance = Math.max(luminance, squid.getDarkTicksRemaining() > 0 ? GLOW_SQUID_DARK_LUMINANCE : GLOW_SQUID_LUMINANCE);
		}
		if (config.creeperSwellMode != DynamicLightMode.OFF && entity instanceof Creeper creeper) {
			luminance = Math.max(luminance, creeperSwellLuminance(creeper, config.creeperSwellMode));
		}
		if (config.blazes && entity instanceof Blaze) {
			luminance = Math.max(luminance, BLAZE_LUMINANCE);
		}
		// Fireball is the common superclass of a blaze's small fireball and a ghast's large one.
		if (config.fireballs && entity instanceof Fireball) {
			luminance = Math.max(luminance, FIREBALL_LUMINANCE);
		}
		if (config.magmaCubes && entity instanceof MagmaCube) {
			luminance = Math.max(luminance, MAGMA_CUBE_LUMINANCE);
		}
		if (config.spectralArrows && entity instanceof SpectralArrow) {
			luminance = Math.max(luminance, SPECTRAL_ARROW_LUMINANCE);
		}
		if (config.allays && entity instanceof Allay) {
			luminance = Math.max(luminance, ALLAY_LUMINANCE);
		}
		if (config.wardenSonicBoom && entity instanceof Warden warden && isSonicBooming(warden, partialTick)) {
			luminance = Math.max(luminance, WARDEN_SONIC_BOOM_LUMINANCE);
		}
		if (config.tntMode != DynamicLightMode.OFF && entity instanceof PrimedTnt tnt) {
			luminance = Math.max(luminance, tntLuminance(tnt, config.tntMode));
		}
		return Math.min(luminance, 15);
	}

	// FANCY ramps up as the creeper's swell animation progresses; SIMPLE sits at a flat
	// CREEPER_SWELL_MAX_LUMINANCE the whole time it is swelling. OFF never reaches this method.
	private static int creeperSwellLuminance(final Creeper creeper, final DynamicLightMode mode) {
		float swelling = creeper.getSwelling(1.0F);
		if (swelling <= 0.0F) {
			return 0;
		}
		return mode == DynamicLightMode.SIMPLE ? CREEPER_SWELL_MAX_LUMINANCE : Math.round(swelling * CREEPER_SWELL_MAX_LUMINANCE);
	}

	// FANCY mirrors the creeper swell but keyed off the fuse counting down. PrimedTnt.getFuse() counts
	// down from whatever it was set to (usually DEFAULT_FUSE_TIME, 80, but a dispenser or command can
	// set another), and the entity cannot report its original length, so the ramp always assumes
	// DEFAULT_FUSE_TIME: exact for normal TNT, an approximation for non-default fuses.
	private static int tntLuminance(final PrimedTnt tnt, final DynamicLightMode mode) {
		if (mode == DynamicLightMode.SIMPLE) {
			return TNT_LUMINANCE;
		}
		float progress = 1.0F - Mth.clamp((float) tnt.getFuse() / PrimedTnt.DEFAULT_FUSE_TIME, 0.0F, 1.0F);
		return Math.round(progress * TNT_LUMINANCE);
	}

	// Vanilla starts a warden's sonicBoomAnimationState for every sonic boom and never stops it, so
	// isStarted() alone would stay true forever; "currently booming" is the window starting
	// WARDEN_SONIC_BOOM_DELAY_MS after that start and lasting WARDEN_SONIC_BOOM_DURATION_MS (a
	// hand-picked width, no vanilla constant exposes the boom's own visual duration).
	private static boolean isSonicBooming(final Warden warden, final float partialTick) {
		if (!warden.sonicBoomAnimationState.isStarted()) {
			return false;
		}
		long elapsed = warden.sonicBoomAnimationState.getTimeInMillis(warden.tickCount + partialTick);
		return elapsed >= WARDEN_SONIC_BOOM_DELAY_MS && elapsed < WARDEN_SONIC_BOOM_DELAY_MS + WARDEN_SONIC_BOOM_DURATION_MS;
	}

	// How bright an item is as a carried/dropped light source. Block items inherit their block's own
	// light emission (torch 14, lantern 15, glowstone 15, ...); the explicit cases cover items whose
	// block form only lights up in a non-default state (glow berries: cave vines only emit 14 WITH
	// berries, and the default state has none) or that have no block form at all (lava bucket).
	private static int itemLuminance(final ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		if (stack.is(Items.LAVA_BUCKET)) {
			return 15;
		}
		if (stack.is(Items.GLOW_BERRIES)) {
			return 14;
		}
		if (stack.getItem() instanceof BlockItem blockItem) {
			return blockItem.getBlock().defaultBlockState().getLightEmission();
		}
		return 0;
	}

	// Rebuild every section within `sectionRadius` sections of `pos` (and, for line sources, of
	// `secondaryPos` too - the whole bounding box between them, expanded by the radius). Radius 1
	// (section + direct neighbors) covers any source reaching up to 16 blocks; longer-reaching
	// sources need ceil(range/16), computed where the Fingerprint is built.
	private static void markSectionsDirty(final ClientLevel level, final BlockPos pos, final @Nullable BlockPos secondaryPos, final int sectionRadius) {
		int x1 = SectionPos.blockToSectionCoord(pos.getX());
		int y1 = SectionPos.blockToSectionCoord(pos.getY());
		int z1 = SectionPos.blockToSectionCoord(pos.getZ());
		if (secondaryPos == null) {
			level.setSectionRangeDirty(x1 - sectionRadius, y1 - sectionRadius, z1 - sectionRadius, x1 + sectionRadius, y1 + sectionRadius, z1 + sectionRadius);
			return;
		}

		int x2 = SectionPos.blockToSectionCoord(secondaryPos.getX());
		int y2 = SectionPos.blockToSectionCoord(secondaryPos.getY());
		int z2 = SectionPos.blockToSectionCoord(secondaryPos.getZ());
		level.setSectionRangeDirty(
			Math.min(x1, x2) - sectionRadius,
			Math.min(y1, y2) - sectionRadius,
			Math.min(z1, z2) - sectionRadius,
			Math.max(x1, x2) + sectionRadius,
			Math.max(y1, y2) + sectionRadius,
			Math.max(z1, z2) + sectionRadius
		);
	}

	// Closest distance from `point` to the segment [a, b], used for line sources; point sources use
	// the plain distance instead.
	private static double distanceToSegment(final Vec3 point, final Vec3 a, final Vec3 b) {
		Vec3 segment = b.subtract(a);
		double lengthSq = segment.lengthSqr();
		if (lengthSq < 1.0E-6) {
			return point.subtract(a).length();
		}

		double t = Mth.clamp(point.subtract(a).dot(segment) / lengthSq, 0.0, 1.0);
		return point.subtract(a.add(segment.scale(t))).length();
	}

	// Light contributed by dynamic sources at a block position, 0-15 (vanilla's block-light scale).
	// Used by terrain lighting (LightCoordsUtilMixin), where a block-quantized target is exactly right.
	public static int getDynamicLightLevel(final BlockPos pos) {
		return getDynamicLightLevel(Vec3.atCenterOf(pos));
	}

	// Same computation against a continuous world position, used by EntityRendererMixin with the
	// entity's own interpolated render position (a block-quantized position would make the light on
	// a moving entity jump every time it crosses a block boundary).
	public static int getDynamicLightLevel(final Vec3 target) {
		List<Source> sources = activeSources;
		if (sources.isEmpty()) {
			return 0;
		}

		double best = 0.0;
		for (Source source : sources) {
			double distance = source.lineEnd() != null ? distanceToSegment(target, source.pos(), source.lineEnd()) : target.subtract(source.pos()).length();
			if (distance >= source.range()) {
				continue;
			}

			double level = source.luminance() * (1.0 - distance / source.range());
			if (level > best) {
				best = level;
			}
		}

		return Math.min((int) Math.round(best), 15);
	}
}
