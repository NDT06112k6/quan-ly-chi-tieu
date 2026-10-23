package com.example.expensetracker.user;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UserController {

    static final String REGISTER_VIEW = "user/register";
    private static final String LOGIN_VIEW = "user/login";
    private static final String LOGIN_MODEL_ATTRIBUTE = "userLoginDTO";
    private static final String LOGIN_ERROR_CODE = "login.failed";
    private static final String LOGIN_ERROR_MESSAGE =
            "Email hoặc mật khẩu không đúng, hoặc tài khoản đã bị khóa.";
    private static final String REGISTER_URL = "/register";
    private static final String LOGIN_URL = "/login";
    private static final String REGISTER_SUCCESS_REDIRECT = "redirect:/login?success";

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping(REGISTER_URL)
    public String showRegisterForm(Model model) {
        UserRegisterDTO dto = new UserRegisterDTO();
        model.addAttribute(UserRegisterDTO.MODEL_ATTRIBUTE, dto);
        return REGISTER_VIEW;
    }

    @PostMapping(REGISTER_URL)
    public String registerUser(
            @Valid @ModelAttribute(UserRegisterDTO.MODEL_ATTRIBUTE) UserRegisterDTO dto,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            dto.clearPasswords();
            return REGISTER_VIEW;
        }

        userService.registerUser(dto);
        dto.clearPasswords();
        return REGISTER_SUCCESS_REDIRECT;
    }

    @GetMapping(LOGIN_URL)
    public String showLoginForm(@RequestParam(name = "error", required = false) String error, Model model) {
        UserLoginDTO dto = new UserLoginDTO("");
        BindingResult bindingResult = new BeanPropertyBindingResult(dto, LOGIN_MODEL_ATTRIBUTE);
        if (error != null) {
            bindingResult.reject(LOGIN_ERROR_CODE, LOGIN_ERROR_MESSAGE);
        }

        model.addAllAttributes(bindingResult.getModel());
        return LOGIN_VIEW;
    }
}
