package com.example.expensetracker.budget;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetPeriodLabelTests {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "MONTHLY | 2026-10 | Tháng 10, 2026",
            "MONTHLY | 2027-01 | Tháng 1, 2027",
            "MONTHLY | 1000-01 | Tháng 1, 1000",
            "WEEKLY | 2026-W43 | Tuần 43, 2026",
            "WEEKLY | 2021-W01 | Tuần 1, 2021",
            "WEEKLY | 2020-W53 | Tuần 53, 2020",
            "WEEKLY | 9999-W52 | Tuần 52, 9999"
    })
    void formatsLabelsWithoutChangingCanonicalPeriod(BudgetType type, String period, String expected) {
        BudgetDTO dto = new BudgetDTO();
        dto.setType(type);
        dto.setPeriod(period);

        String label = dto.getPeriodLabel();

        assertThat(label).isEqualTo(expected);
        assertThat(dto.getPeriod()).isEqualTo(period);
        assertThat(dto.getType()).isEqualTo(type);
    }
}
