package com.example.evalhub;

import com.example.evalhub.entity.*;
import com.example.evalhub.repository.ProgramRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "MANAGER")
class ProgramPublicationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ProgramRepository programs;

    // 화면이 보내는 것과 같은 전체 상태 저장 요청. 일정은 null이면 기존 값을 유지한다.
    private static final String PUBLISH_BODY =
            "{\"title\":\"publication-test\",\"type\":\"MODULE_A\",\"criteria\":[],\"formFields\":[],\"publish\":true}";

    private ResultActions publish(Program program) throws Exception {
        return mvc.perform(put("/api/programs/" + program.getId()).with(csrf())
                .contentType("application/json").content(PUBLISH_BODY));
    }

    private Program draft(long applicationStart, long applicationEnd, long evaluationStart, long evaluationEnd) {
        Program program = new Program(null, "publication-test", null, ProgramType.MODULE_A,
                null, null, null, null, null);
        Instant now = Instant.now();
        program.updateSchedule(now.plusSeconds(applicationStart), now.plusSeconds(applicationEnd),
                now.plusSeconds(evaluationStart), now.plusSeconds(evaluationEnd));
        program.changeStatus(Status.DRAFT);
        return programs.saveAndFlush(program);
    }

    @ParameterizedTest
    @CsvSource({"3600,7200,10800,14400,SCHEDULED", "-3600,3600,7200,10800,RECRUITING",
            "-10800,-7200,3600,7200,PENDING_EVALUATION", "-10800,-7200,-3600,3600,ACTIVE",
            "-14400,-10800,-7200,-3600,INACTIVE"})
    void publicationCalculatesStatusFromPeriods(long start, long end, long evaluationStart, long evaluationEnd, String expected) throws Exception {
        Program program = draft(start, end, evaluationStart, evaluationEnd);
        publish(program)
                .andExpect(status().isOk()).andExpect(jsonPath("$.program.status").value(expected));
        assertThat(programs.findById(program.getId()).orElseThrow().getStatus()).isEqualTo(Status.valueOf(expected));
    }

    @Test void incompleteDraftCannotBePublished() throws Exception {
        Program program = new Program(null, "incomplete-draft", null, ProgramType.MODULE_A,
                null, null, null, null, null);
        program.changeStatus(Status.DRAFT);
        programs.saveAndFlush(program);
        publish(program)
                .andExpect(status().isBadRequest());
        assertThat(program.getStatus()).isEqualTo(Status.DRAFT);
    }

    @Test void invalidPeriodOrderCannotBePublished() throws Exception {
        Program program = draft(7200, 3600, 10800, 14400);
        publish(program)
                .andExpect(status().isBadRequest());
        assertThat(program.getStatus()).isEqualTo(Status.DRAFT);
    }

    @Test void publishedProgramCannotBePublishedAgain() throws Exception {
        Program program = draft(3600, 7200, 10800, 14400);
        publish(program)
                .andExpect(status().isOk());
        publish(program)
                .andExpect(status().isConflict());
        assertThat(program.getStatus()).isEqualTo(Status.SCHEDULED);
    }

    @Test void oldStatusEndpointIsRemoved() throws Exception {
        Program program = draft(3600, 7200, 10800, 14400);
        mvc.perform(post("/api/programs/" + program.getId() + "/status").with(csrf())
                        .contentType("application/json").content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isNotFound());
        assertThat(program.getStatus()).isEqualTo(Status.DRAFT);
    }

    @Test void oldPublishEndpointIsRemoved() throws Exception {
        Program program = draft(3600, 7200, 10800, 14400);
        mvc.perform(post("/api/programs/" + program.getId() + "/publish").with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(program.getStatus()).isEqualTo(Status.DRAFT);
    }

    @Test void createWithPublishOpensInOneRequest() throws Exception {
        Instant now = Instant.now();
        String body = "{\"title\":\"create-and-publish\",\"type\":\"MODULE_A\","
                + "\"applicationStartAt\":\"" + now.plusSeconds(3600) + "\",\"applicationDeadlineAt\":\"" + now.plusSeconds(7200) + "\","
                + "\"evaluationStartAt\":\"" + now.plusSeconds(10800) + "\",\"evaluationDeadlineAt\":\"" + now.plusSeconds(14400) + "\","
                + "\"criteria\":[{\"name\":\"사업성\",\"maxScore\":30}],"
                + "\"formFields\":[{\"label\":\"소개\",\"fieldType\":\"LONG_TEXT\",\"required\":true,\"options\":[]}],"
                + "\"publish\":true}";
        mvc.perform(post("/api/programs").with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.program.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.criteria[0].id").isNumber())
                .andExpect(jsonPath("$.formFields[0].id").isNumber());
    }

    @Test void publishWithoutCriteriaIsAllowed() throws Exception {
        Program program = draft(3600, 7200, 10800, 14400);
        publish(program).andExpect(status().isOk()).andExpect(jsonPath("$.criteria.length()").value(0));
    }

    @Test void missingCriteriaOrFormFieldsListIsRejected() throws Exception {
        mvc.perform(post("/api/programs").with(csrf()).contentType("application/json")
                        .content("{\"title\":\"missing-lists\",\"type\":\"MODULE_A\",\"formFields\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/programs").with(csrf()).contentType("application/json")
                        .content("{\"title\":\"missing-lists\",\"type\":\"MODULE_A\",\"criteria\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test void errorReasonIsExposedAsMessage() throws Exception {
        Program program = new Program(null, "incomplete-draft", null, ProgramType.MODULE_A,
                null, null, null, null, null);
        program.changeStatus(Status.DRAFT);
        programs.saveAndFlush(program);
        publish(program).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Application deadline is required to publish a program."));
    }

    @ParameterizedTest @ValueSource(strings = {"MEMBER", "EVALUATOR"})
    void otherRolesCannotPublish(String role) throws Exception {
        Program program = draft(3600, 7200, 10800, 14400);
        mvc.perform(put("/api/programs/" + program.getId()).with(csrf()).with(user("other").roles(role))
                        .contentType("application/json").content(PUBLISH_BODY))
                .andExpect(status().isForbidden());
        assertThat(program.getStatus()).isEqualTo(Status.DRAFT);
    }

    @Test @WithMockUser(roles = "SUPER_ADMIN") void superAdminCanPublish() throws Exception {
        Program program = draft(3600, 7200, 10800, 14400);
        publish(program)
                .andExpect(status().isOk()).andExpect(jsonPath("$.program.status").value("SCHEDULED"));
    }
}
