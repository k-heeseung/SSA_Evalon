package com.example.evalhub.dto;

import java.util.List;
import java.util.Map;

// 팀 정보 탭에서 팀/본인 정보만 수정할 때 사용한다. 답변·첨부·제출 상태는 건드리지 않는다.
// 팀원 명단은 지원서 저장과 동일한 형식(SaveApplicationRequest.MemberInput)을 재사용한다.
// values는 매니저가 인적사항 항목을 직접 정한 프로그램에서 본인(대표자)의 항목 id별 값이다.
public record UpdateTeamInfoRequest(
        String teamName,
        String participantName,
        String participantEmail,
        String participantPhone,
        List<SaveApplicationRequest.MemberInput> members,
        Map<String, String> values
) {
    public UpdateTeamInfoRequest(String teamName, String participantName, String participantEmail,
            String participantPhone, List<SaveApplicationRequest.MemberInput> members) {
        this(teamName, participantName, participantEmail, participantPhone, members, null);
    }
}
