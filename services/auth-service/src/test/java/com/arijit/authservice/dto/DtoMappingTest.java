package com.arijit.authservice.dto;

import com.arijit.authservice.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DtoMappingTest {

    @Test
    void authResponseOfSetsBearer() {
        AuthResponse r = AuthResponse.of("a", "r", 900);

        assertThat(r.tokenType()).isEqualTo("Bearer");
        assertThat(r.accessToken()).isEqualTo("a");
        assertThat(r.refreshToken()).isEqualTo("r");
        assertThat(r.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void sessionResponseReflectsExpiry() {
        RefreshToken live = RefreshToken.builder().id(UUID.randomUUID()).expiryDate(Instant.now().plusSeconds(60))
                .createdAt(Instant.now()).build();
        RefreshToken dead = RefreshToken.builder().id(UUID.randomUUID()).expiryDate(Instant.now().minusSeconds(60)).build();

        assertThat(SessionResponse.from(live).expired()).isFalse();
        assertThat(SessionResponse.from(dead).expired()).isTrue();
        assertThat(SessionResponse.from(live).id()).isEqualTo(live.getId());
    }

    @Test
    void sessionPageResponseCopiesPagingMetadata() {
        var page = new PageImpl<>(List.of(new SessionResponse(UUID.randomUUID(), Instant.now(), Instant.now(), false)),
                PageRequest.of(2, 5), 11);

        SessionPageResponse r = SessionPageResponse.from(page);

        assertThat(r.page()).isEqualTo(2);
        assertThat(r.size()).isEqualTo(5);
        assertThat(r.totalElements()).isEqualTo(11);
        assertThat(r.totalPages()).isEqualTo(3);
        assertThat(r.content()).hasSize(1);
    }

    @Test
    void adminUserResponseDoesNotExposePassword() {
        User u = User.builder().id(UUID.randomUUID()).name("A").email("a@b.c").phoneNumber("+911234567890")
                .password("secret-hash").roles(Set.of(Role.ROLE_ADMIN)).emailVerified(true).build();

        AdminUserResponse r = AdminUserResponse.from(u);

        assertThat(r.email()).isEqualTo("a@b.c");
        assertThat(r.roles()).containsExactly(Role.ROLE_ADMIN);
        assertThat(r.emailVerified()).isTrue();
        assertThat(r.accountEnabled()).isTrue();
        assertThat(r.toString()).doesNotContain("secret-hash");
    }

    @Test
    void roleDefinitionResponseMapsPermissions() {
        Permission p = Permission.builder().id(UUID.randomUUID()).code("a:b").category("c").build();
        RoleDefinition role = RoleDefinition.builder().id(UUID.randomUUID()).roleKey(Role.ROLE_ADMIN).name("Admin")
                .permissions(Set.of(p)).build();

        RoleDefinitionResponse r = RoleDefinitionResponse.from(role);

        assertThat(r.roleKey()).isEqualTo(Role.ROLE_ADMIN);
        assertThat(r.permissions()).extracting(PermissionResponse::code).containsExactly("a:b");
    }

    @Test
    void userPageAndPermissionPageCopyMetadata() {
        var users = new PageImpl<AdminUserResponse>(List.of(), PageRequest.of(1, 10), 25);
        UserPageResponse up = UserPageResponse.from(users);
        assertThat(up.totalPages()).isEqualTo(3);
        assertThat(up.page()).isEqualTo(1);

        var perms = new PageImpl<PermissionResponse>(List.of(), PageRequest.of(0, 50), 0);
        assertThat(PermissionPageResponse.from(perms).size()).isEqualTo(50);
    }
}
