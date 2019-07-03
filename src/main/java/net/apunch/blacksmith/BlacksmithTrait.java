package net.apunch.blacksmith;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;

import net.apunch.blacksmith.util.Settings.Setting;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.util.DataKey;

public class BlacksmithTrait extends Trait {
	private static final String[] enchantments = new String[Enchantment.values().length];

	private final BlacksmithPlugin plugin;
	private final List<Material> reforgeableItems = new ArrayList<Material>();
	private final Map<String, Calendar> cooldowns = new HashMap<String, Calendar>();
	private ReforgeSession session;

	// Defaults
	private String busyWithPlayerMsg = Setting.BUSY_WITH_PLAYER_MESSAGE.asString();
	private String busyReforgingMsg = Setting.BUSY_WITH_REFORGE_MESSAGE.asString();
	private String costMsg = Setting.COST_MESSAGE.asString();
	private String invalidItemMsg = Setting.INVALID_ITEM_MESSAGE.asString();
	private String startReforgeMsg = Setting.START_REFORGE_MESSAGE.asString();
	private String successMsg = Setting.SUCCESS_MESSAGE.asString();
	private String failMsg = Setting.FAIL_MESSAGE.asString();
	private String insufficientFundsMsg = Setting.INSUFFICIENT_FUNDS_MESSAGE.asString();
	private String cooldownUnexpiredMsg = Setting.COOLDOWN_UNEXPIRED_MESSAGE.asString();
	private String itemChangedMsg = Setting.ITEM_UNEXPECTEDLY_CHANGED_MESSAGE.asString();
	private int minReforgeDelay = Setting.MIN_REFORGE_DELAY.asInt();
	private int maxReforgeDelay = Setting.MAX_REFORGE_DELAY.asInt();
	private int reforgeCooldown = Setting.REFORGE_COOLDOWN.asInt();
	private int failChance = Setting.FAIL_CHANCE.asInt();
	private int extraEnchantmentChance = Setting.EXTRA_ENCHANTMENT_CHANCE.asInt();
	private int maxEnchantments = Setting.MAX_ENCHANTMENTS.asInt();
	private boolean dropItem = Setting.DROP_ITEM.asBoolean();
	private boolean disablecooldown = Setting.DISABLE_COOLDOWN.asBoolean();
	private boolean disabledelay = Setting.DISABLE_DELAY.asBoolean();

	public BlacksmithTrait() {
		super("blacksmith");
		plugin = (BlacksmithPlugin) Bukkit.getServer().getPluginManager().getPlugin("Blacksmith");
		int i = 0;
		for (Enchantment enchantment : Enchantment.values())
			enchantments[i++] = enchantment.getKey().toString();
	}

	@Override
	public void load(DataKey key) {
		for (DataKey sub : key.getRelative("reforgeable-items").getIntegerSubKeys())
			if (Material.getMaterial(sub.getString("").toUpperCase().replace('-', '_')) != null)
				reforgeableItems.add(Material.getMaterial(sub.getString("").toUpperCase().replace('-', '_')));

		// Override defaults if they exist
		if (key.keyExists("messages.busy-with-player"))
			busyWithPlayerMsg = key.getString("messages.busy-with-player");
		if (key.keyExists("messages.busy-with-reforge"))
			busyReforgingMsg = key.getString("messages.busy-with-reforge");
		if (key.keyExists("messages.cost"))
			costMsg = key.getString("messages.cost");
		if (key.keyExists("messages.invalid-item"))
			invalidItemMsg = key.getString("messages.invalid-item");
		if (key.keyExists("messages.start-reforge"))
			startReforgeMsg = key.getString("messages.start-reforge");
		if (key.keyExists("messages.successful-reforge"))
			successMsg = key.getString("messages.successful-reforge");
		if (key.keyExists("messages.fail-reforge"))
			failMsg = key.getString("messages.fail-reforge");
		if (key.keyExists("messages.insufficient-funds"))
			insufficientFundsMsg = key.getString("messages.insufficient-funds");
		if (key.keyExists("messages.cooldown-not-expired"))
			cooldownUnexpiredMsg = key.getString("messages.cooldown-not-expired");
		if (key.keyExists("messages.item-changed-during-reforge"))
			itemChangedMsg = key.getString("messages.item-changed-during-reforge");
		if (key.keyExists("delays-in-seconds.minimum"))
			minReforgeDelay = key.getInt("delays-in-seconds.minimum");
		if (key.keyExists("delays-in-seconds.maximum"))
			maxReforgeDelay = key.getInt("delays-in-seconds.maximum");
		if (key.keyExists("delays-in-seconds.reforge-cooldown"))
			reforgeCooldown = key.getInt("delays-in-seconds.reforge-cooldown");
		if (key.keyExists("percent-chance-to-fail-reforge"))
			failChance = key.getInt("percent-chance-to-fail-reforge");
		if (key.keyExists("maximum-enchantments"))
			maxEnchantments = key.getInt("maximum-enchantments");
		if (key.keyExists("extra-enchantments-chance"))
			extraEnchantmentChance = key.getInt("extra-enchantment-chance");
		if (key.keyExists("dropitem"))
			dropItem = key.getBoolean("dropitem");
		if (key.keyExists("disable-cooldown"))
			disablecooldown = key.getBoolean("disable-cooldown");
		if (key.keyExists("disable-delay"))
			disabledelay = key.getBoolean("disable-delay");
	}

