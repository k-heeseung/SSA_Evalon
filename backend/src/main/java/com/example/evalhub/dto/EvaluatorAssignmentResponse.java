package com.example.evalhub.dto;

import com.example.evalhub.entity.AssignmentStatus;
import com.example.evalhub.entity.Status;

import java.time.LocalDate;

// 평가자 대시보드에서 배정(프로그램)별 채점 진행 현황을 보여주기 위한 응답이다.
// status 는 팀별 제출 현황으로부터 계산된 집계 상태다.
// 결과 조회 권한만 있는 배정(canEvaluate = false)은 팀 수·제출 수를 0으로 내려준다.
public record EvaluatorAssignmentResponse(
        Long id,
        Long programId,
        String programTitle,
        LocalDate evaluationStart,
        LocalDate evaluationDeadline,
        AssignmentStatus status,
        int teamCount,
        int submittedCount,
        int draftCount,
        ProgramScheduleResponse schedule,
        boolean canEvaluate,
        boolean canViewResults,
        Status programStatus,
        // 결과 조회 권한이 있고 프로그램이 평가 진행(ACTIVE) 또는 완료(INACTIVE) 상태일 때 true
        boolean resultsAvailable
) {
}
