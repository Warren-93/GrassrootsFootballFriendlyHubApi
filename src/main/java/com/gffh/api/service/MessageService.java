package com.gffh.api.service;

import com.gffh.api.domain.Fixture;
import com.gffh.api.domain.Message;
import com.gffh.api.domain.NotificationType;
import com.gffh.api.repository.FixtureRepository;
import com.gffh.api.repository.MessageRepository;
import com.gffh.api.web.MessageDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** SCR-FX-05. Purpose: let the two teams on a confirmed fixture coordinate logistics in one place. */
@Service
public class MessageService {

    private final MessageRepository messages;
    private final FixtureRepository fixtures;
    private final MembershipService membershipService;
    private final NotificationService notificationService;

    public MessageService(MessageRepository messages, FixtureRepository fixtures,
                          MembershipService membershipService, NotificationService notificationService) {
        this.messages = messages;
        this.fixtures = fixtures;
        this.membershipService = membershipService;
        this.notificationService = notificationService;
    }

    public List<MessageDtos.MessageView> list(String userId, String fixtureId) {
        Fixture fixture = requireVisible(userId, fixtureId);
        return messages.findByFixtureId(fixture.id()).stream().map(MessageDtos.MessageView::from).toList();
    }

    public MessageDtos.MessageView send(String userId, String fixtureId, MessageDtos.SendMessageRequest request) {
        Fixture fixture = requireVisible(userId, fixtureId);
        String senderTeamId = canManage(userId, fixture.homeTeamId()) ? fixture.homeTeamId() : fixture.awayTeamId();
        String otherTeamId = senderTeamId.equals(fixture.homeTeamId()) ? fixture.awayTeamId() : fixture.homeTeamId();

        Message saved = messages.save(new Message(null, fixture.id(), senderTeamId, userId,
                request.body().trim(), Instant.now()));

        notificationService.notifyTeam(otherTeamId, NotificationType.MESSAGE_RECEIVED,
                "New fixture message", request.body().trim(), null, fixture.id());

        return MessageDtos.MessageView.from(saved);
    }

    private Fixture requireVisible(String userId, String fixtureId) {
        Fixture fixture = fixtures.findById(fixtureId).orElseThrow(() -> new BusinessRuleException(
                "FIXTURE_NOT_FOUND", HttpStatus.NOT_FOUND, "That fixture could not be found."));
        if (!canManage(userId, fixture.homeTeamId()) && !canManage(userId, fixture.awayTeamId())) {
            throw new BusinessRuleException("FIXTURE_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "That fixture could not be found.");
        }
        return fixture;
    }

    private boolean canManage(String userId, String teamId) {
        var role = membershipService.roleFor(userId, teamId);
        return role != null && role.canManageTeam();
    }
}
