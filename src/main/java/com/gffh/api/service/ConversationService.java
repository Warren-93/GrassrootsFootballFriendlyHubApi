package com.gffh.api.service;

import com.gffh.api.domain.Conversation;
import com.gffh.api.domain.Message;
import com.gffh.api.domain.NotificationType;
import com.gffh.api.domain.Team;
import com.gffh.api.repository.AvailabilityRepository;
import com.gffh.api.repository.BlockRepository;
import com.gffh.api.repository.ConversationRepository;
import com.gffh.api.repository.FriendlyRequestRepository;
import com.gffh.api.repository.MessageRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.web.ConversationDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Team-to-team chat, decoupled from any particular request or fixture. A team
 * becomes contactable the moment it publishes availability - the whole point
 * is to let two teams talk before either has committed to a formal invitation,
 * not just to coordinate logistics after one's been accepted.
 */
@Service
public class ConversationService {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final TeamRepository teams;
    private final AvailabilityRepository availability;
    private final BlockRepository blocks;
    private final FriendlyRequestRepository friendlyRequests;
    private final MembershipService membershipService;
    private final NotificationService notificationService;

    public ConversationService(ConversationRepository conversations, MessageRepository messages,
                               TeamRepository teams, AvailabilityRepository availability, BlockRepository blocks,
                               FriendlyRequestRepository friendlyRequests, MembershipService membershipService,
                               NotificationService notificationService) {
        this.conversations = conversations;
        this.messages = messages;
        this.teams = teams;
        this.availability = availability;
        this.blocks = blocks;
        this.friendlyRequests = friendlyRequests;
        this.membershipService = membershipService;
        this.notificationService = notificationService;
    }

    /**
     * Finds the existing conversation between these two teams, or starts one.
     * A brand new conversation requires the other team to actually be
     * reachable: they've published availability, or the two teams already
     * have some request history (any status) - otherwise this would just be
     * cold-messaging a team that never opted into being found at all.
     */
    public ConversationDtos.ConversationView start(String userId, ConversationDtos.StartConversationRequest request) {
        membershipService.requireCanManageTeam(userId, request.teamId());
        if (request.teamId().equals(request.otherTeamId())) {
            throw new BusinessRuleException("CANNOT_MESSAGE_OWN_TEAM", HttpStatus.BAD_REQUEST,
                    "You can't start a conversation with your own team.");
        }

        Team otherTeam = teams.findById(request.otherTeamId()).orElseThrow(BusinessRuleException::blocked);
        if (otherTeam.suspended() || otherTeam.archived()) {
            throw BusinessRuleException.blocked();
        }
        if (blocks.blockedTeamIdsEitherDirection(request.teamId()).contains(request.otherTeamId())) {
            throw BusinessRuleException.blocked();
        }

        Optional<Conversation> existing = conversations.findBetweenTeams(request.teamId(), request.otherTeamId());
        if (existing.isPresent()) {
            return ConversationDtos.ConversationView.of(existing.get(), otherTeam);
        }

        boolean hasPublishedAvailability = !availability
                .findBookableForTeamBetween(request.otherTeamId(), LocalDate.now(), LocalDate.now().plusYears(2))
                .isEmpty();
        boolean hasRequestHistory = friendlyRequests.existsBetween(request.teamId(), request.otherTeamId());
        if (!hasPublishedAvailability && !hasRequestHistory) {
            throw new BusinessRuleException("TEAM_NOT_CONTACTABLE", HttpStatus.CONFLICT,
                    "That team hasn't published any availability yet, so they can't be messaged.");
        }

        Instant now = Instant.now();
        Conversation saved = conversations.save(
                new Conversation(null, request.teamId(), request.otherTeamId(), now, now, null, null));
        return ConversationDtos.ConversationView.of(saved, otherTeam);
    }

    public List<ConversationDtos.ConversationView> list(String userId, String teamId) {
        membershipService.requireCanManageTeam(userId, teamId);
        return conversations.findByTeamId(teamId).stream()
                .map(c -> {
                    Team other = teams.findById(c.otherTeam(teamId)).orElse(null);
                    return other == null ? null : ConversationDtos.ConversationView.of(c, other);
                })
                .filter(v -> v != null)
                .toList();
    }

    public ConversationDtos.ConversationView get(String userId, String conversationId) {
        Conversation conversation = requireVisible(userId, conversationId);
        String otherTeamId = canManage(userId, conversation.teamAId())
                ? conversation.teamBId() : conversation.teamAId();
        Team other = teams.findById(otherTeamId).orElseThrow(BusinessRuleException::blocked);
        return ConversationDtos.ConversationView.of(conversation, other);
    }

    public List<ConversationDtos.MessageView> messages(String userId, String conversationId) {
        Conversation conversation = requireVisible(userId, conversationId);
        return messages.findByConversationId(conversation.id()).stream()
                .map(ConversationDtos.MessageView::from).toList();
    }

    public ConversationDtos.MessageView send(String userId, String conversationId,
                                             ConversationDtos.SendMessageRequest request) {
        Conversation conversation = requireVisible(userId, conversationId);
        if (blocks.blockedTeamIdsEitherDirection(conversation.teamAId()).contains(conversation.teamBId())) {
            throw BusinessRuleException.blocked();
        }

        String senderTeamId = membershipService.roleFor(userId, conversation.teamAId()) != null
                && membershipService.roleFor(userId, conversation.teamAId()).canManageTeam()
                ? conversation.teamAId() : conversation.teamBId();
        String otherTeamId = conversation.otherTeam(senderTeamId);
        String body = request.body().trim();

        Message saved = messages.save(new Message(null, conversation.id(), senderTeamId, userId, body, Instant.now()));

        conversations.save(new Conversation(conversation.id(), conversation.teamAId(), conversation.teamBId(),
                conversation.createdAt(), saved.createdAt(), body, senderTeamId));

        notificationService.notifyTeam(otherTeamId, NotificationType.MESSAGE_RECEIVED,
                "New message", body, null, null, conversation.id());

        return ConversationDtos.MessageView.from(saved);
    }

    private Conversation requireVisible(String userId, String conversationId) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(BusinessRuleException::blocked);
        if (!canManage(userId, conversation.teamAId()) && !canManage(userId, conversation.teamBId())) {
            throw BusinessRuleException.blocked();
        }
        return conversation;
    }

    private boolean canManage(String userId, String teamId) {
        var role = membershipService.roleFor(userId, teamId);
        return role != null && role.canManageTeam();
    }
}
