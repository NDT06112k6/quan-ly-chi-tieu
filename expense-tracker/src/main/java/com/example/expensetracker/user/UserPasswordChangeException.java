package com.example.expensetracker.user;

public class UserPasswordChangeException extends RuntimeException {

    private static final String OLD_PASSWORD_FIELD = "oldPassword";
    private static final String NEW_PASSWORD_FIELD = "newPassword";
    private final String fieldName;

    private UserPasswordChangeException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }

    public static UserPasswordChangeException incorrectOldPassword() {
        UserPasswordChangeException exception = new UserPasswordChangeException(
                OLD_PASSWORD_FIELD, "Mật khẩu hiện tại không đúng.");
        return exception;
    }

    public static UserPasswordChangeException passwordExceedsBcryptLimit() {
        UserPasswordChangeException exception = new UserPasswordChangeException(
                NEW_PASSWORD_FIELD, "Mật khẩu mới không được vượt quá 72 byte UTF-8.");
        return exception;
    }

    public static UserPasswordChangeException passwordChangedConcurrently() {
        UserPasswordChangeException exception = new UserPasswordChangeException(
                OLD_PASSWORD_FIELD, "Mật khẩu đã thay đổi hoặc tài khoản đã bị khóa. Vui lòng đăng nhập lại.");
        return exception;
    }

    public String getFieldName() {
        return fieldName;
    }
}
