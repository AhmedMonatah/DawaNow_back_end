package com.example.dawanow.service;

import com.example.dawanow.dtos.response.MedicineRequestResultResponse;
import com.example.dawanow.dtos.response.RequestResultUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory registry of SSE streams subscribed by customers waiting for
 * medicine request result updates. One emitter list per request id.
 *
 * Broadcasts (heartbeats and deltas) vastly outnumber subscriptions, so each
 * per-request list is a {@link CopyOnWriteArrayList}. Empty lists are removed
 * from the registry when the last emitter for a request disconnects.
 *
 * Note: the registry is instance-local. If the app is deployed with multiple
 * instances behind a load balancer, an offer handled by a different instance
 * than the one holding the SSE connection will not reach the client. A shared
 * pub/sub transport would be required for multi-instance setups.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestResultSseService {

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emittersByRequestId =
            new ConcurrentHashMap<>();

    @Value("${dawanow.request.search-timeout-minutes:15}")
    private long searchTimeoutMinutes;

    public SseEmitter subscribe(Long requestId) {
        SseEmitter emitter = new SseEmitter(searchTimeoutMinutes * 60_000L + 30_000L);
        emittersByRequestId.computeIfAbsent(requestId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(requestId, emitter));
        emitter.onTimeout(() -> {
            finish(emitter, "timeout");
            remove(requestId, emitter);
        });
        emitter.onError(error -> {
            finish(emitter, "error");
            remove(requestId, emitter);
        });
        return emitter;
    }

    public void sendSnapshot(Long requestId, MedicineRequestResultResponse snapshot) {
        send(requestId, "snapshot", snapshot);
    }

    public void publishDelta(Long requestId, RequestResultUpdateEvent event) {
        send(requestId, "request-item-updated", event);
    }

    /**
     * Sends a final event and completes every open stream for a request.
     * Used when the request is confirmed and no further updates can happen.
     */
    public void closeForRequest(Long requestId) {
        List<SseEmitter> emitters = emittersByRequestId.remove(requestId);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            finish(emitter, "confirmed");
        }
    }

    private void finish(SseEmitter emitter, String reason) {
        try {
            emitter.send(SseEmitter.event().name("stream-closed").data(reason));
        } catch (IOException ignored) {
            // client already gone
        }
        emitter.complete();
    }

    @Scheduled(fixedRateString = "${dawanow.request.sse-heartbeat-seconds:20}000")
    public void heartbeat() {
        emittersByRequestId.forEach((requestId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (IOException e) {
                    remove(requestId, emitter);
                }
            }
        });
    }

    private void send(Long requestId, String eventName, Object payload) {
        List<SseEmitter> emitters = emittersByRequestId.get(requestId);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException e) {
                remove(requestId, emitter);
            }
        }
    }

    private void remove(Long requestId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByRequestId.get(requestId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByRequestId.remove(requestId, emitters);
        }
    }
}
