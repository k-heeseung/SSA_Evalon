package com.example.evalhub.security;

import com.example.evalhub.entity.EvaluationAssignment;
import com.example.evalhub.repository.EvaluationAssignmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Predicate;

// 배정 단위 권한(심사 / 결과 조회)을 @PreAuthorize에서 확인한다.
//   @PreAuthorize("hasRole('EVALUATOR') and @assignmentAccess.canEvaluate(#assignmentId, principal)")
// 역할은 hasRole이, '본인 배정이고 해당 권한이 켜져 있는가'는 이 빈이 본다. 서비스 계층은 권한을 다시 확인하지 않는다.
// 남의 배정이거나 권한이 꺼져 있으면 false → 403.
// 배정이 없으면 true를 돌려 서비스가 404를 내게 한다. (해제된 배정을 열면 화면이 '배정 해제'로 안내하는 기존 동작 유지,
// AccountDeletionService.canManagerDelete와 같은 방식)
@Component("assignmentAccess")
public class AssignmentAccess {

    private final EvaluationAssignmentRepository assignments;

    public AssignmentAccess(EvaluationAssignmentRepository assignments) {
        this.assignments = assignments;
    }

    @Transactional(readOnly = true)
    public boolean canEvaluate(Long assignmentId, Object principal) {
        return owns(assignmentId, principal, EvaluationAssignment::isCanEvaluate);
    }

    @Transactional(readOnly = true)
    public boolean canViewResults(Long assignmentId, Object principal) {
        return owns(assignmentId, principal, EvaluationAssignment::isCanViewResults);
    }

    private boolean owns(Long assignmentId, Object principal, Predicate<EvaluationAssignment> permission) {
        if (assignmentId == null || !(principal instanceof AuthenticatedUser user)) {
            return false;
        }
        return assignments.findById(assignmentId)
                .map(assignment -> assignment.getEvaluator().getId().equals(user.id()) && permission.test(assignment))
                .orElse(true);
    }
}
