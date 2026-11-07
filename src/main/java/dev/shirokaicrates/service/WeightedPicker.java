package dev.shirokaicrates.service;
import dev.shirokaicrates.config.Models.Reward;
import java.util.List;
import java.util.random.RandomGenerator;
public final class WeightedPicker {
    private WeightedPicker() {}
    public static Reward pick(List<Reward> rewards, RandomGenerator random) {
        double total=total(rewards), ticket=random.nextDouble(total);
        for(Reward reward: rewards) { ticket-=reward.weight(); if(ticket<0) return reward; }
        return rewards.getLast();
    }
    public static double total(List<Reward> rewards) {
        if(rewards.isEmpty()) throw new IllegalArgumentException("Sem prêmios");
        double sum=0;
        for(Reward r:rewards) { if(!Double.isFinite(r.weight()) || r.weight()<=0) throw new IllegalArgumentException("Peso inválido"); sum+=r.weight(); }
        if(!Double.isFinite(sum)) throw new IllegalArgumentException("Soma inválida"); return sum;
    }
    public static double percentage(Reward r,List<Reward> rewards) { return 100*r.weight()/total(rewards); }
}
