-- 삭제(soft delete)된 심사위원 계정이 원래 이메일을 계속 점유해서
-- 같은 이메일로 계정을 다시 만들 수 없던 문제를 정리한다.
-- 형식은 User.deletedEmail()과 같아야 한다.
update users
set email = 'deleted-' || id || '@deleted.invalid'
where deleted_at is not null
  and role = 'EVALUATOR';
