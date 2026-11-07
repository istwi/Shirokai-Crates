package dev.shirokaicrates;

import dev.shirokaicrates.config.*;
import dev.shirokaicrates.config.Models.*;
import dev.shirokaicrates.storage.*;
import dev.shirokaicrates.storage.Transaction.State;
import dev.shirokaicrates.service.WeightedPicker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    @TempDir Path temp;
    Path config() throws Exception {
        Path config=temp.resolve("config");
        try(var paths=Files.walk(Path.of("src/main/resources/defaults"))) {
            for(Path p:paths.toList()) {
                Path target=config.resolve(Path.of("src/main/resources/defaults").relativize(p));
                if(Files.isDirectory(p))Files.createDirectories(target);else Files.copy(p,target);
            }
        }
        return config;
    }
    Snapshot load(Path config) throws Exception { return new ConfigLoader(config).load(r->{},i->{},s->{}); }
    @Test void createsEditableCrateWithIndependentKeyAndPersists() throws Exception {
        Path c=config();
        Path file=new ConfigLoader(c).create("shiny3");
        Crate created=load(c).crates().get("shiny3");
        assertEquals("shiny3",created.keyType());
        assertEquals("minecraft:tripwire_hook",created.key().item());
        assertEquals("minecraft:amethyst_shard",created.fragment().item());
        assertEquals(4,created.fragmentsRequired());
        assertEquals("minecraft:diamond",created.rewards().getFirst().item());
        Files.writeString(file,Files.readString(file).replace("minecraft:diamond","minecraft:emerald"));
        assertEquals("minecraft:emerald",new ConfigLoader(c).load(r->{},i->{},s->{}).crates().get("shiny3").rewards().getFirst().item());
    }
    @Test void creationRejectsInvalidIdsAndNeverOverwritesFilesOrSymlinks() throws Exception {
        Path c=config();ConfigLoader loader=new ConfigLoader(c);
        for(String id:List.of("../escape","UPPER","", "a".repeat(65)))
            assertThrows(IllegalArgumentException.class,()->loader.create(id));
        Path existing=c.resolve("crates/shiny1.json");String original=Files.readString(existing);
        assertThrows(FileAlreadyExistsException.class,()->loader.create("shiny1"));
        assertEquals(original,Files.readString(existing));
        Path link=c.resolve("crates/link.json");Files.createSymbolicLink(link,temp.resolve("absent.json"));
        assertThrows(FileAlreadyExistsException.class,()->loader.create("link"));
        Files.delete(link);
        Path directory=c.resolve("crates");Files.move(directory,c.resolve("saved-crates"));
        Files.createSymbolicLink(directory,c.resolve("saved-crates"));
        assertThrows(java.io.IOException.class,()->loader.create("blocked"));
    }
    @Test void concurrentCreationPublishesOneCompleteFileWithoutTemporaryFiles() throws Exception {
        Path c=config();ConfigLoader loader=new ConfigLoader(c);
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(8)) {
            List<java.util.concurrent.Future<Boolean>> attempts=new ArrayList<>();
            for(int i=0;i<8;i++) attempts.add(workers.submit(()->{
                try { loader.create("race");return true; }
                catch(FileAlreadyExistsException expected) { return false; }
            }));
            int winners=0;for(var attempt:attempts)if(attempt.get())winners++;
            assertEquals(1,winners);
        }
        assertNotNull(load(c).crates().get("race"));
        try(var files=Files.list(c.resolve("crates"))) {
            assertFalse(files.anyMatch(file->file.getFileName().toString().startsWith(".create-")));
        }
    }
    @Test void idsComeFromFilenamesAndExamplesLoad() throws Exception {
        Snapshot s=load(config());assertEquals(Set.of("shiny1","shiny2","lendaria","mega","evento_natal"),s.crates().keySet());
        assertEquals("shiny1",s.crates().get("shiny1").id());assertFalse(s.crates().containsKey("missing"));
    }
    @Test void rejectsTraversalUppercaseAndSymbols() {
        for(String invalid:List.of("../foo","a/b","SHINY","a.b","", "á"))assertFalse(ConfigLoader.ID.matcher(invalid).matches());
        for(String valid:List.of("shiny1","evento_natal","evento-2","1","_evento","-evento"))assertTrue(ConfigLoader.ID.matcher(valid).matches());
    }
    @Test void rejectsInvalidFilenameAndSymlink() throws Exception {
        Path c=config();Files.copy(c.resolve("crates/shiny1.json"),c.resolve("crates/Invalid.json"));assertThrows(Exception.class,()->load(c));
        Files.delete(c.resolve("crates/Invalid.json"));Files.createSymbolicLink(c.resolve("crates/evil.json"),c.resolve("crates/shiny1.json"));assertThrows(Exception.class,()->load(c));
    }
    @Test void rejectsDuplicateJsonFields() throws Exception {
        Path p=temp.resolve("duplicate.json");Files.writeString(p,"{\"x\":1,\"x\":2}");assertThrows(Exception.class,()->ConfigLoader.read(p));
    }
    @Test void rejectsExplicitIdAndInvalidWeight() throws Exception {
        Path c=config(),p=c.resolve("crates/shiny1.json");String original=Files.readString(p);
        Files.writeString(p,original.replaceFirst("\\{","{\"id\":\"fake\","));assertThrows(Exception.class,()->load(c));
        Files.writeString(p,original.replace("\"weight\": 60","\"weight\": -1"));assertThrows(Exception.class,()->load(c));
    }
    @Test void rejectsFractionalCountsAndZeroWeight() throws Exception {
        Path c=config(),p=c.resolve("crates/shiny1.json");String original=Files.readString(p);
        Files.writeString(p,original.replace("\"count\": 3","\"count\": 1.5"));assertThrows(Exception.class,()->load(c));
        Files.writeString(p,original.replace("\"weight\": 60","\"weight\": 0"));assertThrows(Exception.class,()->load(c));
    }
    @Test void reloadKeepsSnapshotAndPhysicalRegistrationsIndependent() throws Exception {
        Path c=config();Snapshot before=load(c);BlockRegistry blocks=new BlockRegistry(temp.resolve("blocks.json"));
        var p1=new BlockRegistry.Position("minecraft:overworld",1,64,1);var p2=new BlockRegistry.Position("minecraft:the_nether",2,70,3);
        blocks.put(new BlockRegistry.Entry(p1,"shiny1","minecraft:chest"));blocks.put(new BlockRegistry.Entry(p2,"shiny1","minecraft:barrel"));
        Path crate=c.resolve("crates/shiny1.json");Files.writeString(crate,Files.readString(crate).replace("✦ Caixa Shiny I","Novo nome"));
        Snapshot after=load(c);assertNotEquals(before.crates().get("shiny1").name(),after.crates().get("shiny1").name());
        assertEquals("shiny1",blocks.get(p1).crateId());assertEquals("shiny1",blocks.get(p2).crateId());
        assertEquals(2,new BlockRegistry(temp.resolve("blocks.json")).all().size());
        Files.writeString(crate,"{broken");assertThrows(Exception.class,()->load(c));assertEquals(2,blocks.all().size());
        blocks.put(new BlockRegistry.Entry(p1,"shiny2","minecraft:chest"));assertEquals("shiny2",blocks.get(p1).crateId());assertEquals("shiny1",blocks.get(p2).crateId());
        blocks.remove(p1);assertNull(blocks.get(p1));assertEquals(1,blocks.all().size());
    }
    @Test void immutableRewardSurvivesReloadAndLedgerRestart() throws Exception {
        Path c=config();Crate crate=load(c).crates().get("shiny1");
        Transaction tx=Transaction.reserve(UUID.randomUUID(),"Tester",crate,crate.rewards().getFirst());
        Path data=temp.resolve("transactions");
        try(Ledger l=new Ledger(data)) { l.create(tx).join();l.update(tx.id(),t->t.transition(State.READY,"")).join(); }
        Files.writeString(c.resolve("crates/shiny1.json"),"{}");
        try(Ledger l=new Ledger(data)) { assertEquals(State.READY,l.get(tx.id()).state());assertEquals(tx.reward(),l.get(tx.id()).reward());assertEquals(1,l.pending(tx.player()).size()); }
    }
    @Test void terminalStatesCannotBeClaimedAgain() throws Exception {
        Crate c=load(config()).crates().get("shiny1");Transaction t=Transaction.reserve(UUID.randomUUID(),"T",c,c.rewards().getFirst());
        t=t.transition(State.READY,"").transition(State.DELIVERING,"").transition(State.DELIVERED,"");
        Transaction done=t;assertThrows(IllegalStateException.class,()->done.transition(State.DELIVERING,""));
    }
    @Test void oneConcurrentDeliveryWins() throws Exception {
        Crate c=load(config()).crates().get("shiny1");Transaction t=Transaction.reserve(UUID.randomUUID(),"T",c,c.rewards().getFirst());
        try(Ledger l=new Ledger(temp.resolve("ledger"))) {
            l.create(t).join();l.update(t.id(),tx->tx.transition(State.READY,"")).join();
            var a=l.update(t.id(),tx->tx.transition(State.DELIVERING,""));var b=l.update(t.id(),tx->tx.transition(State.DELIVERING,""));
            a.join();assertThrows(Exception.class,b::join);assertTrue(l.healthy());
        }
    }
    @Test void weightedDrawMatchesConfiguredDistribution() throws Exception {
        var rewards=load(config()).crates().get("shiny1").rewards();Random random=new Random(123);Map<Rarity,Integer> count=new EnumMap<>(Rarity.class);
        for(int i=0;i<100000;i++)count.merge(WeightedPicker.pick(rewards,random).rarity(),1,Integer::sum);
        for(Reward r:rewards)assertEquals(WeightedPicker.percentage(r,rewards),count.get(r.rarity())/1000.0,0.6);
    }
    @Test void externalUncertaintyRequiresExplicitTransition() throws Exception {
        Crate c=load(config()).crates().get("shiny1");Transaction t=Transaction.reserve(UUID.randomUUID(),"T",c,c.rewards().getFirst())
                .transition(State.READY,"").transition(State.DELIVERING,"").transition(State.UNCERTAIN,"timeout");
        Transaction uncertain=t;assertThrows(IllegalStateException.class,()->uncertain.transition(State.DELIVERING,""));
        assertEquals(State.READY,t.transition(State.READY,"manual").state());
    }
    @Test void atomicFileFailureDoesNotReplaceOriginal() throws Exception {
        Path p=temp.resolve("state.json");AtomicFiles.write(p,"old");
        AtomicFiles.write(p,"new");assertEquals("new",Files.readString(p));
        Path link=temp.resolve("symlink.json");Files.createSymbolicLink(link,p);assertThrows(Exception.class,()->AtomicFiles.write(link,"bad"));assertEquals("new",Files.readString(p));
    }
    @Test void corruptionIsNotSilentlyDiscarded() throws Exception {
        Path d=temp.resolve("ledger");Files.createDirectories(d);Files.writeString(d.resolve(UUID.randomUUID()+".json"),"{}");
        assertThrows(Exception.class,()->new Ledger(d));
    }
    @Test void announcementThreshold() { assertFalse(Rarity.common.announced());assertFalse(Rarity.rare.announced());assertTrue(Rarity.epic.announced());assertTrue(Rarity.legendary.announced());assertTrue(Rarity.special.announced()); }
    @Test void storageFailureBlocksFurtherWrites() throws Exception {
        Crate c=load(config()).crates().get("shiny1");Transaction t=Transaction.reserve(UUID.randomUUID(),"T",c,c.rewards().getFirst());
        Path data=temp.resolve("data");
        try(Ledger l=new Ledger(data)) {
            Files.setPosixFilePermissions(data,java.nio.file.attribute.PosixFilePermissions.fromString("r-x------"));
            try {
                var original=assertThrows(java.util.concurrent.CompletionException.class,()->l.create(t).join());
                assertFalse(l.healthy());
                var blocked=assertThrows(java.util.concurrent.CompletionException.class,()->l.create(t).join());
                assertSame(original.getCause(),blocked.getCause().getCause(),"Blocked operations retain the original disk error");
            }
            finally { Files.setPosixFilePermissions(data,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")); }
        }
    }
    @Test void pruningNeverDeletesPendingEntitlements() throws Exception {
        Crate c=load(config()).crates().get("shiny1");Transaction pending=Transaction.reserve(UUID.randomUUID(),"T",c,c.rewards().getFirst());
        Transaction delivered=Transaction.reserve(UUID.randomUUID(),"T",c,c.rewards().getFirst());
        try(Ledger l=new Ledger(temp.resolve("ledger"))) {
            l.create(pending).join();l.update(pending.id(),t->t.transition(State.READY,"")).join();
            l.create(delivered).join();l.update(delivered.id(),t->t.transition(State.READY,"").transition(State.DELIVERING,"").transition(State.DELIVERED,"")).join();
            assertEquals(1,l.prune(90,0).join());assertNotNull(l.get(pending.id()));assertNull(l.get(delivered.id()));
        }
    }
    @Test void independentConcurrentPlayersKeepUniqueTransactions() throws Exception {
        Crate c=load(config()).crates().get("shiny1");
        try(Ledger l=new Ledger(temp.resolve("ledger"))) {
            List<Transaction> transactions=new ArrayList<>();List<java.util.concurrent.CompletableFuture<Transaction>> writes=new ArrayList<>();
            for(int i=0;i<32;i++) { Transaction t=Transaction.reserve(UUID.randomUUID(),"P"+i,c,c.rewards().getFirst());transactions.add(t);writes.add(l.create(t)); }
            java.util.concurrent.CompletableFuture.allOf(writes.toArray(java.util.concurrent.CompletableFuture[]::new)).join();
            assertEquals(32,transactions.stream().map(Transaction::id).distinct().count());
            for(Transaction t:transactions)assertEquals(1,l.pending(t.player()).size());
        }
    }
}
