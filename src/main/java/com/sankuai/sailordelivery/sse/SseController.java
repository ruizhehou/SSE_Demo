package com.sankuai.sailordelivery.sse;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/sse")
public class SseController {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * 客户端订阅 SSE 流
     * 用法: GET /sse/subscribe/{clientId}
     */
    @GetMapping(value = "/subscribe/{clientId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String clientId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> emitters.remove(clientId));
        emitter.onTimeout(() -> emitters.remove(clientId));
        emitter.onError(e -> emitters.remove(clientId));

        emitters.put(clientId, emitter);

        // 发送连接成功事件
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("Connected, clientId=" + clientId));
        } catch (IOException e) {
            emitters.remove(clientId);
        }

        return emitter;
    }

    /**
     * 向指定客户端推送消息
     * 用法: GET /sse/push/{clientId}?message=hello
     */
    @GetMapping("/push/{clientId}")
    public String push(@PathVariable String clientId,
            @org.springframework.web.bind.annotation.RequestParam String message) {
        SseEmitter emitter = emitters.get(clientId);
        if (emitter == null) {
            return "Client not found: " + clientId;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(message));
            return "Sent to " + clientId;
        } catch (IOException e) {
            emitters.remove(clientId);
            return "Failed to send: " + e.getMessage();
        }
    }

    /**
     * 广播消息给所有在线客户端
     * 用法: GET /sse/broadcast?message=hello
     */
    @GetMapping("/broadcast")
    public String broadcast(@org.springframework.web.bind.annotation.RequestParam String message) {
        emitters.forEach((clientId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("broadcast")
                        .data(message));
            } catch (IOException e) {
                emitters.remove(clientId);
            }
        });
        return "Broadcast to " + emitters.size() + " clients";
    }

    /**
     * 模拟服务端定时推送（演示用）
     * 用法: GET /sse/demo/{clientId}
     */
    @GetMapping("/demo/{clientId}")
    public String startDemo(@PathVariable String clientId) {
        SseEmitter emitter = emitters.get(clientId);
        if (emitter == null) {
            return "Client not found: " + clientId;
        }
        scheduler.scheduleAtFixedRate(() -> {
            SseEmitter e = emitters.get(clientId);
            if (e == null) {
                return;
            }
            try {
                e.send(SseEmitter.event()
                        .name("tick")
                        .data("Server time: " + System.currentTimeMillis()));
            } catch (IOException ex) {
                emitters.remove(clientId);
            }
        }, 0, 1, TimeUnit.SECONDS);
        return "Demo started for " + clientId;
    }
}
