package com.scoreboard.cricket.api;

import com.scoreboard.cricket.model.CreateMatchRequest;
import com.scoreboard.cricket.model.DeliveryRequest;
import com.scoreboard.cricket.model.MatchSnapshot;
import com.scoreboard.cricket.service.ScoreEventHub;
import com.scoreboard.cricket.service.ScoreService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchController {
    private final ScoreService scoreService;
    private final ScoreEventHub eventHub;

    public MatchController(ScoreService scoreService, ScoreEventHub eventHub) {
        this.scoreService = scoreService;
        this.eventHub = eventHub;
    }

    @GetMapping
    public List<MatchSnapshot> getMatches() {
        return scoreService.getMatches();
    }

    @PostMapping
    public ResponseEntity<MatchSnapshot> createMatch(@Valid @RequestBody CreateMatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scoreService.createMatch(request));
    }

    @GetMapping("/{id}")
    public MatchSnapshot getMatch(@PathVariable String id) {
        return scoreService.getMatch(id);
    }

    @PostMapping("/{id}/start")
    public MatchSnapshot startMatch(@PathVariable String id) {
        return scoreService.startMatch(id);
    }

    @PostMapping("/{id}/deliveries")
    public MatchSnapshot recordDelivery(
            @PathVariable String id,
            @Valid @RequestBody DeliveryRequest delivery) {
        return scoreService.recordDelivery(id, delivery);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamScores() throws IOException {
        SseEmitter emitter = eventHub.subscribe();
        emitter.send(SseEmitter.event().name("snapshot").data(scoreService.getMatches()));
        return emitter;
    }
}
