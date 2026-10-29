package com.scoreboard.cricket.model;

import java.time.Instant;
import java.util.List;

public record MatchSnapshot(
        String id,
        String competition,
        String venue,
        String status,
        int maxOvers,
        TeamScore homeTeam,
        TeamScore awayTeam,
        String battingTeam,
        String bowlingTeam,
        int innings,
        String striker,
        String nonStriker,
        String bowler,
        BatterStats strikerStats,
        BatterStats nonStrikerStats,
        BowlerStats bowlerStats,
        List<String> battingLineup,
        List<String> bowlingLineup,
        List<String> recentDeliveries,
        String summary,
        long revision,
        Instant updatedAt) {

    public record TeamScore(String name, String code, int runs, int wickets, int balls) {
    }

    public record BatterStats(int runs, int balls, int fours, int sixes) {
    }

    public record BowlerStats(int balls, int runs, int wickets) {
    }
}
