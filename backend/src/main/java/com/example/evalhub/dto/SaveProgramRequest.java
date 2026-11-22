package com.example.evalhub.dto;

import com.example.evalhub.entity.ParticipantType;
import com.example.evalhub.entity.ProgramType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

// 프로그램 생성(POST /api/programs)과 저장(PUT /api/programs/{id})이 함께 쓰는 요청.
// 화면의 전체 상태를 한 번에 보내고, 서버가 하나의 트랜잭션으로 프로그램·평가 기준·지원 양식 항목을 저장한다.
//
// criteria / formFields / bonusItems는 배열 순서가 곧 displayOrder다.
// 항목의 id가 있으면 기존 행을 제자리에서 수정하고, null이면 새로 추가하며, 요청에 없는 기존 행은 삭제한다.
// criteria와 formFields는 필수다. 비우려면 빈 배열을 보낸다. (누락을 '전부 삭제'로 해석하지 않기 위함)
public record SaveProgramRequest(
        String title,
        String description,
        ProgramType type,
        ParticipantType participantType,
        String eligibilityCriteria,
        OffsetDateTime applicationStartAt,
        OffsetDateTime applicationDeadlineAt,
        OffsetDateTime evaluationStartAt,
        OffsetDateTime evaluationDeadlineAt,
        Boolean attachmentsEnabled,
        Integer maxAttachments,
        Boolean bonusEnabled,
        List<BonusItemRequest> bonusItems,
        List<Criterion> criteria,
        List<FormField> formFields,
        boolean publish
) {
    public record Criterion(Long id, String name, String description, BigDecimal maxScore) {
    }

    public record FormField(Long id, String label, String fieldType, boolean required, List<String> options) {
    }
}
