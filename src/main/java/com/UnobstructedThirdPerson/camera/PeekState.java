package com.UnobstructedThirdPerson.camera;

import java.util.concurrent.ScheduledFuture;

final class PeekState {
    private final ScheduledFuture<?> task;
    private volatile PeekMode mode;

    PeekState(ScheduledFuture<?> task, PeekMode mode) {
        this.task = task;
        this.mode = mode;
    }

    PeekMode getMode() {
        return mode;
    }

    void setMode(PeekMode mode) {
        this.mode = mode;
    }

    void stop() {
        this.task.cancel(false);
    }
}
