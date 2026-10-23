package com.gffh.api.repository;

import com.gffh.api.model.FriendlyRequest;
import com.gffh.api.model.enums.RequestStatus;
import com.gffh.api.repository.custom.FriendlyRequestRepositoryCustom;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public interface FriendlyRequestRepository extends MongoRepository<FriendlyRequest, String>, FriendlyRequestRepositoryCustom {

    /** Still being negotiated - see {@link #existsOpenBetween}. ACCEPTED is transient but counts. */
    List<RequestStatus> NEGOTIATING = List.of(
            RequestStatus.SENT, RequestStatus.CHANGES_REQUESTED, RequestStatus.UPDATED, RequestStatus.ACCEPTED);

    List<RequestStatus> TERMINAL = Stream.of(RequestStatus.values()).filter(RequestStatus::isTerminal).toList();

    /** Every request this team sent or received. */
    @Query("{ $or: [ { 'senderTeamId': ?0 }, { 'recipientTeamId': ?0 } ] }")
    List<FriendlyRequest> findByTeamId(String teamId);

    List<FriendlyRequest> findByStatusInAndDateBefore(Collection<RequestStatus> statuses, LocalDate date);

    @Query(value = "{ $or: [ { 'senderTeamId': ?0, 'recipientTeamId': ?1 }, { 'senderTeamId': ?1, 'recipientTeamId': ?0 } ],"
            + " 'status': { $in: ?2 } }", exists = true)
    boolean existsBetweenWithStatusIn(String teamAId, String teamBId, Collection<RequestStatus> statuses);

    /** Whether any request, of any status, has ever existed between these two teams - past or present relationship. */
    @Query(value = "{ $or: [ { 'senderTeamId': ?0, 'recipientTeamId': ?1 }, { 'senderTeamId': ?1, 'recipientTeamId': ?0 } ] }",
            exists = true)
    boolean existsBetween(String teamAId, String teamBId);

    @Query(value = "{ $or: [ { 'senderTeamId': ?0 }, { 'recipientTeamId': ?0 } ], 'status': { $nin: ?1 } }", exists = true)
    boolean existsForTeamWithStatusNotIn(String teamId, Collection<RequestStatus> statuses);

    /** CONFIRMED requests whose date has already passed - the fixture auto-completion job's input. */
    default List<FriendlyRequest> findConfirmedBefore(LocalDate date) {
        return findByStatusInAndDateBefore(List.of(RequestStatus.CONFIRMED), date);
    }

    /** Requests still being negotiated (see {@link #existsOpenBetween}) whose date has already passed - the expiry job's input. */
    default List<FriendlyRequest> findNegotiatingBefore(LocalDate date) {
        return findByStatusInAndDateBefore(NEGOTIATING, date);
    }

    /**
     * Whether these two teams are still negotiating a request (SENT,
     * CHANGES_REQUESTED, UPDATED or ACCEPTED). A CONFIRMED friendly doesn't
     * count, so two teams can arrange a second game once the first is agreed;
     * each availability slot is still only booked once.
     */
    default boolean existsOpenBetween(String teamAId, String teamBId) {
        return existsBetweenWithStatusIn(teamAId, teamBId, NEGOTIATING);
    }

    /** Whether this team (sender or recipient) has any open (non-terminal) negotiation - blocks archiving it out from under it. */
    default boolean existsOpenForTeam(String teamId) {
        return existsForTeamWithStatusNotIn(teamId, TERMINAL);
    }
}
