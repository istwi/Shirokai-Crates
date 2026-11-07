package dev.shirokaicrates.mixin;
import dev.shirokaicrates.ShirokaiCrates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerPlayerEntity.class)
public abstract class CheckpointDamageMixin {
    @Inject(method="damage",at=@At("HEAD"),cancellable=true)
    private void shirokai$durabilityBarrier(DamageSource source,float amount,CallbackInfoReturnable<Boolean> cir) {
        var service=ShirokaiCrates.service();
        if(service!=null && service.checkpoints().blocked(((ServerPlayerEntity)(Object)this).getUuid()))cir.setReturnValue(false);
    }
}
