package com.example.evalhub.controller;

import com.example.evalhub.dto.*;
import com.example.evalhub.entity.Role;
import com.example.evalhub.security.AuthenticatedUser;
import com.example.evalhub.service.ApplicationService;
import com.example.evalhub.service.AccountDeletionService;
import com.example.evalhub.service.EvaluationService;
import com.example.evalhub.service.ProgramManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Program Management", description = "프로그램, 팀/팀원, 평가 질문, 심사위원, 평가 배정 관리 API")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER')")
public class ProgramManagementController {

    private final ProgramManagementService programManagementService;
    private final EvaluationService evaluationService;
    private final ApplicationService applicationService;
    private final AccountDeletionService accountDeletionService;

    public ProgramManagementController(ProgramManagementService programManagementService, EvaluationService evaluationService, ApplicationService applicationService, AccountDeletionService accountDeletionService) {
        this.programManagementService = programManagementService;
        this.evaluationService = evaluationService;
        this.applicationService = applicationService;
        this.accountDeletionService = accountDeletionService;
    }

    // 매니저가 팀 관리 화면에서 해당 팀의 지원 정보(본인/팀원 정보 + 지원서 양식 답변)를 조회한다.
    @Operation(summary = "팀 지원 정보 조회", description = "팀에 연결된 지원서의 본인/팀원 정보와 양식 답변을 반환합니다. 연결된 지원서가 없으면 404.")
    @GetMapping("/teams/{teamId}/application")
    public ApplicationDetailResponse getTeamApplication(@PathVariable Long teamId) {
        return applicationService.getApplicationByTeam(teamId);
    }

    // 매니저가 팀이 제출한 지원 정보(팀명·본인/팀원 정보 + 양식 답변)를 수정한다. 제출 상태는 유지된다.
    @Operation(summary = "팀 지원 정보 수정", description = "매니저가 팀이 제출한 지원 정보(팀명·본인/팀원·양식 답변)를 수정합니다. 첨부파일과 제출 상태는 변경하지 않습니다.")
    @PutMapping("/teams/{teamId}/application")
    public ApplicationDetailResponse updateTeamApplication(
            @PathVariable Long teamId,
            @RequestBody SaveApplicationRequest request
    ) {
        return applicationService.updateApplicationByTeam(teamId, request);
    }

    // 프로그램 편집 화면의 전체 상태(기본 정보·평가 기준·지원 양식 항목)를 한 트랜잭션으로 생성한다.
    // 담당 매니저는 본문이 아니라 로그인 정보로 정한다. MANAGER가 만들면 본인, SUPER_ADMIN이면 비워 둔다.
    @Operation(summary = "프로그램 생성", description = "기본 정보, 평가 기준(criteria), 지원 양식 항목(formFields)을 한 번에 저장합니다. 하나라도 실패하면 전부 롤백됩니다. publish=true면 같은 트랜잭션에서 개설하며, 실제 상태는 기간으로 계산합니다. criteria/formFields는 배열 순서가 표시 순서이며 필수입니다(비우려면 빈 배열).")
    @PostMapping("/programs")
    public ProgramEditorResponse createProgram(
            @RequestBody SaveProgramRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long managerId = user != null && user.role() == Role.MANAGER ? user.id() : null;
        return programManagementService.createProgram(request, managerId);
    }

    // 관리자 화면에서 등록된 프로그램 목록을 조회한다.
    @Operation(summary = "프로그램 목록 조회")
    @GetMapping("/programs")
    public List<ProgramResponse> findPrograms() {
        return programManagementService.findPrograms();
    }

    // 지원자 홈에 노출할 지원 접수 중(RECRUITING) 프로그램 목록을 조회한다.
    @Operation(summary = "지원 접수 중 프로그램 목록 조회", description = "접수 예정·접수 중 프로그램을 반환합니다. 마감 시각이 지난 프로그램은 제외하고, schedule에 정확한 기간 및 현재 작업 가능 여부를 제공합니다.")
    @GetMapping("/programs/open")
    @PreAuthorize("permitAll()")
    public List<ProgramResponse> findOpenPrograms() {
        return programManagementService.findOpenPrograms();
    }

    // 기존 프로그램을 화면의 전체 상태로 저장한다. (임시저장 이어쓰기, 수정, 임시저장 후 개설)
    @Operation(summary = "프로그램 저장", description = "기본 정보, 평가 기준, 지원 양식 항목을 한 트랜잭션으로 저장합니다. 항목은 id가 있으면 제자리 수정, null이면 추가, 요청에 없으면 삭제합니다. 답변이 달린 양식 항목 삭제, 심사 시작 후 평가 기준 구성·배점 변경은 409입니다. publish=true는 DRAFT에서만 허용합니다. 일정 *At 필드가 null이면 기존 값을 유지합니다.")
    @PutMapping("/programs/{programId}")
    public ProgramEditorResponse updateProgram(@PathVariable Long programId, @RequestBody SaveProgramRequest request) {
        return programManagementService.updateProgram(programId, request);
    }

