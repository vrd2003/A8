package com.scoreboard.cricket.service;

import com.scoreboard.cricket.model.CreateMatchRequest;
import com.scoreboard.cricket.model.DeliveryRequest;
import com.scoreboard.cricket.model.MatchSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScoreServiceTest {
    private final Map<String, MatchState> storedMatches = new ConcurrentHashMap<>();
    private MatchRepository repository;
    private ScoreService scoreService;

    @BeforeEach
    void setUp() {
        repository = mock(MatchRepository.class);
        when(repository.findAll()).thenAnswer(invocation -> List.copyOf(storedMatches.values()));
        when(repository.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(storedMatches.get(invocation.getArgument(0))));
        when(repository.save(any(MatchState.class))).thenAnswer(invocation -> {
            MatchState match = invocation.getArgument(0);
            storedMatches.put(match.getId(), match);
            return match;
        });
        scoreService = new ScoreService(repository, new ScoreEventHub());
    }

    @AfterEach
    void clearStoredMatches() {
        storedMatches.clear();
    }

    @Test
    void startsWithNoFabricatedMatches() {
        assertThat(scoreService.getMatches()).isEmpty();
    }

    @Test
    void createsAndStartsAMatchUsingUserProvidedDetails() {
        MatchSnapshot created = scoreService.createMatch(matchRequest("HOME", 20, false));

        verify(repository).save(any(MatchState.class));
        assertThat(created.status()).isEqualTo("UPCOMING");
        assertThat(created.competition()).isEqualTo("Club final");
        assertThat(created.homeTeam().name()).isEqualTo("Falcons");
        assertThat(created.homeTeam().runs()).isZero();
        assertThat(created.maxOvers()).isEqualTo(20);
        assertThat(created.battingLineup()).containsExactly("Opening batter", "Second batter", "Third batter");

        MatchSnapshot started = scoreService.startMatch(created.id());
        assertThat(started.status()).isEqualTo("LIVE");
        assertThat(started.striker()).isEqualTo("Opening batter");
        assertThat(started.nonStriker()).isEqualTo("Second batter");
        assertThat(started.bowler()).isEqualTo("Bowling captain");
    }

    @Test
    void readsPreviouslySavedMatchesFromTheRepository() {
        MatchSnapshot saved = scoreService.createMatch(matchRequest("HOME", 20, false));
        ScoreService restartedService = new ScoreService(repository, new ScoreEventHub());

        assertThat(restartedService.getMatch(saved.id()).competition()).isEqualTo("Club final");
        assertThat(restartedService.getMatches()).extracting(MatchSnapshot::id).containsExactly(saved.id());
    }

    @Test
    void rejectsUpdatesWhenMongoReportsAConcurrentWrite() {
        MatchSnapshot upcoming = scoreService.createMatch(matchRequest("HOME", 20, false));
        doThrow(new OptimisticLockingFailureException("stale version"))
                .when(repository).save(any(MatchState.class));

        assertThatThrownBy(() -> scoreService.startMatch(upcoming.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void recordsRunsAndUpdatesBatterAndBowlerStatistics() {
        MatchSnapshot match = scoreService.createMatch(matchRequest("HOME", 20, true));

        MatchSnapshot updated = scoreService.recordDelivery(match.id(), new DeliveryRequest(4, false));

        assertThat(updated.homeTeam().runs()).isEqualTo(4);
        assertThat(updated.homeTeam().balls()).isEqualTo(1);
        assertThat(updated.striker()).isEqualTo("Opening batter");
        assertThat(updated.strikerStats().runs()).isEqualTo(4);
        assertThat(updated.strikerStats().fours()).isEqualTo(1);
        assertThat(updated.bowlerStats().balls()).isEqualTo(1);
        assertThat(updated.bowlerStats().runs()).isEqualTo(4);
        assertThat(updated.revision()).isEqualTo(1);
    }

    @Test
    void aWicketBringsInTheNextBatter() {
        MatchSnapshot match = scoreService.createMatch(matchRequest("HOME", 20, true));

        MatchSnapshot updated = scoreService.recordDelivery(match.id(), new DeliveryRequest(0, true));

        assertThat(updated.homeTeam().wickets()).isEqualTo(1);
        assertThat(updated.status()).isEqualTo("LIVE");
        assertThat(updated.striker()).isEqualTo("Third batter");
    }

    @Test
    void allOutCompletesAnInnings() {
        MatchSnapshot match = scoreService.createMatch(matchRequest("HOME", 20, true,
                "Falcons", "Hawks", List.of("Opening batter", "Second batter")));

        MatchSnapshot updated = scoreService.recordDelivery(match.id(), new DeliveryRequest(0, true));

        assertThat(updated.homeTeam().wickets()).isEqualTo(1);
        assertThat(updated.status()).isEqualTo("COMPLETED");
        assertThat(updated.summary()).contains("innings complete");
    }

    @Test
    void completesAnInningsWhenItsConfiguredOversAreUsed() {
        MatchSnapshot match = scoreService.createMatch(matchRequest("HOME", 1, true));

        for (int ball = 0; ball < 6; ball++) {
            match = scoreService.recordDelivery(match.id(), new DeliveryRequest(1, false));
        }

        assertThat(match.status()).isEqualTo("COMPLETED");
        assertThat(match.homeTeam().balls()).isEqualTo(6);
        assertThat(match.homeTeam().runs()).isEqualTo(6);
        String matchId = match.id();
        assertThatThrownBy(() -> scoreService.recordDelivery(matchId, new DeliveryRequest(0, false)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectsDuplicateTeamAndPlayerNames() {
        assertThatThrownBy(() -> scoreService.createMatch(matchRequest("HOME", 20, false,
                "Falcons", "Falcons", List.of("A", "B", "C"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> scoreService.createMatch(matchRequest("HOME", 20, false,
                "Falcons", "Hawks", List.of("A", "a", "C"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void keepsTeamsDistinctWhenTheirAbbreviationsMatch() {
        MatchSnapshot match = scoreService.createMatch(matchRequest("AWAY", 20, true,
                "Falcons", "Falling Stars", List.of("A", "B", "C")));

        MatchSnapshot updated = scoreService.recordDelivery(match.id(), new DeliveryRequest(1, false));

        assertThat(updated.homeTeam().code()).isNotEqualTo(updated.awayTeam().code());
        assertThat(updated.homeTeam().runs()).isZero();
        assertThat(updated.awayTeam().runs()).isEqualTo(1);
    }

    @Test
    void rejectsStartingOrScoringInvalidMatches() {
        MatchSnapshot upcoming = scoreService.createMatch(matchRequest("HOME", 20, false));
        assertThatThrownBy(() -> scoreService.recordDelivery(upcoming.id(), new DeliveryRequest(1, false)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> scoreService.startMatch("missing"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    private CreateMatchRequest matchRequest(String battingFirst, int maxOvers, boolean startImmediately) {
        return matchRequest(battingFirst, maxOvers, startImmediately, "Falcons", "Hawks",
                List.of("Opening batter", "Second batter", "Third batter"));
    }

    private CreateMatchRequest matchRequest(
            String battingFirst,
            int maxOvers,
            boolean startImmediately,
            String homeTeam,
            String awayTeam,
            List<String> homePlayers) {
        return new CreateMatchRequest(
                "Club final",
                "Community ground",
                homeTeam,
                awayTeam,
                homePlayers,
                List.of("Bowling captain", "Second bowler"),
                battingFirst,
                maxOvers,
                startImmediately);
    }
}
