package com.scoreboard.cricket.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record DeliveryRequest(
        @Min(0) @Max(6) int runs,
        boolean wicket) {
}
