package com.example.evalhub.dto;

// canEvaluate / canViewResults를 둘 다 생략하면 예전처럼 심사 권한만 준다.
public record CreateAssignmentRequest(
        Long evaluatorId,
        Boolean canEvaluate,
        Boolean canViewResults
) {
    public CreateAssignmentRequest(Long evaluatorId) {
        this(evaluatorId, null, null);
    }

    public boolean evaluatePermission() {
        return canEvaluate == null ? canViewResults == null : canEvaluate;
    }

    public boolean viewResultsPermission() {
        return Boolean.TRUE.equals(canViewResults);
    }
}
