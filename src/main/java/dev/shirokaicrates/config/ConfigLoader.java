package dev.shirokaicrates.config;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.shirokaicrates.config.Models.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Builds a complete immutable candidate. The caller publishes it only after runtime validation. */
public final class ConfigLoader {
    public static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,64}");
    private final Path root;
    public ConfigLoader(Path root) { this.root = root; }
    /** Publish a fully written file without replacing an existing file or symlink. */
    public Path create(String id) throws IOException {
        if (!ID.matcher(id).matches()) throw new IllegalArgumentException("ID inválido: use 1..64 letras minúsculas, números, _ ou -.");
        Path directory = root.resolve("crates");
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory))
            throw new IOException("Pasta crates inválida");
        JsonObject template = JsonParser.parseString("""
                {"name":"","key":{"item":"minecraft:tripwire_hook","name":"","lore":[]},
                 "fragment":{"item":"minecraft:amethyst_shard","name":"","lore":["Converta 4 fragmentos com /crates fragments."]},
                 "fragmentsRequired":4,"permission":"shirokaicrates.use",
                 "rewards":[{"name":"Diamante de exemplo","type":"item","item":"minecraft:diamond","count":1,"rarity":"common","weight":1}]}
                """).getAsJsonObject();
        template.addProperty("name", "Caixa " + id);
        template.getAsJsonObject("key").addProperty("name", "Chave " + id);
        template.getAsJsonObject("key").getAsJsonArray("lore").add("Utilize esta chave na Caixa " + id + ".");
        template.getAsJsonObject("fragment").addProperty("name", "Fragmento " + id);
        Path target = directory.resolve(id + ".json");
        Path temporary = Files.createTempFile(directory, ".create-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(new GsonBuilder().setPrettyPrinting().create().toJson(template) + "\n");
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            // Atomic link creation fails if target exists, unlike ATOMIC_MOVE's platform-dependent replacement.
            Files.createLink(target, temporary);
        } finally { Files.deleteIfExists(temporary); }
        dev.shirokaicrates.storage.AtomicFiles.syncDirectory(directory);
        return target;
    }
    public Snapshot load(Consumer<Reward> rewardValidator, Consumer<String> itemValidator,
                         Consumer<String> soundValidator) throws IOException {
        JsonObject global = read(root.resolve("config.json"));
        Settings settings = new Settings(integer(global,"maxConcurrent",32,1,256),
                integer(global,"historyDays",90,1,3650), integer(global,"maxHistoryEntries",100000,100,1000000),
                integer(global,"targetDistance",6,1,16), blockEffects(global));
        JsonObject a = read(root.resolve("animations/horizontal.json"));
        Animation animation = new Animation(integer(a,"durationTicks",100,20,600),
                integer(a,"initialInterval",1,1,10), decimal(a,"deceleration",0.06,0,1),
                string(a,"tickSound","minecraft:block.note_block.hat"),
                string(a,"winSound","minecraft:entity.player.levelup"),
                string(a,"highlightItem","minecraft:gold_ingot"), bool(a,"particles",true));
        itemValidator.accept(animation.highlightItem()); soundValidator.accept(animation.tickSound()); soundValidator.accept(animation.winSound());
        Map<String,String> messages = new HashMap<>();
        read(root.resolve("messages.json")).entrySet().forEach(e -> messages.put(e.getKey(),e.getValue().getAsString()));
        Path directory = root.resolve("crates");
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory))
            throw new IOException("Pasta crates inválida");
        Map<String,Crate> crates = new TreeMap<>();
        try (var paths = Files.list(directory)) {
            for (Path file : paths.sorted().toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".json")) continue;
                String id = name.substring(0,name.length()-5);
                if (!ID.matcher(id).matches()) throw new IOException("ID inválido: " + name);
                try {
                    JsonObject json = read(file);
                    if (json.has("id")) throw new IllegalArgumentException("O ID vem do nome do arquivo; remova o campo id");
                    Token key = token(json.getAsJsonObject("key"));
                    Token fragment = token(json.getAsJsonObject("fragment"));
                    itemValidator.accept(key.item()); itemValidator.accept(fragment.item());
                    String keyType = string(json,"keyType",id);
                    if (!ID.matcher(keyType).matches()) throw new IllegalArgumentException("keyType inválido");
                    JsonArray entries = json.getAsJsonArray("rewards");
                    if (entries == null || entries.isEmpty() || entries.size() > 500) throw new IllegalArgumentException("Esperado 1..500 prêmios");
                    List<Reward> rewards = new ArrayList<>();
                    for (JsonElement entry : entries) {
                        JsonObject r = entry.getAsJsonObject();
                        Kind type = Kind.valueOf(required(r,"type"));
                        boolean direct = entries.get(0).getAsJsonObject().has("chance");
                        if (r.has("chance") != direct || (direct && r.has("weight")))
                            throw new IllegalArgumentException("Use chance em todos os prêmios ou weight em todos, sem misturar");
                        if (!r.has(direct?"chance":"weight")) throw new IllegalArgumentException("Cada prêmio precisa de chance ou weight");
                        BigDecimal weight = r.get(direct?"chance":"weight").getAsBigDecimal().stripTrailingZeros();
                        if (weight.signum()<=0 || weight.scale()>12 || weight.compareTo(new BigDecimal(direct?"100":"1000000000000"))>0)
                            throw new IllegalArgumentException("Chance/peso deve ser positivo, com até 12 casas decimais e dentro do limite");
                        int count = integer(r,"count",1,1,2304);
                        String item = string(r,"item","");
                        String icon = string(r,"icon",type==Kind.item?item:"minecraft:nether_star");
                        String properties = string(r,"properties","");
                        // Explicit fields map to Cobblemon's documented PokemonProperties grammar.
                        if (type==Kind.pokemon && properties.isBlank()) {
                            properties = required(r,"species") + " level=" + integer(r,"level",1,1,100)
                                    + " shiny=" + bool(r,"shiny",false);
                            for (String attribute : List.of("nature","ability"))
                                if (r.has(attribute)) properties += " " + attribute + "=" + r.get(attribute).getAsString();
                        }
                        IvSettings ivs = null;
                        if (type==Kind.pokemon) {
                            List<String> attributes=List.of("hp_iv","attack_iv","defence_iv","special_attack_iv","special_defence_iv","speed_iv");
                            if (r.has("ivs")) {
                                JsonObject policy=r.getAsJsonObject("ivs");
                                if (policy.keySet().stream().anyMatch(k->!Set.of("mode","perfectCount").contains(k)))
                                    throw new IllegalArgumentException("Campos de ivs aceitos: mode e perfectCount");
                                String mode=required(policy,"mode");
                                if (!Set.of("random","perfect").contains(mode)) throw new IllegalArgumentException("ivs.mode deve ser random ou perfect");
                                int perfect=integer(policy,"perfectCount",mode.equals("perfect")?3:0,0,6);
                                if (mode.equals("random") && perfect!=0) throw new IllegalArgumentException("perfectCount exige mode perfect");
                                if (attributes.stream().anyMatch(r::has) || properties.matches("(?is).*\\b(?:ivs|(?:hp|attack|defence|special_attack|special_defence|speed)_iv)\\s*=.*"))
                                    throw new IllegalArgumentException("Não misture ivs com IVs fixos ou properties com IVs");
                                ivs=new IvSettings(mode,perfect);
                            } else {
                                for (String attribute:attributes) if (r.has(attribute)) {
                                    if (!string(r,"properties","").isBlank()) throw new IllegalArgumentException("Defina os IVs em properties ou nos campos individuais, sem misturar");
                                    properties += " " + attribute + "=" + integer(r,attribute,0,0,31);
                                }
                            }
                        } else if (r.has("ivs")) throw new IllegalArgumentException("ivs só pode ser usado em prêmios pokemon");
                        Reward reward = new Reward(required(r,"name"),type,
                                Rarity.valueOf(string(r,"rarity","common")),weight,icon,item,count,
                                r.has("components")?r.get("components").toString():"{}",properties,
                                string(r,"currency",""),r.has("amount")?r.get("amount").getAsBigDecimal():BigDecimal.ZERO,
                                string(r,"command",""),ivs);
                        if (type==Kind.currency && (!Set.of("impactor:dollars","impactor:tokens").contains(reward.currency())
                                || reward.amount().signum() <= 0 || reward.amount().precision()>30)) throw new IllegalArgumentException("Moeda/quantia inválida");
                        if (type==Kind.command && (reward.command().isBlank() || reward.command().startsWith("/")
                                || reward.command().contains("\n") || reward.command().contains("\r") || reward.command().length()>2048))
                            throw new IllegalArgumentException("Comando inválido");
                        itemValidator.accept(icon); rewardValidator.accept(reward); rewards.add(reward);
                    }
                    if (entries.get(0).getAsJsonObject().has("chance") && rewards.stream().map(Reward::weight).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(new BigDecimal("100"))!=0)
                        throw new IllegalArgumentException("As chances dos prêmios precisam somar exatamente 100%");
                    Crate crate = new Crate(id,required(json,"name"),keyType,key,fragment,
                            integer(json,"fragmentsRequired",4,2,64),string(json,"permission","shirokaicrates.use"),rewards);
                    if (crates.putIfAbsent(id,crate)!=null) throw new IllegalArgumentException("ID duplicado");
                } catch (RuntimeException ex) { throw new IOException(name + ": " + ex.getMessage(),ex); }
            }
        }
        // Shared keys are explicit and must use identical physical token definitions.
        Map<String,Token> keys = new HashMap<>();
        for (Crate crate : crates.values()) {
            Token previous = keys.putIfAbsent(crate.keyType(),crate.key());
            if (previous != null && !previous.equals(crate.key())) throw new IOException("Chave compartilhada conflitante: " + crate.keyType());
        }
        return new Snapshot(crates,settings,animation,messages);
    }
    private static BlockEffects blockEffects(JsonObject global) {
        JsonObject e=global.has("crateEffects")?global.getAsJsonObject("crateEffects"):new JsonObject();
        String particle=string(e,"particle","end_rod");
        if (!Set.of("end_rod","enchant","happy_villager","flame").contains(particle)) throw new IllegalArgumentException("Partícula de crate inválida");
        return new BlockEffects(bool(e,"enabled",true),particle,decimal(e,"radius",0.7,0.1,2),decimal(e,"height",1.2,0,3),
                integer(e,"intervalTicks",5,2,100),integer(e,"viewDistance",32,8,32));
    }
    public static JsonObject read(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path)>1048576)
            throw new IOException("Arquivo inválido ou maior que 1 MiB: " + path.getFileName());
        try (Reader input=Files.newBufferedReader(path); JsonReader reader=new JsonReader(input)) {
            reader.setLenient(false);
            JsonElement result = parse(reader,0);
            if (reader.peek()!=JsonToken.END_DOCUMENT || !result.isJsonObject()) throw new IOException("JSON deve ser um objeto único");
            return result.getAsJsonObject();
        } catch (RuntimeException ex) { throw new IOException(path.getFileName()+": "+ex.getMessage(),ex); }
    }
    private static JsonElement parse(JsonReader r,int depth) throws IOException {
        if (depth>32) throw new IOException("JSON muito profundo");
        return switch(r.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject o=new JsonObject(); r.beginObject();
                while(r.hasNext()) { String k=r.nextName(); if(o.has(k)) throw new IOException("Campo duplicado: "+k); o.add(k,parse(r,depth+1)); }
                r.endObject(); yield o;
            }
            case BEGIN_ARRAY -> { JsonArray a=new JsonArray(); r.beginArray(); while(r.hasNext()) a.add(parse(r,depth+1)); r.endArray(); yield a; }
            case STRING -> new JsonPrimitive(r.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(r.nextString()));
            case BOOLEAN -> new JsonPrimitive(r.nextBoolean());
            case NULL -> { r.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IOException("JSON inválido");
        };
    }
    private static Token token(JsonObject j) {
        if(j==null) throw new IllegalArgumentException("key/fragment obrigatório");
        List<String> lore=new ArrayList<>(); if(j.has("lore")) j.getAsJsonArray("lore").forEach(l->lore.add(l.getAsString()));
        if(lore.size()>20) throw new IllegalArgumentException("Lore muito longa");
        return new Token(required(j,"item"),required(j,"name"),lore);
    }
    private static String required(JsonObject j,String k) { String s=string(j,k,""); if(s.isBlank() || s.length()>2048) throw new IllegalArgumentException(k+" obrigatório/ inválido"); return s; }
    private static String string(JsonObject j,String k,String d) { return j.has(k)?j.get(k).getAsString():d; }
    private static boolean bool(JsonObject j,String k,boolean d) { return j.has(k)?j.get(k).getAsBoolean():d; }
    private static int integer(JsonObject j,String k,int d,int min,int max) {
        int n=j.has(k)?j.get(k).getAsBigDecimal().intValueExact():d;
        if(n<min || n>max) throw new IllegalArgumentException(k+" fora de "+min+".."+max); return n;
    }
    private static double decimal(JsonObject j,String k,double d,double min,double max) {
        double n=j.has(k)?j.get(k).getAsDouble():d;
        if(!Double.isFinite(n) || n<min || n>max) throw new IllegalArgumentException(k+" inválido"); return n;
    }
}
