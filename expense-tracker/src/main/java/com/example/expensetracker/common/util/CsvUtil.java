package com.example.expensetracker.common.util;

import java.util.List;

public final class CsvUtil {

    public static final String LINE_ENDING = "\r\n";
    private static final char DELIMITER = ',';
    private static final String QUOTE = "\"";
    private static final String ESCAPED_QUOTE = "\"\"";
    private static final String FORMULA_PREFIXES = "=+-@＝＋－＠";
    private static final String TEXT_PREFIX = "'";

    private CsvUtil() {
    }

    public static void appendRow(StringBuilder csv, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                csv.append(DELIMITER);
            }
            String value = escapeField(values.get(index));
            csv.append(value);
        }
        csv.append(LINE_ENDING);
    }

    private static String escapeField(String value) {
        if (value == null) {
            return "";
        }
        String safeValue = value;
        boolean formula = isSpreadsheetFormula(value);
        if (formula) {
            safeValue = TEXT_PREFIX + value;
        }
        if (formula || safeValue.indexOf(DELIMITER) >= 0 || safeValue.contains(QUOTE)
                || safeValue.contains("\r") || safeValue.contains("\n")) {
            String escapedValue = safeValue.replace(QUOTE, ESCAPED_QUOTE);
            String quotedValue = QUOTE + escapedValue + QUOTE;
            return quotedValue;
        }
        return safeValue;
    }

    private static boolean isSpreadsheetFormula(String value) {
        if (value.startsWith("\t") || value.startsWith("\r") || value.startsWith("\n")) {
            return true;
        }
        String trimmedValue = value.stripLeading();
        if (trimmedValue.isEmpty()) {
            return false;
        }
        int firstCharacter = trimmedValue.codePointAt(0);
        boolean formula = FORMULA_PREFIXES.indexOf(firstCharacter) >= 0;
        return formula;
    }
}
