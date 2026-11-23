package com.example.evalhub;

import com.example.evalhub.dto.AssignmentResponse;
import com.example.evalhub.dto.CreateAssignmentRequest;
import com.example.evalhub.dto.EvaluatorAssignmentResponse;
import com.example.evalhub.dto.ProgramResponse;
import com.example.evalhub.dto.TeamEvaluationResult;
import com.example.evalhub.dto.UpdateAssignmentPermissionsRequest;
import com.example.evalhub.entity.EvaluationAssignment;
import com.example.evalhub.entity.EvaluationSubmission;
import com.example.evalhub.entity.ParticipantType;
import com.example.evalhub.entity.Program;
import com.example.evalhub.entity.ProgramType;
import com.example.evalhub.entity.Role;
import com.example.evalhub.entity.Status;
import com.example.evalhub.entity.Team;
import com.example.evalhub.entity.User;
import com.example.evalhub.repository.EvaluationAssignmentRepository;
import com.example.evalhub.repository.EvaluationSubmissionRepository;
import com.example.evalhub.repository.ProgramRepository;
import com.example.evalhub.repository.TeamRepository;
import com.example.evalhub.repository.UserRepository;
import com.example.evalhub.security.AuthenticatedUser;
import com.example.evalhub.service.EvaluationService;
import com.example.evalhub.service.ProgramManagementService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 배정 단위 '심사' / '결과 조회' 권한 (V28, security/AssignmentAccess)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AssignmentPermissionIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private ProgramManagementService management;
    @Autowired private EvaluationService evaluationService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProgramRepository programRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private EvaluationAssignmentRepository assignmentRepository;
    @Autowired private EvaluationSubmissionRepository submissionRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @Test
    void existingStyleInsertDefaultsToEvaluateOnlyAndBothFalseIsRejected() {
        Fixture f = fixture();
        User evaluator = saveUser(Role.EVALUATOR);
        // V28 이전처럼 권한 컬럼 없이 넣은 배정은 심사만 가진다.
        jdbc.update("insert into evaluation_assignments(program_id, evaluator_id, status) values (?, ?, 'ASSIGNED')",
                f.program().getId(), evaluator.getId());
        Map<String, Object> row = jdbc.queryForMap(
                "select can_evaluate, can_view_results from evaluation_assignments where program_id = ? and evaluator_id = ?",
                f.program().getId(), evaluator.getId());
        assertThat(row.get("can_evaluate")).isEqualTo(true);
        assertThat(row.get("can_view_results")).isEqualTo(false);

        assertThatThrownBy(() -> jdbc.update(
                "update evaluation_assignments set can_evaluate = false, can_view_results = false where program_id = ? and evaluator_id = ?",
                f.program().getId(), evaluator.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createAssignmentWithoutFlagsGivesEvaluateOnly() {
        Fixture f = fixture();
        User evaluator = saveUser(Role.EVALUATOR);
        AssignmentResponse created = management.createAssignment(f.program().getId(), new CreateAssignmentRequest(evaluator.getId()));
        assertThat(created.canEvaluate()).isTrue();
        assertThat(created.canViewResults()).isFalse();
    }

    @Test
    void createAssignmentRejectsNoPermission() throws Exception {
        Fixture f = fixture();
        User evaluator = saveUser(Role.EVALUATOR);
        mvc.perform(post("/api/programs/" + f.program().getId() + "/assignments").with(asManager()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evaluatorId\":" + evaluator.getId() + ",\"canEvaluate\":false,\"canViewResults\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/programs/" + f.program().getId() + "/assignments").with(asManager()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evaluatorId\":" + evaluator.getId() + ",\"canEvaluate\":true,\"canViewResults\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEvaluate").value(true))
                .andExpect(jsonPath("$.canViewResults").value(true));
    }

    @Test
    void resultsOnlyEvaluatorCanReadResultsButNotEvaluate() throws Exception {
        Fixture f = fixture();
        User viewer = saveUser(Role.EVALUATOR);
        EvaluationAssignment assignment = assign(f.program(), viewer, false, true);
        String base = "/api/evaluation-assignments/" + assignment.getId();

        expect(get(base + "/results"), viewer, status().isOk());
        mvc.perform(get(base + "/results").with(user(AuthenticatedUser.from(viewer))))
                .andExpect(jsonPath("$.program.id").value(f.program().getId()))
                .andExpect(jsonPath("$.results[0].teamId").value(f.team().getId()))
                .andExpect(jsonPath("$.criteria").isArray());
        expect(get(base + "/results/pdf"), viewer, status().isOk());

        expect(get(base + "/teams"), viewer, status().isForbidden());
        expect(get(base + "/teams/" + f.team().getId()), viewer, status().isForbidden());
        expect(put(base + "/teams/" + f.team().getId() + "/draft").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"scores\":[]}"), viewer, status().isForbidden());
        expect(post(base + "/teams/" + f.team().getId() + "/submit").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"scores\":[]}"), viewer, status().isForbidden());
        expect(get("/api/teams/" + f.team().getId() + "/attachments"), viewer, status().isForbidden());

        // 매니저 API는 그대로 매니저 전용
        expect(get("/api/programs/" + f.program().getId() + "/evaluation-results"), viewer, status().isForbidden());
        expect(get("/api/programs/" + f.program().getId() + "/evaluation-results/pdf"), viewer, status().isForbidden());
        expect(post("/api/programs/" + f.program().getId() + "/teams/" + f.team().getId() + "/status").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PASSED\"}"), viewer, status().isForbidden());
        expect(put("/api/assignments/" + assignment.getId() + "/permissions").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"canEvaluate\":true,\"canViewResults\":true}"),
                viewer, status().isForbidden());
    }

    @Test
    void evaluateOnlyEvaluatorCannotReadResults() throws Exception {
        Fixture f = fixture();
        User judge = saveUser(Role.EVALUATOR);
        EvaluationAssignment assignment = assign(f.program(), judge, true, false);
        String base = "/api/evaluation-assignments/" + assignment.getId();

        expect(get(base + "/results"), judge, status().isForbidden());
        expect(get(base + "/results/pdf"), judge, status().isForbidden());
        expect(get(base + "/teams"), judge, notForbidden());
        expect(get("/api/teams/" + f.team().getId() + "/attachments"), judge, status().isOk());
    }

    @Test
    void evaluatorWithBothPermissionsCanDoBoth() throws Exception {
        Fixture f = fixture();
        User chair = saveUser(Role.EVALUATOR);
        EvaluationAssignment assignment = assign(f.program(), chair, true, true);
        String base = "/api/evaluation-assignments/" + assignment.getId();

        expect(get(base + "/results"), chair, status().isOk());
        expect(get(base + "/teams"), chair, notForbidden());
    }

    @Test
    void anotherEvaluatorsAssignmentIsForbidden() throws Exception {
        Fixture f = fixture();
        User owner = saveUser(Role.EVALUATOR);
        User other = saveUser(Role.EVALUATOR);
        EvaluationAssignment assignment = assign(f.program(), owner, true, true);
        String base = "/api/evaluation-assignments/" + assignment.getId();

        expect(get(base + "/results"), other, status().isForbidden());
        expect(get(base + "/teams"), other, status().isForbidden());
    }

    @Test
    void missingAssignmentIsNotFound() throws Exception {
        User evaluator = saveUser(Role.EVALUATOR);
        expect(get("/api/evaluation-assignments/999999999/results"), evaluator, status().isNotFound());
        expect(get("/api/evaluation-assignments/999999999/teams"), evaluator, status().isNotFound());
    }

    @Test
    void resultsAreClosedBeforeEvaluationStarts() throws Exception {
        Fixture f = fixture();
        f.program().changeStatus(Status.PENDING_EVALUATION);
        User viewer = saveUser(Role.EVALUATOR);
        EvaluationAssignment assignment = assign(f.program(), viewer, false, true);

        expect(get("/api/evaluation-assignments/" + assignment.getId() + "/results"), viewer, status().isConflict());
        List<EvaluatorAssignmentResponse> mine = evaluationService.getEvaluatorAssignments(viewer.getId());
        assertThat(mine).singleElement().satisfies(row -> {
            assertThat(row.canEvaluate()).isFalse();
            assertThat(row.canViewResults()).isTrue();
            assertThat(row.resultsAvailable()).isFalse();
        });

        f.program().changeStatus(Status.INACTIVE);
        expect(get("/api/evaluation-assignments/" + assignment.getId() + "/results"), viewer, status().isOk());
    }

    @Test
    void resultsOnlyAssignmentsAreNotCountedAsEvaluators() {
        Fixture f = fixture();
        User judge = saveUser(Role.EVALUATOR);
        User viewer = saveUser(Role.EVALUATOR);
        assign(f.program(), judge, true, false);
        assign(f.program(), viewer, false, true);

        List<TeamEvaluationResult> results = evaluationService.getEvaluationResults(f.program().getId());
        assertThat(results).singleElement().satisfies(team ->
                assertThat(team.evaluations()).extracting("evaluatorId").containsExactly(judge.getId()));

        ProgramResponse listed = management.findPrograms().stream()
                .filter(program -> program.id().equals(f.program().getId())).findFirst().orElseThrow();
        assertThat(listed.evaluatorCount()).isEqualTo(1);

        assertThat(management.findEvaluators()).filteredOn(u -> u.id().equals(viewer.getId())).singleElement()
                .satisfies(u -> {
                    assertThat(u.assignmentCount()).isZero();
                    assertThat(u.resultAssignmentCount()).isEqualTo(1);
                });
    }

    @Test
    void permissionsCanBeChangedButNotToNothing() throws Exception {
        Fixture f = fixture();
        User evaluator = saveUser(Role.EVALUATOR);
        EvaluationAssignment assignment = assign(f.program(), evaluator, true, false);
        String url = "/api/assignments/" + assignment.getId() + "/permissions";

        mvc.perform(put(url).with(asManager()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canEvaluate\":false,\"canViewResults\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(url).with(asManager()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canEvaluate\":false,\"canViewResults\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEvaluate").value(false))
                .andExpect(jsonPath("$.canViewResults").value(true));
    }

    @Test
    void removingEvaluatePermissionWithRecordsIsConflict() {
        Fixture f = fixture();
        User evaluator = saveUser(Role.EVALUATOR);
        EvaluationAssignment assignment = assign(f.program(), evaluator, true, false);
        submissionRepository.save(new EvaluationSubmission(assignment, f.team()));
        entityManager.flush();

        assertThatThrownBy(() -> management.updateAssignmentPermissions(assignment.getId(),
                new UpdateAssignmentPermissionsRequest(false, true)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        // 결과 조회만 추가하는 것은 기록이 있어도 된다.
        AssignmentResponse updated = management.updateAssignmentPermissions(assignment.getId(),
                new UpdateAssignmentPermissionsRequest(true, true));
        assertThat(updated.canEvaluate()).isTrue();
        assertThat(updated.canViewResults()).isTrue();
    }

    private void expect(MockHttpServletRequestBuilder request, User as, ResultMatcher matcher) throws Exception {
        mvc.perform(request.with(user(AuthenticatedUser.from(as)))).andExpect(matcher);
    }

    private ResultMatcher notForbidden() {
        return result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asManager() {
        return user("manager").roles("MANAGER");
    }

    private EvaluationAssignment assign(Program program, User evaluator, boolean canEvaluate, boolean canViewResults) {
        EvaluationAssignment assignment = assignmentRepository.save(
                new EvaluationAssignment(program, evaluator, canEvaluate, canViewResults));
        entityManager.flush();
        return assignment;
    }

    private record Fixture(Program program, Team team) {}

    private Fixture fixture() {
        User manager = saveUser(Role.MANAGER);
        Program program = programRepository.save(new Program(
                manager,
                "permission-test-" + UUID.randomUUID(),
                null,
                ProgramType.MODULE_A,
                LocalDate.now().minusDays(20),
                LocalDate.now().minusDays(10),
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(10),
                null
        ));
        program.changeStatus(Status.ACTIVE);
        Team team = teamRepository.save(new Team(program, "permission-team", ParticipantType.INDIVIDUAL,
                null, "permission-team", null, null));
        entityManager.flush();
        return new Fixture(program, team);
    }

    private User saveUser(Role role) {
        return userRepository.save(new User(
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com",
                "encoded-password",
                role
        ));
    }
}
