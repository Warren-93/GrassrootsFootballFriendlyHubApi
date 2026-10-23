package com.gffh.api.repository;

import com.gffh.api.model.Venue;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;

public interface VenueRepository extends MongoRepository<Venue, String> {

    List<Venue> findByClubId(String clubId);

    /** Unsets every other venue's default flag for this club, so exactly one default holds (SCR-PR-05). */
    @Query("{ 'clubId': ?0 }")
    @Update("{ $set: { 'isDefault': false } }")
    void clearDefaultForClub(String clubId);
}
