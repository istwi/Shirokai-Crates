package dev.shirokaicrates.ui;

import dev.shirokaicrates.service.CrateService;
import dev.shirokaicrates.config.Models.*;
import dev.shirokaicrates.service.WeightedPicker;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import java.util.*;

public final class Menus {
    private final CrateService service;
    public Menus(CrateService service) { this.service=service; }
    public static ItemStack icon(String id,String title,String... lines) {
        return decorate(new ItemStack(Registries.ITEM.get(Identifier.of(id))),title,lines);
    }
    private static ItemStack decorate(ItemStack stack,String title,String... lines) {
        stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal(title));
        stack.set(DataComponentTypes.LORE,new LoreComponent(Arrays.stream(lines).map(s->(Text)Text.literal(s)).toList())); return stack;
    }
    public static Formatting color(Rarity rarity) {
        return switch(rarity) { case common->Formatting.AQUA; case rare->Formatting.BLUE; case epic->Formatting.LIGHT_PURPLE; case legendary->Formatting.GOLD; case special->Formatting.DARK_PURPLE; };
    }
    public static ItemStack rewardIcon(Reward r,List<Reward> rewards) {
        ItemStack stack=decorate(rewardBase(r),r.name(),"Raridade: "+r.rarity(),"Quantidade: "+r.count(),
                "Chance: "+WeightedPicker.percentageText(r,rewards),r.type()==Kind.pokemon?r.properties()+(r.ivs()==null?"":r.ivs().mode().equals("perfect")?" | IVs: "+r.ivs().perfectCount()+" perfeitos garantidos; demais aleatórios":" | IVs aleatórios"):r.type()==Kind.currency?r.currency()+" "+r.amount():"Somente visualização");
        stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal(r.name()).formatted(color(r.rarity()))); return stack;
    }
    private static ItemStack rewardBase(Reward reward) {
        return reward.type()==Kind.pokemon?dev.shirokaicrates.integration.PokemonIcons.icon(reward):new ItemStack(Registries.ITEM.get(Identifier.of(reward.icon())));
    }
    private static void navigation(CrateMenu menu,int page,int size) {
        if(page>0) menu.set(45,icon("minecraft:arrow","§bAnterior"));
        if((page+1)*45<size) menu.set(53,icon("minecraft:arrow","§bPróxima"));
        menu.set(49,icon("minecraft:book","§6Página "+(page+1)));
    }
    public void crates(ServerPlayerEntity player,int page) {
        List<Crate> crates=service.snapshot().crates().values().stream().sorted(Comparator.comparing(Crate::id)).toList();
        CrateMenu menu=CrateMenu.open(player,"✦ SHIROKAI - CAIXAS",slot->{
            if(slot==45 && page>0) crates(player,page-1);
            else if(slot==53 && (page+1)*45<crates.size()) crates(player,page+1);
            else if(slot<45 && page*45+slot<crates.size()) preview(player,crates.get(page*45+slot).id(),0);
        });
        if(menu==null)return;
        for(int i=0;i<45 && page*45+i<crates.size();i++) {
            Crate c=crates.get(page*45+i); menu.set(i,icon(c.key().item(),"§b"+c.name(),"ID: "+c.id(),"Clique para visualizar os prêmios.","Abra somente no bloco, com a chave na mão."));
        }
        navigation(menu,page,crates.size());
    }
    public void preview(ServerPlayerEntity player,String id,int page) {
        if(!service.allowed(player,"shirokaicrates.preview"))return;
        Crate crate=service.snapshot().crates().get(id); if(crate==null) { service.message(player,"unavailable"); return; }
        List<Reward> rewards=crate.rewards();
        CrateMenu menu=CrateMenu.open(player,"✦ SHIROKAI - RECOMPENSAS",slot->{
            if(slot==45 && page>0)preview(player,id,page-1);
            if(slot==53 && (page+1)*45<rewards.size())preview(player,id,page+1);
        }); if(menu==null)return;
        for(int i=0;i<45 && page*45+i<rewards.size();i++)menu.set(i,rewardIcon(rewards.get(page*45+i),rewards));
        navigation(menu,page,rewards.size());
    }
    public void fragments(ServerPlayerEntity player,int page) {
        if(!service.allowed(player,"shirokaicrates.fragments"))return;
        List<Crate> crates=service.snapshot().crates().values().stream().sorted(Comparator.comparing(Crate::id)).toList();
        CrateMenu menu=CrateMenu.open(player,"✦ SHIROKAI - FRAGMENTOS",slot->{
            if(slot==45 && page>0)fragments(player,page-1);
            else if(slot==53 && (page+1)*45<crates.size())fragments(player,page+1);
            else if(slot<45 && page*45+slot<crates.size()) { service.convert(player,crates.get(page*45+slot).id()); fragments(player,page); }
        }); if(menu==null)return;
        for(int i=0;i<45 && page*45+i<crates.size();i++) {
            Crate c=crates.get(page*45+i); menu.set(i,icon(c.fragment().item(),"§b"+c.fragment().name(),"Disponíveis: "+service.fragments(player,c),
                    "Necessários: "+c.fragmentsRequired(),"Resultado: "+c.key().name(),"Clique para converter uma chave."));
        }
        navigation(menu,page,crates.size());
    }
    public void pending(ServerPlayerEntity player,int page) {
        if(!service.allowed(player,"shirokaicrates.pending"))return;
        var pending=service.ledger().pending(player.getUuid());
        CrateMenu menu=CrateMenu.open(player,"✦ SHIROKAI - PENDÊNCIAS",slot->{
            if(slot==45 && page>0)pending(player,page-1);
            else if(slot==53 && (page+1)*45<pending.size())pending(player,page+1);
            else if(slot<45 && page*45+slot<pending.size())service.claim(player,pending.get(page*45+slot).id());
        }); if(menu==null)return;
        for(int i=0;i<45 && page*45+i<pending.size();i++) {
            var tx=pending.get(page*45+i); menu.set(i,decorate(rewardBase(tx.reward()),"§6"+tx.reward().name(),tx.crateName(),"ID: "+tx.id(),"Estado: "+tx.state(),tx.detail(),"Clique para resgatar; estados incertos exigem revisão administrativa."));
        }
        navigation(menu,page,pending.size());
    }
}
