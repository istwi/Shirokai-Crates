package dev.shirokaicrates.storage;

import dev.shirokaicrates.config.ConfigLoader;
import dev.shirokaicrates.service.Tokens;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Run on actual Windows and Linux providers: directory flushes cannot be emulated by changing os.name. */
class PortableStorageTest {
    @TempDir Path temp;
    @Test void atomicWritesReplaceCompleteFilesAndCanSyncParent() throws Exception {
        Path file=temp.resolve("data/state.json");
        AtomicFiles.write(file,"old");AtomicFiles.write(file,"new");
        AtomicFiles.syncDirectory(file.getParent());
        assertEquals("new",Files.readString(file));
    }
    @Test void registeredBlockSurvivesReopeningRegistry() throws Exception {
        Path file=temp.resolve("world/shirokaicrates/blocks.json");
        var position=new BlockRegistry.Position("minecraft:overworld",1,64,2);
        var entry=new BlockRegistry.Entry(position,"shiny3","minecraft:chest");
        new BlockRegistry(file).put(entry);
        assertEquals(entry,new BlockRegistry(file).get(position));
    }
    @Test void playerCheckpointWritesReadableNbtAndReplacesPreviousCheckpoint() throws Exception {
        Path directory=temp.resolve("world/playerdata");UUID player=UUID.randomUUID();
        NbtCompound snapshot=new NbtCompound();snapshot.putString("test","old");
        PlayerCheckpoint.write(directory,player,snapshot);
        snapshot.putString("test","new");PlayerCheckpoint.write(directory,player,snapshot);
        assertEquals("new",NbtIo.readCompressed(directory.resolve(player+".dat"),NbtSizeTracker.ofUnlimitedBytes()).getString("test"));
    }
    @Test void freshTokenSecretCanBeCreatedAndReusedWithoutPosixPermissions() throws Exception {
        Path secret=temp.resolve("world/shirokaicrates/token-secret.bin");
        new Tokens(secret);byte[] original=Files.readAllBytes(secret);
        assertEquals(32,original.length);new Tokens(secret);
        assertArrayEquals(original,Files.readAllBytes(secret));
    }
    @Test void crateCreationPublishesCompleteJsonAndNeverReplacesIt() throws Exception {
        Path config=temp.resolve("config");Files.createDirectories(config.resolve("crates"));
        ConfigLoader loader=new ConfigLoader(config);Path file=loader.create("shiny3");
        String original=Files.readString(file);
        assertEquals("minecraft:tripwire_hook",ConfigLoader.read(file).getAsJsonObject("key").get("item").getAsString());
        assertThrows(FileAlreadyExistsException.class,()->loader.create("shiny3"));
        assertEquals(original,Files.readString(file));
    }
}
