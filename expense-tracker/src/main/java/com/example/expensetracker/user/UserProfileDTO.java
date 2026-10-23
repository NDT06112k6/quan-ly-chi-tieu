package com.example.expensetracker.user;

public record UserProfileDTO(String email, String displayName) {

    public static final String MODEL_ATTRIBUTE = "profile";
}
