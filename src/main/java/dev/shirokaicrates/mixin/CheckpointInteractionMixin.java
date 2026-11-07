package dev.shirokaicrates.mixin;

import dev.shirokaicrates.ShirokaiCrates;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class CheckpointInteractionMixin {
    @Shadow public ServerPlayerEntity player;
    @Inject(method={"onClickSlot","onButtonClick","onCreativeInventoryAction","onPlayerAction","onPlayerInteractBlock","onPlayerInteractItem","onPlayerInteractEntity"},at=@At("HEAD"),cancellable=true)
    private void shirokai$durabilityBarrier(CallbackInfo ci) {
        // NetworkThreadUtils reschedules packets first; mutation checks run only on the main thread.
        if(!player.getServer().isOnThread())return;
        var service=ShirokaiCrates.service();
        if(service!=null && service.checkpoints().blocked(player.getUuid())) {
            player.currentScreenHandler.syncState();ci.cancel();
        }
    }
}
