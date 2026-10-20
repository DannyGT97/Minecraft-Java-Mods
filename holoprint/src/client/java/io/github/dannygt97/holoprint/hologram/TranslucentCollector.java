package io.github.dannygt97.holoprint.hologram;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;

/**
 * Wraps the game's render collector so the models that block-entity renderers submit (chests, ender chests, beds, signs...)
 * come out translucent with the hologram's tint, like the rest of the hologram. Models the renderer hands over with a ready-made
 * render type are passed on untouched.
 */
final class TranslucentCollector {
	private TranslucentCollector() {}

	static SubmitNodeCollector wrap(SubmitNodeCollector delegate, int argb) {
		return (SubmitNodeCollector) Proxy.newProxyInstance(TranslucentCollector.class.getClassLoader(), new Class<?>[] {SubmitNodeCollector.class}, handler(delegate, argb));
	}

	private static InvocationHandler handler(OrderedSubmitNodeCollector delegate, int argb) {
		return (proxy, method, args) -> {
			try {
				if (method.getName().equals("order") && args != null && args.length == 1) {
					return Proxy.newProxyInstance(TranslucentCollector.class.getClassLoader(), new Class<?>[] {OrderedSubmitNodeCollector.class},
						handler((OrderedSubmitNodeCollector) method.invoke(delegate, args), argb));
				}
				if (method.getName().equals("submitModel") && args != null) {
					if (args.length == 9 && args[6] instanceof SpriteId id) {
						// model, state, pose, light, overlay, tint, sprite id, sprites, outline
						RenderType type = id.renderType(RenderTypes::entityTranslucent);
						UvMapping uv = ((SpriteGetter) args[7]).get(id);
						submit(delegate, args[0], args[1], (PoseStack) args[2], type, (int) args[3], (int) args[4], tint(argb, (int) args[5]), uv, (int) args[8]);
						return null;
					}
					if (args.length == 7 && args[3] instanceof Identifier texture) {
						// model, state, pose, texture, light, overlay, outline
						submit(delegate, args[0], args[1], (PoseStack) args[2], RenderTypes.entityTranslucent(texture), (int) args[4], (int) args[5], argb, null, (int) args[6]);
						return null;
					}
				}
				return method.invoke(delegate, args);
			} catch (InvocationTargetException e) {
				throw e.getCause();
			}
		};
	}

	/** The hologram's colour and opacity, multiplied with the tint the renderer asked for (usually white). */
	private static int tint(int argb, int tint) {
		if (tint == -1) {
			return argb;
		}
		int a = ((argb >>> 24) & 0xFF) * ((tint >>> 24) & 0xFF) / 255;
		int r = ((argb >>> 16) & 0xFF) * ((tint >>> 16) & 0xFF) / 255;
		int g = ((argb >>> 8) & 0xFF) * ((tint >>> 8) & 0xFF) / 255;
		int b = (argb & 0xFF) * (tint & 0xFF) / 255;
		return a << 24 | r << 16 | g << 8 | b;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void submit(OrderedSubmitNodeCollector delegate, Object model, Object state, PoseStack pose, RenderType type, int light, int overlay, int tint, UvMapping uv, int outline) {
		delegate.submitModel((Model) model, state, pose, type, light, overlay, tint, uv, outline);
	}
}
