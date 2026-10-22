package com.gffh.api.repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * Everything stored about a set of teams, clubs and (optionally) one user
 * account, across every collection - for the admin's permanent delete. Kept
 * in one place, instead of a delete method on each repository, so that the
 * preview an admin approves and the delete that runs use exactly the same
 * criteria.
 */
public interface CascadeDeletionRepository {

    /**
     * @param teamIds   teams to remove, with their availability, requests, fixtures, chats, blocks,
     *                  memberships, join codes, invites, verification requests and reports
     * @param clubIds   clubs to remove, with their venues and club-level memberships
     * @param userId    the account to remove with its memberships, sessions, devices, email links,
     *                  notifications and settings; null when only teams/clubs are being deleted
     * @param userEmail that account's email, for team invites sent to it; null with userId
     * @param notificationsBefore notifications about the teams created at or after this moment are
     *                  kept - they're the "fixture cancelled" notices just sent to the other teams
     */
    record Scope(Set<String> teamIds, Set<String> clubIds, String userId, String userEmail,
                 Instant notificationsBefore) {}

    /** How many records each kind of data holds for the scope, by label, in deletion order. Changes nothing. */
    LinkedHashMap<String, Long> count(Scope scope);

    /**
     * Removes it all, children before the team, club and account themselves,
     * so a delete cut short can simply be run again. Returns what was removed.
     */
    LinkedHashMap<String, Long> delete(Scope scope);
}
