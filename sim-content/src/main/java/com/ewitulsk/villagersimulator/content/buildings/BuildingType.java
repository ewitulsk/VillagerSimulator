package com.ewitulsk.villagersimulator.content.buildings;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.registry.RegistryKey;
import com.ewitulsk.villagersimulator.content.VS;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A building definition, loaded from {@code data/<ns>/villagersimulator/building_types/*.json}. Points are block
 * positions relative to the blueprint origin (see docs/ARCHITECTURE.md §13.1):
 * <ul>
 *   <li>{@code bed}: where a resident sleeps (one per resident)</li>
 *   <li>{@code work}: where a worker stands (one per job slot)</li>
 *   <li>{@code service}: where visitors are served, e.g. where villagers eat</li>
 *   <li>{@code wander}: spots for free time</li>
 *   <li>{@code anchor}: the building anchor block</li>
 * </ul>
 *
 * @param blueprint    structure template id ({@code data/<ns>/structure/<path>.nbt})
 * @param size         blueprint size {@code [x, y, z]}
 * @param services     what visitors can do here: {@code eat}, {@code gather}
 * @param initialStock goods the building starts with
 */
public record BuildingType(Id blueprint, List<Integer> size, Map<String, List<List<Integer>>> points,
                           Optional<Job> job, List<String> services, Map<String, Integer> initialStock) {

    /** A job at this building: {@code slots} workers doing {@code activity}. */
    public record Job(Id id, int slots, Id activity) {
        public static final Codec<Job> CODEC = RecordCodecBuilder.create(i -> i.group(
                Id.CODEC.fieldOf("id").forGetter(Job::id),
                Codec.INT.fieldOf("slots").forGetter(Job::slots),
                Id.CODEC.fieldOf("activity").forGetter(Job::activity)
        ).apply(i, Job::new));
    }

    public static final Codec<BuildingType> CODEC = RecordCodecBuilder.create(i -> i.group(
            Id.CODEC.fieldOf("blueprint").forGetter(BuildingType::blueprint),
            Codec.INT.listOf().fieldOf("size").forGetter(BuildingType::size),
            Codec.unboundedMap(Codec.STRING, Codec.INT.listOf().listOf()).fieldOf("points").forGetter(BuildingType::points),
            Job.CODEC.optionalFieldOf("job").forGetter(BuildingType::job),
            Codec.STRING.listOf().optionalFieldOf("services", List.of()).forGetter(BuildingType::services),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("initial_stock", Map.of()).forGetter(BuildingType::initialStock)
    ).apply(i, BuildingType::new));

    public static final RegistryKey<BuildingType> REGISTRY = new RegistryKey<>(VS.id("building_type"), "building_types", CODEC);

    public static final String BED = "bed";
    public static final String WORK = "work";
    public static final String SERVICE = "service";
    public static final String WANDER = "wander";
    public static final String ANCHOR = "anchor";

    public List<List<Integer>> points(String kind) {
        return points.getOrDefault(kind, List.of());
    }

    public int beds() {
        return points(BED).size();
    }

    public boolean offers(String service) {
        return services.contains(service);
    }

    public int sizeX() {
        return size.get(0);
    }

    public int sizeY() {
        return size.get(1);
    }

    public int sizeZ() {
        return size.get(2);
    }
}
