package net.apunch.blacksmith;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;

import net.apunch.blacksmith.util.Settings.Setting;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.util.DataKey;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;

public class BlacksmithTrait extends Trait {
	private static final NamespacedKey[] enchantments = new NamespacedKey[Enchantment.values().length];

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
			enchantments[i++] = enchantment.getKey();
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
		if (key.keyExists("drop-item"))
			dropItem = key.getBoolean("drop-item");
		if (key.keyExists("disable-cooldown"))
			disablecooldown = key.getBoolean("disable-cooldown");
		if (key.keyExists("disable-delay"))
			disabledelay = key.getBoolean("disable-delay");
	}

	@EventHandler
	public void onRightClick(NPCRightClickEvent event) {
		// Ensure npc entity.
		if (this.npc != event.getNPC())
			return;

		onNPCInteract(event.getClicker());
	}

	@EventHandler
	public void onLeftClick(NPCLeftClickEvent event) {
		// Ensure npc entity.
		if (this.npc != event.getNPC())
			return;

		onNPCInteract(event.getClicker());
	}

	public void onNPCInteract(Player player) {

		if ((disablecooldown & (cooldowns.get(player.getName()) != (null)))) {
			cooldowns.remove(player.getName());
		}

		if (!player.hasPermission("blacksmith.reforge"))
			return;

		if (cooldowns.get(player.getName()) != null) {
			if (!Calendar.getInstance().after(cooldowns.get(player.getName()))) {
				player.sendMessage(parseConfVars(cooldownUnexpiredMsg, null, null));
				return;
			}
			cooldowns.remove(player.getName());
		}

		// Get the currently held item.
		ItemStack hand = player.getInventory().getItemInMainHand();

		if (session != null) {

			// Handle session timeout.
			if (System.currentTimeMillis() > _sessionstart + 10 * 1000
					|| this.npc.getEntity().getLocation().distance(session.player.getLocation()) > 20) {
				session = null;
			}

			// Ensure the NPC is free.
			if (!session.isInSession(player)) {
				player.sendMessage(parseConfVars(busyWithPlayerMsg, null, null));
				return;
			}

			// Check if we're reforging.
			if (session.isRunning()) {
				player.sendMessage(parseConfVars(busyReforgingMsg, null, null));
				return;
			}

			if (session.handleInteraction())
				session = null;
			else
				reforge(npc, player);

		} else {
			if ((!plugin.isTool(hand) && !plugin.isArmor(hand))
					|| (!reforgeableItems.isEmpty() && !reforgeableItems.contains(hand.getType()))) {

				player.sendMessage(parseConfVars(invalidItemMsg, null, null));
				return;
			}

			// Get the cost of the repair.
			String cost = plugin.formatCost(player);

			_sessionstart = System.currentTimeMillis();
			session = new ReforgeSession(player, npc);

			player.sendMessage(parseConfVars(costMsg, plugin.getItemName(hand), cost));
		}
	}

	public String parseConfVars(String in, String itemName, String cost) {
		// Replace with name, item name, and item cost.
		return in.replace("<npc>", npc.getName()).replace("<item>", (itemName == null ? "" : itemName))
				.replace("<price>", (cost == null ? "" : cost));
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

	private void setHeldItem(ItemStack itemStack) {
		// Npc should be living entity.
		if (npc.getEntity() instanceof LivingEntity) {
			((LivingEntity) npc.getEntity()).getEquipment().setItemInMainHand(itemStack);
		}
	}

	private void reforge(NPC npc, Player player) {
		// Initial forge message.
		player.sendMessage(parseConfVars(startReforgeMsg, null, null));

		// plugin.deposit(npc, player); // CitiTrader dependency outdated and broken

		plugin.withdraw(player);
		session.beginReforge();

		// Add the player's item to the NPC's hand.
		setHeldItem(player.getInventory().getItemInMainHand());

		// Remove the item from the player's hand.
		player.getInventory().setItemInMainHand(null);
	}

	private class ReforgeSession implements Runnable {
		private final Player player;
		private final NPC npc;
		private final ItemStack reforge;
		private int taskId;

		private ReforgeSession(Player player, NPC npc) {
			this.player = player;
			this.npc = npc;

			// Get the player's item.
			reforge = player.getInventory().getItemInMainHand();
		}

		@Override
		public void run() {
			String msg = (reforgeItemInHand() ? successMsg : failMsg);
			player.sendMessage(parseConfVars(msg, plugin.getItemName(reforge), null));

			// Remove the NPC's held item.
			setHeldItem(null);

			if (!disabledelay) {

				// Handle item return method.
				if (dropItem) {
					// Drop the item for the player to pick up.
					player.getWorld().dropItemNaturally(npc.getEntity().getLocation(), reforge);
				} else {

					// Add the item to the player's inventory.
					player.getInventory().addItem(reforge);
				}
			} else {
				//
				player.getInventory().setItemInMainHand(reforge);
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
				// Get the current damage of the item.
				int damage = plugin.getItemStackDamage(reforge);

				// Damage the item
				int newDamage = (short) (damage + damage * random.nextInt(8));

				// Get the max durability of the item.
				short maxDurability = reforge.getType().getMaxDurability();

				if (newDamage <= 0)
					newDamage = (short) (maxDurability / 3);
				else if (damage + newDamage > maxDurability)
					newDamage = (short) (maxDurability - random.nextInt(maxDurability - 25));

				// Assign the new damage to the item stack.
				plugin.setItemStackDamage(reforge, newDamage);
				return false;
			}

			// Remove the damage.
			plugin.setItemStackDamage(reforge, 0);

			// Add random enchantments

			// If durability is full, chance is multiplied by 4. Seems unbalanced, so
			// disabled for now.
			/*
			 * if (reforge.getDurability() == 0) chance *= 4; else
			 */

			if (random.nextInt(100) < extraEnchantmentChance
					&& reforge.getEnchantments().keySet().size() < maxEnchantments) {

				Enchantment enchantment = Enchantment.getByKey(enchantments[random.nextInt(enchantments.length)]);

				// Get the bonus enchantment level.
				int bonusEnchant = random.nextInt(Math.max(enchantment.getMaxLevel() - enchantment.getStartLevel(), 1));

				if (enchantment.canEnchantItem(reforge))
					reforge.addEnchantment(enchantment, bonusEnchant + enchantment.getStartLevel());

			}

			return true;
		}

		// Return if the session should end
		private boolean handleInteraction() {

			// Prevent player from switching items during session
			if (!reforge.equals(player.getInventory().getItemInMainHand())) {
				player.sendMessage(parseConfVars(itemChangedMsg, null, null));
				return true;
			}

			// Ensure player has enough.
			if (!plugin.doesPlayerHaveEnough(player)) {
				player.sendMessage(parseConfVars(insufficientFundsMsg, null, null));
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
			if (!disablecooldown) {
				taskId = plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, this,
						(new Random().nextInt(maxReforgeDelay) + minReforgeDelay) * 20);
			} else {
				taskId = plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, this, 0);
			}
		}
	}
}
