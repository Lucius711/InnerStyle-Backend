package com.innerstyle.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innerstyle.common.exception.ConflictException;
import com.innerstyle.common.exception.GlobalExceptionHandler;
import com.innerstyle.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc (standalone) integration tests for {@link AuthController} request validation and the
 * error-envelope contract produced by {@link GlobalExceptionHandler}. No security / DB context.
 */
class AuthControllerValidationTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private AuthService authService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    private String json(Map<String, ?> body) throws Exception {
        return mapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("register: blank email → 400 validation.email.required")
    void register_blankEmail() throws Exception {
        mvc.perform(post("/user/auth/register").contentType("application/json")
                .content(json(Map.of("email", "", "password", "S3curePass!", "fullName", "Huy"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.email").value("validation.email.required"));
    }

    @Test
    @DisplayName("register: invalid email format → 400 validation.email.invalid")
    void register_invalidEmail() throws Exception {
        mvc.perform(post("/user/auth/register").contentType("application/json")
                .content(json(Map.of("email", "not-an-email", "password", "S3curePass!",
                    "fullName", "Huy"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.email").value("validation.email.invalid"));
    }

    @Test
    @DisplayName("register: short password → 400 validation.password.length")
    void register_shortPassword() throws Exception {
        mvc.perform(post("/user/auth/register").contentType("application/json")
                .content(json(Map.of("email", "a@b.com", "password", "short", "fullName", "Huy"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.password").value("validation.password.length"));
    }

    @Test
    @DisplayName("register: duplicate email (service) → 409 auth.emailExists")
    void register_duplicate() throws Exception {
        doThrow(new ConflictException("auth.emailExists")).when(authService).register(any());
        mvc.perform(post("/user/auth/register").contentType("application/json")
                .content(json(Map.of("email", "a@b.com", "password", "S3curePass!", "fullName", "Huy"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error._").value("auth.emailExists"));
    }

    @Test
    @DisplayName("verify-email: blank otp → 400 validation.otp.required")
    void verifyEmail_blankOtp() throws Exception {
        mvc.perform(post("/user/auth/verify-email").contentType("application/json")
                .content(json(Map.of("email", "a@b.com", "otp", ""))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.otp").value("validation.otp.required"));
    }

    @Test
    @DisplayName("verify-email: non-numeric otp → 400 validation.otp.invalid")
    void verifyEmail_nonNumericOtp() throws Exception {
        mvc.perform(post("/user/auth/verify-email").contentType("application/json")
                .content(json(Map.of("email", "a@b.com", "otp", "abc123"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.otp").value("validation.otp.invalid"));
    }

    @Test
    @DisplayName("login: blank email + password → 400 with both field codes")
    void login_blank() throws Exception {
        mvc.perform(post("/user/auth/login").contentType("application/json")
                .content(json(Map.of("email", "", "password", ""))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.email").value("validation.email.required"))
            .andExpect(jsonPath("$.error.password").value("validation.password.required"));
    }

    @Test
    @DisplayName("malformed JSON body → 400 validation.body.malformed")
    void malformedBody() throws Exception {
        mvc.perform(post("/user/auth/login").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error._").value("validation.body.malformed"));
    }
}
