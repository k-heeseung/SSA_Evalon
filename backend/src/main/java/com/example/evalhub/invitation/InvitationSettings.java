package com.example.evalhub.invitation;

import com.example.evalhub.entity.Role;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "evalhub.mail")
public record InvitationSettings(boolean enabled, String clientId, String clientSecret,
        String refreshToken, String senderEmail, String loginUrl, String encryptionKey,
        Duration retention, int maxAttempts) {
    public InvitationSettings {
        if (retention == null || retention.isNegative() || retention.isZero() || maxAttempts < 1)
            throw new IllegalArgumentException("Mail retention and max-attempts must be positive.");
        URI login = URI.create(loginUrl);
        if (login.getHost() == null || !("http".equals(login.getScheme()) || "https".equals(login.getScheme())))
            throw new IllegalArgumentException("Mail login-url must be an absolute HTTP(S) URL.");
        if (enabled && (blank(clientId) || blank(clientSecret) || blank(refreshToken)
                || !InvitationSecrets.validEmail(senderEmail)))
            throw new IllegalArgumentException("Gmail OAuth credentials and sender-email are required when mail is enabled.");
    }
    // 로그인 화면(LoginPage.jsx)의 역할 탭 key에 맞춘 링크. 설정값에 role이 이미 있으면 바꿔 넣는다.
    public String loginUrlFor(Role role) {
        return UriComponentsBuilder.fromUriString(loginUrl)
                .replaceQueryParam("role", role == Role.MANAGER ? "manager" : "evaluator")
                .build().toUriString();
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    // Never render credentials through generated record toString().
    @Override public String toString() { return "InvitationSettings[enabled=" + enabled + "]"; }
}

@Configuration
@EnableConfigurationProperties(InvitationSettings.class)
class InvitationConfiguration {
    @org.springframework.context.annotation.Bean
    org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("evalhub-scheduled-");
        return scheduler;
    }
    @org.springframework.context.annotation.Bean
    org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler invitationTaskScheduler() {
        var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("invitation-mail-");
        return scheduler;
    }
}
