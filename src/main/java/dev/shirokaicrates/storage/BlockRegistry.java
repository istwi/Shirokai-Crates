package dev.shirokaicrates.storage;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class BlockRegistry {
    public record Position(String dimension,int x,int y,int z) {}
    public record Entry(Position position,String crateId,String blockType) {}
    private final Path file;
    private volatile Map<Position,Entry> entries=Map.of();
    public BlockRegistry(Path file) throws IOException {
        this.file=file;
        if(Files.exists(file)) {
            List<Entry> loaded=new Gson().fromJson(dev.shirokaicrates.config.ConfigLoader.read(file).get("blocks"),new TypeToken<List<Entry>>(){}.getType());
            if(loaded==null) throw new IOException("Registros inválidos");
            Map<Position,Entry> next=new HashMap<>();
            for(Entry e:loaded) {
                if(e==null || e.position()==null || e.crateId()==null || !dev.shirokaicrates.config.ConfigLoader.ID.matcher(e.crateId()).matches()
                        || e.blockType()==null || e.position().dimension()==null || next.putIfAbsent(e.position(),e)!=null)
                    throw new IOException("Registro físico inválido/duplicado");
            }
            entries=Map.copyOf(next);
        }
    }
    public Entry get(Position p) { return entries.get(p); }
    public Collection<Entry> all() { return entries.values(); }
    public synchronized void put(Entry entry) throws IOException {
        Map<Position,Entry> next=new HashMap<>(entries); next.put(entry.position(),entry); commit(next);
    }
    public synchronized void remove(Position position) throws IOException {
        Map<Position,Entry> next=new HashMap<>(entries); next.remove(position); commit(next);
    }
    private void commit(Map<Position,Entry> next) throws IOException {
        JsonObject json=new JsonObject(); json.add("blocks",new Gson().toJsonTree(next.values()));
        AtomicFiles.write(file,new GsonBuilder().setPrettyPrinting().create().toJson(json)); entries=Map.copyOf(next);
    }
}
