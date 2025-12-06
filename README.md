Main Features

Create custom GUIs with a custom title and any number of rows.

Add static (permanent) items that stay the same across all pages.

Add page-specific items with optional click actions.

Built-in multi-page support, including customizable next/previous page buttons.

Back button support to return to another menu.

Border or row filling utilities for fast UI layout creation.

Automatic event handling, including click, drag, and close listeners.

Per-player GUI instances, ensuring the menu is unique to each viewer.

Sound effects on interaction for better user feedback.

What You Can Do With It

setItem(slot, item) – Sets a static item in the menu.

setItem(slot, item, action) – Adds a static item with a click action.

addPage() – Creates a new page.

setPageItem(page, slot, item, action) – Adds page-specific items and actions.

setNavigation(prevSlot, prevItem, nextSlot, nextItem) – Enables page navigation.

setBackButton(slot, item, action) – Creates a button that closes the menu and runs a callback.

open() – Opens the GUI for the player.

refresh() – Re-renders the GUI content.

close() – Closes the GUI and unregisters listeners.

How It Works

Each GUI instance is tied to a specific player.

All events (click, drag, close) are automatically handled and cancelled appropriately.

Page switching automatically redraws the inventory and plays a sound.

When the GUI is closed, all events are unregistered to avoid memory leaks.

Extras

getCurrentPage() – Returns the currently open page index.

getTotalPages() – Returns the total page count.

getStandardSlots() – Provides a standard layout of centered item slots.
