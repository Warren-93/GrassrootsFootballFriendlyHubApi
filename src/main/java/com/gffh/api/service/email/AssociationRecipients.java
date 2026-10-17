package com.gffh.api.service.email;

import com.gffh.api.model.Team;

import java.util.List;

/**
 * Extension point for copying fixture confirmations to the association (county
 * FA, league) a team plays under. Nothing implements it yet, so associations
 * are not emailed. To start, add a {@code @Component} that returns the
 * association's contact address(es) for a team - for example by looking up
 * {@link Team#league()} - and {@link FixtureConfirmationEmails} picks it up.
 */
public interface AssociationRecipients {

    /** Email addresses that should get a copy of a confirmation involving {@code team}. */
    List<String> emailsFor(Team team);
}
