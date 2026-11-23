package com.example.evalhub.dto;

import com.example.evalhub.entity.Role;

public record CreateUserRequest(
        String email,
        String password,
        Role role,
        String name,
        String department,
        // true면 계정 생성과 같은 트랜잭션에서 안내 메일을 발송 대기열에 넣는다. 매니저 계정에만 허용.
        Boolean sendInvitation
) {
    public CreateUserRequest(String email, String password, Role role, String name, String department) {
        this(email, password, role, name, department, false);
    }

    public boolean invitationRequested() { return Boolean.TRUE.equals(sendInvitation); }
}
