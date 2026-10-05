package dev.zcode.piston_diversified.logic;

import dev.zcode.piston_diversified.PdHelpers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
//? if >=1.21.2 {
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
//?}

/**
 * The 马铃薯活塞's self-managed event queue: the piston-less "推出事件" the landed head registers
 * behind itself (planned as a block-independent event, since a scheduled tick cannot be attached
 * to the plain block that lands there). Persisted per dimension as saved data, so a restart or a
 * chunk unload cannot silently drop a structure that is still flying — on reload the pending
 * pushes simply resume one tick later.
 */
public final class PotatoFlightQueue {
    private PotatoFlightQueue() {
    }

    /** One pending push: the landed cell, the direction it keeps flying, the structure record. */
    public record Event(String dimension, BlockPos pos, Direction direction, long[] record, String expectedBlock, int age) {
        Event withAge(int newAge) {
            return new Event(this.dimension, this.pos, this.direction, this.record, this.expectedBlock, newAge);
        }
    }

    /** Register a pending push; it fires one tick later. */
    public static void add(ServerLevel level, BlockPos pos, Direction direction, long[] record, net.minecraft.world.level.block.Block expected) {
        if (record.length == 0) {
            return;
        }
        data(level).events.add(new Event(
            PdHelpers.dimensionId(level),
            pos.immutable(),
            direction,
            record.clone(),
            net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(expected).toString(),
            0
        ));
        data(level).setDirty();
    }

    /** Advance every pending event by one tick and run those that reached their 1gt delay. */
    public static void tick(ServerLevel level) {
        PotatoFlightData data = data(level);
        if (data.events.isEmpty()) {
            return;
        }
        List<Event> remaining = new ArrayList<>();
        boolean changed = false;
        for (Event event : data.events) {
            if (!event.dimension().equals(PdHelpers.dimensionId(level))) {
                remaining.add(event);
                continue;
            }
            Event aged = event.withAge(event.age() + 1);
            if (aged.age() < 1) {
                remaining.add(aged);
                continue;
            }
            changed = true;
            PotatoPushLogic.flightPush(level, aged);
        }
        data.events.clear();
        data.events.addAll(remaining);
        if (changed || data.events.size() != remaining.size()) {
            data.setDirty();
        }
    }

    public static PotatoFlightData data(ServerLevel level) {
        //? if >=1.21.2 {
        return level.getDataStorage().computeIfAbsent(PotatoFlightData.TYPE);
        //?} else {
        return level.getDataStorage().computeIfAbsent(PotatoFlightData::load, PotatoFlightData::new, PdHelpers.POTATO_FLIGHT_DATA_ID);
        //?}
    }
}