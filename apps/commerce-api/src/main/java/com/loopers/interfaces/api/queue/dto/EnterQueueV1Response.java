package com.loopers.interfaces.api.queue.dto;

import com.loopers.application.queue.QueueInfo;

public record EnterQueueV1Response(
    long position,
    long waitingCount
) {
    public static EnterQueueV1Response from(QueueInfo.Enter info) {
        return new EnterQueueV1Response(info.position(), info.waitingCount());
    }
}
