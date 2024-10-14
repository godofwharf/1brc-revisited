package dev.godofwharf.onebrc;

import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public class LinearProbedMap<K, V> {
    private Entry<K, V>[] table;
    private int n;
    private int size;

    public LinearProbedMap(final int capacity) {
        // slots are numbered from [0, n - 1)
        // where n is smallest power of 2 which is greater than capacity
        // Let us assume n is some 2^k
        // n when represented in binary has the following form: 1 followed by 'k' zeroes
        // -1 which is 1 followed by 31 zeroes can be right-shifted by number of leading zeroes to get n
        n = -1 >>> Integer.numberOfLeadingZeros(capacity - 1);
        size = n + 1;
        table = new Entry[size];
    }

    public V get(final K key) {
        int h = key.hashCode();
        return findElement(key, h);
    }

    public void put(final K key,
                    final V value) {
        int h = key.hashCode();
        int slot = findSlot(key, h);
        if (table[slot] == null) {
            table[slot] = new Entry<>(key, value, h);
            return;
        }
        table[slot].value = value;
    }

    public V compute(final K key,
                     final BiFunction<K, V, V> biFunction) {
        int h = key.hashCode();
        int slot = findSlot(key, h);
        Entry<K, V> entry = table[slot];
        entry = (entry == null) ? new Entry<>(key, null, h): entry;
        V oldValue = entry.value;
        entry.setValue(biFunction.apply(key, oldValue));
        table[slot] = entry;
        return oldValue;
    }

    public Set<Map.Entry<K, V>> entrySet() {
        Set<Map.Entry<K, V>> set = new HashSet<>();
        for (int i = 0; i < size; i++) {
            if (table[i] != null) {
                set.add(new AbstractMap.SimpleEntry<>(table[i].key, table[i].value));
            }
        }
        return set;
    }

    private V findElement(final K key,
                          final int h) {
        int slot = h & n;
        int originalSlot = slot;
        if (table[slot] == null) {
            return null;
        } else if (isSameKey(key, h, slot)) {
            return table[slot].value;
        }
        // there is a collision! need to look at other possible slots
        slot = (slot + 1) & n;
        while (slot != originalSlot &&
                table[slot] != null) {
            if (table[slot].hashcode == h &&
                    table[slot].value.equals(key)) {
                return table[slot].value;
            }
            slot = (slot + 1) & n;
        }
        return null;
    }

    private int findSlot(final K key,
                         final int h) {
        int slot = (h & n);
        int originalSlot = slot;
        if (isValidSlot(key, h, slot)) {
            return slot;
        }
        // look at other slots
        slot = ((slot + 1) & n);
        // Stop conditions:
        // Loop until either one of these happen
        // if slot == originalSlot
        // or
        // if isValidSlot(key, h, slot)
        while (slot != originalSlot &&
                !isValidSlot(key, h, slot)) {
            // check next possible slot
            slot = ((slot + 1) & n);
        }
        if (slot == originalSlot) {
            throw new RuntimeException("No slot found for key: %s".formatted(key));
        }
        return slot;
    }

    private boolean isValidSlot(final K key,
                                final int h,
                                final int slot) {
        // slot is valid
        // if either the entry corresponding to slot in table is empty
        // or
        // if the entry's key is the same as new key
        return table[slot] == null || isSameKey(key, h, slot);
    }

    private boolean isSameKey(final K key,
                              final int h,
                              final int slot) {
        // key is same
        // if the hashcode of entry's key == new key's hashcode
        // and
        // entry's key == new key
        return h == table[slot].hashcode && table[slot].key.equals(key);
    }

    public static class Entry<K, V> {
        private K key;
        private V value;
        private int hashcode;

        public Entry(final K key,
                     final V value,
                     int hashcode) {
            this.key = key;
            this.value = value;
            this.hashcode = hashcode;
        }

        public void setValue(final V value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "key=%s/value=%s/hashcode=%d".formatted(key, value, hashcode);
        }
    }
}
