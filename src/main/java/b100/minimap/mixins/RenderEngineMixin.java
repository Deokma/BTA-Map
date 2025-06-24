package b100.minimap.mixins;

import b100.minimap.Minimap;

import net.minecraft.client.render.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TextureManager.class, remap = false)
public abstract class RenderEngineMixin {
    @Inject(method = "refreshTextures", at = @At("TAIL"))
	protected void refreshTextures(CallbackInfo ci) {
	    Minimap.instance.onReload();
	}
}
