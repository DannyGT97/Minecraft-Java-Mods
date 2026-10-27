package io.github.dannygt97.holoprint.mixin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;

// The game can freeze for good right after a resource reload (a language change): the render thread waits for a shader to be compiled by
// the background pool, while every thread of that pool is busy re-meshing terrain and spinning until the render thread frees the terrain
// buffer, which it can't do while it waits. A shader needed by the render thread is compiled by the render thread itself instead.
@Mixin(PipelineCache.class)
public abstract class PipelineCacheMixin {
	@Redirect(method = "get", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/device/GpuDevice;compilePipeline(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lcom/mojang/renderpearl/api/pipeline/ShaderSource;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
	private CompletableFuture<CompiledRenderPipeline.Pending> holoprint$compileHere(final GpuDevice device, final RenderPipeline pipeline, final ShaderSource source, final Executor background) {
		return device.compilePipeline(pipeline, source, Runnable::run);
	}
}
