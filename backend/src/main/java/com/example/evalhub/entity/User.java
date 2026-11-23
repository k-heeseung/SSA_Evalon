package com.example.evalhub.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column
    private String name;

    // 숫자만 남긴 정규화 형태로 저장한다. (PhoneNumbers.normalize)
    @Column
    private String phone;

    @Column
    private String department;

    @Column(columnDefinition = "timestamp with time zone")
    private java.time.Instant deletedAt;

    public boolean isDeleted() { return deletedAt != null; }

    public String getDisplayName() { return isDeleted() ? "삭제된 심사위원" : name; }

    public String getDisplayEmail() { return isDeleted() ? "" : email; }

    public void deleteEvaluatorAccount() {
        if (role != Role.EVALUATOR) throw new IllegalStateException("Only evaluator accounts can be soft-deleted.");
        if (deletedAt == null) deletedAt = java.time.Instant.now();
        status = Status.INACTIVE;
        // users.email은 unique라, 원래 주소를 비워 두어야 같은 이메일로 계정을 다시 만들 수 있다.
        // V26 마이그레이션과 같은 형식을 유지한다.
        email = deletedEmail(id);
    }

    public static String deletedEmail(Long id) {
        return "deleted-" + id + "@deleted.invalid";
    }

    public User(String email, String password, Role role) {
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public User(String email, String password, Role role, String name, String department) {
        this(email, password, role, name, null, department);
    }

    public User(String email, String password, Role role, String name, String phone, String department) {
        this(email, password, role);
        this.name = name;
        this.phone = phone;
        this.department = department;
    }

    public void changeStatus(Status status) {
        this.status = status;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}
