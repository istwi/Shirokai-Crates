package dev.shirokaicrates.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class Models {
    private Models() {}
    public enum Kind { item, pokemon, currency, command }
    public enum Rarity {
        common, rare, epic, legendary, special;
        public boolean announced() { return ordinal() >= epic.ordinal(); }
    }
    public record Token(String item, String name, List<String> lore) {
        public Token { lore = List.copyOf(lore); }
    }
    public record Reward(String name, Kind type, Rarity rarity, BigDecimal weight, String icon,
                         String item, int count, String components, String properties,
                         String currency, BigDecimal amount, String command, IvSettings ivs) {}
    public record IvSettings(String mode, int perfectCount) {}
    public record Crate(String id, String name, String keyType, Token key, Token fragment,
                        int fragmentsRequired, String permission, List<Reward> rewards) {
        public Crate { rewards = List.copyOf(rewards); }
    }
    public record Animation(int durationTicks, int initialInterval, double deceleration,
                            String tickSound, String winSound, String highlightItem, boolean particles) {}
    public record Settings(int maxConcurrent, int historyDays, int maxHistoryEntries, int targetDistance, BlockEffects crateEffects) {}
    public record BlockEffects(boolean enabled, String particle, double radius, double height, int intervalTicks, int viewDistance) {
        public static BlockEffects defaults() { return new BlockEffects(true,"end_rod",0.7,1.2,5,32); }
    }
    public record Snapshot(Map<String, Crate> crates, Settings settings, Animation animation,
                           Map<String, String> messages) {
        public Snapshot { crates = Map.copyOf(crates); messages = Map.copyOf(messages); }
    }
}
