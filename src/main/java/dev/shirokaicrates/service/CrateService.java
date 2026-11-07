package dev.shirokaicrates.service;

import dev.shirokaicrates.ShirokaiCrates;
import dev.shirokaicrates.config.*;
import dev.shirokaicrates.config.Models.*;
import dev.shirokaicrates.integration.*;
import dev.shirokaicrates.storage.*;
import dev.shirokaicrates.storage.Transaction.State;
import dev.shirokaicrates.ui.*;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class CrateService implements AutoCloseable {
    private final MinecraftServer server;
    private final ConfigLoader loader;
    private final BlockRegistry blocks;
    private final Ledger ledger;
    private final Tokens tokens;
    private final Rewards rewards;
    private final Menus menus;
    private final SecureRandom random=new SecureRandom();
    private volatile Snapshot active;
    private final PlayerCheckpoint checkpoints=new PlayerCheckpoint();
    private boolean configurationValid=true;
    private boolean reloading;
    private final ExecutorService configIo=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("shirokai-config").factory());
    private final Set<UUID> busy=new HashSet<>();
    private final Map<UUID,Session> animations=new HashMap<>();
    private int ticks;
    private static final class Session {
        final Transaction tx; final Crate crate; final Animation animation; final CrateMenu menu;
        int elapsed=0,next=0;
        final List<Reward> reel=new ArrayList<>();
        Session(Transaction tx,Crate crate,Animation animation,CrateMenu menu) { this.tx=tx;this.crate=crate;this.animation=animation;this.menu=menu; }
    }
    public CrateService(MinecraftServer server,Path config,Path data) throws IOException {
        this.server=server; Files.createDirectories(data);
        defaults(config);
        loader=new ConfigLoader(config); blocks=new BlockRegistry(data.resolve("blocks.json"));
        ledger=new Ledger(data.resolve("transactions")); tokens=new Tokens(data.resolve("token-secret.bin"));
        rewards=new Rewards(server); menus=new Menus(this);
        try {
            active=loader.load(rewards::validate,Rewards::validateItem,Rewards::validateSound);
            prune();
        } catch(Exception ex) {
            configurationValid=false;
            active=new Snapshot(Map.of(),new Settings(32,90,100000,6),new Animation(100,1,0.06,"minecraft:block.note_block.hat","minecraft:entity.player.levelup","minecraft:gold_ingot",true),Map.of());
            ShirokaiCrates.LOG.error("Configuração inicial rejeitada. Registros físicos protegidos; corrija os JSON e use /crates reload.",ex);
        }
    }
    public Snapshot snapshot() { return active; }
    public Ledger ledger() { return ledger; }
    public PlayerCheckpoint checkpoints() { return checkpoints; }
    public Menus menus() { return menus; }
    public boolean healthy() { return configurationValid && checkpoints.healthy() && ledger.healthy(); }
    public static BlockRegistry.Position position(World world,BlockPos pos) {
        return new BlockRegistry.Position(world.getRegistryKey().getValue().toString(),pos.getX(),pos.getY(),pos.getZ());
    }
    public boolean protectedBlock(World world,BlockPos pos) { return blocks.get(position(world,pos))!=null; }
    public boolean allowed(ServerPlayerEntity player,String permission) {
        if(Permissions.has(player,permission) || (!permission.equals("shirokaicrates.admin") && Permissions.has(player,"shirokaicrates.admin"))) return true;
        message(player,"permission"); return false;
    }
    public void message(ServerPlayerEntity player,String key) { tell(player,active.messages().getOrDefault(key,key)); }
    public void tell(ServerPlayerEntity player,String message) { player.sendMessage(Text.literal("✦ Shirokai | "+message),false); }
    private void fail(Throwable error) { ShirokaiCrates.LOG.error("Operação de crates falhou; confira a persistência",error); }
    private <T> void completion(CompletableFuture<T> future,Consumer<T> success,UUID unlock) {
        future.whenComplete((result,error)->server.execute(()->{
            if(error!=null) { fail(error); if(unlock!=null) { busy.remove(unlock); var p=server.getPlayerManager().getPlayer(unlock); if(p!=null)message(p,"storageFailure"); } return; }
            try { success.accept(result); }
            catch(Exception | LinkageError ex) { fail(ex); if(unlock!=null)busy.remove(unlock); }
        }));
    }
    public void register(ServerPlayerEntity player,BlockPos pos,String id) {
        if(!allowed(player,"shirokaicrates.admin"))return;
        Crate crate=active.crates().get(id); if(crate==null) { message(player,"unknownId"); return; }
        var state=player.getWorld().getBlockState(pos);
        if(state.isAir() || state.isOf(Blocks.MOVING_PISTON) || state.isOf(Blocks.PISTON_HEAD)
                || state.getBlock() instanceof net.minecraft.block.ChestBlock && state.get(net.minecraft.block.ChestBlock.CHEST_TYPE)!=net.minecraft.block.enums.ChestType.SINGLE) { tell(player,"Bloco incompatível."); return; }
        var entry=new BlockRegistry.Entry(position(player.getWorld(),pos),id,Registries.BLOCK.getId(state.getBlock()).toString());
        completion(ledger.submit(()->{ blocks.put(entry);return entry; }),saved->tell(player,crate.name()+" registrada com sucesso!"),null);
    }
    public void remove(ServerPlayerEntity player,BlockPos pos) {
        if(!allowed(player,"shirokaicrates.admin"))return;
        var position=position(player.getWorld(),pos);
        if(blocks.get(position)==null) { tell(player,"Este bloco não está registrado."); return; }
        completion(ledger.submit(()->{ blocks.remove(position);return true; }),done->tell(player,"Registro removido; o bloco foi preservado."),null);
    }
    public void reload(ServerPlayerEntity player) {
        if(!allowed(player,"shirokaicrates.reload"))return;
        if(reloading)return; reloading=true;
        CompletableFuture.supplyAsync(()->{try{return loader.load(r->{},id->{},id->{});}catch(IOException ex){throw new CompletionException(ex);}},configIo).whenComplete((candidate,error)->server.execute(()->{
            try {
                if(error!=null)throw new CompletionException(error);
                // Validate against live registries on the server thread before the single publication.
                for(Crate c:candidate.crates().values()) {
                    Rewards.validateItem(c.key().item()); Rewards.validateItem(c.fragment().item());
                    for(Reward r:c.rewards()) { Rewards.validateItem(r.icon()); rewards.validate(r); }
                }
                Rewards.validateItem(candidate.animation().highlightItem());
                Rewards.validateSound(candidate.animation().tickSound()); Rewards.validateSound(candidate.animation().winSound());
                active=candidate; configurationValid=true; tell(player,"Configuração recarregada. Todos os blocos já usam os novos arquivos.");
            } catch(Exception | LinkageError ex) { ShirokaiCrates.LOG.error("Reload rejeitado; configuração e blocos preservados",ex); tell(player,"Reload rejeitado. Veja o console; a configuração anterior foi preservada."); }
            finally { reloading=false; }
        }));
    }
    public void interact(ServerPlayerEntity player,BlockPos pos) {
        var entry=blocks.get(position(player.getWorld(),pos)); if(entry==null)return;
        Crate crate=active.crates().get(entry.crateId());
        if(crate==null || !entry.blockType().equals(Registries.BLOCK.getId(player.getWorld().getBlockState(pos).getBlock()).toString()) || !healthy()) { message(player,"unavailable");return; }
        if(player.isSpectator() || !player.isAlive() || !allowed(player,"shirokaicrates.use") || !allowed(player,crate.permission()))return;
        if(!tokens.matches(player.getMainHandStack(),crate,false)) { message(player,tokens.isKey(player.getMainHandStack())?"wrongKey":"noKey");return; }
        if(busy.contains(player.getUuid()) || busy.size()>=active.settings().maxConcurrent()) { message(player,"busy");return; }
        if(!player.currentScreenHandler.getCursorStack().isEmpty()) { tell(player,"Guarde o item que está no cursor antes de abrir.");return; }
        busy.add(player.getUuid());
        Transaction tx=rewards.prepare(player,Transaction.reserve(player.getUuid(),player.getGameProfile().getName(),crate,WeightedPicker.pick(crate.rewards(),random)));
        Animation animation=active.animation();
        completion(ledger.create(tx),reserved->{
            if(player.isDisconnected() || !tokens.matches(player.getMainHandStack(),crate,false)) {
                completion(ledger.update(tx.id(),t->t.transition(State.CANCELLED,"Chave não consumida")),t->busy.remove(player.getUuid()),player.getUuid());return;
            }
            player.getMainHandStack().decrement(1); receipts(player).put(tx.id().toString(),"OPENED");
            checkpoint(player,()->completion(ledger.update(tx.id(),t->t.transition(State.READY,"")),ready->{
                if(player.isDisconnected()) { busy.remove(player.getUuid());return; }
                CrateMenu menu=CrateMenu.open(player,"✦ SHIROKAI - CAIXAS",slot->{});
                if(menu==null) { deliverLocked(player,ready);return; }
                menu.set(13,Menus.icon(animation.highlightItem(),"§6▼ PRÊMIO ▼"));
                menu.set(31,Menus.icon(animation.highlightItem(),"§6▲ PRÊMIO ▲"));
                animations.put(player.getUuid(),new Session(ready,crate,animation,menu));
            },player.getUuid()));
        },player.getUuid());
    }
    private Map<String,String> receipts(ServerPlayerEntity player) { return ((PlayerReceipts)player).shirokai$receipts(); }
    private void checkpoint(ServerPlayerEntity player,Runnable afterCommit) {
        player.stopUsingItem();
        checkpoints.save(player,true).whenComplete((ignored,error)->server.execute(()->{
            if(error!=null) { fail(error);busy.remove(player.getUuid());if(!player.isDisconnected())message(player,"storageFailure");return; }
            try { afterCommit.run(); }catch(Exception | LinkageError ex) { fail(ex);busy.remove(player.getUuid()); }
        }));
    }
    public void tick() {
        ticks++;
        var iterator=animations.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next(); Session session=entry.getValue(); ServerPlayerEntity player=server.getPlayerManager().getPlayer(entry.getKey());
            if(player==null) { iterator.remove();busy.remove(entry.getKey());continue; }
            session.elapsed++;
            if(session.elapsed>=session.animation.durationTicks()) {
                if(player.currentScreenHandler==session.menu) {
                    session.menu.set(22,Menus.rewardIcon(session.tx.reward(),session.crate.rewards()));
                    sound(player,session.animation.winSound());
                }
                if(session.animation.particles()) player.getServerWorld().spawnParticles(ParticleTypes.FIREWORK,player.getX(),player.getY()+1,player.getZ(),16,0.4,0.5,0.4,0.05);
                iterator.remove(); deliverLocked(player,session.tx);continue;
            }
            if(session.elapsed>=session.next && player.currentScreenHandler==session.menu) {
                if(session.reel.isEmpty())for(int i=0;i<9;i++)session.reel.add(WeightedPicker.pick(session.crate.rewards(),random));
                session.reel.removeFirst();session.reel.add(WeightedPicker.pick(session.crate.rewards(),random));
                for(int slot=18;slot<=26;slot++) session.menu.set(slot,Menus.rewardIcon(session.reel.get(slot-18),session.crate.rewards()));
                sound(player,session.animation.tickSound());
                double progress=(double)session.elapsed/session.animation.durationTicks();
                session.next=session.elapsed+Math.min(20,(int)Math.ceil(session.animation.initialInterval()+progress*progress*session.animation.deceleration()*session.animation.durationTicks()));
            }
        }
        if(ticks%72000==0) prune();
    }
    private void prune() {
        ledger.prune(active.settings().historyDays(),active.settings().maxHistoryEntries()).whenComplete((removed,error)->server.execute(()->{
            if(error!=null) { fail(error);return; }
            if(removed>0)for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList())
                receipts(player).keySet().removeIf(id->{try{return ledger.get(UUID.fromString(id))==null;}catch(IllegalArgumentException ex){return true;}});
        }));
    }
    private void sound(ServerPlayerEntity player,String id) { player.playSoundToPlayer(Registries.SOUND_EVENT.get(Identifier.of(id)),SoundCategory.PLAYERS,0.6f,1f); }
    public void claim(ServerPlayerEntity player,UUID id) {
        if(!allowed(player,"shirokaicrates.pending") || !healthy())return;
        Transaction tx=ledger.get(id);
        if(tx==null || !tx.player().equals(player.getUuid()) || tx.state()!=State.READY) { tell(player,"Prêmio indisponível para resgate automático. Estados incertos exigem reconciliação.");return; }
        if(!busy.add(player.getUuid())) { message(player,"busy");return; }
        deliverLocked(player,tx);
    }
    private void deliverLocked(ServerPlayerEntity player,Transaction tx) {
        if(player.isDisconnected()) { busy.remove(player.getUuid());return; }
        if("DELIVERED".equals(receipts(player).get(tx.id().toString()))) { finish(tx);return; }
        completion(ledger.update(tx.id(),t->t.transition(State.DELIVERING,"Entrega iniciada")),delivering->{
            if(player.isDisconnected()) { completion(ledger.update(tx.id(),t->t.transition(State.READY,"Jogador desconectado antes da entrega")),t->busy.remove(player.getUuid()),player.getUuid());return; }
            rewards.deliver(player,delivering).whenComplete((outcome,error)->server.execute(()->{
                if(error!=null) { outcomeUncertain(player,delivering,error.getMessage());return; }
                if(outcome==Rewards.Outcome.RETRY) {
                    completion(ledger.update(tx.id(),t->t.transition(State.READY,"Sem espaço disponível")),t->{busy.remove(player.getUuid());message(player,"pending");},player.getUuid());
                } else if(outcome==Rewards.Outcome.UNCERTAIN) outcomeUncertain(player,delivering,"Resultado externo não confirmado; não repetir sem revisão");
                else {
                    ServerPlayerEntity live=server.getPlayerManager().getPlayer(tx.player());
                    if(live!=null) {
                        receipts(live).put(tx.id().toString(),"DELIVERED");
                        checkpoint(live,()->finish(delivering));
                    } else finish(delivering);
                }
            }));
        },player.getUuid());
    }
    private void outcomeUncertain(ServerPlayerEntity player,Transaction tx,String detail) {
        completion(ledger.update(tx.id(),t->t.transition(State.UNCERTAIN,detail)),t->{ busy.remove(player.getUuid()); message(player,"uncertain"); },player.getUuid());
    }
    private void finish(Transaction tx) {
        completion(ledger.update(tx.id(),t->(t.state()==State.READY?t.transition(State.DELIVERING,"Recibo de entrega já confirmado"):t).transition(State.DELIVERED,"").announcementClaimed()),done->{
            busy.remove(done.player()); var player=server.getPlayerManager().getPlayer(done.player());
            if(player!=null)tell(player,"Você recebeu "+done.reward().name()+"!");
            if(!tx.announced() && done.reward().rarity().announced()) {
                String template=active.messages().getOrDefault("announcement","{player} recebeu {reward} na {crate}! [{rarity}]");
                server.getPlayerManager().broadcast(Text.literal("✦ Shirokai | "+template.replace("{player}",done.playerName())
                        .replace("{reward}",done.reward().name()).replace("{crate}",done.crateName()).replace("{rarity}",done.reward().rarity().name())),false);
            }
        },tx.player());
    }
    public void joined(ServerPlayerEntity player) {
        // Recovery is scheduled after login and never throws through the join event.
        server.execute(()->{
            try {
                if(!checkpoints.healthy()) { message(player,"storageFailure");return; }
                if(busy.contains(player.getUuid())) { message(player,"pending");return; }
                for(Transaction tx:ledger.pending(player.getUuid())) {
                    String receipt=receipts(player).get(tx.id().toString());
                    if(tx.state()==State.RESERVED) {
                        boolean consumed="OPENED".equals(receipt) || "DELIVERED".equals(receipt);
                        completion(ledger.update(tx.id(),t->t.transition(consumed?State.READY:State.CANCELLED,consumed?"Recuperada após reinício":"Consumo não persistido; chave preservada")),t->{if(consumed)message(player,"pending");},null);
                    } else if(tx.state()==State.DELIVERING) {
                        if("DELIVERED".equals(receipt))finish(tx);
                        else {
                            // Items and receipt share one atomic playerdata checkpoint; external stores do not.
                            State state=tx.reward().type()==Kind.item?State.READY:State.UNCERTAIN;
                            completion(ledger.update(tx.id(),t->t.transition(state,"Recuperação após interrupção")),t->message(player,"pending"),null);
                        }
                    }
                }
                receipts(player).keySet().removeIf(id->{try{return ledger.get(UUID.fromString(id))==null;}catch(IllegalArgumentException ex){return true;}});
                if(!ledger.pending(player.getUuid()).isEmpty())message(player,"pending");
            } catch(Exception | LinkageError ex) { fail(ex); }
        });
    }
    public void disconnected(ServerPlayerEntity player) { if(animations.remove(player.getUuid())!=null)busy.remove(player.getUuid()); /* In-flight ledger callbacks release their own locks. */ }
    public void copyReceipts(ServerPlayerEntity oldPlayer,ServerPlayerEntity newPlayer) { receipts(newPlayer).putAll(receipts(oldPlayer)); }
    public int fragments(ServerPlayerEntity player,Crate crate) {
        int count=0;for(int i=0;i<36;i++)if(tokens.matches(player.getInventory().getStack(i),crate,true))count+=player.getInventory().getStack(i).getCount();return count;
    }
    public void convert(ServerPlayerEntity player,String id) {
        if(!allowed(player,"shirokaicrates.fragments") || !healthy() || busy.contains(player.getUuid()))return;
        Crate crate=active.crates().get(id);if(crate==null)return;
        if(fragments(player,crate)<crate.fragmentsRequired()) { tell(player,"Fragmentos insuficientes.");return; }
        List<ItemStack> before=new ArrayList<>();for(int i=0;i<36;i++)before.add(player.getInventory().getStack(i).copy());
        int remaining=crate.fragmentsRequired();
        for(int i=0;i<36 && remaining>0;i++) {
            ItemStack stack=player.getInventory().getStack(i);if(tokens.matches(stack,crate,true)) { int n=Math.min(stack.getCount(),remaining);stack.decrement(n);remaining-=n; }
        }
        ItemStack key=tokens.create(crate,false,1);
        if(!Rewards.fits(player,key)) { for(int i=0;i<36;i++)player.getInventory().setStack(i,before.get(i));tell(player,"Sem espaço para a chave.");return; }
        Rewards.insert(player,key);
        busy.add(player.getUuid());
        checkpoint(player,()->{busy.remove(player.getUuid());tell(player,"Fragmentos convertidos em "+crate.key().name()+".");});
    }
    public void give(ServerPlayerEntity admin,ServerPlayerEntity target,String id,int count,boolean fragment) {
        if(!allowed(admin,"shirokaicrates.give") || !healthy())return;
        Crate crate=active.crates().get(id);if(crate==null) { message(admin,"unknownId");return; }
        ItemStack stack=tokens.create(crate,fragment,count);
        if(!Rewards.fits(target,stack)) { tell(admin,"O inventário do jogador não tem espaço.");return; }
        if(!busy.add(target.getUuid())) { message(admin,"busy");return; }
        Rewards.insert(target,stack);checkpoint(target,()->{busy.remove(target.getUuid());tell(admin,"Itens entregues a "+target.getGameProfile().getName()+".");});
    }
    public void reconcile(ServerPlayerEntity admin,UUID id,String action) {
        if(!allowed(admin,"shirokaicrates.admin"))return;
        Transaction tx=ledger.get(id);if(tx==null || tx.state()!=State.UNCERTAIN) { tell(admin,"Somente transações UNCERTAIN podem ser reconciliadas.");return; }
        State state=switch(action) { case "delivered"->State.DELIVERED;case "retry"->State.READY;case "cancel"->State.CANCELLED;default->throw new IllegalArgumentException("Ação inválida"); };
        ShirokaiCrates.LOG.warn("Reconciliação manual admin={} transaction={} action={}",admin.getUuid(),id,action);
        completion(ledger.update(id,t->t.transition(state,"Reconciliação manual por "+admin.getUuid())),done->tell(admin,"Transação reconciliada: "+done.state()),null);
    }
    private static void defaults(Path config) throws IOException {
        if(Files.exists(config.resolve("config.json")))return;
        for(String file:List.of("messages.json","animations/horizontal.json","crates/shiny1.json","crates/shiny2.json","crates/lendaria.json","crates/mega.json","crates/evento_natal.json","config.json")) {
            Path target=config.resolve(file); Files.createDirectories(target.getParent());
            if(!Files.exists(target)) try(InputStream input=CrateService.class.getResourceAsStream("/defaults/"+file)) {
                if(input==null)throw new IOException("Exemplo ausente: "+file);
                Files.copy(input,target);
            }
        }
    }
    @Override public void close() { animations.clear();busy.clear();configIo.shutdownNow();checkpoints.close();ledger.close(); }
}
