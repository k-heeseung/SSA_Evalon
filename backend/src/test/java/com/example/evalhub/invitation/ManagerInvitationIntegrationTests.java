package com.example.evalhub.invitation;

import com.example.evalhub.entity.Role;
import com.example.evalhub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "evalhub.mail.enabled=true", "evalhub.mail.client-id=test", "evalhub.mail.client-secret=test",
    "evalhub.mail.refresh-token=test", "evalhub.mail.sender-email=sender@example.com",
    "evalhub.mail.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "evalhub.mail.max-attempts=2", "evalhub.mail.poll-interval-ms=3600000"
})
@AutoConfigureMockMvc
@Transactional
class ManagerInvitationIntegrationTests {
    @Autowired EvaluatorInvitationService service;
    @Autowired InvitationSecrets secrets;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean InvitationSender sender;

    private String body(String email, String role, boolean sendInvitation) {
        return "{\"email\":\"" + email + "\",\"password\":\"Temp-Pass-123\",\"role\":\"" + role
                + "\",\"name\":\"매니저\",\"department\":\"학과\",\"sendInvitation\":" + sendInvitation + "}";
    }

    private long createManager(boolean sendInvitation) throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/users").with(user("admin").roles("SUPER_ADMIN")).with(csrf())
                        .contentType("application/json").content(body(email, "MANAGER", sendInvitation)))
                .andExpect(status().isOk());
        return users.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    private long invitationCount(long userId) {
        return jdbc.queryForObject("select count(*) from evaluator_invitations where user_id=?", Long.class, userId);
    }

    @Test void managerCreationQueuesInvitationAndSendsManagerMail() throws Exception {
        long id = createManager(true);
        String encrypted = jdbc.queryForObject("select encrypted_password from evaluator_invitations where user_id=?", String.class, id);
        assertThat(secrets.decrypt(encrypted, id)).isEqualTo("Temp-Pass-123");
        assertThat(service.get(id, Role.MANAGER).status()).isEqualTo("PENDING");

        mvc.perform(get("/api/manager-invitations").with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitations[?(@.userId == " + id + ")]").exists());
        // 심사위원 화면에는 매니저 메일이 섞이지 않는다.
        assertThat(service.list().invitations()).extracting(EvaluatorInvitationService.InvitationView::userId).doesNotContain(id);

        when(sender.send(anyString(), anyString(), any())).thenReturn("gmail-id");
        service.tick();
        verify(sender).send(users.findById(id).orElseThrow().getEmail(), "Temp-Pass-123", Role.MANAGER);
        assertThat(service.get(id, Role.MANAGER).status()).isEqualTo("SENT");
    }

    @Test void uncheckedOptionCreatesAccountWithoutInvitation() throws Exception {
        long id = createManager(false);
        assertThat(invitationCount(id)).isZero();
    }

    @Test void invitationIsOnlyAllowedForManagers() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/users").with(user("admin").roles("SUPER_ADMIN")).with(csrf())
                        .contentType("application/json").content(body(email, "MEMBER", true)))
                .andExpect(status().isBadRequest());
        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test void managerInvitationEndpointsAreSuperAdminOnly() throws Exception {
        long id = createManager(true);
        mvc.perform(get("/api/manager-invitations").with(user("manager").roles("MANAGER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/managers/" + id + "/invitation/retry").with(user("manager").roles("MANAGER")).with(csrf()))
                .andExpect(status().isForbidden());
        // 심사위원용 재시도 API로 매니저 메일을 건드릴 수 없다.
        mvc.perform(post("/api/evaluators/" + id + "/invitation/retry").with(user("manager").roles("MANAGER")).with(csrf()))
                .andExpect(status().isNotFound());
    }
}
