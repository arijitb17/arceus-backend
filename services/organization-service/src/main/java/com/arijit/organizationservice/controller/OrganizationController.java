package com.arijit.organizationservice.controller;

import com.arijit.organizationservice.dto.AddMemberRequest;
import com.arijit.organizationservice.dto.CreateOrganizationRequest;
import com.arijit.organizationservice.dto.MemberResponse;
import com.arijit.organizationservice.dto.OrganizationResponse;
import com.arijit.organizationservice.entity.Organization;
import com.arijit.organizationservice.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class OrganizationController {
    private final OrganizationService organizationService;

    @PostMapping
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest request,
                                                         Principal principal) {
        Organization org = organizationService.create(request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrganizationResponse.from(org, com.arijit.organizationservice.entity.OrgRole.OWNER));
    }

    @GetMapping
    public List<OrganizationResponse> list(Principal principal) {
        return organizationService.listForUser(principal.getName()).stream()
                .map(org -> OrganizationResponse.from(org, organizationService.roleOf(org.getId(), principal.getName())))
                .toList();
    }

    @GetMapping("/{organizationId}")
    public OrganizationResponse get(@PathVariable UUID organizationId, Principal principal) {
        Organization org = organizationService.get(organizationId, principal.getName());
        return OrganizationResponse.from(org, organizationService.roleOf(organizationId, principal.getName()));
    }

    @DeleteMapping("/{organizationId}")
    public ResponseEntity<Void> delete(@PathVariable UUID organizationId, Principal principal) {
        organizationService.delete(organizationId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{organizationId}/members")
    public List<MemberResponse> listMembers(@PathVariable UUID organizationId, Principal principal) {
        return organizationService.listMembers(organizationId, principal.getName()).stream()
                .map(MemberResponse::from)
                .toList();
    }

    @PostMapping("/{organizationId}/members")
    public ResponseEntity<MemberResponse> addMember(@PathVariable UUID organizationId,
                                                      @Valid @RequestBody AddMemberRequest request,
                                                      Principal principal) {
        var member = organizationService.addMember(organizationId, request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(MemberResponse.from(member));
    }

    @DeleteMapping("/{organizationId}/members/{userEmail}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID organizationId, @PathVariable String userEmail,
                                              Principal principal) {
        organizationService.removeMember(organizationId, userEmail, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