    @Operation(summary = "프로그램 삭제", description = "프로그램과 하위 팀, 질문, 배정이 모두 삭제됩니다.")
    @DeleteMapping("/programs/{programId}")
    public void deleteProgram(@PathVariable Long programId) {
        programManagementService.deleteProgram(programId);
    }

    // 프로그램에 평가 대상 팀을 등록한다. 개인 참가자도 1인 팀으로 등록한다.
    @Operation(summary = "팀/팀원 등록", description = "평가 대상 팀을 등록합니다. 개인 참가자는 participantType=INDIVIDUAL인 1인 팀으로 등록합니다.")
    @PostMapping("/programs/{programId}/teams")
    public TeamResponse createTeam(@PathVariable Long programId, @RequestBody CreateTeamRequest request) {
        return programManagementService.createTeam(programId, request);
    }

    // 프로그램별 평가 대상 목록을 조회한다.
    @Operation(summary = "팀 목록 조회", description = "프로그램에 등록된 평가 대상 팀 목록을 조회합니다.")
    @GetMapping("/programs/{programId}/teams")
    public List<TeamResponse> findTeams(@PathVariable Long programId) {
        return programManagementService.findTeams(programId);
    }

    @Operation(summary = "팀 단건 조회")
    @GetMapping("/programs/{programId}/teams/{teamId}")
    public TeamResponse findTeam(@PathVariable Long programId, @PathVariable Long teamId) {
        return programManagementService.findTeam(programId, teamId);
    }

    // 팀(평가 대상) 정보를 수정한다.
    @Operation(summary = "팀 수정", description = "팀 이름, 설명, 제출 URL, 비고를 수정합니다.")
    @PutMapping("/programs/{programId}/teams/{teamId}")
    public TeamResponse updateTeam(
            @PathVariable Long programId,
            @PathVariable Long teamId,
            @RequestBody UpdateTeamRequest request
    ) {
        return programManagementService.updateTeam(programId, teamId, request);
    }

    @Operation(summary = "팀 삭제", description = "팀과 하위 팀원, 첨부파일, 배정이 모두 삭제됩니다.")
    @DeleteMapping("/programs/{programId}/teams/{teamId}")
    public void deleteTeam(@PathVariable Long programId, @PathVariable Long teamId) {
        programManagementService.deleteTeam(programId, teamId);
    }

    @Operation(summary = "팀 상태 변경", description = "팀의 평가 상태를 PENDING / PASSED / FAILED 중 하나로 변경합니다.")
    @PostMapping("/programs/{programId}/teams/{teamId}/status")
    public TeamResponse changeTeamStatus(
            @PathVariable Long programId,
            @PathVariable Long teamId,
            @RequestBody ChangeTeamStatusRequest request
    ) {
        return programManagementService.changeTeamStatus(programId, teamId, request);
    }

    // 매니저가 팀별 항목별 가산점을 입력한다.
    @Operation(summary = "팀 가산점 입력", description = "모든 항목의 itemId와 score를 scores 배열로 전송합니다. 미심사는 null, 점수는 0~항목별 최대 점수입니다.")
    @PostMapping("/programs/{programId}/teams/{teamId}/bonus")
    public TeamResponse updateTeamBonus(
            @PathVariable Long programId,
            @PathVariable Long teamId,
            @RequestBody UpdateTeamBonusRequest request
    ) {
        return programManagementService.updateTeamBonus(programId, teamId, request);
    }

    // 팀에 소속된 팀원 목록을 조회한다.
    @Operation(summary = "팀원 목록 조회")
    @GetMapping("/teams/{teamId}/members")
    public List<TeamMemberResponse> findTeamMembers(@PathVariable Long teamId) {
        return programManagementService.findTeamMembers(teamId);
    }

    @Operation(summary = "팀원 추가")
    @PostMapping("/teams/{teamId}/members")
    public TeamMemberResponse addTeamMember(
            @PathVariable Long teamId,
            @RequestBody AddTeamMemberRequest request
    ) {
        return programManagementService.addTeamMember(teamId, request);
    }

    @Operation(summary = "팀원 삭제")
    @DeleteMapping("/teams/{teamId}/members/{memberId}")
    public void removeTeamMember(
            @PathVariable Long teamId,
            @PathVariable Long memberId
    ) {
        programManagementService.removeTeamMember(teamId, memberId);
    }

    // 프로그램 평가 화면에 표시할 질문 목록을 순서대로 조회한다.
    @Operation(summary = "평가 질문 목록 조회")
    @GetMapping("/programs/{programId}/criteria")
    public List<CriterionResponse> findCriteria(@PathVariable Long programId) {
        return programManagementService.findCriteria(programId);
    }

