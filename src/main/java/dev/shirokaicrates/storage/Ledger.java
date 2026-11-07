package dev.shirokaicrates.storage;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.UnaryOperator;

/** All disk writes use one executor. Snapshots are published only after fsync + atomic rename. */
public final class Ledger implements AutoCloseable {
    private static final Gson JSON=new GsonBuilder().registerTypeAdapter(Instant.class,
            (JsonSerializer<Instant>)(s,t,c)->new JsonPrimitive(s.toString()))
            .registerTypeAdapter(Instant.class,(JsonDeserializer<Instant>)(j,t,c)->Instant.parse(j.getAsString())).setPrettyPrinting().create();
    private final Path directory;
    private final ConcurrentHashMap<UUID,Transaction> records=new ConcurrentHashMap<>();
    private final ExecutorService io=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("shirokai-ledger").factory());
    private volatile boolean healthy=true;
    public Ledger(Path directory) throws IOException {
        this.directory=directory; Files.createDirectories(directory);
        try(var files=Files.list(directory)) {
            for(Path file:files.filter(p->p.toString().endsWith(".json")).toList()) {
                Transaction tx=JSON.fromJson(dev.shirokaicrates.config.ConfigLoader.read(file),Transaction.class);
                if(tx==null || tx.id()==null || tx.player()==null || tx.state()==null || tx.created()==null || tx.reward()==null
                        || !file.getFileName().toString().equals(tx.id()+".json") || records.putIfAbsent(tx.id(),tx)!=null)
                    throw new IOException("Transação inválida: "+file.getFileName());
            }
        }
    }
    public boolean healthy() { return healthy; }
    public Transaction get(UUID id) { return records.get(id); }
    public List<Transaction> player(UUID player) {
        return records.values().stream().filter(t->t.player().equals(player)).sorted(Comparator.comparing(Transaction::created).reversed()).toList();
    }
    public List<Transaction> pending(UUID player) {
        return player(player).stream().filter(t->t.state()!=Transaction.State.DELIVERED && t.state()!=Transaction.State.CANCELLED).toList();
    }
    public CompletableFuture<Transaction> create(Transaction tx) {
        return submit(()->{ if(records.containsKey(tx.id())) throw new IOException("Transação duplicada"); persist(tx); return tx; });
    }
    public CompletableFuture<Transaction> update(UUID id,UnaryOperator<Transaction> fn) {
        return submit(()->{ Transaction current=Objects.requireNonNull(records.get(id),"Transação inexistente"); Transaction next=fn.apply(current); persist(next); return next; });
    }
    private void persist(Transaction tx) throws IOException { AtomicFiles.write(directory.resolve(tx.id()+".json"),JSON.toJson(tx)); records.put(tx.id(),tx); }
    public <T> CompletableFuture<T> submit(Callable<T> action) {
        if(!healthy) return CompletableFuture.failedFuture(new IOException("Persistência bloqueada"));
        return CompletableFuture.supplyAsync(()->{
            if(!healthy) throw new CompletionException(new IOException("Persistência bloqueada"));
            try { return action.call(); }
            catch(Exception ex) { if(ex instanceof IOException) healthy=false; throw new CompletionException(ex); }
        },io);
    }
    public CompletableFuture<Integer> prune(int days,int maxEntries) {
        return submit(()->{
            List<Transaction> terminal=records.values().stream().filter(t->t.state()==Transaction.State.DELIVERED || t.state()==Transaction.State.CANCELLED)
                    .sorted(Comparator.comparing(Transaction::created).reversed()).toList();
            Instant cutoff=Instant.now().minusSeconds(days*86400L); int removed=0;
            for(int i=0;i<terminal.size();i++) {
                Transaction tx=terminal.get(i);
                if(i>=maxEntries || tx.created().isBefore(cutoff)) { Files.delete(directory.resolve(tx.id()+".json")); records.remove(tx.id()); removed++; }
            }
            return removed;
        });
    }
    @Override public void close() { io.shutdown(); try { if(!io.awaitTermination(30,TimeUnit.SECONDS)) io.shutdownNow(); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); } }
}
