package com.example.evalhub.invitation;

import com.example.evalhub.dto.UserResponse;
import com.example.evalhub.entity.Role;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER')")
public class EvaluatorInvitationController {
    private final EvaluatorInvitationService service;
    public EvaluatorInvitationController(EvaluatorInvitationService service) { this.service = service; }
    @PostMapping("/evaluators")
    public UserResponse create(@RequestBody EvaluatorInvitationService.CreateEvaluatorRequest request) {
        return service.create(request);
    }
    @GetMapping("/evaluator-invitations")
    public EvaluatorInvitationService.InvitationList list() { return service.list(); }
    @GetMapping("/evaluators/{id}/invitation")
    public EvaluatorInvitationService.InvitationView get(@PathVariable long id) { return service.get(id); }
    @PostMapping("/evaluators/{id}/invitation/retry")
    public EvaluatorInvitationService.InvitationView retry(@PathVariable long id) { return service.retry(id); }

    // 매니저 계정 안내 메일 (최고 관리자 전용)
    @GetMapping("/manager-invitations")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public EvaluatorInvitationService.InvitationList listManagers() { return service.list(Role.MANAGER); }
    @PostMapping("/managers/{id}/invitation/retry")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public EvaluatorInvitationService.InvitationView retryManager(@PathVariable long id) { return service.retry(id, Role.MANAGER); }
}
