package com.gffh.api.repository.mongo;

import com.gffh.api.repository.CascadeDeletionRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Works on the stored collections and field names directly rather than the
 * per-type documents, so it stays one readable list of "what belongs to a
 * team, a club and an account". Each collection is matched by one combined
 * query, so a record linked several ways (a membership both of the deleted
 * user and on a deleted team) is counted once.
 */
@Repository
public class MongoCascadeDeletionRepository implements CascadeDeletionRepository {

    private final MongoTemplate mongoTemplate;

    public MongoCascadeDeletionRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    private record Step(String label, String collection, Criteria criteria) {}

    @Override
    public LinkedHashMap<String, Long> count(Scope scope) {
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        for (Step step : steps(scope)) {
            counts.put(step.label(), mongoTemplate.count(new Query(step.criteria()), step.collection()));
        }
        return counts;
    }

    @Override
    public LinkedHashMap<String, Long> delete(Scope scope) {
        LinkedHashMap<String, Long> removed = new LinkedHashMap<>();
        for (Step step : steps(scope)) {
            removed.put(step.label(), mongoTemplate.remove(new Query(step.criteria()), step.collection()).getDeletedCount());
        }
        return removed;
    }

    /** In deletion order: what hangs off a team or account first, the team, club and account last. */
    private List<Step> steps(Scope scope) {
        Set<String> teams = scope.teamIds();
        Set<String> clubs = scope.clubIds();
        String userId = scope.userId();

        List<String> conversationIds = ids("conversations", anyOf(teams, "teamAId", "teamBId"));
        List<String> requestIds = ids("friendlyRequests", anyOf(teams, "senderTeamId", "recipientTeamId"));
        List<String> fixtureIds = ids("fixtures", anyOf(teams, "homeTeamId", "awayTeamId"));

        List<Step> steps = new ArrayList<>();
        steps.add(new Step("Availability slots", "availability", in("teamId", teams)));
        steps.add(new Step("Friendly requests", "friendlyRequests", anyOf(teams, "senderTeamId", "recipientTeamId")));
        steps.add(new Step("Fixtures", "fixtures", anyOf(teams, "homeTeamId", "awayTeamId")));
        steps.add(new Step("Chat messages", "messages", in("conversationId", conversationIds)));
        steps.add(new Step("Chats", "conversations", in("_id", conversationIds)));
        steps.add(new Step("Blocks", "blocks", anyOf(teams, "blockingTeamId", "blockedTeamId")));
        steps.add(new Step("Reports", "reports", anyOf(teams, "reporterTeamId", "reportedTeamId")));
        steps.add(new Step("Verification requests", "verification_requests", in("teamId", teams)));
        steps.add(new Step("Join codes", "joinCodes", in("teamId", teams)));

        List<Criteria> invites = new ArrayList<>(List.of(in("teamId", teams)));
        if (scope.userEmail() != null) {
            invites.add(Criteria.where("email").is(scope.userEmail().trim().toLowerCase(Locale.ROOT)));
        }
        steps.add(new Step("Team invites", "memberInvites", or(invites)));

        List<Criteria> notifications = new ArrayList<>();
        notifications.add(new Criteria().andOperator(
                new Criteria().orOperator(
                        in("relatedTeamId", teams),
                        in("relatedRequestId", requestIds),
                        in("relatedFixtureId", fixtureIds),
                        in("relatedConversationId", conversationIds)),
                Criteria.where("createdAt").lt(scope.notificationsBefore())));
        if (userId != null) notifications.add(Criteria.where("userId").is(userId));
        steps.add(new Step("Notifications", "notifications", or(notifications)));

        List<Criteria> memberships = new ArrayList<>(List.of(in("teamId", teams), in("clubId", clubs)));
        if (userId != null) memberships.add(Criteria.where("userId").is(userId));
        steps.add(new Step("Memberships", "memberships", or(memberships)));

        steps.add(new Step("Teams", "teams", in("_id", teams)));
        steps.add(new Step("Venues", "venues", in("clubId", clubs)));
        steps.add(new Step("Clubs", "clubs", in("_id", clubs)));

        if (userId != null) {
            steps.add(new Step("Sign-in sessions", "refreshTokens", Criteria.where("userId").is(userId)));
            steps.add(new Step("Push devices", "pushTokens", Criteria.where("userId").is(userId)));
            steps.add(new Step("Email links", "verificationTokens", Criteria.where("userId").is(userId)));
            steps.add(new Step("Notification settings", "notificationPreferences", Criteria.where("_id").is(userId)));
            steps.add(new Step("Test account record", "test_accounts", Criteria.where("userId").is(userId)));
            steps.add(new Step("Account", "users", Criteria.where("_id").is(userId)));
        }
        return steps;
    }

    private List<String> ids(String collection, Criteria criteria) {
        Query query = new Query(criteria);
        query.fields().include("_id");
        return mongoTemplate.find(query, Document.class, collection).stream()
                .map(d -> String.valueOf(d.get("_id")))
                .toList();
    }

    private static Criteria in(String field, Collection<String> values) {
        return Criteria.where(field).in(values);
    }

    private static Criteria anyOf(Collection<String> values, String... fields) {
        List<Criteria> each = new ArrayList<>();
        for (String field : fields) each.add(in(field, values));
        return or(each);
    }

    private static Criteria or(List<Criteria> criteria) {
        return new Criteria().orOperator(criteria.toArray(new Criteria[0]));
    }
}
