package com.UnobstructedThirdPerson.camera;

final class ManagedBlock {
    final BlockSnapshot original;
    final int fakeId;
    volatile long lastRefreshMillis;

    ManagedBlock(BlockSnapshot original, int fakeId) {
        this.original = original;
        this.fakeId = fakeId;
        this.lastRefreshMillis = System.currentTimeMillis();
    }

    void refresh() {
        this.lastRefreshMillis = System.currentTimeMillis();
    }

    boolean isExpired(long decayThresholdMillis) {
        return (System.currentTimeMillis() - lastRefreshMillis) > decayThresholdMillis;
    }
}
