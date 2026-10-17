package com.gffh.api.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbilityLevelTest {

    @Test
    @DisplayName("the pre-division levels map onto divisions, current names parse as themselves")
    void legacyValuesMap() {
        assertThat(AbilityLevel.fromValue("DEVELOPMENT")).isEqualTo(AbilityLevel.DIVISION_3_PLUS);
        assertThat(AbilityLevel.fromValue("INTERMEDIATE")).isEqualTo(AbilityLevel.DIVISION_2);
        assertThat(AbilityLevel.fromValue("COMPETITIVE")).isEqualTo(AbilityLevel.DIVISION_1);
        assertThat(AbilityLevel.fromValue("PREMIER")).isEqualTo(AbilityLevel.PREMIER);
        assertThat(AbilityLevel.fromValue(null)).isNull();
        assertThatThrownBy(() -> AbilityLevel.fromValue("NOPE")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("closeness follows the division ladder, not declaration order")
    void bandsApart() {
        assertThat(AbilityLevel.DIVISION_1.bandsApart(AbilityLevel.DIVISION_1)).isZero();
        assertThat(AbilityLevel.PREMIER.bandsApart(AbilityLevel.DIVISION_1)).isEqualTo(1);
        assertThat(AbilityLevel.DIVISION_2.bandsApart(AbilityLevel.DIVISION_3_PLUS)).isEqualTo(1);
        assertThat(AbilityLevel.PREMIER.bandsApart(AbilityLevel.FESTIVAL)).isEqualTo(5);
    }
}
