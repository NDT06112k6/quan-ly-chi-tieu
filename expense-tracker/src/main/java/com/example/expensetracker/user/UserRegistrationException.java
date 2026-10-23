package com.example.expensetracker.user;

public class UserRegistrationException extends RuntimeException {

    private final String fieldName;

    private UserRegistrationException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }

    public static UserRegistrationException emailAlreadyExists() {
        UserRegistrationException exception = new UserRegistrationException(
                "email", "Email này đã được sử dụng.");
        return exception;
    }

    public static UserRegistrationException passwordsDoNotMatch() {
        UserRegistrationException exception = new UserRegistrationException(
                "confirmPassword", "Mật khẩu nhập lại không khớp.");
        return exception;
    }

    public static UserRegistrationException passwordExceedsBcryptLimit() {
        UserRegistrationException exception = new UserRegistrationException(
                "password", "Mật khẩu không được vượt quá 72 byte UTF-8.");
        return exception;
    }

    public String getFieldName() {
        return fieldName;
    }
}
