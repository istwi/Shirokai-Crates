package dev.shirokaicrates.mixin;
import dev.shirokaicrates.ShirokaiCrates;
import net.minecraft.world.World;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(World.class)
public abstract class WorldMixin {
    @Inject(method="setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
    private void shirokai$protect(BlockPos pos,BlockState state,int flags,int depth,CallbackInfoReturnable<Boolean> cir) {
        World world=(World)(Object)this;
        if(!world.isClient && ShirokaiCrates.protectedBlock(world,pos) && !world.getBlockState(pos).equals(state)) cir.setReturnValue(false);
    }
}
