package com.example.expensetracker.common.util;

import java.time.LocalDate;
import java.time.YearMonth;

public final class MonthUtil {

    private static final int FIRST_DAY_OF_MONTH = 1;

    private MonthUtil() {
    }

    public static LocalDate firstDayOfMonth(String month) {
        YearMonth yearMonth = YearMonth.parse(month);
        LocalDate firstDay = yearMonth.atDay(FIRST_DAY_OF_MONTH);
        return firstDay;
    }

    public static String formatMonth(LocalDate date) {
        YearMonth yearMonth = YearMonth.from(date);
        String month = yearMonth.toString();
        return month;
    }
}
