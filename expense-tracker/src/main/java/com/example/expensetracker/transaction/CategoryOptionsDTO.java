package com.example.expensetracker.transaction;

import com.example.expensetracker.category.CategoryDTO;

import java.util.List;

public record CategoryOptionsDTO(List<CategoryDTO> content, int page, boolean hasNext) {
}
