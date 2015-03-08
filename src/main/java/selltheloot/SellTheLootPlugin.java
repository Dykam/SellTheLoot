package selltheloot;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class SellTheLootPlugin extends JavaPlugin implements Listener {
    final int shopSize = 9 * 4;
    String signLine1 = "" + ChatColor.DARK_PURPLE + ChatColor.BOLD + "[STL]";
    String invalidSignLine1 = "" + ChatColor.DARK_RED + ChatColor.BOLD + "[STL]";
    String baseSignLine1 = "[STL]";
    Map<String, Shop> shops;
    BlockFace[] horizontalNeighbours = { BlockFace.NORTH,  BlockFace.WEST,  BlockFace.SOUTH, BlockFace.EAST };
    Map<InventoryView, Shop> openShops;

    public static Economy econ;

    @Override
    public void onEnable() {
        if(!setupEconomy()) {
            getLogger().severe("Disabled due to no Vault dependency found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        openShops = new HashMap<InventoryView, Shop>();
        shops = new HashMap<String, Shop>();
        loadConfig();
    }

    @Override
    public void onDisable() {
    }

    /**
     * Open STL shop in case the player right clicks a sign.
     * @param e The player interact event.
     */
    @EventHandler
    private void onPlayerInteract(PlayerInteractEvent e) {
        if(e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        Block block = e.getClickedBlock();
        if(block.getType() != Material.SIGN_POST && block.getType() != Material.WALL_SIGN)
            return;
        Sign sign = (Sign)block.getState();
        if(!sign.getLine(0).equals(signLine1)) {
            return;
        }

        String shopName = sign.getLine(1);
        Player player = e.getPlayer();

        if(player.isSneaking())
            return;

        if(!player.hasPermission("selltheloot.use")) {
            player.sendMessage(ChatColor.DARK_PURPLE + "[STL]" + ChatColor.RED + " You have no permission to use this STL shop!");
            return;
        }
        Shop shop = shops.get(shopName);
        if(shop == null)
            return;

        Inventory inventory = initializeInventory(player, shop);
        openShops.put(player.openInventory(inventory), shop);

        e.setCancelled(true);
    }

    @EventHandler
    private void onBlockBreakEvent(BlockBreakEvent e) {
        Block block = e.getBlock();
        Player player = e.getPlayer();
        if(block.getType() == Material.SIGN_POST || block.getType() == Material.WALL_SIGN) {
            Sign sign = (Sign)block.getState();
            if(!checkCanBreakSign(player, sign)) {
                e.setCancelled(true);
            }
        } else {
            for(BlockFace face : horizontalNeighbours) {
                Block neighbour = block.getRelative(face);
                if(neighbour.getType() != Material.WALL_SIGN)
                    continue;
                Sign sign = (Sign) neighbour.getState();
                org.bukkit.material.Sign signData = (org.bukkit.material.Sign)neighbour.getState().getData();
                if(face.getOppositeFace() == signData.getAttachedFace() && !checkCanBreakSign(player, sign)) {
                    e.setCancelled(true);
                }
            }
            Block neighbour = block.getRelative(BlockFace.UP);
            if(neighbour.getType() != Material.SIGN_POST)
                return;
            Sign sign = (Sign) neighbour.getState();
            org.bukkit.material.Sign signData = (org.bukkit.material.Sign)neighbour.getState().getData();
            if(!signData.isWallSign() && !checkCanBreakSign(player, sign)) {
                e.setCancelled(true);
            }
        }
    }

    private boolean checkCanBreakSign(Player player, Sign sign) {
        if(!isSTLShop(sign))
            return true;
        if(player.hasPermission("selltheloot.setup")) {
            String shop = sign.getLine(1);
            removed(player, shop);
            return true;
        }
        player.sendMessage(ChatColor.DARK_PURPLE + "[STL]" + ChatColor.RED + " You have no permission to remove this STL shop!");
        return false;
    }

    /**
     * Prevents block physics from breaking STL shops.
     * @param e The block physics event.
     */
    @EventHandler
    private void onBlockPhysics(BlockPhysicsEvent e) {Block block = e.getBlock();
        if(block.getType() == Material.SIGN_POST && block.getType() == Material.WALL_SIGN) {
            e.setCancelled(true);
        } else {
            for(BlockFace face : horizontalNeighbours) {
                Block neighbour = block.getRelative(face);
                if(neighbour.getType() != Material.WALL_SIGN)
                    continue;
                Sign sign = (Sign) neighbour.getState();
                org.bukkit.material.Sign signData = (org.bukkit.material.Sign)sign.getData();
                if(face.getOppositeFace() == signData.getAttachedFace()) {
                    e.setCancelled(true);
                    return;
                }
            }
            Block neighbour = block.getRelative(BlockFace.UP);
            if(neighbour.getType() != Material.SIGN_POST)
                return;
            Sign sign = (Sign) neighbour.getState();
            org.bukkit.material.Sign signData = (org.bukkit.material.Sign)sign.getData();
            if(!signData.isWallSign()) {
                e.setCancelled(true);
            }
        }
    }

    /*/**
     * Prevent STL shops from blowing up.
     * @param e The entity explode event.
     */
    /*@EventHandler // Handling preventing of exploding the block the sign is attached to can become too much of a performance hit.
    private void onEntityExplode(EntityExplodeEvent e) {
        Iterator<Block> blocks = e.blockList().iterator();
        while(blocks.hasNext()) {
            Block block = blocks.next();
            if(!(block.getState() instanceof Sign))
                continue;
            Sign sign = (Sign)block.getState();
            if(isSTLShop(sign))
                blocks.remove();
        }
    }*/

    /**
     * Makes sure only those with permissions can create, modify or remove STL shops.
     * @param e The sign change event.
     */
    @EventHandler
    private void onSignChange(SignChangeEvent e) {
        Sign sign = (Sign)e.getBlock().getState();
        boolean wasSTL = ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase(baseSignLine1),
                willBeSTL = ChatColor.stripColor(e.getLine(0)).equalsIgnoreCase(baseSignLine1);
        if(!wasSTL && !willBeSTL)
            return;

        if(!e.getPlayer().hasPermission("selltheloot.setup")) {
            if(wasSTL && willBeSTL) {
                e.getPlayer().sendMessage(ChatColor.DARK_PURPLE + "[STL]" + ChatColor.RED + " You have no permission to change this STL shop!");
            } else if(wasSTL) {
                e.getPlayer().sendMessage(ChatColor.DARK_PURPLE + "[STL]" + ChatColor.RED + " You have no permission to remove this STL shop!");
            } else {
                e.getPlayer().sendMessage(ChatColor.DARK_PURPLE + "[STL]" + ChatColor.RED + " You have no permission to create an STL shop!");
            }
            return;
        }

        String wasShop = sign.getLine(1),
                willBeShop = e.getLine(1);

        if(e.getLine(1).length() == 0) {
            e.setLine(0, invalidSignLine1);
            e.setLine(1, ChatColor.MAGIC + "shopname");
            e.getPlayer().sendMessage(ChatColor.DARK_PURPLE + "[STL]" + ChatColor.RED + " Please supply a shop name on the second line.");
            return;
        } else if(wasSTL && willBeSTL) {
            modified(e.getPlayer(), wasShop, willBeShop);
        } else if(wasSTL) {
            removed(e.getPlayer(), wasShop);
        } else {
            created(e.getPlayer(), willBeShop);
        }

        if(willBeSTL) {
            e.setLine(0, signLine1);
            if(!shops.containsKey(willBeShop))
                e.getPlayer().sendMessage(ChatColor.DARK_PURPLE + "[STL]" + ChatColor.RED + " Shop not found in configuration.");
            else {
                e.getPlayer().sendMessage(ChatColor.DARK_PURPLE + "[STL] Shop found in configuration.");
            }
        }
    }

    @EventHandler
    private void onInventoryClose(InventoryCloseEvent e) {
        if(!openShops.containsKey(e.getView()))
            return;
        Shop shop = openShops.get(e.getView());
        final Player player = (Player)e.getPlayer();

        int returnedItems = 0, returnedItemStacks = 0, soldItems = 0, soldItemStacks = 0;
        float profit = 0;
        StringBuilder receipt = new StringBuilder();
        List<ItemStack> rejected = new LinkedList<ItemStack>();

        getLogger().info("Starting STL transaction. " + player.getName() + " @ " + shop.getName() + ".");

        for(ItemStack itemStack : e.getInventory()) {
            if(itemStack == null)
                continue;
            if(!shop.sellable(itemStack)) {
                player.getInventory().addItem(itemStack);
                returnedItemStacks++;
                returnedItems += itemStack.getAmount();
                rejected.add(itemStack);
                continue;
            }
            soldItemStacks++;
            soldItems += itemStack.getAmount();
            float price = shop.getPrice(itemStack);
            profit += price;
            receipt.append("" + ChatColor.RESET + ChatColor.DARK_GREEN + itemStack.getType().toString() + "\n  x" + Integer.toString(itemStack.getAmount()) + " " + econ.format(price) + "\n");
        }

        if(returnedItems != 0) {
            player.sendMessage(ChatColor.DARK_PURPLE + "[STL]" + ChatColor.RED + " You tried to sell " + Integer.toString(returnedItems) + " items in " + Integer.toString(returnedItemStacks) + " stacks. Returned the items to your inventory.");
            Bukkit.getScheduler().runTask(this, new Runnable() {
                @SuppressWarnings("deprecation")
                @Override
                public void run() {
                    player.updateInventory(); // Nasty deprecation. No workaround exists yet.
                }
            });
            receipt.append("\n" + ChatColor.RED + ChatColor.BOLD + "Rejected items\n");
            for(ItemStack itemStack : rejected) {
                receipt.append("" + ChatColor.RESET + ChatColor.RED + itemStack.getType().toString() + "\n  x" + Integer.toString(itemStack.getAmount()) + "\n");
            }
        }
        if(soldItems != 0) {
            if(econ.fractionalDigits() != -1) {
                double precision = Math.pow(0.1, econ.fractionalDigits());
                profit = (float)(Math.floor(profit / precision) * precision);
            }
            econ.depositPlayer(player.getName(), profit);
            player.sendMessage(ChatColor.DARK_PURPLE + "[STL]" + ChatColor.DARK_GREEN + " Sold " + Integer.toString(soldItems) + " items in " + Integer.toString(soldItemStacks) + " stacks for " + econ.format(profit) + ".");
        }

        getLogger().info("Finishing STL transaction. " + player.getName() + " @ " + shop.getName() + " for " + econ.format(profit) + ".");

        if((soldItems != 0 || returnedItems != 0) && getConfig().getBoolean("receipt.enabled", false)) {
            ItemStack receiptItem = new ItemStack(Material.WRITTEN_BOOK);
            BookMeta meta = (BookMeta)receiptItem.getItemMeta();
            meta.setAuthor(getConfig().getString("receipt.author", "SellTheLoot"));
            meta.setTitle("Receipt: " + econ.format(profit));
            meta.setPages(receipt.toString());
            receiptItem.setItemMeta(meta);
            player.getInventory().addItem(receiptItem);
        }
    }

    /**
     * Checks while the player drags the items if he is allowed to sell it.
     * @param e The inventory click event.
     */
    @EventHandler
    private void onInventoryClick(final InventoryClickEvent e) {
        // Check if he placed in his own inventory, which is of course allowed.
        if(e.getRawSlot() >= shopSize)
            return;
        if(!openShops.containsKey(e.getView()))
            return;
        Shop shop = openShops.get(e.getView());
        final Player player = (Player)e.getWhoClicked();
        ItemStack item = null;
        if(e.getCursor().getType() != Material.AIR) {
            item = e.getCursor();
        }
        if(item != null) {
            if(!shop.sellable(item)) {
                e.setCancelled(true);
                Bukkit.getScheduler().runTask(this, new Runnable() {
                    @SuppressWarnings("deprecation")
                    @Override
                    public void run() {
                        player.updateInventory(); // Nasty deprecation. No workaround exists yet.
                    }
                });
            } else {
                // Fancy lore depicting price?
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!command.getName().equals("selltheloot"))
            return false;

        if(args.length != 1)
            return false;

        if(args[0].equals("reload")) {
            loadConfig(true);
            sender.sendMessage(ChatColor.DARK_PURPLE + "[STL]" + ChatColor.DARK_GREEN + " Reloaded configuration for SellTheLood.");
            return true;
        }

        return false;
    }
    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
        if (economyProvider != null) {
            econ = economyProvider.getProvider();
        }

        return (econ != null);
    }

    boolean isSTLShop(Sign sign) {
        return sign.getLine(0).equals(signLine1);
    }

    Inventory initializeInventory(InventoryHolder holder, Shop shop) {;
        if(shop.getTitle() != null) {
            return Bukkit.createInventory(holder, shopSize, shop.getTitle());
        } else {
            return Bukkit.createInventory(holder, shopSize);
        }
    }

    void loadConfig() {
        loadConfig(false);
    }
    void loadConfig(boolean reload) {
        saveDefaultConfig();
        if(reload)
            reloadConfig();
        shops.clear();

        ConfigurationSection shopSections = getConfig().getConfigurationSection("shops");
        if(shopSections != null) {
            for(String shopName : shopSections.getKeys(false)) {
                ConfigurationSection shopSection = shopSections.getConfigurationSection(shopName);
                ConfigurationSection multipliersSection = shopSection.getConfigurationSection("multipliers");
                ConfigurationSection itemsSection = shopSection.getConfigurationSection("items");
                String title = shopSection.getString("title", shopName);
                Map<Enchantment, Float> multipliers = new HashMap<Enchantment, Float>();
                float defaultMultiplier = 1;
                if(multipliersSection != null) {
                    defaultMultiplier = (float)multipliersSection.getDouble("default", 1);
                    for(String enchantmentName : multipliersSection.getKeys(false)) {
                        if(enchantmentName.equals("default"))
                            continue;
                        if(!multipliersSection.isDouble(enchantmentName) && !multipliersSection.isInt(enchantmentName)) {
                            getLogger().warning("Multiplier " + multipliersSection.getCurrentPath() + "." + enchantmentName + " does not have a valid value.");
                            continue;
                        }
                        Enchantment enchantment = null;
                        if(enchantmentName.matches("^\\d+$"))
                            enchantment = Enchantment.getById(Integer.parseInt(enchantmentName));
                        if(enchantment == null)
                            enchantment = Enchantment.getByName(enchantmentName);
                        if(enchantment == null) {
                            getLogger().warning("Multiplier " + multipliersSection.getCurrentPath() + "." + enchantmentName + " does not have a matching enchantment.");
                        } else {
                            multipliers.put(enchantment, (float)multipliersSection.getDouble(enchantmentName));
                        }
                    }
                }
                Map<Material, Float> prices = new HashMap<Material, Float>();
                Map<Material, Map<Byte, Float>> dataPrices = new HashMap<Material, Map<Byte, Float>>();
                if(itemsSection != null) {
                    for(String key : itemsSection.getKeys(false)) {
                        String itemName = key;
                        if(!itemsSection.isDouble(itemName) && !itemsSection.isInt(itemName)) {
                            getLogger().warning("Item " + itemsSection.getCurrentPath() + "." + key + " does not have a valid value.");
                            continue;
                        }
                        Material material = null;
                        Byte data = null;
                        if(itemName.matches("^[^:]+:\\d+$")) {
                            String[] parts = itemName.split(":");
                            data = Byte.parseByte(parts[1]);
                            itemName = parts[0];
                        }
                        if(itemName.matches("^\\d+$"))
                            material = Material.getMaterial(Integer.parseInt(itemName));
                        if(material == null)
                            material = Material.matchMaterial(itemName);
                        if(material == null) {
                            getLogger().warning("Item " + itemsSection.getCurrentPath() + "." + key + " does not have a matching item.");
                        } else {
                            if(data == null)
                                prices.put(material, (float)itemsSection.getDouble(key));
                            else {
                                if(!dataPrices.containsKey(material)) {
                                    dataPrices.put(material, new HashMap<Byte, Float>());
                                }
                                dataPrices.get(material).put(data, (float)itemsSection.getDouble(key));
                            }
                        }
                    }
                }

                shops.put(shopName, new Shop(this, shopName, title, defaultMultiplier,  multipliers,  prices, dataPrices));
            }
        }
    }

    void created(CommandSender sender, String current) {
            sender.sendMessage(ChatColor.DARK_PURPLE + "[STL]" + ChatColor.DARK_GREEN + " Created an STL shop with config "
                    + ChatColor.BOLD + current
                    + ChatColor.RESET + ChatColor.DARK_GREEN + ".");
    }

    void modified(CommandSender sender, String previous, String current) {
            sender.sendMessage(ChatColor.DARK_PURPLE + "[STL]" + ChatColor.DARK_GREEN + " Modified the STL shop config from"
                    + ChatColor.BOLD + previous
                    + ChatColor.RESET + ChatColor.DARK_GREEN + " to "
                    + ChatColor.BOLD + current
                    + ChatColor.RESET + ChatColor.DARK_GREEN + ".");
    }

    void removed(CommandSender sender, String previous) {
            sender.sendMessage(ChatColor.DARK_PURPLE + "[STL]" + ChatColor.DARK_GREEN + " Removed the STL shop with config "
                    + ChatColor.BOLD + previous
                    + ChatColor.RESET + ChatColor.DARK_GREEN + ".");
    }

    // Utilities to easily set metadata on objects
    void setData(Metadatable metadatable, String key, Object data) {
        metadatable.setMetadata(key, new FixedMetadataValue(this, data));
    }
    MetadataValue getData(Metadatable metadatable, String key) {
        for(MetadataValue value : metadatable.getMetadata(key)) {
            if(value.getOwningPlugin().equals(this))
                return value;
        }
        return null;
    }
}
