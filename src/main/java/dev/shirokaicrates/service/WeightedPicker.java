package dev.shirokaicrates.service;

import dev.shirokaicrates.config.Models.Reward;
import java.math.*;
import java.util.*;
import java.util.random.RandomGenerator;

/** Integer tickets preserve even very small decimal weights without floating-point cancellation. */
public final class WeightedPicker {
    private WeightedPicker() {}
    private record Distribution(BigInteger[] cumulative, BigInteger total) {}
    private static final Map<List<Reward>,Distribution> CACHE=Collections.synchronizedMap(new WeakHashMap<>());
    public static Reward pick(List<Reward> rewards,RandomGenerator random) {
        Distribution d=CACHE.computeIfAbsent(rewards,WeightedPicker::distribution);
        BigInteger ticket=randomBelow(d.total(),random);
        int low=0,high=d.cumulative().length-1;
        while(low<high) { int mid=(low+high)>>>1;if(ticket.compareTo(d.cumulative()[mid])<0)high=mid;else low=mid+1; }
        return rewards.get(low);
    }
    private static Distribution distribution(List<Reward> rewards) {
        total(rewards);
        int scale=rewards.stream().mapToInt(r->r.weight().scale()).max().orElse(0);
        BigInteger sum=BigInteger.ZERO;BigInteger[] cumulative=new BigInteger[rewards.size()];
        for(int i=0;i<rewards.size();i++) { sum=sum.add(rewards.get(i).weight().movePointRight(scale).toBigIntegerExact());cumulative[i]=sum; }
        return new Distribution(cumulative,sum);
    }
    static BigInteger randomBelow(BigInteger bound,RandomGenerator random) {
        int bits=bound.bitLength();byte[] bytes=new byte[(bits+7)/8];BigInteger value;
        do { random.nextBytes(bytes);bytes[0]&=(byte)(0xff>>>(bytes.length*8-bits));value=new BigInteger(1,bytes); } while(value.compareTo(bound)>=0);
        return value;
    }
    public static BigDecimal total(List<Reward> rewards) {
        if(rewards.isEmpty())throw new IllegalArgumentException("Sem prêmios");
        BigDecimal sum=BigDecimal.ZERO;
        for(Reward r:rewards) { if(r.weight()==null || r.weight().signum()<=0)throw new IllegalArgumentException("Peso inválido");sum=sum.add(r.weight()); }
        return sum;
    }
    public static double percentage(Reward r,List<Reward> rewards) { return r.weight().multiply(BigDecimal.valueOf(100)).divide(total(rewards),20,RoundingMode.HALF_UP).doubleValue(); }
    public static String percentageText(Reward r,List<Reward> rewards) {
        BigDecimal exact=r.weight().multiply(BigDecimal.valueOf(100)).divide(total(rewards),24,RoundingMode.HALF_UP);
        BigDecimal displayed=exact.setScale(6,RoundingMode.HALF_UP);
        if(displayed.signum()==0)displayed=exact.setScale(12,RoundingMode.HALF_UP);
        if(displayed.signum()==0)return "<0,000000000001%";
        return displayed.stripTrailingZeros().toPlainString().replace('.',',')+"%";
    }
}
