package com.example.evalhub;

import com.example.evalhub.entity.Program;
import com.example.evalhub.entity.ProgramType;
import com.example.evalhub.entity.Status;
import com.example.evalhub.repository.EvaluationCriterionRepository;
import com.example.evalhub.repository.ProgramRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 프로그램 저장이 한 트랜잭션으로 묶였는지 확인한다.
// 테스트 트랜잭션 안에서는 롤백 여부를 볼 수 없으므로 이 클래스는 @Transactional을 쓰지 않고 직접 정리한다.
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "MANAGER")
class ProgramSaveAtomicityIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ProgramRepository programs;
    @Autowired EvaluationCriterionRepository criteria;

    private final List<Long> created = new ArrayList<>();

    @AfterEach void cleanUp() {
        created.forEach(programs::deleteById);
    }

    private long countByTitle(String title) {
        return programs.findAll().stream().filter(p -> title.equals(p.getTitle())).count();
    }

    @Test void failedPublishOnCreateLeavesNoDraftBehind() throws Exception {
        String title = "atomic-" + UUID.randomUUID();
        // 기준·양식은 정상이지만 개설에 필요한 마감일이 없어 마지막 단계에서 실패한다.
        String body = "{\"title\":\"" + title + "\",\"type\":\"MODULE_A\","
                + "\"criteria\":[{\"name\":\"사업성\",\"maxScore\":30}],"
                + "\"formFields\":[{\"label\":\"소개\",\"fieldType\":\"LONG_TEXT\",\"required\":true,\"options\":[]}],"
                + "\"publish\":true}";
        mvc.perform(post("/api/programs").with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        assertThat(countByTitle(title)).isZero();
    }

    @Test void failedFormFieldOnUpdateRollsBackProgramAndCriteria() throws Exception {
        Program draft = new Program(null, "atomic-draft", null, ProgramType.MODULE_A, null, null, null, null, null);
        draft.changeStatus(Status.DRAFT);
        Long id = programs.saveAndFlush(draft).getId();
        created.add(id);

        // 프로그램 명과 평가 기준은 정상이지만 양식 항목 유형이 잘못되어 실패한다.
        String body = "{\"title\":\"renamed\",\"type\":\"MODULE_A\","
                + "\"criteria\":[{\"name\":\"사업성\",\"maxScore\":30}],"
                + "\"formFields\":[{\"label\":\"소개\",\"fieldType\":\"UNKNOWN\",\"required\":true,\"options\":[]}]}";
        mvc.perform(put("/api/programs/" + id).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());

        assertThat(programs.findById(id).orElseThrow().getTitle()).isEqualTo("atomic-draft");
        assertThat(criteria.findByProgramIdOrderByDisplayOrderAsc(id)).isEmpty();
    }
}
