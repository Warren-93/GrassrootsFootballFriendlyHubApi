package com.gffh.api.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrganisationTest {

    @Test
    @DisplayName("youth teams get youth, schools and girls' bodies; adults get amateur, welfare, junior and women's")
    void availableForAgeGroup() {
        assertThat(Organisation.availableFor(AgeGroup.U12, Gender.GIRLS))
                .containsExactly(Organisation.SYFA, Organisation.SWF, Organisation.SSFA, Organisation.OTHER);
        assertThat(Organisation.availableFor(AgeGroup.ADULT, Gender.WOMEN))
                .containsExactly(Organisation.SAFA, Organisation.SWF, Organisation.SWFA, Organisation.SJFA, Organisation.OTHER);
        assertThat(Organisation.SSFA.isAvailableFor(AgeGroup.ADULT, Gender.MIXED)).isFalse();
        assertThat(Organisation.SAFA.isAvailableFor(AgeGroup.U16, Gender.BOYS)).isFalse();
        assertThat(Organisation.SWFA.isAvailableFor(AgeGroup.VETERANS, Gender.MEN)).isTrue();
    }

    @Test
    @DisplayName("Scottish Women's Football is hidden from boys' and men's teams but kept for girls, women and mixed")
    void womensFootballFollowsGender() {
        assertThat(Organisation.availableFor(AgeGroup.U12, Gender.BOYS))
                .containsExactly(Organisation.SYFA, Organisation.SSFA, Organisation.OTHER);
        assertThat(Organisation.availableFor(AgeGroup.ADULT, Gender.MEN))
                .containsExactly(Organisation.SAFA, Organisation.SWFA, Organisation.SJFA, Organisation.OTHER);
        assertThat(Organisation.SWF.isAvailableFor(AgeGroup.U14, Gender.MIXED)).isTrue();
        assertThat(Organisation.SWF.isAvailableFor(AgeGroup.ADULT, Gender.WOMEN)).isTrue();
        assertThat(Organisation.SWF.isAvailableFor(AgeGroup.ADULT, null)).isTrue();
    }
}
