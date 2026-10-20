package io.github.dannygt97.holoprint.schematic;

import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Block states as the .litematic palette stores them: {Name: "minecraft:oak_log", Properties:
 * {axis: "x"}} with every property value as a string. Written here instead of with the game's own
 * block-state NBT helpers, whose layout differs between versions. Reading accepts both the classic
 * (Name/Properties) and the newer id/properties keys; writing always uses the classic layout.
 */
public final class BlockStateTags {
	private BlockStateTags() {}

	/** The block's registry name from a palette entry. */
	public static String nameOf(CompoundTag tag) {
		return tag.getString("Name").or(() -> tag.getString("id")).or(() -> tag.getString("name")).orElse("minecraft:air");
	}

	/** True when this game has the block a palette entry names. */
	public static boolean isKnown(CompoundTag tag) {
		Identifier id = Identifier.tryParse(nameOf(tag));
		return id != null && BuiltInRegistries.BLOCK.containsKey(id);
	}

	/** The state a palette entry describes. Properties this game does not know are ignored. */
	public static BlockState read(CompoundTag tag) {
		Identifier id = Identifier.tryParse(nameOf(tag));
		if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
			throw new IllegalArgumentException("Unknown block " + nameOf(tag));
		}
		Block block = BuiltInRegistries.BLOCK.getValue(id);
		BlockState state = block.defaultBlockState();
		CompoundTag properties = tag.getCompound("Properties").or(() -> tag.getCompound("properties")).orElseGet(CompoundTag::new);
		for (String key : properties.keySet()) {
			Property<?> property = block.getStateDefinition().getProperty(key);
			if (property != null) {
				state = withValue(state, property, properties.getStringOr(key, ""));
			}
		}
		return state;
	}

	/** A state written the way commands do, {@code minecraft:oak_stairs[facing=north,half=bottom]}, as a palette entry. */
	public static CompoundTag parse(String text) {
		CompoundTag tag = new CompoundTag();
		String trimmed = text.trim();
		int bracket = trimmed.indexOf('[');
		String name = bracket < 0 ? trimmed : trimmed.substring(0, bracket);
		tag.putString("Name", name.contains(":") ? name : "minecraft:" + name);
		if (bracket >= 0 && trimmed.endsWith("]")) {
			CompoundTag properties = new CompoundTag();
			for (String pair : trimmed.substring(bracket + 1, trimmed.length() - 1).split(",")) {
				int equals = pair.indexOf('=');
				if (equals > 0) {
					properties.putString(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
				}
			}
			if (!properties.isEmpty()) {
				tag.put("Properties", properties);
			}
		}
		return tag;
	}

	public static CompoundTag write(BlockState state) {
		CompoundTag tag = new CompoundTag();
		tag.putString("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
		if (!state.getProperties().isEmpty()) {
			CompoundTag properties = new CompoundTag();
			for (Property<?> property : state.getProperties()) {
				properties.putString(property.getName(), valueName(state, property));
			}
			tag.put("Properties", properties);
		}
		return tag;
	}

	private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String value) {
		Optional<T> parsed = property.getValue(value);
		return parsed.isPresent() ? state.setValue(property, parsed.get()) : state;
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}
}
