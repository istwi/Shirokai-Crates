package dev.shirokaicrates.service;

import dev.shirokaicrates.config.Models.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public final class Tokens {
    private final byte[] secret;
    public Tokens(Path secretPath) throws IOException {
        if(!Files.exists(secretPath)) {
            Files.createDirectories(secretPath.getParent());
            byte[] generated=new byte[32]; new SecureRandom().nextBytes(generated);
            Files.write(secretPath,generated,StandardOpenOption.CREATE_NEW);
            Files.setPosixFilePermissions(secretPath,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        }
        if(Files.isSymbolicLink(secretPath)) throw new IOException("Secret não pode ser symlink");
        secret=Files.readAllBytes(secretPath);
        if(secret.length!=32) throw new IOException("Secret inválido: preserve o arquivo original");
    }
    public ItemStack create(Crate crate,boolean fragment,int count) {
        Token token=fragment?crate.fragment():crate.key();
        ItemStack stack=new ItemStack(Registries.ITEM.get(Identifier.of(token.item())),count);
        stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal(token.name()));
        stack.set(DataComponentTypes.LORE,new LoreComponent(token.lore().stream().map(Text::literal).map(t->(Text)t).toList()));
        String id=fragment?crate.id():crate.keyType();
        NbtCompound nbt=new NbtCompound(); nbt.putString("kind",fragment?"fragment":"key"); nbt.putString("id",id);
        nbt.putString("signature",sign(fragment?"fragment":"key",id));
        NbtCompound data=new NbtCompound(); data.put("shirokaicrates",nbt);
        stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(data)); return stack;
    }
    public boolean matches(ItemStack stack,Crate crate,boolean fragment) {
        if(stack.isEmpty() || !Registries.ITEM.getId(stack.getItem()).toString().equals((fragment?crate.fragment():crate.key()).item())) return false;
        String id=fragment?crate.id():crate.keyType(), kind=fragment?"fragment":"key";
        NbtCompound nbt=stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt().getCompound("shirokaicrates");
        return kind.equals(nbt.getString("kind")) && id.equals(nbt.getString("id")) && MessageDigest.isEqual(
                sign(kind,id).getBytes(StandardCharsets.US_ASCII),nbt.getString("signature").getBytes(StandardCharsets.US_ASCII));
    }
    public boolean isKey(ItemStack stack) {
        return "key".equals(stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt().getCompound("shirokaicrates").getString("kind"));
    }
    private String sign(String kind,String id) {
        try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((kind+":"+id).getBytes(StandardCharsets.UTF_8)));
        } catch(GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
}
