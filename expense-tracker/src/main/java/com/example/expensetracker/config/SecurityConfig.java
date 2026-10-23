package com.example.expensetracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String LOGIN_URL = "/login";
    private static final String REGISTER_URL = "/register";
    private static final String CSS_PATTERN = "/css/**";
    private static final String JS_PATTERN = "/js/**";
    private static final String LOGOUT_SUCCESS_URL = "/login?logout";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(LOGIN_URL, REGISTER_URL, CSS_PATTERN, JS_PATTERN)
                .permitAll()
                .anyRequest()
                .authenticated());
        http.formLogin(form -> form
                .loginPage(LOGIN_URL));
        http.logout(logout -> logout
                .logoutSuccessUrl(LOGOUT_SUCCESS_URL));

        SecurityFilterChain filterChain = http.build();
        return filterChain;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return passwordEncoder;
    }
}
