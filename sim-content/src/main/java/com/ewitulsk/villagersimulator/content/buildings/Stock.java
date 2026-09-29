package com.ewitulsk.villagersimulator.content.buildings;

import com.mojang.serialization.Codec;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Goods held by a building. Phase 0 keeps a simple ledger keyed by good name; real containers and the goods registry
 * arrive in Phase 12.
 */
public record Stock(Map<String, Integer> items) {
    public static final Codec<Stock> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(Stock::new, Stock::items);

    public Stock {
        items = Collections.unmodifiableMap(new TreeMap<>(items));
    }

    public int count(String good) {
        return items.getOrDefault(good, 0);
    }

    public Stock with(String good, int count) {
        Map<String, Integer> m = new TreeMap<>(items);
        if (count <= 0) m.remove(good);
        else m.put(good, count);
        return new Stock(m);
    }
}
