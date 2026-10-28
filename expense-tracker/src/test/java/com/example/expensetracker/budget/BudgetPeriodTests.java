package com.example.expensetracker.budget;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetPeriodTests {

    @ParameterizedTest
    @CsvSource({
            "MONTHLY, 2024-02-29, 2024-02, 2024-02-01, 2024-02-29",
            "MONTHLY, 9999-12-31, 9999-12, 9999-12-01, 9999-12-31",
            "WEEKLY, 2026-10-22, 2026-W43, 2026-10-19, 2026-10-25",
            "WEEKLY, 2026-10-25, 2026-W43, 2026-10-19, 2026-10-25",
            "WEEKLY, 2026-10-26, 2026-W44, 2026-10-26, 2026-11-01",
            "WEEKLY, 2021-01-01, 2020-W53, 2020-12-28, 2021-01-03",
            "WEEKLY, 2021-01-04, 2021-W01, 2021-01-04, 2021-01-10",
            "WEEKLY, 2018-12-31, 2019-W01, 2018-12-31, 2019-01-06",
            "WEEKLY, 2024-02-29, 2024-W09, 2024-02-26, 2024-03-03"
    })
    void derivesCanonicalPeriodAndInclusiveBounds(BudgetType type, String date, String expected,
            String expectedStart, String expectedEnd) {
        BudgetPeriod period = BudgetPeriod.containing(type, LocalDate.parse(date));

        assertThat(period.period()).isEqualTo(expected);
        assertThat(period.type()).isEqualTo(type);
        assertThat(period.startDate()).isEqualTo(LocalDate.parse(expectedStart));
        assertThat(period.endDate()).isEqualTo(LocalDate.parse(expectedEnd));
        assertThat(new BudgetPeriod(type, expected)).isEqualTo(period);
    }

    @ParameterizedTest
    @CsvSource({
            "MONTHLY, 2026-13", "MONTHLY, 2026-1", "MONTHLY, 2026-W43",
            "WEEKLY, 2026-10", "WEEKLY, 2021-W53", "WEEKLY, 2026-W00",
            "WEEKLY, 2026-W54", "WEEKLY, 2026-W1", "WEEKLY, 2026-w43",
            "MONTHLY, 0999-12", "WEEKLY, 0999-W52"
    })
    void rejectsMalformedOrNonexistentPeriods(BudgetType type, String period) {
        assertThat(BudgetPeriod.isValid(type, period)).isFalse();
        assertThatThrownBy(() -> new BudgetPeriod(type, period)).isInstanceOf(IllegalArgumentException.class);
    }
}
