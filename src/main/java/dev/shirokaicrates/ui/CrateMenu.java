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
        super(display.size()==27?ScreenHandlerType.GENERIC_9X3:ScreenHandlerType.GENERIC_9X6,syncId,playerInventory,display,display.size()/9);
        this.display=display; this.click=click;
    }
    @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity player) {
        if(player.currentScreenHandler==this && slot>=0 && slot<display.size() && button==0 && action==SlotActionType.PICKUP) click.accept(slot);
        if(player.currentScreenHandler==this)syncState();
    }
    @Override public ItemStack quickMove(PlayerEntity player,int slot) { return ItemStack.EMPTY; }
    @Override public boolean canUse(PlayerEntity player) { return player.isAlive(); }
    @Override public boolean canInsertIntoSlot(ItemStack stack,net.minecraft.screen.slot.Slot slot) { return false; }
    public void set(int slot,ItemStack stack) { display.setStack(slot,stack); sendContentUpdates(); }
    public static CrateMenu open(ServerPlayerEntity player,String title,IntConsumer action) {
        return open(player,title,6,action);
    }
    public static CrateMenu open(ServerPlayerEntity player,String title,int rows,IntConsumer action) {
        if(rows!=3 && rows!=6)throw new IllegalArgumentException("Esperado 3 ou 6 linhas");
        SimpleInventory inventory=new SimpleInventory(rows*9);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,p)->new CrateMenu(id,inv,inventory,action),Text.literal(title)));
        return player.currentScreenHandler instanceof CrateMenu menu?menu:null;
    }
}
