package com.example.evalhub;

import com.example.evalhub.dto.*;
import com.example.evalhub.entity.*;
import com.example.evalhub.repository.*;
import com.example.evalhub.service.ApplicationService;
import com.example.evalhub.service.ProgramManagementService;
import com.example.evalhub.util.ApplicantFields;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 매니저가 정하는 지원자 인적사항 항목(programs.applicant_fields)의 저장·검증과 예전 고정 항목 방식의 유지.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "MANAGER")
class ApplicantFieldsIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ApplicationService applicationService;
    @Autowired ProgramManagementService programService;
    @Autowired ApplicationRepository applications;
    @Autowired ProgramRepository programs;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired EntityManager entityManager;

    private User manager;
    private User member;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        manager = users.save(new User("manager-" + suffix + "@example.com", "encoded", Role.MANAGER));
        member = users.save(new User("member-" + suffix + "@example.com", "encoded", Role.MEMBER,
                "지원자계정", null, null));
    }

    @Test
    void creatingProgramAssignsIdsAndOmittedFieldsMeanLegacy() throws Exception {
        String body = mvc.perform(post("/api/programs").with(csrf()).contentType("application/json").content("""
                        {"title":"custom","type":"MODULE_A","participantType":"TEAM","criteria":[],"formFields":[],
                         "applicantFields":[{"id":null,"label":" 이메일 "},{"label":"연락처"}]}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var fields = mapper.readTree(body).get("program").get("applicantFields");
        assertThat(fields).hasSize(2);
        assertThat(fields.get(0).get("label").asText()).isEqualTo("이메일");
        assertThat(fields.get(0).get("id").asText()).isNotBlank();
        assertThat(fields.get(1).get("label").asText()).isEqualTo("연락처");

        String legacy = mvc.perform(post("/api/programs").with(csrf()).contentType("application/json").content("""
                        {"title":"legacy","type":"MODULE_A","criteria":[],"formFields":[]}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(legacy).get("program").get("applicantFields").isNull()).isTrue();
    }

    // 프로그램 편집 화면의 저장(PUT)과 같은 본문으로, 기본 정보·지원 방식·지원자 정보 항목이 모두 바뀌는지 확인한다.
    @Test
    void editorSaveUpdatesProgramAndSwitchesLegacyProgramWithoutApplications() throws Exception {
        String created = mvc.perform(post("/api/programs").with(csrf()).contentType("application/json").content("""
                        {"title":"before","type":"MODULE_A","participantType":"INDIVIDUAL","criteria":[],"formFields":[],
                         "applicationStartAt":"2030-11-10T09:00:00+09:00","applicationDeadlineAt":"2030-11-20T18:00:00+09:00",
                         "evaluationStartAt":"2030-11-20T18:00:00+09:00","evaluationDeadlineAt":"2030-11-30T18:00:00+09:00",
                         "publish":true}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(created).get("program").get("id").asLong();
        assertThat(mapper.readTree(created).get("program").get("applicantFields").isNull()).isTrue();

        String saved = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/programs/" + id)
                        .with(csrf()).contentType("application/json").content("""
                        {"title":"after","description":"desc","type":"MODULE_A","managerId":null,
                         "applicationStartAt":"2030-11-10T09:00:00+09:00","applicationDeadlineAt":"2030-11-20T18:00:00+09:00",
                         "evaluationStartAt":"2030-11-20T18:00:00+09:00","evaluationDeadlineAt":"2030-11-30T18:00:00+09:00",
                         "eligibilityCriteria":null,"attachmentsEnabled":false,"maxAttachments":null,"bonusEnabled":false,
                         "participantType":"TEAM","criteria":[],"formFields":[],
                         "applicantFields":[{"id":null,"label":"이름"},{"id":null,"label":"학번"}],"publish":false}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var program = mapper.readTree(saved).get("program");
        assertThat(program.get("title").asText()).isEqualTo("after");
        assertThat(program.get("participantType").asText()).isEqualTo("TEAM");
        assertThat(program.get("applicantFields")).hasSize(2);

        entityManager.flush();
        entityManager.clear();
        Program reloaded = programs.findById(id).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("after");
        assertThat(ApplicantFields.parse(reloaded.getApplicantFields())).extracting(ApplicantField::label)
                .containsExactly("이름", "학번");
    }

    @Test
    void teamFieldLabelAndDuplicatesAreRejected() throws Exception {
        mvc.perform(post("/api/programs").with(csrf()).contentType("application/json").content("""
                        {"title":"dup","type":"MODULE_A","participantType":"TEAM","criteria":[],"formFields":[],
                         "applicantFields":[{"label":"팀 이름"}]}
                        """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/programs").with(csrf()).contentType("application/json").content("""
                        {"title":"dup","type":"MODULE_A","criteria":[],"formFields":[],
                         "applicantFields":[{"label":"이메일"},{"label":"이메일"}]}
                        """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/programs").with(csrf()).contentType("application/json").content("""
                        {"title":"blank","type":"MODULE_A","criteria":[],"formFields":[],
                         "applicantFields":[{"label":"  "}]}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void saveApplicationJsonCarriesItemValues() throws Exception {
        var request = mapper.readValue("""
                {"teamName":"t","values":{"a":"1"},"members":[{"values":{"a":"2"}}],"answers":[]}
                """, SaveApplicationRequest.class);
        assertThat(request.values()).containsEntry("a", "1");
        assertThat(request.members().get(0).values()).containsEntry("a", "2");
    }

    @Test
    void teamSubmissionRequiresTeamNameAndEveryItemForLeaderAndMembers() {
        Program program = openProgram(ParticipantType.TEAM, List.of(field("e", "이메일"), field("p", "연락처")));
        Application application = startApplication(program);

        assertRejected(() -> submit(application, null, Map.of("e", "a@x.com", "p", "010"), List.of()), "팀 이름");
        assertRejected(() -> submit(application, "팀", Map.of("e", "a@x.com"), List.of()), "대표자 정보가 비어 있습니다: 연락처");
        assertRejected(() -> submit(application, "팀", Map.of("e", "a@x.com", "p", "010"),
                List.of(memberInput(Map.of("e", "b@x.com")))), "팀원 1의 정보가 비어 있습니다: 연락처");

        // 아무것도 입력하지 않은 팀원 행은 버리고, 항목에 없는 키는 저장하지 않는다.
        var detail = submit(application, " 우리팀 ", Map.of("e", " a@x.com ", "p", "010", "unknown", "x"),
                List.of(memberInput(Map.of("e", "b@x.com", "p", "011")), memberInput(Map.of("e", " "))));
        assertThat(detail.status()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(detail.teamName()).isEqualTo("우리팀");
        assertThat(detail.applicantFields()).extracting(ApplicantField::id).containsExactly("e", "p");
        assertThat(detail.applicantInfo().values()).containsExactly(Map.entry("e", "a@x.com"), Map.entry("p", "010"));
        assertThat(detail.applicantInfo().members()).hasSize(1);
        assertThat(detail.applicantInfo().members().get(0).values()).containsEntry("p", "011");
    }

    @Test
    void individualSubmissionRequiresEveryItemAndUsesAccountNameForList() {
        Program program = openProgram(ParticipantType.INDIVIDUAL, List.of(field("s", "학번")));
        Application application = startApplication(program);

        assertRejected(() -> submit(application, null, Map.of(), List.of()), "필수 항목이 비어 있습니다: 학번");
        var detail = submit(application, null, Map.of("s", "2024"), List.of());
        assertThat(detail.status()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(teams.findById(detail.teamId()).orElseThrow().getName()).isEqualTo("지원자계정");
    }

    @Test
    void teamInfoEditUsesTheSameRules() {
        Program program = openProgram(ParticipantType.TEAM, List.of(field("e", "이메일")));
        Application application = startApplication(program);
        submit(application, "팀", Map.of("e", "a@x.com"), List.of());

        assertRejected(() -> applicationService.updateTeamInfo(application.getId(), member.getId(),
                new UpdateTeamInfoRequest("팀", null, null, null, List.of(), Map.of())), "대표자 정보가 비어 있습니다: 이메일");
        var updated = applicationService.updateTeamInfo(application.getId(), member.getId(),
                new UpdateTeamInfoRequest("새 팀", null, null, null, List.of(), Map.of("e", "new@x.com")));
        assertThat(updated.teamName()).isEqualTo("새 팀");
        assertThat(updated.applicantInfo().values()).containsEntry("e", "new@x.com");
    }

    @Test
    void renamingAndReorderingKeepValuesButAnsweredItemsCannotBeDeleted() {
        Program program = openProgram(ParticipantType.TEAM, List.of(field("e", "이메일"), field("p", "연락처")));
        Application application = startApplication(program);
        applicationService.saveDraft(application.getId(), member.getId(),
                new SaveApplicationRequest("팀", null, null, null, List.of(), List.of(), Map.of("e", "a@x.com")));

        // 순서 변경 + 라벨 수정 + 값이 없는 항목(연락처) 삭제 + 새 항목 추가
        var saved = programService.updateProgram(program.getId(), save(List.of(
                new ApplicantField(null, "학교"), new ApplicantField("e", "이메일 주소"))));
        assertThat(saved.program().applicantFields()).extracting(ApplicantField::label).containsExactly("학교", "이메일 주소");
        assertThat(saved.program().applicantFields().get(1).id()).isEqualTo("e");
        assertThat(saved.program().applicantFields().get(0).id()).isNotBlank().isNotEqualTo("p");

        var detail = applicationService.saveDraft(application.getId(), member.getId(),
                new SaveApplicationRequest("팀", null, null, null, List.of(), List.of(), Map.of("e", "a@x.com")));
        assertThat(detail.applicantInfo().values()).containsEntry("e", "a@x.com");

        // 값이 입력된 항목(이메일) 삭제는 막는다.
        assertThatThrownBy(() -> programService.updateProgram(program.getId(), save(List.of())))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.getReason()).contains("이메일 주소");
                });

        // 항목 목록을 보내지 않으면 기존 설정을 유지한다.
        var kept = programService.updateProgram(program.getId(), save(null));
        assertThat(kept.program().applicantFields()).hasSize(2);
    }

    @Test
    void legacyProgramKeepsFixedFieldsAndCannotSwitchOnceApplied() {
        Program program = openProgram(ParticipantType.TEAM, null);
        Application application = startApplication(program);

        assertRejected(() -> applicationService.submit(application.getId(), member.getId(),
                new SaveApplicationRequest("팀", null, null, null, List.of(), List.of())), "팀장(본인) 이름");
        var detail = applicationService.submit(application.getId(), member.getId(),
                new SaveApplicationRequest("팀", "팀장", null, "010-1234-5678",
                        List.of(new SaveApplicationRequest.MemberInput("팀원", null, null)), List.of()));
        assertThat(detail.status()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(detail.applicantFields()).isNull();
        assertThat(detail.applicantInfo().name()).isEqualTo("팀장");
        assertThat(detail.applicantInfo().phone()).isEqualTo("01012345678");
        // 예전 방식의 JSON에는 values 키를 쓰지 않는다.
        assertThat(teams.findById(detail.teamId()).orElseThrow().getApplicantInfo()).doesNotContain("values");

        assertThatThrownBy(() -> programService.updateProgram(program.getId(), save(List.of(new ApplicantField(null, "이메일")))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private Program openProgram(ParticipantType type, List<ApplicantField> fields) {
        Program program = new Program(manager, "applicant-fields", null, ProgramType.MODULE_A,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(3), LocalDate.now().plusDays(4), null);
        program.updateParticipantType(type);
        program.updateApplicantFields(ApplicantFields.toJson(fields));
        program.changeStatus(Status.RECRUITING);
        return programs.save(program);
    }

    private Application startApplication(Program program) {
        Team team = teams.save(new Team(program, "", program.getParticipantType(), null, "", null, null));
        return applications.save(new Application(program, member, team));
    }

    private ApplicationDetailResponse submit(Application application, String teamName, Map<String, String> values,
            List<SaveApplicationRequest.MemberInput> members) {
        return applicationService.submit(application.getId(), member.getId(),
                new SaveApplicationRequest(teamName, null, null, null, members, List.of(), values));
    }

    private SaveProgramRequest save(List<ApplicantField> fields) {
        return new SaveProgramRequest("applicant-fields", null, ProgramType.MODULE_A, null, null,
                null, null, null, null, null, null, null, null, List.of(), List.of(), fields, false);
    }

    private static ApplicantField field(String id, String label) {
        return new ApplicantField(id, label);
    }

    private static SaveApplicationRequest.MemberInput memberInput(Map<String, String> values) {
        return new SaveApplicationRequest.MemberInput(null, null, null, values);
    }

    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String reason) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class, error -> {
            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(error.getReason()).contains(reason);
        });
    }
}
