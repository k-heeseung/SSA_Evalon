package com.example.evalhub.dto;

import com.example.evalhub.entity.Role;
import com.example.evalhub.entity.Status;
import com.example.evalhub.entity.User;

// assignmentCount: 심사 권한이 있는 배정 수, resultAssignmentCount: 결과 조회 권한이 있는 배정 수 (심사위원 목록용)
public record UserResponse(
        Long id,
        String email,
        Role role,
        Status status,
        String name,
        String phone,
        String department,
        long assignmentCount,
        boolean deleted,
        long resultAssignmentCount
) {

    public static UserResponse from(User user) {
        return from(user, 0L, 0L);
    }

    public static UserResponse from(User user, long assignmentCount) {
        return from(user, assignmentCount, 0L);
    }

    public static UserResponse from(User user, long assignmentCount, long resultAssignmentCount) {
        return new UserResponse(user.getId(), user.getDisplayEmail(), user.getRole(), user.getStatus(), user.getDisplayName(),
                user.isDeleted() ? null : user.getPhone(), user.isDeleted() ? null : user.getDepartment(), assignmentCount,
                user.isDeleted(), resultAssignmentCount);
    }
}
