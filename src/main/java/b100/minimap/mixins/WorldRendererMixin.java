package b100.minimap.mixins;

import b100.minimap.Minimap;

import net.minecraft.client.render.WorldRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = WorldRenderer.class, remap = false)
public abstract class WorldRendererMixin {
    @Inject(method = "updateCameraAndRender", at = @At("TAIL"))
	protected void updateCameraAndRender(float partialTick, CallbackInfo ci) {
	    Minimap.instance.onRenderGui(partialTick);
	}
}
