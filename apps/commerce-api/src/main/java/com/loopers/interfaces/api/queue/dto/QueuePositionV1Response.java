package com.loopers.interfaces.api.queue.dto;

import com.loopers.application.queue.QueueInfo;

public record QueuePositionV1Response(
    long position,
    long estimatedWaitSeconds,
    String token
) {
    public static QueuePositionV1Response from(QueueInfo.Position info) {
        return new QueuePositionV1Response(info.position(), info.estimatedWaitSeconds(), info.token());
    }
}
