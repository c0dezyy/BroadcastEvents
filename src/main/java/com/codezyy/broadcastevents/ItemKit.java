package com.codezyy.broadcastevents;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Storage (36 slots), armor (4) and offhand. Used for event kits and saved player inventories. */
public final class ItemKit {
    public ItemStack[] storage = new ItemStack[36];
    public ItemStack[] armor = new ItemStack[4];
    public ItemStack offhand;

    public static ItemKit capture(PlayerInventory inv) {
        ItemKit k = new ItemKit();
        k.storage = copy(inv.getStorageContents(), 36);
        k.armor = copy(inv.getArmorContents(), 4);
        k.offhand = clean(inv.getItemInOffHand());
        return k;
    }

    private static ItemStack clean(ItemStack i) {
        return (i == null || i.getType().isAir()) ? null : i.clone();
    }

    private static ItemStack[] copy(ItemStack[] src, int size) {
        ItemStack[] out = new ItemStack[size];
        if (src != null) for (int i = 0; i < Math.min(size, src.length); i++) out[i] = clean(src[i]);
        return out;
    }

    public void applyTo(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setStorageContents(copy(storage, 36));
        inv.setArmorContents(copy(armor, 4));
        inv.setItemInOffHand(offhand == null ? new ItemStack(Material.AIR) : offhand.clone());
        p.updateInventory();
    }

    public int count() {
        int n = 0;
        for (ItemStack i : storage) if (i != null) n++;
        for (ItemStack i : armor) if (i != null) n++;
        if (offhand != null) n++;
        return n;
    }

    public void save(ConfigurationSection s) {
        for (int i = 0; i < 36; i++) if (storage[i] != null) s.set("storage." + i, storage[i]);
        for (int i = 0; i < 4; i++) if (armor[i] != null) s.set("armor." + i, armor[i]);
        if (offhand != null) s.set("offhand", offhand);
    }

    public static ItemKit load(ConfigurationSection s) {
        ItemKit k = new ItemKit();
        if (s == null) return k;
        for (int i = 0; i < 36; i++) k.storage[i] = s.getItemStack("storage." + i);
        for (int i = 0; i < 4; i++) k.armor[i] = s.getItemStack("armor." + i);
        k.offhand = s.getItemStack("offhand");
        return k;
    }

    /** Human readable lines for the inventory log file. */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 36; i++)
            if (storage[i] != null) out.add((i < 9 ? "hotbar " : "inventory ") + i + ": " + line(storage[i]));
        String[] names = {"boots", "leggings", "chestplate", "helmet"};
        for (int i = 0; i < 4; i++) if (armor[i] != null) out.add("armor " + names[i] + ": " + line(armor[i]));
        if (offhand != null) out.add("offhand: " + line(offhand));
        return out;
    }

    private static String line(ItemStack it) {
        StringBuilder sb = new StringBuilder(it.getType().name()).append(" x").append(it.getAmount());
        ItemMeta m = it.getItemMeta();
        if (m != null && m.hasDisplayName() && m.displayName() != null)
            sb.append(" name=\"").append(PlainTextComponentSerializer.plainText().serialize(m.displayName())).append('"');
        if (!it.getEnchantments().isEmpty()) {
            sb.append(" enchants=");
            it.getEnchantments().forEach((en, lv) -> sb.append(en.getKey().getKey()).append(':').append(lv).append(' '));
        }
        return sb.toString().trim();
    }
}
