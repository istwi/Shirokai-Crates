package dev.shirokaicrates.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static java.nio.file.StandardOpenOption.*;

public final class AtomicFiles {
    private AtomicFiles() {}
    /** No unsafe fallback: a filesystem without atomic replacement is not suitable for the ledger. */
    public static void write(Path target,String text) throws IOException {
        Files.createDirectories(target.getParent());
        if(Files.isSymbolicLink(target) || Files.isSymbolicLink(target.getParent())) throw new IOException("Symlink em dados persistentes");
        Path temporary=Files.createTempFile(target.getParent(),".write-",".tmp");
        try {
            try(FileChannel channel=FileChannel.open(temporary,WRITE)) {
                ByteBuffer bytes=StandardCharsets.UTF_8.encode(text);
                while(bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            try(FileChannel directory=FileChannel.open(target.getParent(),READ)) { directory.force(true); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
