package com.example.evalhub.dto;

// 배정 매트릭스에서 배정은 그대로 두고 권한만 바꿀 때 쓴다. 둘 다 false면 400 (해제는 DELETE /api/assignments/{id}).
public record UpdateAssignmentPermissionsRequest(
        Boolean canEvaluate,
        Boolean canViewResults
) {
}
