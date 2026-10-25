package com.arijit.notificationservice.service;

import com.arijit.notificationservice.dto.*;
import com.arijit.notificationservice.entity.NotificationChannel;
import com.arijit.notificationservice.entity.NotificationTemplate;
import com.arijit.notificationservice.exception.ApiException;
import com.arijit.notificationservice.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateServicesTest {

    @Mock NotificationTemplateRepository repo;
    @InjectMocks TemplateManagementService service;

    private NotificationTemplate template() {
        return NotificationTemplate.builder().id(UUID.randomUUID()).channel(NotificationChannel.EMAIL)
                .code("WELCOME").subject("s").body("b").active(true).build();
    }

    // ---- TemplateRenderer ----

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void rendererSubstitutesPlaceholdersWithWhitespaceTolerance() {
        assertThat(renderer.render("Hi {{name}} and {{ other }}!", Map.of("name", "Ash", "other", 5)))
                .isEqualTo("Hi Ash and 5!");
    }

    @Test
    void rendererLeavesUnknownPlaceholdersAndHandlesNulls() {
        assertThat(renderer.render("Hi {{who}}", Map.of())).isEqualTo("Hi {{who}}");
        assertThat(renderer.render("Hi {{who}}", null)).isEqualTo("Hi {{who}}");
        assertThat(renderer.render(null, Map.of())).isNull();
    }

    @Test
    void rendererTreatsReplacementLiterally() {
        assertThat(renderer.render("Cost: {{amount}}", Map.of("amount", "$5 \\ $1"))).isEqualTo("Cost: $5 \\ $1");
    }

    @Test
    void rendererHandlesRepeatedPlaceholders() {
        assertThat(renderer.render("{{a}}-{{a}}", Map.of("a", "x"))).isEqualTo("x-x");
    }

    // ---- TemplateManagementService ----

    @Test
    void listClampsPagingAndFiltersByChannel() {
        when(repo.findByChannel(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(template())));
        when(repo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.list(NotificationChannel.EMAIL, -1, 1000);
        service.list(null, 0, 0);

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findByChannel(org.mockito.ArgumentMatchers.eq(NotificationChannel.EMAIL), cap.capture());
        assertThat(cap.getValue().getPageNumber()).isZero();
        assertThat(cap.getValue().getPageSize()).isEqualTo(100);
        ArgumentCaptor<Pageable> all = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findAll(all.capture());
        assertThat(all.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    void createPersistsActiveTemplate() {
        when(repo.existsByChannelAndCode(NotificationChannel.EMAIL, "NEW")).thenReturn(false);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        TemplateResponse r = service.create(new CreateTemplateRequest(NotificationChannel.EMAIL, "NEW", "subj", "body"));

        assertThat(r.active()).isTrue();
        assertThat(r.code()).isEqualTo("NEW");
        assertThat(r.subject()).isEqualTo("subj");
    }

    @Test
    void createDuplicateConflicts() {
        when(repo.existsByChannelAndCode(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateTemplateRequest(NotificationChannel.EMAIL, "X", null, "b")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(repo, never()).save(any());
    }

    @Test
    void updateChangesOnlySubjectAndBody() {
        NotificationTemplate t = template();
        when(repo.findById(t.getId())).thenReturn(Optional.of(t));
        when(repo.save(t)).thenReturn(t);

        TemplateResponse r = service.update(t.getId(), new UpdateTemplateRequest("new subj", "new body"));

        assertThat(r.subject()).isEqualTo("new subj");
        assertThat(r.body()).isEqualTo("new body");
        assertThat(r.code()).isEqualTo("WELCOME");
        assertThat(r.channel()).isEqualTo(NotificationChannel.EMAIL);
    }

    @Test
    void updateStatusTogglesActive() {
        NotificationTemplate t = template();
        when(repo.findById(t.getId())).thenReturn(Optional.of(t));
        when(repo.save(t)).thenReturn(t);

        assertThat(service.updateStatus(t.getId(), new UpdateTemplateStatusRequest(false)).active()).isFalse();
    }

    @Test
    void getAndDeleteUnknownAre404() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ApiException.class);
        verify(repo, never()).delete(any());
    }

    @Test
    void deleteRemovesExisting() {
        NotificationTemplate t = template();
        when(repo.findById(t.getId())).thenReturn(Optional.of(t));

        service.delete(t.getId());

        verify(repo).delete(t);
    }
}
