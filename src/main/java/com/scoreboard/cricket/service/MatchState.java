package com.scoreboard.cricket.service;

import com.scoreboard.cricket.model.CreateMatchRequest;
import com.scoreboard.cricket.model.DeliveryRequest;
import com.scoreboard.cricket.model.MatchSnapshot;
import com.scoreboard.cricket.model.MatchSnapshot.BatterStats;
import com.scoreboard.cricket.model.MatchSnapshot.BowlerStats;
import com.scoreboard.cricket.model.MatchSnapshot.TeamScore;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "matches")
@CompoundIndex(name = "match_status_updated_idx", def = "{'status': 1, 'updatedAt': -1}")
public class MatchState {
    @Id
    private String id;
    @Version
    private Long version;
    private String competition;
    private String venue;
    private int maxOvers;
    private TeamScore homeTeam;
    private TeamScore awayTeam;
    private boolean battingHome;
    private List<String> battingLineup;
    private List<String> bowlingLineup;
    private List<String> recentDeliveries = new ArrayList<>();

    private String status;
    private int runs;
    private int wickets;
    private int balls;
    private int strikerIndex;
    private int nonStrikerIndex;
    private int nextBatterIndex;
    private int strikerRuns;
    private int strikerBalls;
    private int strikerFours;
    private int strikerSixes;
    private int nonStrikerRuns;
    private int nonStrikerBalls;
    private int nonStrikerFours;
    private int nonStrikerSixes;
    private int bowlerRuns;
    private int bowlerWickets;
    private long revision;
    private String summary;
    private Instant updatedAt;

    public MatchState() {
    }

    public MatchState(String id, CreateMatchRequest request, TeamScore homeTeam, TeamScore awayTeam) {
        this.id = id;
        this.competition = request.competition().trim();
        this.venue = request.venue().trim();
        this.maxOvers = request.maxOvers();
        this.battingHome = request.battingFirst().equals("HOME");
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.battingLineup = cleanPlayers(battingHome ? request.homePlayers() : request.awayPlayers());
        this.bowlingLineup = cleanPlayers(battingHome ? request.awayPlayers() : request.homePlayers());
        this.status = Boolean.TRUE.equals(request.startImmediately()) ? "LIVE" : "UPCOMING";
        this.nonStrikerIndex = 1;
        this.nextBatterIndex = 2;
        this.summary = status.equals("LIVE")
                ? "First innings underway"
                : "Match scheduled · " + maxOvers + " overs per innings";
        this.updatedAt = Instant.now();
    }

    public synchronized MatchSnapshot snapshot() {
        TeamScore currentHome = currentScore(homeTeam);
        TeamScore currentAway = currentScore(awayTeam);
        return new MatchSnapshot(
                id,
                competition,
                venue,
                status,
                maxOvers,
                currentHome,
                currentAway,
                battingHome ? homeTeam.code() : awayTeam.code(),
                battingHome ? awayTeam.code() : homeTeam.code(),
                1,
                battingLineup.get(strikerIndex),
                battingLineup.get(nonStrikerIndex),
                bowlingLineup.get(0),
                new BatterStats(strikerRuns, strikerBalls, strikerFours, strikerSixes),
                new BatterStats(nonStrikerRuns, nonStrikerBalls, nonStrikerFours, nonStrikerSixes),
                new BowlerStats(balls, bowlerRuns, bowlerWickets),
                List.copyOf(battingLineup),
                List.copyOf(bowlingLineup),
                List.copyOf(recentDeliveries),
                summary,
                revision,
                updatedAt);
    }

    public synchronized MatchSnapshot start() {
        if (!status.equals("UPCOMING")) {
            throw new IllegalStateException("Only upcoming matches can be started");
        }
        status = "LIVE";
        summary = "First innings underway";
        revision++;
        updatedAt = Instant.now();
        return snapshot();
    }

    public synchronized MatchSnapshot recordDelivery(DeliveryRequest delivery) {
        if (!status.equals("LIVE")) {
            throw new IllegalStateException("Only live matches can be updated");
        }
        if (wickets >= maxWickets() || balls >= maxOvers * 6) {
            throw new IllegalStateException("The innings is already complete");
        }

        int runsOffBat = delivery.runs();
        balls++;
        runs += runsOffBat;
        bowlerRuns += runsOffBat;
        addToStriker(runsOffBat);

        if (delivery.wicket()) {
            wickets++;
            bowlerWickets++;
            if (wickets < maxWickets()) {
                strikerIndex = nextBatterIndex++;
                strikerRuns = 0;
                strikerBalls = 0;
                strikerFours = 0;
                strikerSixes = 0;
            }
        }

        if (runsOffBat % 2 == 1) {
            swapStrike();
        }
        if (balls % 6 == 0) {
            swapStrike();
        }

        String outcome = delivery.wicket() ? runsOffBat + " W" : Integer.toString(runsOffBat);
        recentDeliveries.add(outcome);
        if (recentDeliveries.size() > 12) {
            recentDeliveries.remove(0);
        }
        if (wickets >= maxWickets() || balls >= maxOvers * 6) {
            status = "COMPLETED";
            summary = battingTeamName() + " innings complete · " + runs + "/" + wickets;
        } else {
            summary = battingTeamName() + " " + runs + "/" + wickets + " after " + overs();
        }
        revision++;
        updatedAt = Instant.now();
        return snapshot();
    }

    private TeamScore currentScore(TeamScore team) {
        boolean isBattingTeam = team == homeTeam ? battingHome : !battingHome;
        if (!isBattingTeam) {
            return team;
        }
        return new TeamScore(team.name(), team.code(), runs, wickets, balls);
    }

    private String battingTeamName() {
        return battingHome ? homeTeam.name() : awayTeam.name();
    }

    private int maxWickets() {
        return Math.min(10, battingLineup.size() - 1);
    }

    private void addToStriker(int runsOffBat) {
        strikerRuns += runsOffBat;
        strikerBalls++;
        if (runsOffBat == 4) {
            strikerFours++;
        } else if (runsOffBat == 6) {
            strikerSixes++;
        }
    }

    private void swapStrike() {
        int index = strikerIndex;
        strikerIndex = nonStrikerIndex;
        nonStrikerIndex = index;

        int stat = strikerRuns;
        strikerRuns = nonStrikerRuns;
        nonStrikerRuns = stat;
        stat = strikerBalls;
        strikerBalls = nonStrikerBalls;
        nonStrikerBalls = stat;
        stat = strikerFours;
        strikerFours = nonStrikerFours;
        nonStrikerFours = stat;
        stat = strikerSixes;
        strikerSixes = nonStrikerSixes;
        nonStrikerSixes = stat;
    }

    private String overs() {
        return balls / 6 + "." + balls % 6;
    }

    private static List<String> cleanPlayers(List<String> players) {
        return players.stream().map(String::trim).toList();
    }

    public String getId() {
        return id;
    }
}
