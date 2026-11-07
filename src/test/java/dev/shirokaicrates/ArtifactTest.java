package dev.shirokaicrates;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

/** Inspect the actual remapped delivery artifacts, not the unprocessed source resources. */
class ArtifactTest {
    private ZipFile installable() throws IOException { return new ZipFile(System.getProperty("shirokai.installableJar")); }
    private JsonObject json(ZipFile zip,String name) throws IOException {
        var entry=zip.getEntry(name);assertNotNull(entry,"Missing resource: "+name);
        try(var input=zip.getInputStream(entry)) { return JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject(); }
    }
    private void compiledClass(ZipFile zip,String name) throws IOException {
        var entry=zip.getEntry(name.replace('.','/')+".class");assertNotNull(entry,"Missing compiled class: "+name);
        try(var input=new DataInputStream(zip.getInputStream(entry))) {
            assertEquals(0xCAFEBABE,input.readInt(),"Invalid class header: "+name);
            input.readUnsignedShort();assertEquals(65,input.readUnsignedShort(),"Class must target Java 21: "+name);
        }
    }
    @Test void installableHasExpandedVersionAndCompiledEntrypoints() throws Exception {
        try(var zip=installable()) {
            var mod=json(zip,"fabric.mod.json");
            assertEquals(System.getProperty("shirokai.expectedVersion"),mod.get("version").getAsString());
            assertFalse(mod.toString().contains("${"),"Unexpanded template in executable mod metadata");
            assertEquals("server",mod.get("environment").getAsString());
            assertEquals("1.21.1",mod.getAsJsonObject("depends").get("minecraft").getAsString());
            for(var entry:mod.getAsJsonObject("entrypoints").entrySet())
                for(var value:entry.getValue().getAsJsonArray())compiledClass(zip,value.isJsonPrimitive()?value.getAsString():value.getAsJsonObject().get("value").getAsString());
        }
    }
    @Test void everyDeclaredMixinIsCompiledAndWorldProtectionRemainsRegistered() throws Exception {
        try(var zip=installable()) {
            var mod=json(zip,"fabric.mod.json");boolean worldProtection=false;int count=0;
            for(var entry:mod.getAsJsonArray("mixins")) {
                var config=json(zip,entry.isJsonPrimitive()?entry.getAsString():entry.getAsJsonObject().get("config").getAsString());
                assertEquals("JAVA_21",config.get("compatibilityLevel").getAsString());
                for(String side:new String[]{"mixins","server","client"})if(config.has(side))for(var name:config.getAsJsonArray(side)) {
                    String type=config.get("package").getAsString()+"."+name.getAsString();
                    compiledClass(zip,type);count++;
                    worldProtection|=type.equals("dev.shirokaicrates.mixin.WorldMixin");
                }
            }
            assertTrue(count>0);assertTrue(worldProtection,"WorldMixin must not be removed to hide a packaging error");
        }
    }
    @Test void sourcesCannotBeMistakenForAnInstallableFabricMod() throws Exception {
        try(var zip=new ZipFile(System.getProperty("shirokai.sourcesJar"))) {
            assertNull(zip.getEntry("fabric.mod.json"),"Fabric must not recognize the source archive as a mod");
            assertNull(zip.getEntry("shirokaicrates.mixins.json"),"Source archives must not advertise uncompiled mixins");
            assertNotNull(zip.getEntry("dev/shirokaicrates/mixin/WorldMixin.java"));
            assertFalse(zip.stream().anyMatch(e->e.getName().endsWith(".class")));
        }
    }
}
