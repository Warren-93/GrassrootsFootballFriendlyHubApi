package com.gffh.api.service.email;

import com.gffh.api.model.MemberInvite;
import com.gffh.api.model.Team;
import com.gffh.api.model.enums.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * SCR-PR-04's two emails: "you've been added" to someone who already has an
 * account, and an invitation to an address that doesn't have one yet.
 *
 * <p>Like {@link FixtureConfirmationEmails}, sending happens off the request
 * thread and a mail failure never fails adding the member.
 */
@Service
public class MemberEmails {

    private static final Logger log = LoggerFactory.getLogger(MemberEmails.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK)
            .withZone(ZoneId.of("Europe/London"));

    private final EmailService email;
    private final Executor executor;

    @Autowired
    public MemberEmails(EmailService email) {
        this(email, Executors.newVirtualThreadPerTaskExecutor());
    }

    MemberEmails(EmailService email, Executor executor) {
        this.email = email;
        this.executor = executor;
    }

    /** Tells an existing account holder they now have a role on {@code team}. Never throws. */
    public void sendAdded(String to, String inviterName, Team team, Role role) {
        queue("added-to-team", () -> email.send(to, oneLine("You've been added to " + team.name()),
                addedBody(inviterName, team, role)));
    }

    /** Invites {@code invite.email()} to register, after which the invite becomes a membership. Never throws. */
    public void sendInvite(MemberInvite invite, String inviterName, Team team) {
        queue("member-invite", () -> email.send(invite.email(),
                oneLine("%s invited you to join %s on Kickabout".formatted(inviterName, team.name())),
                inviteBody(invite, inviterName, team)));
    }

    /** Names are typed by users; a subject must stay on one line. */
    private static String oneLine(String s) {
        return s.replaceAll("\\s+", " ").strip();
    }

    private void queue(String what, Runnable send) {
        if (!email.isConfigured()) {
            log.info("Email not configured; no {} email", what);
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    send.run();
                } catch (Exception e) {
                    log.warn("Could not send {} email", what, e);
                }
            });
        } catch (Exception e) {
            log.warn("Could not queue {} email", what, e);
        }
    }

    String addedBody(String inviterName, Team team, Role role) {
        return """
                %s added you to %s on Kickabout as %s.

                See the team on Kickabout:
                %s

                If you think this is a mistake, ask one of the team's managers to remove you.
                """.formatted(inviterName, team.name(), describe(role), email.appLink("/team/" + team.id()));
    }

    String inviteBody(MemberInvite invite, String inviterName, Team team) {
        return """
                %s invited you to join %s on Kickabout as %s.

                Kickabout helps grassroots teams find and arrange friendlies. To accept, create your account with this email address:

                %s

                Once you've confirmed your email address, you'll be added to %s automatically. The invitation expires on %s.

                If you weren't expecting this, you can ignore this email.
                """.formatted(inviterName, team.name(), describe(invite.role()), registerLink(invite.email()),
                team.name(), DATE.format(invite.expiresAt()));
    }

    /** {@code ${APP_BASE_URL}/register?email=...}, which the clients use to prefill the email field. */
    String registerLink(String address) {
        return email.appLink("/register?email=" + URLEncoder.encode(address, StandardCharsets.UTF_8));
    }

    private static String describe(Role role) {
        return switch (role) {
            case TEAM_MANAGER -> "a team manager";
            case CLUB_ADMIN -> "a club admin";
            default -> "a member";
        };
    }
}
