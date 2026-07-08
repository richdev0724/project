package com.marketmono.service.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

/**
 * Thread-safe local fallback buffer for recent broadcast payloads.
 *
 * <p>Entries are stored in append order with newest entries at the end.
 */
public class LocalRecentBuffer<T> {

    private final int capacity;
    private final List<T> buffer;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    public LocalRecentBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.buffer = new ArrayList<>(capacity);
    }

    public void appendAll(List<T> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        rwLock.writeLock().lock();
        try {
            if (items.size() >= capacity) {
                buffer.clear();
                buffer.addAll(items.subList(items.size() - capacity, items.size()));
                return;
            }

            buffer.addAll(items);
            int overflow = buffer.size() - capacity;
            if (overflow > 0) {
                buffer.subList(0, overflow).clear();
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public List<T> snapshot(int limit) {
        int capped = Math.min(Math.max(limit, 1), capacity);

        rwLock.readLock().lock();
        try {
            if (buffer.isEmpty()) {
                return List.of();
            }

            int from = Math.max(0, buffer.size() - capped);
            return List.copyOf(buffer.subList(from, buffer.size()));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<T> snapshotNewestFirst(int limit, Predicate<T> predicate) {
        int capped = Math.min(Math.max(limit, 1), capacity);

        rwLock.readLock().lock();
        try {
            if (buffer.isEmpty()) {
                return List.of();
            }

            List<T> filtered = new ArrayList<>(Math.min(buffer.size(), capped));
            for (int index = buffer.size() - 1; index >= 0 && filtered.size() < capped; index--) {
                T item = buffer.get(index);
                if (predicate.test(item)) {
                    filtered.add(item);
                }
            }
            return List.copyOf(filtered);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public int getBufferSize() {
        rwLock.readLock().lock();
        try {
            return buffer.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }
}
