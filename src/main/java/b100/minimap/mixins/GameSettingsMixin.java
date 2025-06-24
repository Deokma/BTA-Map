package b100.minimap.mixins;

import b100.minimap.Minimap;

import b100.minimap.mc.impl.TileColorsBTA;
import net.minecraft.client.option.GameSettings;
import net.minecraft.client.option.Option;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GameSettings.class, remap = false)
public abstract class GameSettingsMixin {
    @Inject(method = "optionChanged", at = @At("TAIL"))
	protected void optionChanged(Option option, CallbackInfo ci) {
	    GameSettings self = (GameSettings) (Object) this;
		TileColorsBTA.instance.onOptionValueChanged(self, option);
	}
}
