package com.gffh.api.model.enums;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgeGroupTest {

    @Test
    void zeroToleranceIsTheExactGroupOnly() {
        assertEquals(Set.of(AgeGroup.U12), AgeGroup.U12.withinBands(0));
        assertEquals(Set.of(AgeGroup.ADULT), AgeGroup.ADULT.withinBands(0));
    }

    @Test
    void toleranceWidensYouthGroupsBothWays() {
        assertEquals(Set.of(AgeGroup.U11, AgeGroup.U12, AgeGroup.U13), AgeGroup.U12.withinBands(1));
        assertEquals(Set.of(AgeGroup.U7, AgeGroup.U8, AgeGroup.U9), AgeGroup.U7.withinBands(2));
    }

    @Test
    void youthAndAdultGroupsNeverBridge() {
        assertEquals(Set.of(AgeGroup.U16, AgeGroup.U17, AgeGroup.U18), AgeGroup.U18.withinBands(2));
        assertEquals(Set.of(AgeGroup.ADULT), AgeGroup.ADULT.withinBands(5));
        assertEquals(Set.of(AgeGroup.VETERANS), AgeGroup.VETERANS.withinBands(5));
    }
}
