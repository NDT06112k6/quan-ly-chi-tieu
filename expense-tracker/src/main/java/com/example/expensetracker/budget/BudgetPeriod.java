package com.example.expensetracker.budget;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Objects;

public record BudgetPeriod(BudgetType type, String period) {

    private static final String MONTH_PATTERN = "[1-9]\\d{3}-(0[1-9]|1[0-2])";
    private static final String WEEK_PATTERN = "[1-9]\\d{3}-W(0[1-9]|[1-4]\\d|5[0-3])";
    private static final int FIRST_DAY = 1;
    private static final int ISO_REFERENCE_DAY = 4;
    private static final int WEEK_OFFSET = 6;
    private static final int YEAR_LENGTH = 4;
    private static final int WEEK_OFFSET_IN_PERIOD = 6;

    public BudgetPeriod {
        if (!isValid(type, period)) {
            throw new IllegalArgumentException("Invalid budget period for the selected type");
        }
    }

    public static boolean isValid(BudgetType type, String period) {
        if (type == null || period == null) {
            return false;
        }
        if (type == BudgetType.MONTHLY) {
            boolean valid = period.matches(MONTH_PATTERN);
            return valid;
        }
        if (!period.matches(WEEK_PATTERN)) {
            return false;
        }
        LocalDate monday = isoWeekStart(period);
        int requestedYear = Integer.parseInt(period.substring(0, YEAR_LENGTH));
        int requestedWeek = Integer.parseInt(period.substring(WEEK_OFFSET_IN_PERIOD));
        boolean valid = monday.get(WeekFields.ISO.weekBasedYear()) == requestedYear
                && monday.get(WeekFields.ISO.weekOfWeekBasedYear()) == requestedWeek;
        return valid;
    }

    public static BudgetPeriod containing(BudgetType type, LocalDate date) {
        Objects.requireNonNull(type, "Budget type must not be null");
        Objects.requireNonNull(date, "Budget date must not be null");
        String period;
        if (type == BudgetType.MONTHLY) {
            period = YearMonth.from(date).toString();
        } else {
            int year = date.get(WeekFields.ISO.weekBasedYear());
            int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
            period = String.format(Locale.ROOT, "%04d-W%02d", year, week);
        }
        BudgetPeriod budgetPeriod = new BudgetPeriod(type, period);
        return budgetPeriod;
    }

    public LocalDate startDate() {
        if (type == BudgetType.MONTHLY) {
            LocalDate start = YearMonth.parse(period).atDay(FIRST_DAY);
            return start;
        }
        LocalDate start = isoWeekStart(period);
        return start;
    }

    public LocalDate endDate() {
        if (type == BudgetType.MONTHLY) {
            LocalDate end = YearMonth.parse(period).atEndOfMonth();
            return end;
        }
        LocalDate end = startDate().plusDays(WEEK_OFFSET);
        return end;
    }

    private static LocalDate isoWeekStart(String period) {
        int year = Integer.parseInt(period.substring(0, YEAR_LENGTH));
        int week = Integer.parseInt(period.substring(WEEK_OFFSET_IN_PERIOD));
        LocalDate monday = LocalDate.of(year, FIRST_DAY, ISO_REFERENCE_DAY)
                .with(WeekFields.ISO.weekOfWeekBasedYear(), week)
                .with(WeekFields.ISO.dayOfWeek(), FIRST_DAY);
        return monday;
    }
}
