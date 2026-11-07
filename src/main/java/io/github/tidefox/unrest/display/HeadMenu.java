package io.github.tidefox.unrest.display;

import io.github.tidefox.unrest.config.UnrestConfig;
import io.github.tidefox.unrest.core.SleepMath;
import io.github.tidefox.unrest.core.SleepTracker;
import io.github.tidefox.unrest.core.SleepTracker.Status;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.play.server.SPacketOpenWindow;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** The read-only {@code /unrest} chest screen with every player's head (DESIGN.md §7.1). */
public final class HeadMenu {

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;

    private HeadMenu() {}

    public static void open(EntityPlayerMP viewer, SleepTracker tracker) {
        InventoryBasic inv = new InventoryBasic("Unrest", true, SIZE);
        fill(inv, tracker);

        if (viewer.openContainer != viewer.inventoryContainer) {
            viewer.closeScreen();
        }
        viewer.getNextWindowId();
        viewer.connection.sendPacket(new SPacketOpenWindow(viewer.currentWindowId, "minecraft:container",
                inv.getDisplayName(), inv.getSizeInventory()));
        viewer.openContainer = new ReadOnlyContainer(viewer.inventory, inv, viewer);
        viewer.openContainer.windowId = viewer.currentWindowId;
        viewer.openContainer.addListener(viewer);
        MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(viewer, viewer.openContainer));
    }

    private static void fill(IInventory inv, SleepTracker tracker) {
        WorldServer world = tracker.overworld();
        SleepTracker.Night night = tracker.night();
        SleepTracker.Census census = tracker.census();
        double meter = tracker.data().getMeter();
        double debt = tracker.data().getDebt();

        String nightName = night == null ? "Day" : night.type.displayName();
        double dawn = tracker.secondsToDawn(world);
        inv.setInventorySlotContents(0, item(Items.CLOCK, 0, nightName,
                night == null ? "No night in progress"
                        : Double.isInfinite(dawn) ? "Dawn: not in sight" : "Dawn in ~" + SleepMath.formatDuration(dawn)));
        inv.setInventorySlotContents(2, item(Items.EXPERIENCE_BOTTLE, 0, "Sleep meter",
                Math.round(meter * 100) + "%"));
        inv.setInventorySlotContents(4, item(Items.DYE, debtDye(debt), "Unrest",
                dots(debt) + String.format(" %.2f", debt)));
        inv.setInventorySlotContents(6, item(Items.PAPER, 0, "Sleepers",
                census.sleepers.size() + " / " + census.counted.size(),
                "Preset: " + tracker.presetName(world),
                "Mode: " + UnrestConfig.mode.id()));

        List<EntityPlayerMP> players = new ArrayList<>(tracker.overworld().getMinecraftServer().getPlayerList().getPlayers());
        Collections.sort(players, Comparator
                .comparingInt((EntityPlayerMP p) -> order(census.status.get(p.getUniqueID())))
                .thenComparing(EntityPlayerMP::getName, String.CASE_INSENSITIVE_ORDER));
        int slot = 9;
        for (EntityPlayerMP p : players) {
            if (slot >= SIZE) {
                break;
            }
            inv.setInventorySlotContents(slot++, head(p, lore(tracker, p, census.status.get(p.getUniqueID()))));
        }
    }

    private static int order(Status s) {
        if (s == null) {
            return 4;
        }
        switch (s) {
            case SLEEPING:
                return 0;
            case IN_BED:
            case AWAKE:
                return 1;
            case AFK:
                return 2;
            default:
                return 3;
        }
    }

    private static String lore(SleepTracker tracker, EntityPlayerMP p, Status s) {
        if (s == null) {
            return "Awake";
        }
        switch (s) {
            case SLEEPING:
                String zz = UnrestConfig.asciiOnly ? "Zz" : "💤";
                return zz + " Sleeping " + SleepMath.formatDuration(tracker.activity().millisInBed(p) / 1000.0);
            case IN_BED:
                return "In bed";
            case AFK:
                return "AFK (" + tracker.activity().idleMillis(p) / 60000L + "m)";
            case ELSEWHERE:
                return "Elsewhere";
            default:
                return "Awake";
        }
    }

    private static ItemStack head(EntityPlayerMP p, String lore) {
        ItemStack stack = new ItemStack(Items.SKULL, 1, 3);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("SkullOwner", NBTUtil.writeGameProfile(new NBTTagCompound(), p.getGameProfile()));
        stack.setTagCompound(tag);
        named(stack, p.getName(), lore);
        return stack;
    }

    private static ItemStack item(Item item, int meta, String name, String... lore) {
        ItemStack stack = new ItemStack(item, 1, meta);
        named(stack, name, lore);
        return stack;
    }

    private static void named(ItemStack stack, String name, String... lore) {
        stack.setStackDisplayName("§r" + name);
        NBTTagCompound display = stack.getOrCreateSubCompound("display");
        NBTTagList list = new NBTTagList();
        for (String line : lore) {
            list.appendTag(new NBTTagString("§7" + line));
        }
        display.setTag("Lore", list);
    }

    /** ●●○○○ for Unrest out of its maximum. */
    static String dots(double debt) {
        int max = (int) Math.ceil(UnrestConfig.debtMax);
        int full = (int) Math.round(Math.min(debt, max));
        String on = UnrestConfig.asciiOnly ? "#" : "●";
        String off = UnrestConfig.asciiOnly ? "-" : "○";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            sb.append(i < full ? on : off);
        }
        return sb.toString();
    }

    /** Dye colour for Unrest: lime, yellow, orange, red. */
    private static int debtDye(double debt) {
        double f = UnrestConfig.debtMax <= 0 ? 0 : debt / UnrestConfig.debtMax;
        if (f < 0.25) {
            return 10;
        }
        if (f < 0.5) {
            return 11;
        }
        if (f < 0.75) {
            return 14;
        }
        return 1;
    }

    /** A chest container where every click is cancelled. */
    static final class ReadOnlyContainer extends ContainerChest {

        ReadOnlyContainer(IInventory playerInventory, IInventory chest, EntityPlayer player) {
            super(playerInventory, chest, player);
        }

        @Override
        public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
            if (player instanceof EntityPlayerMP) {
                EntityPlayerMP mp = (EntityPlayerMP) player;
                // Undo whatever the client predicted.
                mp.sendContainerToPlayer(this);
                mp.connection.sendPacket(new SPacketSetSlot(-1, -1, mp.inventory.getItemStack()));
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack transferStackInSlot(EntityPlayer player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canMergeSlot(ItemStack stack, net.minecraft.inventory.Slot slot) {
            return false;
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }
}
