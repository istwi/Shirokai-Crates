package dev.shirokaicrates.integration;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.item.PokemonItem;
import dev.shirokaicrates.config.Models.Reward;
import net.minecraft.item.ItemStack;
import java.util.*;

/** Common/server API only. Cached templates contain species/aspects, never a generated reward Pokémon. */
public final class PokemonIcons {
    private PokemonIcons() {}
    private static final Map<Reward,ItemStack> TEMPLATES=Collections.synchronizedMap(new WeakHashMap<>());
    public static ItemStack icon(Reward reward) { return TEMPLATES.computeIfAbsent(reward,PokemonIcons::create).copy(); }
    public static void clear() { TEMPLATES.clear(); }
    private static ItemStack create(Reward reward) {
        var properties=PokemonProperties.Companion.parse(reward.properties());
        var species=PokemonSpecies.getByName(properties.getSpecies());
        if(species==null)throw new IllegalArgumentException("Espécie inválida para o modelo: "+properties.getSpecies());
        properties.updateAspects();
        return PokemonItem.from(species,properties.getAspects().toArray(String[]::new));
    }
}
