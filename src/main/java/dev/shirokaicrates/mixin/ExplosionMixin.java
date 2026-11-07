package dev.shirokaicrates.mixin;
import dev.shirokaicrates.ShirokaiCrates;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.util.math.BlockPos;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Explosion.class)
public abstract class ExplosionMixin {
    @Shadow @Final private World world;
    @Shadow @Final private ObjectArrayList<BlockPos> affectedBlocks;
    @Inject(method="collectBlocksAndDamageEntities",at=@At("TAIL"))
    private void shirokai$protect(CallbackInfo ci) {
        affectedBlocks.removeIf(pos->ShirokaiCrates.protectedBlock(world,pos));
    }
}
