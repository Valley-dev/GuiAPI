package dev.shadesfactions.valley.api;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GuiAPI implements Listener {

    private static final Map<UUID, GuiAPI> activeGuis = new ConcurrentHashMap<>();
    
    private final Plugin plugin;
    private final Player player;
    private final String title;
    private final int size;
    private final int rows;
    
    private Inventory inventory;
    private final Map<Integer, ItemStack> permanentItems;
    private final Map<Integer, Runnable> permanentActions;
    private final List<Page> pages;
    private int currentPage;
    private boolean registered;
    private Navigation navigation;
    private BukkitTask closeTask;
    private boolean closed;

    public GuiAPI(Plugin plugin, Player player, String title, int rows) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        if (player == null) throw new IllegalArgumentException("Player cannot be null");
        if (title == null) throw new IllegalArgumentException("Title cannot be null");
        if (rows < 1 || rows > 6) throw new IllegalArgumentException("Rows must be between 1 and 6");
        
        this.plugin = plugin;
        this.player = player;
        this.title = ChatColor.translateAlternateColorCodes('&', title);
        this.rows = rows;
        this.size = rows * 9;
        this.permanentItems = new HashMap<>();
        this.permanentActions = new HashMap<>();
        this.pages = new ArrayList<>();
        this.currentPage = 0;
        this.registered = false;
        this.closed = false;
    }

    public GuiAPI setItem(int slot, ItemStack item) {
        validateSlot(slot);
        if (item != null) {
            permanentItems.put(slot, item.clone());
        } else {
            permanentItems.remove(slot);
        }
        return this;
    }

    public GuiAPI setItem(int slot, ItemStack item, Runnable action) {
        validateSlot(slot);
        if (item != null) {
            permanentItems.put(slot, item.clone());
            if (action != null) {
                permanentActions.put(slot, action);
            } else {
                permanentActions.remove(slot);
            }
        } else {
            permanentItems.remove(slot);
            permanentActions.remove(slot);
        }
        return this;
    }

    public GuiAPI fillBorder(ItemStack item) {
        if (item == null) return this;
        ItemStack border = item.clone();
        
        for (int i = 0; i < 9; i++) {
            permanentItems.put(i, border.clone());
            permanentItems.put((rows - 1) * 9 + i, border.clone());
        }
        
        for (int i = 1; i < rows - 1; i++) {
            permanentItems.put(i * 9, border.clone());
            permanentItems.put(i * 9 + 8, border.clone());
        }
        return this;
    }

    public GuiAPI fillRow(int row, ItemStack item) {
        if (row < 0 || row >= rows) throw new IllegalArgumentException("Invalid row: " + row);
        if (item == null) return this;
        
        ItemStack fill = item.clone();
        for (int i = 0; i < 9; i++) {
            permanentItems.put(row * 9 + i, fill.clone());
        }
        return this;
    }

    public GuiAPI addPage() {
        pages.add(new Page());
        return this;
    }

    public GuiAPI setPageItem(int page, int slot, ItemStack item) {
        ensurePage(page);
        validateSlot(slot);
        pages.get(page).setItem(slot, item);
        return this;
    }

    public GuiAPI setPageItem(int page, int slot, ItemStack item, Runnable action) {
        ensurePage(page);
        validateSlot(slot);
        pages.get(page).setItem(slot, item, action);
        return this;
    }

    public GuiAPI setNavigation(int prevSlot, ItemStack prevItem, int nextSlot, ItemStack nextItem) {
        validateSlot(prevSlot);
        validateSlot(nextSlot);
        if (prevSlot == nextSlot) throw new IllegalArgumentException("Navigation slots must be different");
        
        this.navigation = new Navigation(
            prevSlot, 
            prevItem != null ? prevItem.clone() : null,
            nextSlot, 
            nextItem != null ? nextItem.clone() : null
        );

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
            if (action != null) {
                Bukkit.getScheduler().runTask(plugin, action);
            }
        });
    }

    public void open() {
        if (closed) throw new IllegalStateException("Cannot reopen a closed GUI");
        if (!player.isOnline()) return;
        
        GuiAPI existing = activeGuis.get(player.getUniqueId());
        if (existing != null && existing != this) {
            existing.forceClose();
        }
        
        if (pages.isEmpty()) addPage();
        
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        
        this.inventory = buildInventory();
        activeGuis.put(player.getUniqueId(), this);
        playSound(Sound.BLOCK_NOTE_BLOCK_PLING);
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !closed) {
                player.openInventory(inventory);
            }
        });
    }

    public void close() {
        if (closed) return;
        
        if (player.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (inventory != null && player.getOpenInventory().getTopInventory().equals(inventory)) {
                    player.closeInventory();
                }
            });
        }
        
        cleanup();
    }

    public void refresh() {
        if (closed || inventory == null) return;
        if (!player.isOnline()) {
            cleanup();
            return;
        }
        
        if (player.getOpenInventory().getTopInventory().equals(inventory)) {
            render();
        }
    }

    private Inventory buildInventory() {
        Inventory inv = Bukkit.createInventory(null, size, title);
        populateInventory(inv);
        return inv;
    }

    private void render() {
        if (inventory == null || closed) return;
        
        inventory.clear();
        populateInventory(inventory);
        
        try {
            player.updateInventory();
        } catch (Exception ignored) {}
    }

    private void populateInventory(Inventory inv) {
        if (currentPage < pages.size()) {
            pages.get(currentPage).getItems().forEach((slot, item) -> {
                if (slot >= 0 && slot < size && item != null) {
                    inv.setItem(slot, item.clone());
                }
            });
        }

        permanentItems.forEach((slot, item) -> {
            if (navigation == null || (slot != navigation.prevSlot && slot != navigation.nextSlot)) {
                if (slot >= 0 && slot < size && item != null) {
                    inv.setItem(slot, item.clone());
                }
            }
        });

        if (navigation != null) {
            if (currentPage > 0 && navigation.prevItem != null) {
                inv.setItem(navigation.prevSlot, navigation.prevItem.clone());
            } else if (permanentItems.containsKey(navigation.prevSlot)) {
                ItemStack fallback = permanentItems.get(navigation.prevSlot);
                if (fallback != null) {
                    inv.setItem(navigation.prevSlot, fallback.clone());
                }
            }

            if (currentPage < pages.size() - 1 && navigation.nextItem != null) {
                inv.setItem(navigation.nextSlot, navigation.nextItem.clone());
            } else if (permanentItems.containsKey(navigation.nextSlot)) {
                ItemStack fallback = permanentItems.get(navigation.nextSlot);
                if (fallback != null) {
                    inv.setItem(navigation.nextSlot, fallback.clone());
                }
            }
        }
    }

    private void ensurePage(int page) {
        if (page < 0) throw new IllegalArgumentException("Page cannot be negative");
        while (pages.size() <= page) {
            addPage();
        }
    }

    private void validateSlot(int slot) {
        if (slot < 0 || slot >= size) {
            throw new IllegalArgumentException("Slot " + slot + " is out of bounds for inventory size " + size);
        }
    }

    private void cleanup() {
        closed = true;
        activeGuis.remove(player.getUniqueId());
        
        if (closeTask != null && !closeTask.isCancelled()) {
            closeTask.cancel();
            closeTask = null;
        }
        
        unregister();
        inventory = null;
    }

    private void forceClose() {
        if (player.isOnline() && inventory != null) {
            if (player.getOpenInventory().getTopInventory().equals(inventory)) {
                player.closeInventory();
            }
        }
        cleanup();
    }

    private void unregister() {
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (!e.getWhoClicked().equals(player)) return;
        if (inventory == null || closed) return;
        if (!e.getView().getTopInventory().equals(inventory)) return;

        e.setCancelled(true);

        if (e.getClickedInventory() == null) return;
        if (!e.getClickedInventory().equals(inventory)) return;
        
        ItemStack current = e.getCurrentItem();
        if (current == null || current.getType().isAir()) return;

        int slot = e.getSlot();
        if (slot < 0 || slot >= size) return;

        playSound(Sound.UI_BUTTON_CLICK);

        if (permanentActions.containsKey(slot)) {
            Runnable action = permanentActions.get(slot);
            if (action != null) {
                try {
                    action.run();
                } catch (Exception ex) {
                    plugin.getLogger().warning("Error executing GUI action: " + ex.getMessage());
                }
            }
            return;
        }

        if (currentPage < pages.size()) {
            Runnable action = pages.get(currentPage).getAction(slot);
            if (action != null) {
                try {
                    action.run();
                } catch (Exception ex) {
                    plugin.getLogger().warning("Error executing page action: " + ex.getMessage());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (!e.getWhoClicked().equals(player)) return;
        if (inventory == null || closed) return;
        if (!e.getView().getTopInventory().equals(inventory)) return;
        
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!e.getPlayer().equals(player)) return;
        if (inventory == null) return;
        if (!e.getInventory().equals(inventory)) return;
        
        closeTask = Bukkit.getScheduler().runTaskLater(plugin, this::cleanup, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        if (!e.getPlayer().equals(player)) return;
        cleanup();
    }

    private void playSound(Sound sound) {
        if (!player.isOnline()) return;
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

    public boolean isClosed() {
        return closed;
    }

    public static GuiAPI getActiveGui(Player player) {
        return activeGuis.get(player.getUniqueId());
    }

    public static void closeAll() {
        new ArrayList<>(activeGuis.values()).forEach(GuiAPI::forceClose);
    }

    public static int[] getStandardSlots() {
        return new int[]{
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };
    }

    private static class Page {
        private final Map<Integer, ItemStack> items = new HashMap<>();
        private final Map<Integer, Runnable> actions = new HashMap<>();

        public void setItem(int slot, ItemStack item) {
            if (item != null) {
                items.put(slot, item.clone());
            } else {
                items.remove(slot);
            }
        }

        public void setItem(int slot, ItemStack item, Runnable action) {
            if (item != null) {
                items.put(slot, item.clone());
                if (action != null) {
                    actions.put(slot, action);
                } else {
                    actions.remove(slot);
                }
            } else {
                items.remove(slot);
                actions.remove(slot);
            }
        }

        public Map<Integer, ItemStack> getItems() {
            return items;
        }

        public Runnable getAction(int slot) {
            return actions.get(slot);
        }
    }

    private static class Navigation {
        final int prevSlot;
        final ItemStack prevItem;
        final int nextSlot;
        final ItemStack nextItem;

        Navigation(int prevSlot, ItemStack prevItem, int nextSlot, ItemStack nextItem) {
            this.prevSlot = prevSlot;
            this.prevItem = prevItem;
            this.nextSlot = nextSlot;
            this.nextItem = nextItem;
        }
    }
}
