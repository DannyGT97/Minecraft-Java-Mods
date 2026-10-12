package com.dstsoftware.dynamiclights.client.gui;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.dstsoftware.dynamiclights.client.config.DynamicLightsConfig;
import com.dstsoftware.dynamiclights.client.config.DynamicLightsConfig.DynamicLightMode;
import com.dstsoftware.dynamiclights.client.config.DynamicLightsConfig.GuardianLaserMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DynamicLightsConfigScreen extends SettingsScreen {
	// Everything below the two top-level gates is greyed out while both are off.
	private static final BooleanSupplier ANY_ENABLED = () -> cfg().playerDynamicLightEnabled || cfg().entitiesDynamicLightEnabled;

	public DynamicLightsConfigScreen(Screen parent) {
		super(parent, Component.translatable("dynamiclights.config.title"), "dynamiclights", categories(), DynamicLightsConfig::reset);
	}

	private static DynamicLightsConfig cfg() {
		return DynamicLightsConfig.get();
	}

	private static List<Category> categories() {
		return List.of(
			new Category(
				Component.translatable("dynamiclights.config.category.general"),
				Component.translatable("dynamiclights.config.category.general.desc"),
				List.of(
					toggle("player", () -> cfg().playerDynamicLightEnabled, value -> cfg().playerDynamicLightEnabled = value, () -> true),
					toggle("entities", () -> cfg().entitiesDynamicLightEnabled, value -> cfg().entitiesDynamicLightEnabled = value, () -> true)
				)
			),
			new Category(
				Component.translatable("dynamiclights.config.category.entities"),
				Component.translatable("dynamiclights.config.category.entities.desc"),
				List.of(
					toggle("items", () -> cfg().droppedAndHeldItems, value -> cfg().droppedAndHeldItems = value, ANY_ENABLED),
					toggle("glow_squids", () -> cfg().glowSquids, value -> cfg().glowSquids = value, ANY_ENABLED),
					toggle("blazes", () -> cfg().blazes, value -> cfg().blazes = value, ANY_ENABLED),
					toggle("fireballs", () -> cfg().fireballs, value -> cfg().fireballs = value, ANY_ENABLED),
					toggle("magma_cubes", () -> cfg().magmaCubes, value -> cfg().magmaCubes = value, ANY_ENABLED),
					toggle("spectral_arrows", () -> cfg().spectralArrows, value -> cfg().spectralArrows = value, ANY_ENABLED),
					toggle("allays", () -> cfg().allays, value -> cfg().allays = value, ANY_ENABLED)
				)
			),
			new Category(
				Component.translatable("dynamiclights.config.category.special"),
				Component.translatable("dynamiclights.config.category.special.desc"),
				List.of(
					toggle("on_fire", () -> cfg().entitiesOnFire, value -> cfg().entitiesOnFire = value, ANY_ENABLED),
					cycle("creeper_swell", DynamicLightMode.values(), () -> cfg().creeperSwellMode, value -> cfg().creeperSwellMode = value, "mode"),
					cycle("guardian_lasers", GuardianLaserMode.values(), () -> cfg().guardianLaserMode, value -> cfg().guardianLaserMode = value, "guardian_mode"),
					toggle("warden_sonic_boom", () -> cfg().wardenSonicBoom, value -> cfg().wardenSonicBoom = value, ANY_ENABLED),
					cycle("tnt", DynamicLightMode.values(), () -> cfg().tntMode, value -> cfg().tntMode = value, "mode")
				)
			)
		);
	}

	private static Option toggle(String name, BooleanSupplier getter, Consumer<Boolean> setter, BooleanSupplier active) {
		return Option.toggle("dynamiclights.config." + name, getter, value -> {
			setter.accept(value);
			DynamicLightsConfig.save();
		}, active, Component.translatable("dynamiclights.config." + name + ".tooltip"));
	}

	// An OFF / middle / FANCY choice cycling on each click. The value is colored red, yellow, green.
	private static <E extends Enum<E>> Option cycle(String name, E[] values, Supplier<E> getter, Consumer<E> setter, String valueKey) {
		return new Option(
			() -> {
				E value = getter.get();
				ChatFormatting color = value.ordinal() == 0 ? ChatFormatting.RED : value.ordinal() == values.length - 1 ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
				return Component.translatable("dynamiclights.config." + name,
					Component.translatable("dynamiclights.config." + valueKey + "." + value.name().toLowerCase(Locale.ROOT)).withStyle(color));
			},
			() -> {
				setter.accept(values[(getter.get().ordinal() + 1) % values.length]);
				DynamicLightsConfig.save();
			},
			ANY_ENABLED,
			Component.translatable("dynamiclights.config." + name + ".tooltip")
		);
	}
}
