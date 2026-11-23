-- 매니저가 프로그램마다 정하는 지원자 인적사항 항목 목록. JSON 배열 [{"id": "...", "label": "..."}]로 저장한다.
-- 모든 항목은 단답형·필수이며, 팀 지원이면 대표자와 팀원마다 각각 받는다. (팀 이름은 항상 필수로 고정)
-- null이면 이 기능 이전의 고정 항목(이름 필수, 이메일·연락처 선택, 팀원 명단)을 그대로 쓴다.
-- 기존 프로그램은 모두 null로 남아 예전과 똑같이 동작한다.
alter table programs add column if not exists applicant_fields text;
