package dev.ehtid.thaumcraft.port.core;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pure Java port of the collection semantics of BETA26 thaumcraft.api.aspects.AspectList. */
public final class AspectListData {
    private final LinkedHashMap<AspectData, Integer> aspects = new LinkedHashMap<>();

    public AspectListData copy() {
        AspectListData out = new AspectListData();
        for (AspectData aspect : getAspects()) out.add(aspect, getAmount(aspect));
        return out;
    }

    public int size() { return aspects.size(); }

    public int visSize() {
        int sum = 0;
        for (AspectData aspect : aspects.keySet()) sum += getAmount(aspect);
        return sum;
    }

    public AspectData[] getAspects() {
        return aspects.keySet().toArray(AspectData[]::new);
    }

    public AspectData[] getAspectsSortedByName() {
        AspectData[] values = getAspects();
        try {
            // Equivalent result to TC6's hand-written bubble sort for non-null built-in aspects.
            Arrays.sort(values, (a, b) -> {
                if (a == null || b == null) return 0;
                return a.tag().compareTo(b.tag());
            });
            return values;
        } catch (Exception ignored) {
            return getAspects();
        }
    }

    public AspectData[] getAspectsSortedByAmount() {
        AspectData[] values = getAspects();
        try {
            // Implement the legacy bubble-swap condition directly: only positive entries participate,
            // and larger positive amounts move toward the front; equal amounts retain insertion order.
            boolean swapped;
            do {
                swapped = false;
                for (int i = 0; i < values.length - 1; i++) {
                    int left = getAmount(values[i]);
                    int right = getAmount(values[i + 1]);
                    if (left > 0 && right > 0 && right > left) {
                        AspectData tmp = values[i]; values[i] = values[i + 1]; values[i + 1] = tmp;
                        swapped = true;
                        break;
                    }
                }
            } while (swapped);
            return values;
        } catch (Exception ignored) {
            return getAspects();
        }
    }

    public int getAmount(AspectData aspect) {
        Integer value = aspects.get(aspect);
        return value == null ? 0 : value;
    }

    /** Legacy reduce leaves an explicit zero-valued map entry when amount reaches exactly zero. */
    public boolean reduce(AspectData aspect, int amount) {
        if (getAmount(aspect) < amount) return false;
        aspects.put(aspect, getAmount(aspect) - amount);
        return true;
    }

    public AspectListData remove(AspectData aspect, int amount) {
        int left = getAmount(aspect) - amount;
        if (left <= 0) aspects.remove(aspect);
        else aspects.put(aspect, left);
        return this;
    }

    public AspectListData remove(AspectData aspect) {
        aspects.remove(aspect);
        return this;
    }

    public AspectListData add(AspectData aspect, int amount) {
        if (aspects.containsKey(aspect)) amount += aspects.get(aspect);
        aspects.put(aspect, amount);
        return this;
    }

    /** Legacy merge means max(existing, supplied), not addition. */
    public AspectListData merge(AspectData aspect, int amount) {
        if (aspects.containsKey(aspect) && amount < aspects.get(aspect)) amount = aspects.get(aspect);
        aspects.put(aspect, amount);
        return this;
    }

    public AspectListData add(AspectListData other) {
        for (AspectData aspect : other.getAspects()) add(aspect, other.getAmount(aspect));
        return this;
    }

    public AspectListData remove(AspectListData other) {
        for (AspectData aspect : other.getAspects()) remove(aspect, other.getAmount(aspect));
        return this;
    }

    public AspectListData merge(AspectListData other) {
        for (AspectData aspect : other.getAspects()) merge(aspect, other.getAmount(aspect));
        return this;
    }

    public Map<AspectData, Integer> snapshot() { return Map.copyOf(aspects); }
}
