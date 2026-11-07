package dev.shirokaicrates;

import com.google.gson.*;
import dev.shirokaicrates.config.*;
import dev.shirokaicrates.config.Models.*;
import dev.shirokaicrates.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FeaturesTest {
    @TempDir Path temp;
    Path config(String rewards) throws Exception {
        Path root=temp.resolve(UUID.randomUUID().toString()),defaults=Path.of("src/main/resources/defaults");
        try(var files=Files.walk(defaults)) {
            for(Path p:files.toList()) { Path dest=root.resolve(defaults.relativize(p));if(Files.isDirectory(p))Files.createDirectories(dest);else Files.copy(p,dest); }
        }
        Path file=new ConfigLoader(root).create("test");JsonObject crate=ConfigLoader.read(file);
        crate.add("rewards",JsonParser.parseString(rewards));Files.writeString(file,crate.toString());return root;
    }
    Snapshot load(Path root) throws Exception { return new ConfigLoader(root).load(r->{},i->{},s->{}); }
    List<Reward> rewards(String json) throws Exception { return load(config(json)).crates().get("test").rewards(); }
    static String item(String weight) { return "{\"name\":\"Item\",\"type\":\"item\",\"item\":\"minecraft:diamond\","+weight+"}"; }
    @Test void percentagesAreCompactAndVerySmallChancesNeverBecomeZero() throws Exception {
        var normal=rewards("["+item("\"weight\":60")+","+item("\"weight\":40")+"]");
        assertEquals("60%",WeightedPicker.percentageText(normal.getFirst(),normal));
        var rare=rewards("["+item("\"chance\":99.99")+","+item("\"chance\":0.01")+"]");
        assertEquals("0,01%",WeightedPicker.percentageText(rare.getLast(),rare));
        var tiny=rewards("["+item("\"weight\":1000000000000")+","+item("\"weight\":0.000000000001")+"]");
        assertEquals("<0,000000000001%",WeightedPicker.percentageText(tiny.getLast(),tiny));
    }
    @Test void directChancesMustAddToOneHundredWithoutMixingWeights() {
        for(String body:List.of("["+item("\"chance\":99")+"]", "["+item("\"chance\":100,\"weight\":1")+"]",
                "["+item("\"chance\":99")+","+item("\"weight\":1")+"]", "["+item("\"chance\":0")+"]",
                "["+item("\"weight\":0.0000000000001")+"]"))assertThrows(Exception.class,()->rewards(body));
    }
    private static Random ticket(BigInteger ticket) {
        return new Random(0) { @Override public void nextBytes(byte[] bytes) {
            Arrays.fill(bytes,(byte)0);byte[] value=ticket.toByteArray();int length=Math.min(value.length,bytes.length);
            System.arraycopy(value,value.length-length,bytes,bytes.length-length,length);
        }};
    }
    @Test void tinyLastRewardIsReachableAtExactIntegerBoundary() throws Exception {
        var r=rewards("["+item("\"chance\":99.999999999999")+","+item("\"chance\":0.000000000001")+"]");
        assertSame(r.getFirst(),WeightedPicker.pick(r,ticket(BigInteger.ZERO)));
        assertSame(r.getLast(),WeightedPicker.pick(r,ticket(new BigInteger("99999999999999"))));
    }
    @Test void perfectIvsHaveExactlyConfiguredCountAcrossAllSixStats() {
        Random random=new Random(123);
        for(int count=0;count<=6;count++)for(int i=0;i<1000;i++) {
            int[] values=PokemonIvs.roll(new IvSettings("perfect",count),random);
            assertEquals(count,Arrays.stream(values).filter(v->v==31).count());
            assertTrue(Arrays.stream(values).allMatch(v->v>=0 && v<=31));
        }
    }
    @Test void randomIvsAllowAllValuesIncludingNaturalPerfectStats() {
        Random random=new Random(19);Set<Integer> observed=new HashSet<>();
        for(int i=0;i<1000;i++)for(int value:PokemonIvs.roll(new IvSettings("random",0),random))observed.add(value);
        assertEquals(32,observed.size());assertTrue(observed.contains(0));assertTrue(observed.contains(31));
    }
    @Test void pokemonIvPoliciesLoadAndRejectConflictingOrInvalidSettings() throws Exception {
        String base="{\"name\":\"Pokemon\",\"type\":\"pokemon\",\"species\":\"pikachu\",\"weight\":1,";
        assertEquals(new IvSettings("perfect",3),rewards("["+base+"\"ivs\":{\"mode\":\"perfect\",\"perfectCount\":3}}]").getFirst().ivs());
        assertNull(rewards("["+base+"\"hp_iv\":31}]").getFirst().ivs());
        for(String fields:List.of("\"ivs\":{\"mode\":\"perfect\",\"perfectCount\":7}","\"ivs\":{\"mode\":\"random\",\"perfectCount\":1}",
                "\"ivs\":{\"mode\":\"perfect\",\"count\":6}", "\"ivs\":{\"mode\":\"bad\"}","\"hp_iv\":32", "\"hp_iv\":31,\"ivs\":{\"mode\":\"perfect\"}",
                "\"properties\":\"pikachu hp_iv=31\",\"ivs\":{\"mode\":\"random\"}"))
            assertThrows(Exception.class,()->rewards("["+base+fields+"}]"));
    }
    @Test void blockEffectsDefaultOnAndCanBeDisabledWithoutChangingCrates() throws Exception {
        Path root=config("["+item("\"weight\":1")+"]");
        assertTrue(load(root).settings().crateEffects().enabled());
        JsonObject global=ConfigLoader.read(root.resolve("config.json"));global.add("crateEffects",JsonParser.parseString("{\"enabled\":false}"));
        Files.writeString(root.resolve("config.json"),global.toString());assertFalse(load(root).settings().crateEffects().enabled());
    }
}
