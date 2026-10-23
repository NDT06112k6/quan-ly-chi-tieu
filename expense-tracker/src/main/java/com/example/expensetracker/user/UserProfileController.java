package com.example.expensetracker.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping(UserProfileController.PROFILE_PATH)
public class UserProfileController {

    public static final String PROFILE_PATH = "/profile";
    public static final String CHANGE_PASSWORD_PATH = "/profile/change-password";
    static final String PROFILE_VIEW = "profile/view";
    private static final String SUCCESS_REDIRECT = "redirect:/profile?success";
    private final UserService userService;

    public UserProfileController(UserService userService) {
        this.userService = userService;
    }

    @InitBinder(PasswordChangeDTO.MODEL_ATTRIBUTE)
    public void configurePasswordBinding(WebDataBinder binder) {
        binder.setAllowedFields("oldPassword", "newPassword");
    }

    @ModelAttribute(UserProfileDTO.MODEL_ATTRIBUTE)
    public UserProfileDTO loadProfile(Authentication authentication, HttpServletRequest request) {
        UserProfileDTO profile = userService.getProfile(authentication.getName());
        request.setAttribute(UserProfileDTO.MODEL_ATTRIBUTE, profile);
        return profile;
    }

    @GetMapping
    public String showProfile(Model model) {
        PasswordChangeDTO dto = new PasswordChangeDTO();
        model.addAttribute(PasswordChangeDTO.MODEL_ATTRIBUTE, dto);
        return PROFILE_VIEW;
    }

    @PostMapping("/change-password")
    public ModelAndView changePassword(@Valid @ModelAttribute(PasswordChangeDTO.MODEL_ATTRIBUTE) PasswordChangeDTO dto,
            BindingResult bindingResult,
            @ModelAttribute(name = UserProfileDTO.MODEL_ATTRIBUTE, binding = false) UserProfileDTO profile) {
        if (bindingResult.hasErrors()) {
            dto.clearPasswords();
            ModelAndView modelAndView = new ModelAndView(PROFILE_VIEW, bindingResult.getModel());
            modelAndView.addObject(UserProfileDTO.MODEL_ATTRIBUTE, profile);
            modelAndView.setStatus(HttpStatus.BAD_REQUEST);
            return modelAndView;
        }
        userService.changePassword(profile.email(), dto.getOldPassword(), dto.getNewPassword());
        dto.clearPasswords();
        ModelAndView modelAndView = new ModelAndView(SUCCESS_REDIRECT);
        return modelAndView;
    }
}
