package dev.shadesfactions.valley.api;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class GuiAPI implements Listener {

    private final Plugin plugin;
    private final Player player;
    private final String title;
    private final int rows;
    private Inventory inventory;
    private final Map<Integer, ItemStack> permanentItems = new HashMap<>();
    private final Map<Integer, Runnable> permanentActions = new HashMap<>();
    private final List<GuiPage> pages = new ArrayList<>();
    private int currentPage = 0;
    private boolean registered = false;
    private NavigationButtons navigation;

    public GuiAPI(Plugin plugin, Player player, String title, int rows) {
        this.plugin = plugin;
        this.player = player;
        this.title = ChatColor.translateAlternateColorCodes('&', title);
        this.rows = rows;
    }

    public GuiAPI setItem(int slot, ItemStack item) {
        permanentItems.put(slot, item);
        return this;
    }

    public GuiAPI setItem(int slot, ItemStack item, Runnable action) {
        permanentItems.put(slot, item);
        if (action != null) permanentActions.put(slot, action);
        return this;
    }

    public GuiAPI fillBorder(ItemStack item) {
        for (int i = 0; i < 9; i++) {
            permanentItems.put(i, item);
            permanentItems.put((rows - 1) * 9 + i, item);
        }
        for (int i = 1; i < rows - 1; i++) {
            permanentItems.put(i * 9, item);
            permanentItems.put(i * 9 + 8, item);
        }
        return this;
    }

    public GuiAPI fillRow(int row, ItemStack item) {
        for (int i = 0; i < 9; i++) permanentItems.put(row * 9 + i, item);
        return this;
    }

    public GuiAPI addPage() {
        pages.add(new GuiPage());
        return this;
    }

    public GuiAPI setPageItem(int page, int slot, ItemStack item) {
        ensurePage(page);
        pages.get(page).setItem(slot, item);
        return this;
    }

    public GuiAPI setPageItem(int page, int slot, ItemStack item, Runnable action) {
        ensurePage(page);
        pages.get(page).setItem(slot, item, action);
        return this;
    }

    public GuiAPI setNavigation(int prevSlot, ItemStack prevItem, int nextSlot, ItemStack nextItem) {
        this.navigation = new NavigationButtons(prevSlot, prevItem, nextSlot, nextItem);

        permanentActions.put(prevSlot, () -> {
            if (currentPage > 0) {
                currentPage--;
                playSound(Sound.ITEM_BOOK_PAGE_TURN);
                render();
            }
        });

        permanentActions.put(nextSlot, () -> {
            if (currentPage < pages.size() - 1) {
                currentPage++;
                playSound(Sound.ITEM_BOOK_PAGE_TURN);
                render();
            }
        });

        return this;
    }

    public GuiAPI setBackButton(int slot, ItemStack item, Runnable action) {
        return setItem(slot, item, () -> {
            close();
            if (action != null) action.run();
        });
    }

    public void open() {
        if (pages.isEmpty()) addPage();
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        this.inventory = createInventory();
        playSound(Sound.BLOCK_NOTE_BLOCK_PLING);
        player.openInventory(inventory);
    }

    public void close() {
        player.closeInventory();
        unregister();
    }

    public void refresh() {
        render();
    }

    private Inventory createInventory() {
        Inventory inv = Bukkit.createInventory(null, rows * 9, title);

        if (currentPage < pages.size()) {
            pages.get(currentPage).getItems().forEach(inv::setItem);
        }

        permanentItems.forEach((slot, item) -> {
            if (navigation == null || (slot != navigation.prevSlot && slot != navigation.nextSlot)) {
                inv.setItem(slot, item);
            }
        });

        if (navigation != null) {
            if (currentPage > 0) {
                inv.setItem(navigation.prevSlot, navigation.prevItem);
            } else if (permanentItems.containsKey(navigation.prevSlot)) {
                inv.setItem(navigation.prevSlot, permanentItems.get(navigation.prevSlot));
            }

            if (currentPage < pages.size() - 1) {
                inv.setItem(navigation.nextSlot, navigation.nextItem);
            } else if (permanentItems.containsKey(navigation.nextSlot)) {
                inv.setItem(navigation.nextSlot, permanentItems.get(navigation.nextSlot));
            }
        }

        return inv;
    }

    private void render() {
        if (inventory == null) return;

        inventory.clear();

        if (currentPage < pages.size()) {
            pages.get(currentPage).getItems().forEach(inventory::setItem);
        }

        permanentItems.forEach((slot, item) -> {
            if (navigation == null || (slot != navigation.prevSlot && slot != navigation.nextSlot)) {
                inventory.setItem(slot, item);
            }
        });

        if (navigation != null) {
            if (currentPage > 0) {
                inventory.setItem(navigation.prevSlot, navigation.prevItem);
            } else if (permanentItems.containsKey(navigation.prevSlot)) {
                inventory.setItem(navigation.prevSlot, permanentItems.get(navigation.prevSlot));
            }

            if (currentPage < pages.size() - 1) {
                inventory.setItem(navigation.nextSlot, navigation.nextItem);
            } else if (permanentItems.containsKey(navigation.nextSlot)) {
                inventory.setItem(navigation.nextSlot, permanentItems.get(navigation.nextSlot));
            }
        }

        player.updateInventory();
    }

    private void ensurePage(int page) {
        while (pages.size() <= page) addPage();
    }

    private void unregister() {
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (!e.getWhoClicked().equals(player)) return;
        if (inventory == null) return;
        if (!e.getView().getTopInventory().equals(inventory)) return;

        e.setCancelled(true);

        if (e.getClickedInventory() == null) return;
        if (!e.getClickedInventory().equals(inventory)) return;
        if (e.getCurrentItem() == null || e.getCurrentItem().getType().isAir()) return;

        int slot = e.getSlot();
        if (slot < 0 || slot >= rows * 9) return;

        playSound(Sound.UI_BUTTON_CLICK);

        if (permanentActions.containsKey(slot)) {
            permanentActions.get(slot).run();
            return;
        }

        if (currentPage < pages.size()) {
            Runnable action = pages.get(currentPage).getAction(slot);
            if (action != null) action.run();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (!e.getWhoClicked().equals(player)) return;
        if (inventory == null) return;
        if (!e.getView().getTopInventory().equals(inventory)) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!e.getPlayer().equals(player)) return;
        if (inventory == null) return;
        if (!e.getInventory().equals(inventory)) return;
        unregister();
    }

    private void playSound(Sound sound) {
        try {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }

    public Inventory getInventory() {
        return inventory;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPages() {
        return Math.max(1, pages.size());
    }

    public Player getPlayer() {
        return player;
    }

    public static int[] getStandardSlots() {
        return new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };
    }

    private static class GuiPage {
        private final Map<Integer, ItemStack> items = new HashMap<>();
        private final Map<Integer, Runnable> actions = new HashMap<>();

        public void setItem(int slot, ItemStack item) {
            items.put(slot, item);
        }

        public void setItem(int slot, ItemStack item, Runnable action) {
            items.put(slot, item);
            if (action != null) actions.put(slot, action);
        }

        public Map<Integer, ItemStack> getItems() {
            return items;
        }

        public Runnable getAction(int slot) {
            return actions.get(slot);
        }
    }

    private static class NavigationButtons {
        final int prevSlot;
        final ItemStack prevItem;
        final int nextSlot;
        final ItemStack nextItem;

        NavigationButtons(int prevSlot, ItemStack prevItem, int nextSlot, ItemStack nextItem) {
            this.prevSlot = prevSlot;
            this.prevItem = prevItem;
            this.nextSlot = nextSlot;
            this.nextItem = nextItem;
        }
    }
}