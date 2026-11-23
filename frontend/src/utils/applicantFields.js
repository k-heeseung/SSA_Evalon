// 지원자 인적사항 항목 (programs.applicant_fields ↔ 백엔드 util/ApplicantFields.java)
//
// 매니저가 프로그램마다 항목 이름만 정한다. 모든 항목은 단답형이고 항상 필수다.
// 팀 지원이면 '팀 이름'은 항상 받고, 설정된 항목을 대표자와 팀원마다 각각 받는다.
// 지원자가 입력한 값은 applicantInfo.values / applicantInfo.members[].values에 항목 id를 키로 담긴다.
//
// 항목 목록이 null인 프로그램은 이 기능 이전의 고정 항목 방식(이름 필수, 이메일·연락처 선택, 팀원 명단)을 그대로 쓴다.

// 새 프로그램을 만들 때 미리 넣어 두는 항목. 매니저가 고치거나 지울 수 있다.
export const DEFAULT_APPLICANT_FIELD_LABELS = ['이름', '연락처', '이메일'];

// 매니저가 항목을 직접 정한 프로그램인지. false면 예전 고정 항목 방식이다.
export const usesCustomApplicantFields = (fields) => Array.isArray(fields);

export const applicantValue = (values, fieldId) => String(values?.[fieldId] ?? '');

// 모든 항목이 채워졌는지
export const isApplicantRowComplete = (fields, values) =>
  fields.every((field) => applicantValue(values, field.id).trim() !== '');

// 한 칸이라도 입력됐는지. 아무것도 입력하지 않은 팀원 행은 저장할 때 버린다.
export const hasAnyApplicantValue = (fields, values) =>
  fields.some((field) => applicantValue(values, field.id).trim() !== '');

// 설정된 항목의 값만 앞뒤 공백을 지워 보낸다.
export const trimApplicantValues = (fields, values) => Object.fromEntries(
  fields
    .map((field) => [field.id, applicantValue(values, field.id).trim()])
    .filter(([, value]) => value !== ''),
);

// 비어 있는 항목 이름 목록. 대표자/팀원 구분이 필요하면 prefix를 붙인다.
export const missingApplicantLabels = (fields, values, prefix = '') =>
  fields
    .filter((field) => applicantValue(values, field.id).trim() === '')
    .map((field) => `${prefix}${field.label}`);

// 저장된 지원자 정보 → 입력 화면 상태
export const toCustomApplicantState = (info) => ({
  values: { ...(info?.values || {}) },
  members: Array.isArray(info?.members) ? info.members.map((m) => ({ values: { ...(m?.values || {}) } })) : [],
});

// 입력 화면 상태 → 요청 본문(teamName, values, members). 아무것도 입력하지 않은 팀원 행은 보내지 않는다.
export const customApplicantPayload = (fields, isTeam, { teamName, values, members }) => ({
  teamName: isTeam ? (teamName.trim() || null) : null,
  values: trimApplicantValues(fields, values),
  members: isTeam
    ? members
      .filter((m) => hasAnyApplicantValue(fields, m.values))
      .map((m) => ({ values: trimApplicantValues(fields, m.values) }))
    : [],
});

// 제출 전 비어 있는 필수 항목 이름 목록. 백엔드 ApplicationService.findCustomParticipantError와 같은 규칙.
export const missingCustomApplicantFields = (fields, isTeam, { teamName, values, members }) => {
  const missing = [];
  if (isTeam && !teamName.trim()) missing.push('팀 이름');
  missing.push(...missingApplicantLabels(fields, values, isTeam ? '대표자 ' : ''));
  if (isTeam) {
    members.forEach((m, index) => {
      if (hasAnyApplicantValue(fields, m.values)) {
        missing.push(...missingApplicantLabels(fields, m.values, `팀원 ${index + 1} `));
      }
    });
  }
  return missing;
};

// ---- 프로그램 편집 화면용 상태 ----
let applicantFieldKeySeq = 0;

// 서버 응답 → 편집용 상태. 서버 id를 들고 있어야 다음 저장 때 같은 항목으로 보고 입력값을 보존한다.
// key는 화면 조작용 로컬 key이고, 서버에는 id만 보낸다.
export const toApplicantFieldItems = (fields) => (Array.isArray(fields)
  ? fields.map((f) => ({ key: `af-${f.id}`, id: f.id, label: f.label ?? '' }))
  : null);

export const newApplicantFieldItem = (label = '') => ({
  key: `af-new-${Date.now()}-${applicantFieldKeySeq++}`, id: null, label,
});
