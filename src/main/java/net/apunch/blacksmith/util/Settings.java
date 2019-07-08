package net.apunch.blacksmith.util;

import java.io.File;

import net.apunch.blacksmith.BlacksmithPlugin;

import net.citizensnpcs.api.util.DataKey;
import net.citizensnpcs.api.util.YamlStorage;

public class Settings {
    private final YamlStorage config;

    public Settings(BlacksmithPlugin plugin) {
        config = new YamlStorage(new File(plugin.getDataFolder() + File.separator + "config.yml"), "Blacksmith Configuration");
    }

    public void load() {
        config.load();
        DataKey root = config.getKey("");
        for (Setting setting : Setting.values())
            if (!root.keyExists(setting.path))
                root.setRaw(setting.path, setting.get());
            else
                setting.set(root.getRaw(setting.path));

        config.save();
    }

    public YamlStorage getConfig() {
        return config;
    }

    public enum Setting {
        BASE_PRICE("base-prices.default", 10),
        PRICE_PER_DURABILITY_POINT("price-per-durability-point.default", 1),
        BUSY_WITH_PLAYER_MESSAGE("defaults.messages.busy-with-player", "§cI'm busy at the moment. Come back later!"),
        BUSY_WITH_REFORGE_MESSAGE("defaults.messages.busy-with-reforge", "§cI'm working on it. Be patient!"),
        COOLDOWN_UNEXPIRED_MESSAGE(
                "defaults.messages.cooldown-not-expired",
                "§cYou've already had your chance! Give me a break!"),
        COST_MESSAGE(
                "defaults.messages.cost",
                "§eIt will cost §a<price> §eto reforge that §a<item>§e! Click again to reforge!"),
        DROP_ITEM("defaults.drop-item", true),
        DISABLE_COOLDOWN("defaults.disablecooldown", false),
        DISABLE_DELAY("defaults.disabledelay", false),
        ENCHANTMENT_MODIFIER("enchantment-modifiers.default", 5),
        FAIL_CHANCE("defaults.percent-chance-to-fail-reforge", 10),
        FAIL_MESSAGE("defaults.messages.fail-reforge", "§cWhoops! Didn't mean to do that! Maybe next time?"),
        INSUFFICIENT_FUNDS_MESSAGE(
                "defaults.messages.insufficient-funds",
                "§cYou don't have enough money to reforge that item!"),
        INVALID_ITEM_MESSAGE("defaults.messages.invalid-item", "§cI'm sorry, but I don't know how to reforge that!"),
        ITEM_UNEXPECTEDLY_CHANGED_MESSAGE(
                "defaults.messages.item-changed-during-reforge",
                "§cThat's not the item you wanted to reforge before!"),
        EXTRA_ENCHANTMENT_CHANCE("defaults.percent-chance-for-extra-enchantment", 5),
        MAX_ENCHANTMENTS("defaults.maximum-enchantments", 3),
        MAX_REFORGE_DELAY("defaults.delays-in-seconds.maximum", 20),
        MIN_REFORGE_DELAY("defaults.delays-in-seconds.minimum", 5),
        REFORGE_COOLDOWN("defaults.delays-in-seconds.reforge-cooldown", 60),
        START_REFORGE_MESSAGE("defaults.messages.start-reforge", "§eOk, let's see what I can do..."),
        SUCCESS_MESSAGE("defaults.messages.successful-reforge", "There you go! All better!");

        private String path;
        private Object value;

        Setting(String path, Object value) {
            this.path = path;
            this.value = value;
        }

        public boolean asBoolean() {
            return (Boolean) value;
        }

        public double asDouble() {
            if (value instanceof String)
                return Double.valueOf((String) value);
            if (value instanceof Integer)
                return (Integer) value;
            return (Double) value;
        }

        public int asInt() {
            return (Integer) value;
        }

        public String asString() {
            return value.toString();
        }

        private Object get() {
            return value;
        }

        private void set(Object value) {
            this.value = value;
        }
    }
}