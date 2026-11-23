package com.example.evalhub.dto;

import com.example.evalhub.entity.ParticipantType;
import com.example.evalhub.entity.Program;
import com.example.evalhub.entity.ProgramType;
import com.example.evalhub.entity.Status;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProgramResponse(
        Long id,
        Long managerId,
        String title,
        String description,
        ProgramType type,
        Status status,
        LocalDate applicationStart,
        LocalDate applicationDeadline,
        LocalDate evaluationStart,
        LocalDate evaluationDeadline,
        String eligibilityCriteria,
        boolean attachmentsEnabled,
        Integer maxAttachments,
        ParticipantType participantType,
        boolean bonusEnabled,
        BigDecimal maxBonus,
        long teamCount,
        long evaluatorCount,
        long submittedCount,
        ProgramScheduleResponse schedule,
        java.util.List<BonusItemResponse> bonusItems,
        boolean evaluationStarted,
        // 지원자 인적사항 항목. null이면 예전 고정 항목 방식(이름·이메일·연락처, 팀원 명단)이다.
        java.util.List<ApplicantField> applicantFields
) {

    public static ProgramResponse from(Program program) {
        return from(program, 0L, 0L, 0L);
    }

    public static ProgramResponse from(Program program, long teamCount, long evaluatorCount, long submittedCount) {
        ProgramScheduleResponse schedule = ProgramScheduleResponse.from(program);
        return new ProgramResponse(
                program.getId(),
                program.getManager() == null ? null : program.getManager().getId(),
                program.getTitle(),
                program.getDescription(),
                program.getType(),
                schedule.periodStatus(),
                program.getApplicationStart(),
                program.getApplicationDeadline(),
                program.getEvaluationStart(),
                program.getEvaluationDeadline(),
                program.getEligibilityCriteria(),
                program.isAttachmentsEnabled(),
                program.getMaxAttachments(),
                program.getParticipantType(),
                program.isBonusEnabled(),
                program.getMaxBonus(),
                teamCount,
                evaluatorCount,
                submittedCount,
                schedule,
                program.getBonusItems().stream().map(BonusItemResponse::from).toList(),
                program.isEvaluationStarted(),
                com.example.evalhub.util.ApplicantFields.parse(program.getApplicantFields())
        );
    }
}
