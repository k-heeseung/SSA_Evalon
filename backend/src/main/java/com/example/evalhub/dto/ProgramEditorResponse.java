package com.example.evalhub.dto;

import java.util.List;

// 프로그램 생성·저장 API의 응답. 저장된 평가 기준과 지원 양식 항목의 id를 함께 돌려줘
// 프론트가 다음 저장 때 같은 항목을 새로 만들지 않고 제자리 수정하게 한다.
// 목록 조회(ProgramResponse)에는 싣지 않는다. 프로그램마다 쿼리가 늘어나기 때문이다.
public record ProgramEditorResponse(
        ProgramResponse program,
        List<CriterionResponse> criteria,
        List<FormFieldResponse> formFields
) {
}
