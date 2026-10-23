package com.example.expensetracker.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PasswordChangeDTO {

    public static final String MODEL_ATTRIBUTE = "passwordChangeDTO";

    @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại.")
    @Size(max = UserRegisterDTO.MAX_PASSWORD_LENGTH, message = "Mật khẩu hiện tại tối đa 72 ký tự.")
    private String oldPassword;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới.")
    @Size(min = UserRegisterDTO.MIN_PASSWORD_LENGTH, max = UserRegisterDTO.MAX_PASSWORD_LENGTH,
            message = "Mật khẩu mới phải có từ 8 đến 72 ký tự.")
    private String newPassword;

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public void clearPasswords() {
        oldPassword = null;
        newPassword = null;
    }
}
