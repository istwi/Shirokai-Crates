package dev.shirokaicrates.storage;

import dev.shirokaicrates.ShirokaiCrates;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static java.nio.file.StandardOpenOption.*;

/** Serializes playerdata writes, including vanilla saves of managed players, without blocking ticks. */
public final class PlayerCheckpoint implements AutoCloseable {
    private final ExecutorService io=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("shirokai-playerdata").factory());
    private final Set<UUID> managed=ConcurrentHashMap.newKeySet();
    private final Map<UUID,NbtCompound> latest=new ConcurrentHashMap<>();
    private final Map<UUID,Integer> barriers=new ConcurrentHashMap<>();
    private volatile IOException failure;
    public boolean healthy() { return failure==null; }
    public boolean managed(UUID id) { return managed.contains(id); }
    public boolean blocked(UUID id) { return barriers.getOrDefault(id,0)>0; }
    public NbtCompound latest(UUID id) { NbtCompound value=latest.get(id);return value==null?null:value.copy(); }
    public CompletableFuture<Void> save(ServerPlayerEntity player,boolean barrier) {
        if(!healthy())return CompletableFuture.failedFuture(new IOException("Playerdata persistência bloqueada por uma falha anterior",failure));
        UUID id=player.getUuid(); managed.add(id);
        // NBT and inventory are accessed only on the server thread; the writer receives an isolated snapshot.
        NbtCompound snapshot=new NbtCompound(); player.writeNbt(snapshot);
        snapshot.putInt("DataVersion",SharedConstants.getGameVersion().getSaveVersion().getId());
        Path directory=player.getServer().getSavePath(WorldSavePath.PLAYERDATA);
        latest.put(id,snapshot);
        if(barrier)barriers.merge(id,1,Integer::sum);
        CompletableFuture<Void> future=CompletableFuture.runAsync(()->{
            try { write(directory,id,snapshot);latest.remove(id,snapshot); }
            catch(IOException ex) { failure=ex;throw new CompletionException(ex); }
        },io);
        future.whenComplete((ignored,error)->{if(barrier)barriers.compute(id,(key,n)->n==null||n<=1?null:n-1);});
        return future;
    }
    public void vanillaSave(ServerPlayerEntity player) {
        save(player,false).exceptionally(ex->{ShirokaiCrates.LOG.error("Falha no salvamento coordenado de {}",player.getUuid(),ex);return null;});
    }
    static void write(Path directory,UUID id,NbtCompound snapshot) throws IOException {
        Files.createDirectories(directory);
        Path target=directory.resolve(id+".dat");
        if(Files.isSymbolicLink(target) || Files.isSymbolicLink(directory))throw new IOException("Playerdata symlink");
        Path temporary=Files.createTempFile(directory,".shirokai-",".dat");
        try {
            NbtIo.writeCompressed(snapshot,temporary);
            try(FileChannel channel=FileChannel.open(temporary,WRITE)) { channel.force(true); }
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            AtomicFiles.syncDirectory(directory);
        } finally { Files.deleteIfExists(temporary); }
    }
    @Override public void close() {
        io.shutdown();try { if(!io.awaitTermination(30,TimeUnit.SECONDS))io.shutdownNow(); }
        catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
}
