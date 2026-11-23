package com.example.evalhub.dto;

import java.util.List;

// 결과 조회 권한 심사위원용 응답. 매니저 '평가 결과' 탭이 따로 부르는 프로그램·평가 기준·결과를 한 번에 담는다.
public record AssignmentResultsResponse(
        ProgramResponse program,
        List<CriterionResponse> criteria,
        List<TeamEvaluationResult> results
) {
}