    // 프로그램 제출 항목을 순서대로 조회한다.
    @Operation(summary = "제출 항목 조회", description = "프로그램의 제출 항목을 순서대로 조회합니다.")
    @GetMapping("/programs/{programId}/form-fields")
    @PreAuthorize("permitAll()")
    public List<FormFieldResponse> findFormFields(@PathVariable Long programId) {
        return programManagementService.findFormFields(programId);
    }

    @Operation(summary = "유저 등록", description = "심사위원은 POST /api/evaluators로 생성합니다. 그 외 기존 계정을 등록합니다.")
    @PostMapping("/users")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public UserResponse createUser(@RequestBody CreateUserRequest request) {
        return programManagementService.createUser(request);
    }

    @Operation(summary = "계정 삭제", description = "매니저는 심사위원·지원자만 삭제할 수 있습니다. 심사위원은 논리 삭제하며 평가 기록과 집계를 보존합니다.")
    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or (hasRole('MANAGER') and @accountDeletionService.canManagerDelete(#userId))")
    public void deleteUser(@PathVariable Long userId) {
        accountDeletionService.deleteUser(userId);
    }

    @Operation(summary = "이메일로 사용자 조회", description = "팀원 추가 시 실제 존재하는 사용자인지 확인합니다. 없으면 404를 반환합니다.")
    @GetMapping("/users/lookup")
    public UserResponse lookupUser(@RequestParam String email) {
        return programManagementService.findUserByEmail(email);
    }

    @Operation(summary = "운영 담당자 목록 조회", description = "운영 담당자 역할 계정 목록을 조회합니다. 최고 권한 계정 전용입니다.")
    @GetMapping("/coordinators")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<UserResponse> findManagers() {
        return programManagementService.findManagers();
    }

    @Operation(summary = "심사위원 목록 조회")
    @GetMapping("/evaluators")
    public List<UserResponse> findEvaluators() {
        return programManagementService.findEvaluators();
    }

    @Operation(summary = "프로그램별 배정된 심사위원 목록 조회", description = "해당 프로그램에 배정된 심사위원 목록을 조회합니다.")
    @GetMapping("/programs/{programId}/evaluators")
    public List<UserResponse> findEvaluatorsByProgram(@PathVariable Long programId) {
        return programManagementService.findEvaluatorsByProgram(programId);
    }

    @Operation(summary = "심사위원 상태 변경", description = "심사위원을 활성/비활성으로 전환합니다.")
    @PostMapping("/evaluators/{evaluatorId}/status")
    public UserResponse changeEvaluatorStatus(@PathVariable Long evaluatorId, @RequestBody ChangeStatusRequest request) {
        return programManagementService.changeEvaluatorStatus(evaluatorId, request);
    }

    // 특정 평가 대상과 심사위원을 연결해 평가 작업을 배정한다.
    @Operation(summary = "심사위원 할당", description = "특정 평가 대상 participantId와 심사위원 evaluatorId를 연결합니다.")
    @PostMapping("/programs/{programId}/assignments")
    public AssignmentResponse createAssignment(
            @PathVariable Long programId,
            @RequestBody CreateAssignmentRequest request
    ) {
        return programManagementService.createAssignment(programId, request);
    }

    // 심사위원이 자신에게 배정된 평가 목록을 확인한다.
    @Operation(summary = "심사위원별 배정 목록 조회")
    @GetMapping("/evaluators/{evaluatorId}/assignments")
    public List<AssignmentResponse> findAssignmentsByEvaluator(@PathVariable Long evaluatorId) {
        return programManagementService.findAssignmentsByEvaluator(evaluatorId);
    }

    // 프로그램에 속한 전체 배정 목록을 조회한다.
    @Operation(summary = "프로그램별 배정 목록 조회", description = "배정 매트릭스에서 사용합니다.")
    @GetMapping("/programs/{programId}/assignments")
    public List<AssignmentResponse> findAssignmentsByProgram(@PathVariable Long programId) {
        return programManagementService.findAssignmentsByProgram(programId);
    }

    @Operation(summary = "프로그램 평가 결과 조회", description = "팀별로 배정된 심사위원의 제출 현황과 점수를 조회합니다.")
    @GetMapping("/programs/{programId}/evaluation-results")
    public List<TeamEvaluationResult> getEvaluationResults(@PathVariable Long programId) {
        return evaluationService.getEvaluationResults(programId);
    }

    @Operation(summary = "배정 삭제", description = "제출 여부와 무관하게 배정과 해당 평가 기록을 삭제합니다. 기존 평가는 결과에서 제외되며 평균·순위가 변경될 수 있습니다.")
    @DeleteMapping("/assignments/{assignmentId}")
    public void cancelAssignment(@PathVariable Long assignmentId) {
        programManagementService.cancelAssignment(assignmentId);
    }
}
