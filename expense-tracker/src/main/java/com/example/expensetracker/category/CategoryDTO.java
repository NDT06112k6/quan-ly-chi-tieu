package com.example.expensetracker.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CategoryDTO {

    public static final String MODEL_ATTRIBUTE = "categoryDTO";
    private static final String ALLOWED_TYPES_PATTERN = "CHI|THU";

    private Long id;

    @NotBlank(message = "Vui lòng nhập tên danh mục.")
    @Size(max = 100, message = "Tên danh mục không được vượt quá 100 ký tự.")
    private String name;

    @NotBlank(message = "Vui lòng chọn loại danh mục.")
    @Pattern(regexp = ALLOWED_TYPES_PATTERN, message = "Loại danh mục phải là CHI hoặc THU.")
    private String type;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
