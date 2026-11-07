package dev.shirokaicrates.mixin;

import dev.shirokaicrates.storage.PlayerReceipts;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

@Mixin(PlayerEntity.class)
public abstract class PlayerDataMixin implements PlayerReceipts {
    @Unique private final Map<String,String> shirokai$receipts=new HashMap<>();
    public Map<String,String> shirokai$receipts() { return shirokai$receipts; }
    @Inject(method="writeCustomDataToNbt",at=@At("TAIL"))
    private void shirokai$write(NbtCompound nbt,CallbackInfo ci) {
        NbtCompound receipts=new NbtCompound(); shirokai$receipts.forEach(receipts::putString); nbt.put("ShirokaiCratesReceipts",receipts);
    }
    @Inject(method="readCustomDataFromNbt",at=@At("TAIL"))
    private void shirokai$read(NbtCompound nbt,CallbackInfo ci) {
        shirokai$receipts.clear(); NbtCompound receipts=nbt.getCompound("ShirokaiCratesReceipts");
        for(String id:receipts.getKeys()) shirokai$receipts.put(id,receipts.getString(id));
    }
}
