package com.UnobtrusiveThirdPerson.camera;

import java.util.concurrent.ScheduledFuture;

final class PeekState {
    private final ScheduledFuture<?> task;

    PeekState(ScheduledFuture<?> task) {
        this.task = task;
    }

    void stop() {
        this.task.cancel(false);
    }
}
