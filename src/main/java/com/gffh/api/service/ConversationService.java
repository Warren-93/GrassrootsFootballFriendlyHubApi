package com.gffh.api.service;

import com.gffh.api.dto.ConversationDtos;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.Conversation;
import com.gffh.api.model.Message;
import com.gffh.api.model.MessageGif;
import com.gffh.api.model.Team;
import com.gffh.api.model.enums.NotificationType;
import com.gffh.api.repository.AvailabilityRepository;
import com.gffh.api.repository.BlockRepository;
import com.gffh.api.repository.ConversationRepository;
import com.gffh.api.repository.FriendlyRequestRepository;
import com.gffh.api.repository.MessageRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.service.gif.GifService;
import com.gffh.api.service.moderation.ChatFilterService;
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
    private final AuditLogService auditLogService;
    private final ChatFilterService chatFilter;
    private final GifService gifService;

    /** The inbox preview and notification text for a GIF sent without words. */
    static final String GIF_PREVIEW = "Sent a GIF";

    public ConversationService(ConversationRepository conversations, MessageRepository messages,
                               TeamRepository teams, AvailabilityRepository availability, BlockRepository blocks,
                               FriendlyRequestRepository friendlyRequests, MembershipService membershipService,
                               NotificationService notificationService, AuditLogService auditLogService,
                               ChatFilterService chatFilter, GifService gifService) {
        this.conversations = conversations;
        this.messages = messages;
        this.teams = teams;
        this.availability = availability;
        this.blocks = blocks;
        this.friendlyRequests = friendlyRequests;
        this.membershipService = membershipService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.chatFilter = chatFilter;
        this.gifService = gifService;
    }

    /**
     * Admin-only: the transcript between two teams, for investigating a
     * report. Bypasses the membership check {@link #requireVisible} enforces
     * for team-side access - the caller is a platform admin, not a team
     * member - and records an audit entry every time, since reading message
     * content is a genuinely sensitive action distinct from every other
     * read-only admin view.
     */
    public List<ConversationDtos.MessageView> transcriptForAdmin(String adminId, String teamAId, String teamBId) {
        Optional<Conversation> conversation = conversations.findBetweenTeams(teamAId, teamBId);
        if (conversation.isEmpty()) {
            return List.of();
        }
        auditLogService.record(adminId, "CONVERSATION_VIEWED", "CONVERSATION", conversation.get().getId(), null);
        return messages.findByConversationIdOrderByCreatedAtAsc(conversation.get().getId()).stream()
                .map(ConversationDtos.MessageView::from).toList();
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
        if (otherTeam.isSuspended() || otherTeam.isArchived()) {
            throw BusinessRuleException.blocked();
        }
        if (blocks.blockedTeamIdsEitherDirection(request.teamId()).contains(request.otherTeamId())) {
            throw BusinessRuleException.blocked();
        }

        Optional<Conversation> existing = conversations.findBetweenTeams(request.teamId(), request.otherTeamId());
        if (existing.isPresent()) {
            Conversation conversation = existing.get();
            if (request.fixtureId() != null && !request.fixtureId().equals(conversation.getRelatedFixtureId())) {
                conversation = conversations.save(new Conversation(conversation.getId(), conversation.getTeamAId(),
                        conversation.getTeamBId(), conversation.getCreatedAt(), conversation.getLastMessageAt(),
                        conversation.getLastMessageBody(), conversation.getLastMessageSenderTeamId(), request.fixtureId()));
            }
            return view(conversation, otherTeam);
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
                new Conversation(null, request.teamId(), request.otherTeamId(), now, now, null, null, request.fixtureId()));
        return ConversationDtos.ConversationView.of(saved, otherTeam);
    }

    public List<ConversationDtos.ConversationView> list(String userId, String teamId) {
        membershipService.requireCanManageTeam(userId, teamId);
        return conversations.findByTeamId(teamId).stream()
                .map(c -> {
                    Team other = teams.findById(c.otherTeam(teamId)).orElse(null);
                    return other == null ? null : view(c, other);
                })
                .filter(v -> v != null)
                .toList();
    }

    public ConversationDtos.ConversationView get(String userId, String conversationId) {
        Conversation conversation = requireVisible(userId, conversationId);
        String otherTeamId = canManage(userId, conversation.getTeamAId())
                ? conversation.getTeamBId() : conversation.getTeamAId();
        Team other = teams.findById(otherTeamId).orElseThrow(BusinessRuleException::blocked);
        return view(conversation, other);
    }

    /**
     * A conversation's messages, oldest first. With {@code since}, only those
     * created at or after it: chat screens poll with their newest message's
     * time and merge by id, so a poll returns a message or two instead of the
     * whole history.
     */
    public List<ConversationDtos.MessageView> messages(String userId, String conversationId, Instant since) {
        Conversation conversation = requireVisible(userId, conversationId);
        List<Message> found = since == null
                ? messages.findByConversationIdOrderByCreatedAtAsc(conversation.getId())
                : messages.findByConversationIdSince(conversation.getId(), since);
        return found.stream()
                .map(m -> ConversationDtos.MessageView.from(withMaskedBody(m))).toList();
    }

    public ConversationDtos.MessageView send(String userId, String conversationId,
                                             ConversationDtos.SendMessageRequest request) {
        Conversation conversation = requireVisible(userId, conversationId);
        if (blocks.blockedTeamIdsEitherDirection(conversation.getTeamAId()).contains(conversation.getTeamBId())) {
            throw BusinessRuleException.blocked();
        }

        String senderTeamId = membershipService.roleFor(userId, conversation.getTeamAId()) != null
                && membershipService.roleFor(userId, conversation.getTeamAId()).canManageTeam()
                ? conversation.getTeamAId() : conversation.getTeamBId();
        String otherTeamId = conversation.otherTeam(senderTeamId);
        // Masked before it's stored, so the original wording never reaches
        // the other team, a notification or the admin transcript.
        String body = chatFilter.mask(request.body() == null ? "" : request.body().trim()).text();
        boolean hasGif = request.gifId() != null && !request.gifId().isBlank();
        if (body.isEmpty() && !hasGif) {
            throw new BusinessRuleException("EMPTY_MESSAGE", HttpStatus.BAD_REQUEST, "Type a message or pick a GIF.");
        }
        MessageGif gif = hasGif ? gifService.resolveForMessage(request.gifId().trim()) : null;

        Message saved = messages.save(new Message(null, conversation.getId(), senderTeamId, userId, body, Instant.now(), gif));

        String preview = body.isEmpty() ? GIF_PREVIEW : body;
        conversations.save(new Conversation(conversation.getId(), conversation.getTeamAId(), conversation.getTeamBId(),
                conversation.getCreatedAt(), saved.getCreatedAt(), preview, senderTeamId, conversation.getRelatedFixtureId()));

        notificationService.notifyTeam(otherTeamId, NotificationType.MESSAGE_RECEIVED,
                "New message", preview, null, null, conversation.getId());

        return ConversationDtos.MessageView.from(saved);
    }

    /*
     * Bodies are masked when sent, and again when read: that way a word an
     * admin adds to the list later is also hidden in older messages.
     */
    private Message withMaskedBody(Message m) {
        String body = chatFilter.mask(m.getBody()).text();
        return new Message(m.getId(), m.getConversationId(), m.getSenderTeamId(), m.getSenderUserId(), body, m.getCreatedAt(), m.getGif());
    }

    private ConversationDtos.ConversationView view(Conversation c, Team other) {
        ConversationDtos.ConversationView v = ConversationDtos.ConversationView.of(c, other);
        return new ConversationDtos.ConversationView(v.id(), v.otherTeam(), chatFilter.mask(v.lastMessageBody()).text(),
                v.lastMessageSenderTeamId(), v.lastMessageAt(), v.createdAt(), v.relatedFixtureId());
    }

    private Conversation requireVisible(String userId, String conversationId) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(BusinessRuleException::blocked);
        if (!canManage(userId, conversation.getTeamAId()) && !canManage(userId, conversation.getTeamBId())) {
            throw BusinessRuleException.blocked();
        }
        return conversation;
    }

    private boolean canManage(String userId, String teamId) {
        var role = membershipService.roleFor(userId, teamId);
        return role != null && role.canManageTeam();
    }
}
