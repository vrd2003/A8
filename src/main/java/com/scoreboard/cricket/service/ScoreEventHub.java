package com.scoreboard.cricket.service;

import com.scoreboard.cricket.model.MatchSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ScoreEventHub {
    private static final Logger logger = LoggerFactory.getLogger(ScoreEventHub.class);
    private final CopyOnWriteArrayList<SseEmitter> subscribers = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.add(emitter);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> subscribers.remove(emitter));
        emitter.onError(error -> subscribers.remove(emitter));
        return emitter;
    }

    public void publish(MatchSnapshot match) {
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().name("score").data(match));
            } catch (IOException | IllegalStateException exception) {
                subscribers.remove(emitter);
                emitter.completeWithError(exception);
                logger.debug("Removed disconnected score subscriber", exception);
            }
        }
    }
}
