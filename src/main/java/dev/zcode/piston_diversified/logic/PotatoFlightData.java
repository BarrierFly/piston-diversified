package dev.zcode.piston_diversified.logic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.zcode.piston_diversified.PdHelpers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21.2 {
import net.minecraft.world.level.saveddata.SavedDataType;
//?}

/**
 * Saved data holding the 马铃薯活塞's pending flight pushes ({@link PotatoFlightQueue}).
 * Written when an event is queued or consumed; on load the queue resumes.
 *
 * <p>1.21.2+ goes through a codec (that is what {@code SavedDataType} stores), 1.19.4 keeps the
 * classic {@code save(CompoundTag)} round trip.</p>
 */
public class PotatoFlightData extends SavedData {
    private static final String POTATO_FLIGHT_DATA_ID = PdHelpers.POTATO_FLIGHT_DATA_ID;

    public final List<PotatoFlightQueue.Event> events = new ArrayList<>();

    public PotatoFlightData() {
    }

    //? if <1.21.2 {
    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (PotatoFlightQueue.Event event : this.events) {
            CompoundTag entry = new CompoundTag();
            entry.putString("d", event.dimension());
            entry.putIntArray("p", new int[] {event.pos().getX(), event.pos().getY(), event.pos().getZ()});
            entry.putInt("f", event.direction().get3DDataValue());
            entry.putLongArray("r", event.record());
            entry.putString("b", event.expectedBlock());
            list.add(entry);
        }
        tag.put("Events", list);
        return tag;
    }

    public static PotatoFlightData load(CompoundTag tag) {
        PotatoFlightData data = new PotatoFlightData();
        ListTag list = tag.getList("Events", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int[] p = entry.getIntArray("p");
            if (p.length != 3) {
                continue;
            }
            data.events.add(new PotatoFlightQueue.Event(
                entry.getString("d"),
                new BlockPos(p[0], p[1], p[2]),
                Direction.from3DDataValue(entry.getInt("f")),
                entry.getLongArray("r"),
                entry.getString("b"),
                0
            ));
        }
        return data;
    }
    //?} else {
    /** NbtOps has no long-array codec helper of its own; a list of longs round-trips fine. */
    private static final Codec<List<Long>> RECORD_CODEC = Codec.LONG.listOf();

    private static final Codec<PotatoFlightQueue.Event> EVENT_CODEC = RecordCodecBuilder.<PotatoFlightQueue.Event>create(instance -> instance.group(
        Codec.STRING.fieldOf("dimension").forGetter(PotatoFlightQueue.Event::dimension),
        BlockPos.CODEC.fieldOf("pos").forGetter(PotatoFlightQueue.Event::pos),
        Direction.CODEC.fieldOf("direction").forGetter(PotatoFlightQueue.Event::direction),
        RECORD_CODEC.fieldOf("record").forGetter(event -> java.util.Arrays.stream(event.record()).boxed().toList()),
        Codec.STRING.fieldOf("expected").forGetter(PotatoFlightQueue.Event::expectedBlock)
    ).apply(instance, (dimension, pos, direction, record, expected) ->
        new PotatoFlightQueue.Event(dimension, pos, direction, record.stream().mapToLong(Long::longValue).toArray(), expected, 0)));

    private static final Codec<PotatoFlightData> CODEC = RecordCodecBuilder.<PotatoFlightData>create(instance -> instance.group(
        EVENT_CODEC.listOf().fieldOf("events").forGetter(data -> data.events)
    ).apply(instance, events -> {
        PotatoFlightData data = new PotatoFlightData();
        data.events.addAll(events);
        return data;
    }));

    public static final SavedDataType<PotatoFlightData> TYPE = new SavedDataType<>(
        //? if >=26.1 {
        PdHelpers.mcId(POTATO_FLIGHT_DATA_ID),
        //?} else {
        PdHelpers.POTATO_FLIGHT_DATA_ID,
        //?}
        PotatoFlightData::new,
        CODEC,
        null // no datafix type: the payload is plain NBT only this mod reads
    );
    //?}
}