	@EventHandler
	public void onRightClick(net.citizensnpcs.api.event.NPCRightClickEvent event) {
		if(this.npc!=event.getNPC()) return;

		Player player = event.getClicker();
		if ((disablecooldown & (cooldowns.get(player.getName()) != (null))))
		{
			cooldowns.remove(player.getName());
		}
		if (!player.hasPermission("blacksmith.reforge"))
			return;

		if (cooldowns.get(player.getName()) != null) {
			if (!Calendar.getInstance().after(cooldowns.get(player.getName()))) {
				player.sendMessage(cooldownUnexpiredMsg.replace("<name>", npc.getName()));
				return;
			}
			cooldowns.remove(player.getName());
		}


		ItemStack hand = player.getItemInHand();

		if(session!=null){
			//timeout
			if ( System.currentTimeMillis() > _sessionstart + 10*1000 || this.npc.getEntity().getLocation().distance(session.player.getLocation()) > 20 ){
				session = null;
			}	
		}


		if (session != null) {
			if (!session.isInSession(player)) {

				player.sendMessage(busyWithPlayerMsg.replace("<name>", npc.getName()));
				return;		

			}

			if (session.isRunning()) {
				player.sendMessage(busyReforgingMsg.replace("<name>", npc.getName()));
				return;
			}
			if (session.handleClick())
				session = null;
			else
				reforge(npc, player);
		} else {
			if ((!plugin.isTool(hand) && !plugin.isArmor(hand))
					|| (!reforgeableItems.isEmpty() && !reforgeableItems.contains(hand.getType()))) {
				player.sendMessage(invalidItemMsg.replace("<name>", npc.getName()));
				return;
			}
			
			String cost = plugin.formatCost(player);
			
			_sessionstart = System.currentTimeMillis();
			session = new ReforgeSession(player, npc);
			player.sendMessage(costMsg.replace("<price>", cost).replace("<item>",
					hand.getType().name().toLowerCase().replace('_', ' ')).replace("<name>", npc.getName()));

		}
	}

	private long _sessionstart = System.currentTimeMillis();

	@Override
	public void save(DataKey key) {
		for (int i = 0; i < reforgeableItems.size(); i++)
			key.getRelative("reforgeable-items").setString(String.valueOf(i),
					reforgeableItems.get(i).name().toLowerCase().replace('_', '-'));

		key.setString("messages.busy-with-player", busyWithPlayerMsg);
		key.setString("messages.busy-with-reforge", busyReforgingMsg);
		key.setString("messages.cost", costMsg);
		key.setString("messages.invalid-item", invalidItemMsg);
		key.setString("messages.start-reforge", startReforgeMsg);
		key.setString("messages.successful-reforge", successMsg);
		key.setString("messages.fail-reforge", failMsg);
		key.setString("messages.insufficient-funds", insufficientFundsMsg);
		key.setString("messages.cooldown-not-expired", cooldownUnexpiredMsg);
		key.setString("messages.item-changed-during-reforge", itemChangedMsg);
		key.setInt("delays-in-seconds.minimum", minReforgeDelay);
		key.setInt("delays-in-seconds.maximum", maxReforgeDelay);
		key.setInt("delays-in-seconds.reforge-cooldown", reforgeCooldown);
		key.setInt("percent-chance-to-fail-reforge", failChance);
		key.setInt("percent-chance-for-extra-enchantment", extraEnchantmentChance);
		key.setInt("maximum-enchantments", maxEnchantments);
		key.setBoolean("drop-item", dropItem);
        key.setBoolean("disable-delay", disabledelay);
        key.setBoolean("disable-cooldown", disablecooldown);
	}

