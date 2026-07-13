package com.loopers.application.queue;

public final class QueueInfo {

    private QueueInfo() {
    }

    public record Enter(long position, long waitingCount) {
    }

    public record Position(long position, long estimatedWaitSeconds, String token) {
    }
}
