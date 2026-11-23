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
        List<ApplicantField> applicantFields,
        boolean publish
) {
    // applicantFields(지원자 인적사항 항목)는 POST에서 null이면 예전 고정 항목 방식, PUT에서 null이면 기존 설정 유지.
    // 배열 순서가 곧 표시 순서다. id가 null이면 새 항목으로 서버가 id를 붙이고, 요청에 없는 기존 항목은 삭제한다.
    public SaveProgramRequest(String title, String description, ProgramType type, ParticipantType participantType,
            String eligibilityCriteria, OffsetDateTime applicationStartAt, OffsetDateTime applicationDeadlineAt,
            OffsetDateTime evaluationStartAt, OffsetDateTime evaluationDeadlineAt, Boolean attachmentsEnabled,
            Integer maxAttachments, Boolean bonusEnabled, List<BonusItemRequest> bonusItems,
            List<Criterion> criteria, List<FormField> formFields, boolean publish) {
        this(title, description, type, participantType, eligibilityCriteria, applicationStartAt, applicationDeadlineAt,
                evaluationStartAt, evaluationDeadlineAt, attachmentsEnabled, maxAttachments, bonusEnabled, bonusItems,
                criteria, formFields, null, publish);
    }

    public record Criterion(Long id, String name, String description, BigDecimal maxScore) {
    }

    public record FormField(Long id, String label, String fieldType, boolean required, List<String> options) {
    }
}
