package com.example.expensetracker.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvUtilTests {

    @Test
    void escapesCommasQuotesAndLineBreaksWithoutChangingOrdinaryText() {
        StringBuilder csv = new StringBuilder();
        CsvUtil.appendRow(csv, List.of("Ngày", "Ăn, uống", "0.10", "Một \"ghi chú\"\r\nDòng hai"));
        assertThat(csv.toString()).isEqualTo("Ngày,\"Ăn, uống\",0.10,\"Một \"\"ghi chú\"\"\r\nDòng hai\"\r\n");
    }

    @Test
    void preservesWhitespaceAndWritesNullAsEmptyField() {
        StringBuilder csv = new StringBuilder();
        CsvUtil.appendRow(csv, Arrays.asList("  ordinary text  ", "9999999999999.99", null));
        assertThat(csv.toString()).isEqualTo("  ordinary text  ,9999999999999.99,\r\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+1+2", "-1+2", "@SUM(1,2)", "  =1+1", "\t=1+1", "\r=1+1", "\n=1+1",
            "＝1+1", "＋1+2", "－1+2", "＠SUM(1,2)"})
    void prefixesFormulaLikeCellsAsQuotedText(String value) {
        StringBuilder csv = new StringBuilder();
        CsvUtil.appendRow(csv, List.of(value));
        assertThat(csv.toString()).startsWith("\"'");
        assertThat(csv.toString()).contains(value);
        assertThat(csv.toString()).endsWith("\"\r\n");
    }
}
