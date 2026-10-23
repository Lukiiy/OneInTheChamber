package me.lukiiy.oneInTheChamber;

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.lukiiy.flow.FDefaults;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class Items {
    public static final ItemStack SWORD;
    public static final ItemStack BOW;
    public static final ItemStack ARROW;

    static {
        ItemStack s = ItemStack.of(Material.STONE_SWORD);

        s.setData(DataComponentTypes.ITEM_NAME, Component.text("Sword"));

        SWORD = s;

        ItemStack b = ItemStack.of(Material.BOW);

        b.setData(DataComponentTypes.ITEM_NAME, Component.text("One Shot Bow"));

        BOW = b;

        ItemStack a = ItemStack.of(Material.ARROW);

        a.setData(DataComponentTypes.ITEM_NAME, Component.text("Arrow").color(FDefaults.LIME));

        ARROW = a;
    }
}
