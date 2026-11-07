package dev.shirokaicrates.mixin;
import dev.shirokaicrates.ShirokaiCrates;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=HopperBlockEntity.class,priority=2000)
public abstract class HopperMixin {
    @Inject(method="getBlockInventoryAt",at=@At("HEAD"),cancellable=true)
    private static void shirokai$protect(World world,BlockPos pos,BlockState state,CallbackInfoReturnable<Inventory> cir) {
        if(ShirokaiCrates.protectedBlock(world,pos))cir.setReturnValue(null);
    }
    @Inject(method="extract(Lnet/minecraft/world/World;Lnet/minecraft/block/entity/Hopper;)Z",at=@At("HEAD"),cancellable=true)
    private static void shirokai$preventExtract(World world,net.minecraft.block.entity.Hopper hopper,CallbackInfoReturnable<Boolean> cir) {
        BlockPos source=BlockPos.ofFloored(hopper.getHopperX(),hopper.getHopperY()+1,hopper.getHopperZ());
        if(ShirokaiCrates.protectedBlock(world,source))cir.setReturnValue(false);
    }
    @Inject(method="insert(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/HopperBlockEntity;)Z",at=@At("HEAD"),cancellable=true)
    private static void shirokai$preventInsert(World world,BlockPos pos,HopperBlockEntity hopper,CallbackInfoReturnable<Boolean> cir) {
        BlockPos target=pos.offset(hopper.getCachedState().get(net.minecraft.block.HopperBlock.FACING));
        if(ShirokaiCrates.protectedBlock(world,target))cir.setReturnValue(false);
    }
}
