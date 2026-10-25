package com.arijit.authservice.controller;

import com.arijit.authservice.dto.*;
import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.Role;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.exception.ApiException;
import com.arijit.authservice.repository.UserRepository;
import com.arijit.authservice.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock AuthService authService;
    @Mock UserRepository userRepository;
    @InjectMocks AuthController controller;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ControllerTestSupport.mvc(controller);
        ControllerTestSupport.authenticateAs("ash@pallet.town");
    }

    @AfterEach
    void tearDown() {
        ControllerTestSupport.clear();
    }

    private static final String REGISTER_JSON =
            "{\"name\":\"Ash\",\"email\":\"ash@pallet.town\",\"phoneNumber\":\"+919876543210\",\"password\":\"pikachu123\"}";

    @Test
    void registerReturns201() throws Exception {
        when(authService.register(any())).thenReturn(new MessageResponse("Account created"));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Account created"));
    }

    @Test
    void registerValidationFailureListsFields() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"phoneNumber\":\"123\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.phoneNumber").exists())
                .andExpect(jsonPath("$.fields.password").exists())
                .andExpect(jsonPath("$.fields.name").exists());

        verifyNoInteractions(authService);
    }

    @Test
    void registerConflictIsMappedByAdvice() throws Exception {
        when(authService.register(any())).thenThrow(new ApiException("exists", HttpStatus.CONFLICT));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("exists"));
    }

    @Test
    void verifyEmailAndPhoneDelegate() throws Exception {
        when(authService.verifyEmail(any())).thenReturn(new MessageResponse("e"));
        when(authService.verifyPhone(any())).thenReturn(new MessageResponse("p"));
        String body = "{\"identifier\":\"a@b.com\",\"otp\":\"123456\"}";

        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("e"));
        mvc.perform(post("/api/auth/verify-phone").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("p"));
    }

    @Test
    void loginReturnsMessage() throws Exception {
        when(authService.login(any())).thenReturn(new MessageResponse("Password correct"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password correct"));
    }

    @Test
    void loginRejectsMalformedEmail() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nope\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyLoginOtpReturnsTokens() throws Exception {
        when(authService.verifyLoginOtp(any())).thenReturn(AuthResponse.of("acc", "ref", 900));

        mvc.perform(post("/api/auth/verify-login-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"a@b.com\",\"otp\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("acc"))
                .andExpect(jsonPath("$.refreshToken").value("ref"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900));
    }

    @Test
    void passwordResetFlow() throws Exception {
        when(authService.forgotPassword(any())).thenReturn(new MessageResponse("sent"));
        when(authService.verifyForgotPasswordOtp(any())).thenReturn(new ResetTokenResponse("tok", "ok"));
        when(authService.resetPassword(any())).thenReturn(new MessageResponse("done"));

        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b.com\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/verify-forgot-password-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"a@b.com\",\"otp\":\"1\"}"))
                .andExpect(jsonPath("$.resetToken").value("tok"));
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"tok\",\"newPassword\":\"longenough1\"}"))
                .andExpect(jsonPath("$.message").value("done"));
    }

    @Test
    void resetPasswordRejectsShortPassword() throws Exception {
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"tok\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newPassword").exists());
    }

    @Test
    void changePasswordUsesAuthenticatedPrincipal() throws Exception {
        when(authService.changePassword(eq("ash@pallet.town"), any())).thenReturn(new MessageResponse("Password changed."));

        mvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"brandnew1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed."));

        verify(authService).changePassword(eq("ash@pallet.town"), any(ChangePasswordRequest.class));
    }

    @Test
    void contactChangeEndpointsUsePrincipal() throws Exception {
        when(authService.requestEmailChange(eq("ash@pallet.town"), any())).thenReturn(new MessageResponse("a"));
        when(authService.verifyEmailChange(eq("ash@pallet.town"), any())).thenReturn(new MessageResponse("b"));
        when(authService.requestPhoneChange(eq("ash@pallet.town"), any())).thenReturn(new MessageResponse("c"));
        when(authService.verifyPhoneChange(eq("ash@pallet.town"), any())).thenReturn(new MessageResponse("d"));

        mvc.perform(post("/api/auth/change-email/request").contentType(MediaType.APPLICATION_JSON).content("{\"newEmail\":\"n@x.y\"}"))
                .andExpect(jsonPath("$.message").value("a"));
        mvc.perform(post("/api/auth/change-email/verify").contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"1\"}"))
                .andExpect(jsonPath("$.message").value("b"));
        mvc.perform(post("/api/auth/change-phone/request").contentType(MediaType.APPLICATION_JSON).content("{\"newPhoneNumber\":\"+911234567890\"}"))
                .andExpect(jsonPath("$.message").value("c"));
        mvc.perform(post("/api/auth/change-phone/verify").contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"1\"}"))
                .andExpect(jsonPath("$.message").value("d"));
    }

    @Test
    void refreshAndOAuthExchange() throws Exception {
        when(authService.refresh(any())).thenReturn(AuthResponse.of("a2", "r2", 900));
        when(authService.exchangeOAuth2Code("CODE")).thenReturn(AuthResponse.of("a3", "r3", 900));

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"r\"}"))
                .andExpect(jsonPath("$.accessToken").value("a2"));
        mvc.perform(post("/api/auth/oauth2/exchange").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"CODE\"}"))
                .andExpect(jsonPath("$.accessToken").value("a3"));
    }

    @Test
    void refreshWithBlankTokenIs400() throws Exception {
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutReturns204() throws Exception {
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"r\"}"))
                .andExpect(status().isNoContent());

        verify(authService).logout(any(RefreshTokenRequest.class));
    }

    @Test
    void sessionsListAndRevoke() throws Exception {
        UUID sid = UUID.randomUUID();
        when(authService.listMySessions("ash@pallet.town", 1, 5))
                .thenReturn(new SessionPageResponse(List.of(), 1, 5, 0, 0));

        mvc.perform(get("/api/auth/sessions").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5));

        mvc.perform(delete("/api/auth/sessions/" + sid)).andExpect(status().isNoContent());
        verify(authService).revokeMySession("ash@pallet.town", sid);
    }

    @Test
    void sessionsDefaultPaging() throws Exception {
        when(authService.listMySessions("ash@pallet.town", 0, 20))
                .thenReturn(new SessionPageResponse(List.of(), 0, 20, 0, 0));

        mvc.perform(get("/api/auth/sessions")).andExpect(status().isOk());
    }

    @Test
    void revokeSessionWithBadUuidIs500FromAdviceOrBadRequest() throws Exception {
        mvc.perform(delete("/api/auth/sessions/not-a-uuid"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 400 && s != 500) throw new AssertionError("unexpected status " + s);
                });
    }

    @Test
    void meReturnsProfileWithoutPassword() throws Exception {
        User user = User.builder().id(UUID.randomUUID()).name("Ash").email("ash@pallet.town").phoneNumber("+911")
                .password("hash").provider(AuthProvider.LOCAL).roles(Set.of(Role.ROLE_USER)).emailVerified(true).build();
        when(userRepository.findByEmail("ash@pallet.town")).thenReturn(Optional.of(user));

        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ash@pallet.town"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }
}
