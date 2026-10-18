package com.gffh.api.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrganisationTest {

    @Test
    @DisplayName("youth teams get youth, schools and girls' bodies; adults get amateur, welfare, junior and women's")
    void availableForAgeGroup() {
        assertThat(Organisation.availableFor(AgeGroup.U12))
                .containsExactly(Organisation.SYFA, Organisation.SWF, Organisation.SSFA, Organisation.OTHER);
        assertThat(Organisation.availableFor(AgeGroup.ADULT))
                .containsExactly(Organisation.SAFA, Organisation.SWF, Organisation.SWFA, Organisation.SJFA, Organisation.OTHER);
        assertThat(Organisation.SSFA.isAvailableFor(AgeGroup.ADULT)).isFalse();
        assertThat(Organisation.SAFA.isAvailableFor(AgeGroup.U16)).isFalse();
        assertThat(Organisation.SWFA.isAvailableFor(AgeGroup.VETERANS)).isTrue();
    }
}
