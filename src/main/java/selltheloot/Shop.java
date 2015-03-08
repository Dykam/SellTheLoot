package selltheloot;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

public class Shop {
    SellTheLootPlugin plugin;
    String name;
    String title;
    float defaultMultiplier;
    Map<Enchantment, Float> multipliers;
    Map<Material, Float> prices;
    Map<Material, Map<Byte, Float>> dataPrices;
    public Shop(SellTheLootPlugin plugin, String name, String title, float defaultMultiplier, Map<Enchantment, Float> multipliers, Map<Material, Float> prices, Map<Material, Map<Byte, Float>> dataPrices) {
        this.name = name;
        this.title = title;
        this.defaultMultiplier = defaultMultiplier;
        this.multipliers = multipliers;
        this.prices = prices;
        this.dataPrices = dataPrices;
        this.plugin = plugin;
    }

    public Shop(SellTheLootPlugin plugin, String name) {
        this(plugin, name, "Sell the Loot", 0, new HashMap<Enchantment, Float>(), new HashMap<Material, Float>(), new HashMap<Material, Map<Byte, Float>>());
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public boolean sellable(ItemStack item) {
        if(item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if(meta.hasDisplayName()) {
                if(plugin.getConfig().getBoolean("blacklist.custom-names", false))
                    return false;
                if(plugin.getConfig().getBoolean("blacklist.colored-names", false) && meta.getDisplayName().indexOf('§') != -1)
                    return false;
            }
            if(meta.hasLore() && plugin.getConfig().getBoolean("blacklist.lore", false))
                return false;
        }
        return prices.containsKey(item.getType()) || (dataPrices.containsKey(item.getType()) && dataPrices.get(item.getType()).containsKey(item.getData().getData()));
    }

    public float getPrice(ItemStack item) {
        float price = 0;
        if(dataPrices.containsKey(item.getType()) && dataPrices.get(item.getType()).containsKey(item.getData().getData())) {
            price = dataPrices.get(item.getType()).get(item.getData().getData());
        } else {
            price = prices.get(item.getType());
        }
        if(item.getType().getMaxDurability() != 0) {
            price *= 1f - (float)item.getDurability() / item.getType().getMaxDurability();
        }
        price *= item.getAmount();
        for(Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            // price *= multipler^level (power)
            float multiplier = multipliers.containsKey(entry.getKey()) ? multipliers.get(entry.getKey()) : defaultMultiplier;
            price *= Math.pow(multiplier,  entry.getValue());
        }
        return price;
    }
}
