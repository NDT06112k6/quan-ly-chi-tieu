package com.example.expensetracker.category;

import java.util.List;

public record CategoryOptionsDTO(List<CategoryDTO> content, int page, boolean hasNext) {
}
