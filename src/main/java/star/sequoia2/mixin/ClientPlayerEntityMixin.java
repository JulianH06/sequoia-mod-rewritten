package star.sequoia2.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import star.sequoia2.accessors.EventBusAccessor;
import star.sequoia2.events.PlayerTickEvent;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin extends AbstractClientPlayerEntity implements EventBusAccessor {
    public ClientPlayerEntityMixin() {
        super(MinecraftClient.getInstance().world, MinecraftClient.getInstance().player.getGameProfile());
    }

    @Inject(at = @At(value = "INVOKE",
            target = "Lnet/" + "minecraft/client/network/AbstractClientPlayerEntity;tick()V",
            shift = At.Shift.BEFORE,
            ordinal = 0),
            method = "tick")
    private void onPreTick(CallbackInfo ci) {
        dispatch(new PlayerTickEvent());
    }
}
