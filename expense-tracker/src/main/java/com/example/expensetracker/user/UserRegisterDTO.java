package com.example.expensetracker.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserRegisterDTO {

    public static final String MODEL_ATTRIBUTE = "userRegisterDTO";
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 72;

    @NotBlank(message = "Vui lòng nhập email.")
    @Email(message = "Email không hợp lệ.")
    @Size(max = 255, message = "Email không được vượt quá 255 ký tự.")
    private String email;

    @NotBlank(message = "Vui lòng nhập mật khẩu.")
    @Size(min = MIN_PASSWORD_LENGTH, max = MAX_PASSWORD_LENGTH,
            message = "Mật khẩu phải có từ 8 đến 72 ký tự.")
    private String password;

    @NotBlank(message = "Vui lòng nhập lại mật khẩu.")
    @Size(min = MIN_PASSWORD_LENGTH, max = MAX_PASSWORD_LENGTH,
            message = "Mật khẩu nhập lại phải có từ 8 đến 72 ký tự.")
    private String confirmPassword;

    @NotBlank(message = "Vui lòng nhập tên hiển thị.")
    @Size(max = 100, message = "Tên hiển thị không được vượt quá 100 ký tự.")
    private String displayName;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void clearPasswords() {
        password = null;
        confirmPassword = null;
    }
}
