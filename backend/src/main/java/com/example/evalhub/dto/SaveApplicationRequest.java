package com.example.evalhub.dto;

import java.util.List;
import java.util.Map;

// 임시저장 / 제출 시 지원서 전체 내용을 전송한다. answers는 화면에 표시된 전체 항목을 그대로 보낸다.
// participantName/Email/Phone과 members[].name/email/phone은 예전 고정 항목 방식 프로그램에서 쓴다.
// values와 members[].values는 매니저가 인적사항 항목을 직접 정한 프로그램에서 쓰며, 키는 항목 id다.
public record SaveApplicationRequest(
        String teamName,
        String participantName,
        String participantEmail,
        String participantPhone,
        List<MemberInput> members,
        List<AnswerInput> answers,
        Map<String, String> values
) {

    public SaveApplicationRequest(String teamName, String participantName, String participantEmail,
            String participantPhone, List<MemberInput> members, List<AnswerInput> answers) {
        this(teamName, participantName, participantEmail, participantPhone, members, answers, null);
    }

    public record AnswerInput(
            Long formFieldId,
            String value
    ) {
    }

    public record MemberInput(
            String name,
            String email,
            String phone,
            Map<String, String> values
    ) {
        public MemberInput(String name, String email, String phone) {
            this(name, email, phone, null);
        }
    }
}
