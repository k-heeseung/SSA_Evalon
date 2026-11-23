package com.example.evalhub;

import com.example.evalhub.dto.*;
import com.example.evalhub.entity.*;
import com.example.evalhub.repository.*;
import com.example.evalhub.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CriterionMaxScoreIntegrationTests {
    @Autowired ProgramManagementService manager;
    @Autowired EvaluationService evaluator;
    @Autowired ApplicationService applicant;
    @Autowired ProgramRepository programs;
    @Autowired TeamRepository teams;
    @Autowired UserRepository users;
    @Autowired EvaluationAssignmentRepository assignments;
    @Autowired ApplicationRepository applications;
    @Autowired EvaluationCriterionRepository criteria;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    Program program;
    Long programId, teamId, evaluatorId, assignmentId;

    @BeforeEach void setup() {
        program = programs.save(new Program(null, "max-score-test", null, ProgramType.MODULE_A,
            LocalDate.now().minusDays(2), LocalDate.now().minusDays(1),
            LocalDate.now().minusDays(1), LocalDate.now().plusDays(2), null));
        programId = program.getId();
        teamId = teams.save(new Team(program,"team",ParticipantType.TEAM,null,"team",null,null)).getId();
        var user = users.save(new User(UUID.randomUUID()+"@example.com","encoded",Role.EVALUATOR));
        evaluatorId = user.getId();
        assignmentId = assignments.save(new EvaluationAssignment(program,user)).getId();
        em.flush();
    }
    // 화면 전체 상태 저장 요청. 일정은 null이면 기존 값을 유지한다.
    SaveProgramRequest request(List<SaveProgramRequest.Criterion> cs, List<SaveProgramRequest.FormField> fs) {
        return new SaveProgramRequest("max-score-test",null,ProgramType.MODULE_A,null,null,
            null,null,null,null,null,null,null,null,cs,fs,false);
    }
    SaveProgramRequest.Criterion item(Long id, String name, String max) {
        return new SaveProgramRequest.Criterion(id,name,null,new BigDecimal(max));
    }
    SaveProgramRequest.Criterion keep(CriterionResponse c) {
        return new SaveProgramRequest.Criterion(c.id(),c.name(),c.description(),c.maxScore());
    }
    List<CriterionResponse> save(SaveProgramRequest.Criterion... cs) {
        return manager.updateProgram(programId,request(List.of(cs),List.of())).criteria();
    }
    SaveEvaluationRequest scores(List<CriterionResponse> cs, String... values) {
        var scores = new ArrayList<EvaluationScoreRequest>();
        for(int i=0;i<values.length;i++) scores.add(new EvaluationScoreRequest(cs.get(i).id(),new BigDecimal(values[i])));
        return new SaveEvaluationRequest("comment",scores);
    }
    @Test void variableMaxScoresPersistAndTotalsAreSimpleSums() {
        var cs=save(item(null,"item1","40"),item(null,"item2","35"),item(null,"item3","25"));
        assertThat(evaluator.saveDraft(assignmentId,teamId,evaluatorId,scores(cs,"32")).totalScore()).isEqualByComparingTo("32");
        assertThat(evaluator.submit(assignmentId,teamId,evaluatorId,scores(cs,"32","28","20")).totalScore()).isEqualByComparingTo("80");
        em.flush(); em.clear();
        assertThat(manager.findCriteria(programId)).extracting(CriterionResponse::maxScore)
            .containsExactly(new BigDecimal("40.00"),new BigDecimal("35.00"),new BigDecimal("25.00"));
        assertThat(evaluator.getEvaluationResults(programId).get(0).evaluations().get(0).submission().totalScore()).isEqualByComparingTo("80");
        assertThat(evaluator.submit(assignmentId,teamId,evaluatorId,scores(cs,"40","0","25")).totalScore()).isEqualByComparingTo("65");
    }
    @ParameterizedTest @ValueSource(strings={"-1","40.01","1.01","1.001"})
    void rejectsInvalidScoresWithoutStartingEvaluation(String value) {
        var cs=save(item(null,"item1","40"));
        assertThatThrownBy(()->evaluator.saveDraft(assignmentId,teamId,evaluatorId,scores(cs,value)))
            .isInstanceOf(ResponseStatusException.class);
        assertThat(program.isEvaluationStarted()).isFalse();
    }
    @Test void incompleteFinalSubmissionIsRejected() {
        var cs=save(item(null,"item1","40"),item(null,"item2","35"));
        assertThatThrownBy(()->evaluator.submit(assignmentId,teamId,evaluatorId,scores(cs,"32")))
            .isInstanceOf(ResponseStatusException.class);
    }
    @Test void reorderDeleteAndInsertInOneSaveRespectsUniqueOrder() {
        var cs=save(item(null,"a","10"),item(null,"b","20"),item(null,"c","30"));
        // c를 맨 앞으로, a 삭제, 새 항목을 a가 있던 순서 값 자리에 추가 → unique(program_id, display_order)에 걸리면 안 된다.
        var saved=save(keep(cs.get(2)),item(null,"new","5"),keep(cs.get(1)));
        assertThat(saved).extracting(CriterionResponse::name).containsExactly("c","new","b");
        assertThat(saved).extracting(CriterionResponse::displayOrder).containsExactly(1,2,3);
        assertThat(saved.get(0).id()).isEqualTo(cs.get(2).id());
        assertThat(saved.get(2).id()).isEqualTo(cs.get(1).id());
    }
    @Test void firstDraftLocksStructureAndMaxButAllowsTextChanges() {
        var cs=save(item(null,"item","40"),item(null,"second","20"));
        var c=cs.get(0); var second=cs.get(1);
        evaluator.saveDraft(assignmentId,teamId,evaluatorId,scores(cs,"0"));
        assertThatThrownBy(()->save(keep(c),keep(second),item(null,"added","20"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->save(keep(c))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->save(item(c.id(),"item","50"),keep(second))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->save(keep(second),keep(c))).isInstanceOf(ResponseStatusException.class);
        save(new SaveProgramRequest.Criterion(c.id(),"renamed","description",new BigDecimal("40")),keep(second));
        em.flush(); em.clear();
        assertThat(manager.findCriteria(programId)).first().satisfies(saved->{
            assertThat(saved.name()).isEqualTo("renamed"); assertThat(saved.maxScore()).isEqualByComparingTo("40");
        });
    }
    String body(String criteriaJson) {
        return "{\"title\":\"max-score-test\",\"type\":\"MODULE_A\",\"criteria\":"+criteriaJson+",\"formFields\":[]}";
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","0.01","0.001","1000000"})
    @WithMockUser(roles="MANAGER")
    void apiRejectsInvalidMax(String max) throws Exception {
        mvc.perform(put("/api/programs/{id}",programId).with(csrf()).contentType("application/json")
            .content(body("[{\"name\":\"invalid\",\"maxScore\":"+max+"}]")))
            .andExpect(status().isBadRequest());
    }
    @Test @WithMockUser(roles="MANAGER")
    void apiAcceptsDecimalsAndRejectsMissingMaxAndLockedChanges() throws Exception {
        mvc.perform(put("/api/programs/{id}",programId).with(csrf()).contentType("application/json")
            .content(body("[{\"name\":\"decimal\",\"maxScore\":40.2}]")))
            .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.criteria[0].maxScore").value(40.2));
        var c=manager.findCriteria(programId).get(0);
        mvc.perform(put("/api/programs/{id}",programId).with(csrf()).contentType("application/json")
            .content(body("[{\"id\":"+c.id()+",\"name\":\"decimal\",\"maxScore\":40.2},{\"name\":\"missing\"}]")))
            .andExpect(status().isBadRequest());
        evaluator.saveDraft(assignmentId,teamId,evaluatorId,scores(List.of(c),"40.2"));
        mvc.perform(put("/api/programs/{id}",programId).with(csrf()).contentType("application/json")
            .content(body("[]"))).andExpect(status().isConflict());
        mvc.perform(put("/api/programs/{id}",programId).with(csrf()).contentType("application/json")
            .content(body("[{\"id\":"+c.id()+",\"name\":\"decimal\",\"maxScore\":40.2},{\"name\":\"locked\",\"maxScore\":20}]")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("심사가 시작된 프로그램의 배점과 항목 구성은 변경할 수 없습니다."));
    }
    @Test void noCustomFieldsAllowsApplicationSubmission() {
        program.updateSchedule(java.time.Instant.now().minusSeconds(60),java.time.Instant.now().plusSeconds(3600),
            java.time.Instant.now().plusSeconds(7200),java.time.Instant.now().plusSeconds(10800));
        program.updateParticipantType(ParticipantType.INDIVIDUAL);
        var member=users.save(new User(UUID.randomUUID()+"@example.com","encoded",Role.MEMBER));
        var team=teams.save(new Team(program,"applicant",ParticipantType.INDIVIDUAL,null,"applicant",null,null));
        var application=applications.save(new Application(program,member,team));
        em.flush(); // 저장 API가 프로그램 행을 잠그며 refresh하므로 변경을 먼저 반영한다.
        assertThat(manager.updateProgram(programId,request(List.of(),List.of())).formFields()).isEmpty();
        var request=new SaveApplicationRequest(null,"applicant",member.getEmail(),null,List.of(),List.of());
        assertThat(applicant.submit(application.getId(),member.getId(),request).status()).isEqualTo(ApplicationStatus.SUBMITTED);
    }
}
