package dev.shirokaicrates.integration;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.mojang.brigadier.StringReader;
import dev.shirokaicrates.config.Models.*;
import dev.shirokaicrates.storage.Transaction;
import net.impactdev.impactor.api.economy.EconomyService;
import net.kyori.adventure.key.Key;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.*;
import java.util.concurrent.*;

public final class Rewards {
    private final MinecraftServer server;
    public Rewards(MinecraftServer server) { this.server=server; }
    public static void validateItem(String id) {
        Identifier parsed=Identifier.tryParse(id);
        if(parsed==null || !Registries.ITEM.containsId(parsed) || id.equals("minecraft:air")) throw new IllegalArgumentException("Item inexistente: "+id);
    }
    public static void validateSound(String id) {
        Identifier parsed=Identifier.tryParse(id);
        if(parsed==null || !Registries.SOUND_EVENT.containsId(parsed)) throw new IllegalArgumentException("Som inexistente: "+id);
    }
    public void validate(Reward reward) {
        try {
            switch(reward.type()) {
                case item -> item(reward);
                case pokemon -> {
                    var properties=PokemonProperties.Companion.parse(reward.properties());
                    if(properties.getSpecies()==null || com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName(properties.getSpecies())==null)
                        throw new IllegalArgumentException("Espécie inválida");
                    if(properties.getNature()!=null && com.cobblemon.mod.common.api.pokemon.Natures.getNature(Identifier.of(properties.getNature()))==null)
                        throw new IllegalArgumentException("Natureza inválida");
                    if(properties.getAbility()!=null && com.cobblemon.mod.common.api.abilities.Abilities.get(properties.getAbility())==null)
                        throw new IllegalArgumentException("Habilidade inválida");
                    properties.create(null);
                    PokemonIcons.icon(reward);
                }
                case currency -> { if(EconomyService.instance().currencies().currency(Key.key(reward.currency())).isEmpty()) throw new IllegalArgumentException("Moeda não registrada: "+reward.currency()); }
                case command -> {
                    String command=reward.command().replace("{player}","ShirokaiTest").replace("{uuid}",UUID.randomUUID().toString()).replace("{transaction}",UUID.randomUUID().toString());
                    if(command.contains("{") || command.contains("}")) throw new IllegalArgumentException("Placeholder de comando desconhecido");
                    var parsed=server.getCommandManager().getDispatcher().parse(command,server.getCommandSource().withLevel(4));
                    if(!parsed.getExceptions().isEmpty() || parsed.getReader().canRead()) throw new IllegalArgumentException("Comando não registrado ou incompleto: "+command);
                }
            }
        } catch(Exception ex) { throw new IllegalArgumentException("Prêmio "+reward.name()+": "+ex.getMessage(),ex); }
    }
    public ItemStack item(Reward reward) throws Exception {
        validateItem(reward.item());
        String components=reward.components();
        // Minecraft's component syntax is parsed by its actual registry-aware argument parser.
        String input=reward.item();
        if(components!=null && !components.equals("{}")) {
            var json=com.google.gson.JsonParser.parseString(components).getAsJsonObject();
            List<String> entries=new ArrayList<>();
            json.entrySet().forEach(e->entries.add(e.getKey()+"="+e.getValue()));
            input+="["+String.join(",",entries)+"]";
        }
        var access=CommandRegistryAccess.of(server.getRegistryManager(),server.getOverworld().getEnabledFeatures());
        return ItemStackArgumentType.itemStack(access).parse(new StringReader(input)).createStack(reward.count(),false);
    }
    public Transaction prepare(ServerPlayerEntity player,Transaction tx) {
        if(tx.reward().type()!=Kind.pokemon)return tx;
        var pokemon=PokemonProperties.Companion.parse(tx.reward().properties()).create(player);
        if(tx.reward().ivs()!=null) {
            int[] ivs=dev.shirokaicrates.service.PokemonIvs.roll(tx.reward().ivs(),new java.security.SecureRandom());
            var stats=new com.cobblemon.mod.common.api.pokemon.stats.Stats[]{
                com.cobblemon.mod.common.api.pokemon.stats.Stats.HP,com.cobblemon.mod.common.api.pokemon.stats.Stats.ATTACK,
                com.cobblemon.mod.common.api.pokemon.stats.Stats.DEFENCE,com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_ATTACK,
                com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_DEFENCE,com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED};
            for(int i=0;i<6;i++)pokemon.setIV(stats[i],ivs[i]);
        }
        pokemon.setUuid(UUID.fromString(tx.pokemonUuid()));
        return tx.withPokemon(pokemon.saveToJSON(server.getRegistryManager(),new com.google.gson.JsonObject()).toString());
    }
    public enum Outcome { SUCCESS, RETRY, UNCERTAIN }
    public CompletableFuture<Outcome> deliver(ServerPlayerEntity player,Transaction tx) {
        try {
            Reward reward=tx.reward();
            return switch(reward.type()) {
                case item -> {
                    ItemStack stack=item(reward);
                    if(!fits(player,stack)) yield CompletableFuture.completedFuture(Outcome.RETRY);
                    insert(player,stack); yield CompletableFuture.completedFuture(Outcome.SUCCESS);
                }
                case pokemon -> {
                    if(tx.pokemonJson()==null || tx.pokemonJson().isBlank())throw new IllegalStateException("Snapshot de Pokémon ausente");
                    var pokemon=new com.cobblemon.mod.common.pokemon.Pokemon().loadFromJSON(server.getRegistryManager(),com.google.gson.JsonParser.parseString(tx.pokemonJson()).getAsJsonObject());
                    if(!pokemon.getUuid().toString().equals(tx.pokemonUuid()))throw new IllegalStateException("UUID de Pokémon divergente");
                    if(Cobblemon.INSTANCE.getStorage().getParty(player).get(pokemon.getUuid())!=null || Cobblemon.INSTANCE.getStorage().getPC(player).get(pokemon.getUuid())!=null)
                        yield CompletableFuture.completedFuture(Outcome.SUCCESS);
                    // Cobblemon's player storage add method chooses party then PC.
                    boolean added=Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon);
                    if(!added) added=Cobblemon.INSTANCE.getStorage().getPC(player).add(pokemon);
                    yield CompletableFuture.completedFuture(added?Outcome.SUCCESS:Outcome.RETRY);
                }
                case currency -> {
                    var economy=EconomyService.instance();
                    var currency=economy.currencies().currency(Key.key(reward.currency())).orElseThrow();
                    yield economy.account(currency,player.getUuid()).thenComposeAsync(account->account.depositAsync(reward.amount())
                            .thenCompose(report->report.successful()?economy.save(account).thenApply(saved->Outcome.SUCCESS):CompletableFuture.completedFuture(Outcome.UNCERTAIN)));
                }
                case command -> {
                    if(!player.getGameProfile().getName().matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException("Nome de jogador não seguro para placeholder de comando");
                    String command=reward.command().replace("{player}",player.getGameProfile().getName())
                            .replace("{uuid}",player.getUuidAsString()).replace("{transaction}",tx.id().toString());
                    dev.shirokaicrates.ShirokaiCrates.LOG.info("Executando prêmio comando: transaction={} player={} command={}",tx.id(),player.getUuid(),command);
                    int result=server.getCommandManager().getDispatcher().execute(command,server.getCommandSource().withLevel(4));
                    yield CompletableFuture.completedFuture(result>0?Outcome.SUCCESS:Outcome.UNCERTAIN);
                }
            };
        } catch(Exception | LinkageError ex) {
            dev.shirokaicrates.ShirokaiCrates.LOG.error("Entrega incerta {}",tx.id(),ex);
            return CompletableFuture.completedFuture(Outcome.UNCERTAIN);
        }
    }
    public static boolean fits(ServerPlayerEntity player,ItemStack stack) {
        int remaining=stack.getCount();
        for(int i=0;i<36;i++) {
            ItemStack slot=player.getInventory().getStack(i);
            if(slot.isEmpty()) remaining-=stack.getMaxCount();
            else if(ItemStack.areItemsAndComponentsEqual(slot,stack)) remaining-=Math.max(0,slot.getMaxCount()-slot.getCount());
            if(remaining<=0) return true;
        }
        return false;
    }
    public static void insert(ServerPlayerEntity player,ItemStack stack) {
        int remaining=stack.getCount();
        for(int pass=0;pass<2;pass++) for(int i=0;i<36 && remaining>0;i++) {
            ItemStack slot=player.getInventory().getStack(i);
            if(pass==0 && !slot.isEmpty() && ItemStack.areItemsAndComponentsEqual(slot,stack)) {
                int n=Math.min(remaining,slot.getMaxCount()-slot.getCount()); slot.increment(n); remaining-=n;
            } else if(pass==1 && slot.isEmpty()) {
                int n=Math.min(remaining,stack.getMaxCount()); player.getInventory().setStack(i,stack.copyWithCount(n)); remaining-=n;
            }
        }
        if(remaining>0) throw new IllegalStateException("Inventário mudou durante inserção");
        player.getInventory().markDirty(); player.currentScreenHandler.sendContentUpdates();
    }
}
