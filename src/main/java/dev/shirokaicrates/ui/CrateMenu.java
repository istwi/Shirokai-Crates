package dev.shirokaicrates.ui;

import net.minecraft.entity.player.*;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.function.IntConsumer;

/** Uses a vanilla container protocol; every inventory mutation path is intercepted server-side. */
public final class CrateMenu extends GenericContainerScreenHandler {
    private final SimpleInventory display;
    private final IntConsumer click;
    public CrateMenu(int syncId,PlayerInventory playerInventory,SimpleInventory display,IntConsumer click) {
        super(ScreenHandlerType.GENERIC_9X6,syncId,playerInventory,display,6);
        this.display=display; this.click=click;
    }
    @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity player) {
        if(player.currentScreenHandler==this && slot>=0 && slot<54 && button==0 && action==SlotActionType.PICKUP) click.accept(slot);
        if(player.currentScreenHandler==this)syncState();
    }
    @Override public ItemStack quickMove(PlayerEntity player,int slot) { return ItemStack.EMPTY; }
    @Override public boolean canUse(PlayerEntity player) { return player.isAlive(); }
    @Override public boolean canInsertIntoSlot(ItemStack stack,net.minecraft.screen.slot.Slot slot) { return false; }
    public void set(int slot,ItemStack stack) { display.setStack(slot,stack); sendContentUpdates(); }
    public static CrateMenu open(ServerPlayerEntity player,String title,IntConsumer action) {
        SimpleInventory inventory=new SimpleInventory(54);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,p)->new CrateMenu(id,inv,inventory,action),Text.literal(title)));
        return player.currentScreenHandler instanceof CrateMenu menu?menu:null;
    }
}
