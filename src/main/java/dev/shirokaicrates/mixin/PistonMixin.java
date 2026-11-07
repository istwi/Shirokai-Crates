package dev.shirokaicrates.mixin;
import dev.shirokaicrates.ShirokaiCrates;
import net.minecraft.block.*;
import net.minecraft.world.World;
import net.minecraft.util.math.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PistonBlock.class)
public abstract class PistonMixin {
    @Inject(method="isMovable",at=@At("HEAD"),cancellable=true)
    private static void shirokai$protect(BlockState state,World world,BlockPos pos,Direction direction,boolean canBreak,Direction pistonDir,CallbackInfoReturnable<Boolean> cir) {
        if(!world.isClient && ShirokaiCrates.protectedBlock(world,pos)) cir.setReturnValue(false);
    }
}
