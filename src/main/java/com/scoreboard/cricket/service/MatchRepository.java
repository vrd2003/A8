package com.scoreboard.cricket.service;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MatchRepository extends MongoRepository<MatchState, String> {
}
