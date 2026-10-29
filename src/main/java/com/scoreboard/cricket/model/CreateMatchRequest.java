package com.scoreboard.cricket.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateMatchRequest(
        @NotBlank @Size(max = 100) String competition,
        @NotBlank @Size(max = 100) String venue,
        @NotBlank @Size(max = 50) String homeTeam,
        @NotBlank @Size(max = 50) String awayTeam,
        @NotEmpty @Size(min = 2, max = 11) List<@NotBlank @Size(max = 60) String> homePlayers,
        @NotEmpty @Size(min = 2, max = 11) List<@NotBlank @Size(max = 60) String> awayPlayers,
        @NotBlank String battingFirst,
        @Min(1) @Max(1000) int maxOvers,
        @NotNull Boolean startImmediately) {
}
