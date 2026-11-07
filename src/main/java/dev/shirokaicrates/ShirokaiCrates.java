package dev.shirokaicrates;

import dev.shirokaicrates.integration.Permissions;
import dev.shirokaicrates.service.CrateService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.WorldSavePath;
import com.mojang.brigadier.arguments.*;
import org.slf4j.*;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

public final class ShirokaiCrates implements ModInitializer {
    public static final Logger LOG=LoggerFactory.getLogger("ShirokaiCrates");
    private static volatile CrateService service;
    private static volatile dev.shirokaicrates.storage.BlockRegistry emergencyBlocks;
    public static CrateService service() { return service; }
    public static boolean protectedBlock(net.minecraft.world.World world,BlockPos pos) {
        var current=service;
        return current!=null?current.protectedBlock(world,pos):emergencyBlocks!=null && emergencyBlocks.get(CrateService.position(world,pos))!=null;
    }
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            try {
                emergencyBlocks=new dev.shirokaicrates.storage.BlockRegistry(server.getSavePath(WorldSavePath.ROOT).resolve("shirokaicrates/blocks.json"));
                service=new CrateService(server,FabricLoader.getInstance().getConfigDir().resolve("shirokaicrates"),server.getSavePath(WorldSavePath.ROOT).resolve("shirokaicrates"));LOG.info("Shirokai Crates: {} caixas carregadas",service.snapshot().crates().size()); }
            catch(Exception | LinkageError ex) { LOG.error("Shirokai Crates desativado por falha de inicialização; login permanece disponível",ex); }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{var s=service;if(s!=null)s.close();service=null;});
        ServerTickEvents.END_SERVER_TICK.register(server->{var s=service;if(s!=null)try{s.tick();}catch(Exception | LinkageError ex){LOG.error("Falha de tick de crates",ex);}});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{var s=service;if(s!=null)try{s.joined(handler.player);}catch(Exception | LinkageError ex){LOG.error("Recuperação de crates falhou",ex);}});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{var s=service;if(s!=null)s.disconnected(handler.player);});
        ServerPlayerEvents.COPY_FROM.register((oldPlayer,newPlayer,alive)->{var s=service;if(s!=null)s.copyReceipts(oldPlayer,newPlayer);});
        Identifier phase=Identifier.of("shirokaicrates","after_protection");
        UseBlockCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE,phase);
        UseBlockCallback.EVENT.register(phase,(player,world,hand,hit)->{
            var s=service;
            if(world.isClient || !protectedBlock(world,hit.getBlockPos()))return ActionResult.PASS;
            if(s==null)return ActionResult.FAIL;
            if(hand==Hand.MAIN_HAND && player instanceof ServerPlayerEntity p)try{s.interact(p,hit.getBlockPos());}catch(Exception | LinkageError ex){LOG.error("Interação de crates falhou",ex);}
            return ActionResult.FAIL;
        });
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,entity)->{return !protectedBlock(world,pos);});
        CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->{
            var root=literal("crates").executes(c->run(c.getSource(),p->{if(service.allowed(p,"shirokaicrates.use"))service.menus().crates(p,0);}));
            root.then(literal("preview").then(argument("id",StringArgumentType.word()).suggests((c,b)->suggest(b)).executes(c->run(c.getSource(),p->service.menus().preview(p,StringArgumentType.getString(c,"id"),0)))));
            root.then(literal("fragments").executes(c->run(c.getSource(),p->service.menus().fragments(p,0))));
            root.then(literal("pending").executes(c->run(c.getSource(),p->service.menus().pending(p,0))));
            root.then(literal("setblock").requires(s->permitted(s,"shirokaicrates.admin")).then(argument("id",StringArgumentType.word()).suggests((c,b)->suggest(b)).executes(c->run(c.getSource(),p->{BlockPos pos=target(p);if(pos!=null)service.register(p,pos,StringArgumentType.getString(c,"id"));}))));
            root.then(literal("removeblock").requires(s->permitted(s,"shirokaicrates.admin")).executes(c->run(c.getSource(),p->{BlockPos pos=target(p);if(pos!=null)service.remove(p,pos);})));
            root.then(literal("reload").requires(s->permitted(s,"shirokaicrates.reload")).executes(c->run(c.getSource(),p->service.reload(p))));
            root.then(literal("list").requires(s->permitted(s,"shirokaicrates.admin")).executes(c->run(c.getSource(),p->service.tell(p,String.join(", ",new TreeSet<>(service.snapshot().crates().keySet()))))));
            for(boolean fragment:new boolean[]{false,true})root.then(literal(fragment?"givefragment":"givekey").requires(s->permitted(s,"shirokaicrates.give"))
                    .then(argument("player",EntityArgumentType.player()).then(argument("id",StringArgumentType.word()).suggests((c,b)->suggest(b))
                            .then(argument("count",IntegerArgumentType.integer(1,2304)).executes(c->{ServerPlayerEntity recipient=EntityArgumentType.getPlayer(c,"player");return run(c.getSource(),p->service.give(p,recipient,StringArgumentType.getString(c,"id"),IntegerArgumentType.getInteger(c,"count"),fragment));})))));
            root.then(literal("history").requires(s->permitted(s,"shirokaicrates.admin")).then(argument("player",GameProfileArgumentType.gameProfile()).executes(c->{
                var profiles=GameProfileArgumentType.getProfileArgument(c,"player");
                return run(c.getSource(),p->{for(var profile:profiles)service.ledger().player(profile.getId()).stream().limit(30).forEach(tx->service.tell(p,tx.created()+" | "+tx.crateId()+" | "+tx.reward().name()+" | "+tx.state()+" | "+tx.id()));});
            })));
            var reconcile=literal("reconcile").requires(s->permitted(s,"shirokaicrates.admin"));
            var transaction=argument("transaction",StringArgumentType.word());
            for(String action:List.of("delivered","retry","cancel"))transaction.then(literal(action).executes(c->run(c.getSource(),p->service.reconcile(p,UUID.fromString(StringArgumentType.getString(c,"transaction")),action))));
            root.then(reconcile.then(transaction)); dispatcher.register(root);
        });
    }
    private static boolean permitted(ServerCommandSource source,String node) { return source.getEntity() instanceof ServerPlayerEntity p && (Permissions.has(p,node) || Permissions.has(p,"shirokaicrates.admin")); }
    private static int run(ServerCommandSource source,java.util.function.Consumer<ServerPlayerEntity> action) {
        if(service==null) { source.sendError(Text.literal("Shirokai Crates indisponível; veja o console."));return 0; }
        if(!(source.getEntity() instanceof ServerPlayerEntity p)) { source.sendError(Text.literal("Execute este comando como jogador."));return 0; }
        try { action.accept(p);return 1; }catch(Exception | LinkageError ex){LOG.error("Comando de crates falhou",ex);source.sendError(Text.literal("Operação rejeitada; veja o console."));return 0;}
    }
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        if(service!=null)new TreeSet<>(service.snapshot().crates().keySet()).stream().filter(id->id.startsWith(builder.getRemaining())).forEach(builder::suggest);return builder.buildFuture();
    }
    private static BlockPos target(ServerPlayerEntity player) {
        HitResult result=player.raycast(service.snapshot().settings().targetDistance(),1,false);
        if(result instanceof BlockHitResult block && result.getType()==HitResult.Type.BLOCK)return block.getBlockPos();service.tell(player,"Olhe para um bloco dentro do alcance.");return null;
    }
}
