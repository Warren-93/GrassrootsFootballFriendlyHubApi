package com.gffh.api.service;

import com.gffh.api.domain.Block;
import com.gffh.api.repository.BlockRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.web.BlockDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SCR-PR-11's block action. {@link BlockRepository} already existed and is
 * already read by {@code MatchingService} - this is the write side it was
 * missing a controller for, plus the list/unblock side needed so a manager
 * can see and undo their own team's blocks rather than them being a one-way
 * door.
 */
@Service
public class BlockService {

    private final BlockRepository blocks;
    private final TeamRepository teams;
    private final MembershipService membershipService;
    private final FriendlyRequestService friendlyRequestService;

    public BlockService(BlockRepository blocks, TeamRepository teams, MembershipService membershipService,
                        FriendlyRequestService friendlyRequestService) {
        this.blocks = blocks;
        this.teams = teams;
        this.membershipService = membershipService;
        this.friendlyRequestService = friendlyRequestService;
    }

    public void block(String userId, String teamId, BlockDtos.BlockRequest request) {
        membershipService.requireCanManageTeam(userId, teamId);
        blocks.block(teamId, request.blockedTeamId(), request.reason());
        friendlyRequestService.cancelOpenBetween(teamId, request.blockedTeamId());
    }

    public List<BlockDtos.BlockView> list(String userId, String teamId) {
        membershipService.requireCanManageTeam(userId, teamId);
        return blocks.findByBlockingTeamId(teamId).stream()
                .map(b -> BlockDtos.BlockView.from(b, blockedTeamName(b)))
                .toList();
    }

    public void unblock(String userId, String teamId, String blockId) {
        membershipService.requireCanManageTeam(userId, teamId);
        Block block = blocks.findById(blockId).orElseThrow(() -> new BusinessRuleException(
                "BLOCK_NOT_FOUND", HttpStatus.NOT_FOUND, "That block could not be found."));
        if (!block.blockingTeamId().equals(teamId)) {
            throw new BusinessRuleException("BLOCK_NOT_FOUND", HttpStatus.NOT_FOUND, "That block could not be found.");
        }
        blocks.deleteById(blockId);
    }

    private String blockedTeamName(Block block) {
        return teams.findById(block.blockedTeamId()).map(team -> team.name()).orElse("Unknown team");
    }
}
