-- 배정(프로그램 ↔ 심사위원)마다 '심사'와 '결과 조회' 권한을 따로 준다.
-- 기존 배정은 지금처럼 심사만 할 수 있도록 can_evaluate = true, can_view_results = false로 채운다.
alter table evaluation_assignments
    add column can_evaluate boolean not null default true,
    add column can_view_results boolean not null default false;

alter table evaluation_assignments
    add constraint evaluation_assignments_permission_check
    check (can_evaluate or can_view_results);