	private void reforge(NPC npc, Player player) {
		player.sendMessage( startReforgeMsg);
                
                //plugin.deposit(npc, player); // CitiTrader dependency outdated and broken
                
                plugin.withdraw(player);
		session.beginReforge();
		if (npc.getEntity() instanceof Player)
			((Player) npc.getEntity()).setItemInHand(player.getItemInHand());
        else
        	((LivingEntity) npc.getEntity()).getEquipment().setItemInHand(player.getItemInHand());
		player.setItemInHand(null);
	}

	private class ReforgeSession implements Runnable {
		private final Player player;
		private final NPC npc;
		private final ItemStack reforge;
		private int taskId;

		private ReforgeSession(Player player, NPC npc) {
			this.player = player;
			this.npc = npc;
			reforge = player.getItemInHand();
		}

		@Override
		public void run() {
			player.sendMessage(( reforgeItemInHand() ? successMsg : failMsg).replace("<name>", npc.getName()));
			if (npc.getEntity() instanceof Player)
				((Player) npc.getEntity()).setItemInHand(null);
            else
                ((LivingEntity) npc.getEntity()).getEquipment().setItemInHand(null);
			if (!disabledelay)
			{
				if (dropItem)
					player.getWorld().dropItemNaturally(npc.getEntity().getLocation(), reforge);
				else {
					player.getInventory().addItem(reforge);
					/*
					oldmethode ?
					for (ItemStack stack : player.getInventory().addItem(reforge).values())
						player.getWorld().dropItemNaturally(npc.getEntity().getLocation(), stack);
					 */
				}
			}
			else
			{
				player.setItemInHand(reforge);
			}
			session = null;
			// Start cooldown
			Calendar wait = Calendar.getInstance();
			wait.add(Calendar.SECOND, reforgeCooldown);
			cooldowns.put(player.getName(), wait);
		}

		private boolean reforgeItemInHand() {
			Random random = new Random();
			if (random.nextInt(100) < failChance) {
				for (Enchantment enchantment : reforge.getEnchantments().keySet()) {
					// Remove or downgrade enchantments
					if (random.nextBoolean())
						reforge.removeEnchantment(enchantment);
					else {
						if (reforge.getEnchantmentLevel(enchantment) > 1) {
							reforge.removeEnchantment(enchantment);
							reforge.addEnchantment(enchantment, 1);
						}
					}
				}
				// Damage the item
				short durability = (short) (reforge.getItemMeta(). + reforge.getDurability() * random.nextInt(8));
				short maxDurability = reforge.getType().getMaxDurability();
				if (durability <= 0)
					durability = (short) (maxDurability / 3);
				else if (reforge.getDurability() + durability > maxDurability)
					durability = (short) (maxDurability - random.nextInt(maxDurability - 25));
				reforge.setDurability(durability);
				return false;
			}

			reforge.setDurability((short) 0);

			// Add random enchantments


			// If durability is full, chance is multiplied by 4. Seems unbalanced, so disabled for now.
			/*if (reforge.getDurability() == 0)
            	chance *= 4;
            else */

			int roll = random.nextInt(100);
			if (roll < extraEnchantmentChance && reforge.getEnchantments().keySet().size() < maxEnchantments){

				Enchantment enchantment = Enchantment.getByName(enchantments[random.nextInt(enchantments.length)]);
				if (enchantment.canEnchantItem(reforge)) reforge.addEnchantment(enchantment, random.nextInt(enchantment.getMaxLevel() - enchantment.getStartLevel()) + enchantment.getStartLevel());

			}

			return true;
		}

		// Return if the session should end
		private boolean handleClick() {
			// Prevent player from switching items during session
			if (!reforge.equals(player.getItemInHand())) {
				player.sendMessage(itemChangedMsg.replace("<name>", npc.getName()));
				return true;
			}
			if (!plugin.doesPlayerHaveEnough(player)) {
				player.sendMessage(insufficientFundsMsg.replace("<name>", npc.getName()));
				return true;
			}
			return false;
		}

		private boolean isRunning() {
			return plugin.getServer().getScheduler().isQueued(taskId);
		}

		private boolean isInSession(Player other) {
			return player.getName().equals(other.getName());
		}

		private void beginReforge() {
			if (!disablecooldown)
			{
			taskId = plugin
					.getServer()
					.getScheduler()
					.scheduleSyncDelayedTask(plugin, this,
							(new Random().nextInt(maxReforgeDelay) + minReforgeDelay) * 20);
			}
			else
			{
				taskId = plugin
						.getServer()
						.getScheduler()
						.scheduleSyncDelayedTask(plugin, this,0);
			}
		}
	}
}
