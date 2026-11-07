package dev.shirokaicrates.service;

import dev.shirokaicrates.config.Models.IvSettings;
import java.util.random.RandomGenerator;

public final class PokemonIvs {
    private PokemonIvs() {}
    /** Guarantee N randomly selected perfect stats; all other stats remain uniformly random in 0..31. */
    public static int[] roll(IvSettings settings,RandomGenerator random) {
        if(settings==null || !("random".equals(settings.mode()) || "perfect".equals(settings.mode())) || settings.perfectCount()<0 || settings.perfectCount()>6 || (settings.mode().equals("random") && settings.perfectCount()!=0))
            throw new IllegalArgumentException("Política de IVs inválida");
        int[] values=new int[6],order={0,1,2,3,4,5};
        for(int i=0;i<6;i++)values[i]=random.nextInt(32);
        if(settings.mode().equals("perfect"))for(int i=0;i<settings.perfectCount();i++) {
            int j=i+random.nextInt(6-i),chosen=order[j];order[j]=order[i];order[i]=chosen;values[chosen]=31;
        }
        return values;
    }
}
