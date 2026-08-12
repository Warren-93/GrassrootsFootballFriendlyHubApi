package com.gffh.api.service;

import com.gffh.api.repository.BlockRepository;
import com.gffh.api.web.BlockDtos;
import org.springframework.stereotype.Service;

/**
 * SCR-PR-11's block action. {@link BlockRepository} already existed and is
 * already read by {@code MatchingService} - this is the write side it was
 * missing a controller for.
 */
@Service
public class BlockService {

    private final BlockRepository blocks;
    private final MembershipService membershipService;

    public BlockService(BlockRepository blocks, MembershipService membershipService) {
        this.blocks = blocks;
        this.membershipService = membershipService;
    }

    public void block(String userId, String teamId, BlockDtos.BlockRequest request) {
        membershipService.requireCanManageTeam(userId, teamId);
        blocks.block(teamId, request.blockedTeamId(), request.reason());
    }
}
