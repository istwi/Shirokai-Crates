package dev.shirokaicrates.mixin;

import dev.shirokaicrates.ShirokaiCrates;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.PlayerSaveHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.Optional;

@Mixin(PlayerSaveHandler.class)
public abstract class PlayerSaveMixin {
    @Inject(method="savePlayerData",at=@At("HEAD"),cancellable=true)
    private void shirokai$orderedSave(PlayerEntity player,CallbackInfo ci) {
        var service=ShirokaiCrates.service();
        if(service!=null && player instanceof ServerPlayerEntity serverPlayer && service.checkpoints().managed(player.getUuid())) {
            service.checkpoints().vanillaSave(serverPlayer);ci.cancel();
        }
    }
    @Inject(method="loadPlayerData(Lnet/minecraft/entity/player/PlayerEntity;)Ljava/util/Optional;",at=@At("HEAD"),cancellable=true)
    private void shirokai$pendingLogin(PlayerEntity player,CallbackInfoReturnable<Optional<NbtCompound>> cir) {
        var service=ShirokaiCrates.service();
        if(service!=null) {
            NbtCompound snapshot=service.checkpoints().latest(player.getUuid());
            if(snapshot!=null)try { player.readNbt(snapshot);cir.setReturnValue(Optional.of(snapshot)); }
            catch(RuntimeException | LinkageError ex) { ShirokaiCrates.LOG.error("Snapshot pendente não pôde ser carregado; usando o salvamento vanilla",ex); }
        }
    }
}
