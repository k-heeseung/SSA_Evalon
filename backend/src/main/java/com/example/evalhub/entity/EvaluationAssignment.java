package com.example.evalhub.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "evaluation_assignments")
@Getter
@NoArgsConstructor
public class EvaluationAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluator_id", nullable = false)
    private User evaluator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status = AssignmentStatus.ASSIGNED;

    // 심사 권한: 팀 목록·채점 화면과 첨부 PDF 열람. 결과 집계의 심사위원 열은 이 권한이 있는 배정만 센다.
    @Column(name = "can_evaluate", nullable = false)
    private boolean canEvaluate = true;

    // 결과 조회 권한: 프로그램의 평가 결과(매니저 '평가 결과' 탭 내용)를 읽기 전용으로 본다.
    @Column(name = "can_view_results", nullable = false)
    private boolean canViewResults = false;

    public EvaluationAssignment(Program program, User evaluator) {
        this(program, evaluator, true, false);
    }

    public EvaluationAssignment(Program program, User evaluator, boolean canEvaluate, boolean canViewResults) {
        this.program = program;
        this.evaluator = evaluator;
        changePermissions(canEvaluate, canViewResults);
    }

    // 둘 다 끄는 것은 배정 해제이므로 여기서는 허용하지 않는다. (DB check 제약과 같은 규칙)
    public void changePermissions(boolean canEvaluate, boolean canViewResults) {
        if (!canEvaluate && !canViewResults) {
            throw new IllegalArgumentException("At least one assignment permission is required.");
        }
        this.canEvaluate = canEvaluate;
        this.canViewResults = canViewResults;
    }

    public void markInProgress() {
        if (this.status == AssignmentStatus.ASSIGNED) {
            this.status = AssignmentStatus.IN_PROGRESS;
        }
    }

    public void markSubmitted() {
        this.status = AssignmentStatus.SUBMITTED;
    }

    // 팀별 제출 현황에 따라 배정 상태를 다시 계산한다.
    // 모든 팀 제출 완료 → SUBMITTED, 일부라도 저장/제출 → IN_PROGRESS, 아무 활동 없음 → ASSIGNED
    public void updateProgress(boolean allTeamsSubmitted, boolean anyActivity) {
        if (allTeamsSubmitted) {
            this.status = AssignmentStatus.SUBMITTED;
        } else if (anyActivity) {
            this.status = AssignmentStatus.IN_PROGRESS;
        } else {
            this.status = AssignmentStatus.ASSIGNED;
        }
    }
}
