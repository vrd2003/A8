package com.scoreboard.cricket.service;

import com.scoreboard.cricket.model.CreateMatchRequest;
import com.scoreboard.cricket.model.DeliveryRequest;
import com.scoreboard.cricket.model.MatchSnapshot;
import com.scoreboard.cricket.model.MatchSnapshot.TeamScore;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ScoreService {
    private final MatchRepository matches;
    private final ScoreEventHub eventHub;

    public ScoreService(MatchRepository matches, ScoreEventHub eventHub) {
        this.matches = matches;
        this.eventHub = eventHub;
    }

    public List<MatchSnapshot> getMatches() {
        return matches.findAll().stream()
                .map(MatchState::snapshot)
                .sorted(Comparator
                        .comparingInt((MatchSnapshot match) -> switch (match.status()) {
                            case "LIVE" -> 0;
                            case "UPCOMING" -> 1;
                            default -> 2;
                        })
                        .thenComparing(MatchSnapshot::updatedAt, Comparator.reverseOrder()))
                .toList();
    }

    public MatchSnapshot getMatch(String id) {
        return findMatch(id).snapshot();
    }

    public MatchSnapshot createMatch(CreateMatchRequest request) {
        validateMatch(request);
        TeamScore home = team(request.homeTeam(), "HOME");
        TeamScore away = team(request.awayTeam(), "AWAY");
        if (home.code().equals(away.code())) {
            away = new TeamScore(away.name(), away.code() + "2", 0, 0, 0);
        }

        CreateMatchRequest normalized = new CreateMatchRequest(
                request.competition(),
                request.venue(),
                home.name(),
                away.name(),
                request.homePlayers(),
                request.awayPlayers(),
                request.battingFirst(),
                request.maxOvers(),
                request.startImmediately());
        String id = UUID.randomUUID().toString();
        MatchState newMatch = new MatchState(id, normalized, home, away);
        MatchSnapshot created = matches.save(newMatch).snapshot();
        eventHub.publish(created);
        return created;
    }

    public MatchSnapshot startMatch(String id) {
        MatchSnapshot updated = changeState(id, MatchState::start);
        eventHub.publish(updated);
        return updated;
    }

    public MatchSnapshot recordDelivery(String id, DeliveryRequest delivery) {
        MatchSnapshot updated = changeState(id, match -> match.recordDelivery(delivery));
        eventHub.publish(updated);
        return updated;
    }

    private MatchSnapshot changeState(String id, MatchChange change) {
        MatchState match = findMatch(id);
        try {
            change.apply(match);
            return matches.save(match).snapshot();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This match changed in another request. Refresh the scorecard and try again.",
                    exception);
        }
    }

    private MatchState findMatch(String id) {
        return matches.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found: " + id));
    }

    private void validateMatch(CreateMatchRequest request) {
        if (!request.battingFirst().equals("HOME") && !request.battingFirst().equals("AWAY")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "battingFirst must be HOME or AWAY");
        }
        if (request.homeTeam().trim().equalsIgnoreCase(request.awayTeam().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team names must be different");
        }
        validatePlayers(request.homePlayers(), "homePlayers");
        validatePlayers(request.awayPlayers(), "awayPlayers");
    }

    private void validatePlayers(List<String> players, String field) {
        long uniquePlayers = players.stream()
                .map(String::trim)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .distinct()
                .count();
        if (uniquePlayers != players.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must contain unique player names");
        }
    }

    private static TeamScore team(String name, String fallbackCode) {
        String compact = name.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        String code = (compact + fallbackCode).substring(0, 3);
        return new TeamScore(name.trim(), code, 0, 0, 0);
    }

    @FunctionalInterface
    private interface MatchChange {
        MatchSnapshot apply(MatchState match);
    }
}
