package io.github.tidefox.unrest.data;

import io.github.tidefox.unrest.core.NightType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/** Unrest's saved state on the Overworld (DESIGN.md §10). */
public class UnrestWorldData extends WorldSavedData {

    public static final String NAME = "unrest";

    private double debt;
    private String nightType = "";
    private long nightDay = -1;
    private double meter;

    public UnrestWorldData() {
        this(NAME);
    }

    public UnrestWorldData(String name) {
        super(name);
    }

    public static UnrestWorldData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        UnrestWorldData data = (UnrestWorldData) storage.getOrLoadData(UnrestWorldData.class, NAME);
        if (data == null) {
            data = new UnrestWorldData();
            storage.setData(NAME, data);
        }
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        debt = nbt.getDouble("debt");
        nightType = nbt.getString("nightType");
        nightDay = nbt.hasKey("nightDay") ? nbt.getLong("nightDay") : -1;
        meter = nbt.getDouble("meter");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setDouble("debt", debt);
        nbt.setString("nightType", nightType);
        nbt.setLong("nightDay", nightDay);
        nbt.setDouble("meter", meter);
        return nbt;
    }

    public double getDebt() {
        return debt;
    }

    public void setDebt(double debt) {
        if (this.debt != debt) {
            this.debt = debt;
            markDirty();
        }
    }

    /** The night type saved for {@code day}, or null if none was rolled for that day. */
    public NightType getNightType(long day) {
        return nightDay == day ? NightType.fromId(nightType) : null;
    }

    public void setNight(NightType type, long day) {
        this.nightType = type == null ? "" : type.id();
        this.nightDay = day;
        markDirty();
    }

    public double getMeter() {
        return meter;
    }

    public void setMeter(double meter) {
        if (this.meter != meter) {
            this.meter = meter;
            markDirty();
        }
    }
}
