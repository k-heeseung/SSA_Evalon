// 프로그램 편집 화면의 '지원자 정보 항목' 편집기.
// 항목 이름만 정한다. 모든 항목은 단답형·필수이며, 팀 지원이면 대표자와 팀원마다 각각 받는다.
// items가 null이면 예전 고정 항목 방식 프로그램이다. (utils/applicantFields.js 참고)
import { DEFAULT_APPLICANT_FIELD_LABELS, newApplicantFieldItem } from '../utils/applicantFields';

export default function ApplicantFieldsEditor({ items, onChange, participantType, canSwitchFromLegacy, onSwitchFromLegacy }) {
  const isTeam = participantType === 'TEAM';

  if (items == null) {
    return (
      <div className="program-editor-applicant-fields">
        <h3>지원자 정보 항목</h3>
        <p className="program-editor-help">
          이 프로그램은 기존 고정 항목을 사용합니다: {isTeam ? '팀 이름(필수), 팀장 이름(필수)·이메일·연락처, 팀원 명단' : '이름(필수), 연락처, 이메일'}
        </p>
        {canSwitchFromLegacy ? (
          <button type="button" className="ui-button ui-button--ghost program-editor-applicant-add" onClick={() => onSwitchFromLegacy(DEFAULT_APPLICANT_FIELD_LABELS.map((label) => newApplicantFieldItem(label)))}>
            항목 직접 설정으로 전환
          </button>
        ) : (
          <p className="program-editor-help">이미 지원서가 있어 항목 방식을 바꿀 수 없습니다.</p>
        )}
      </div>
    );
  }

  const update = (key, label) => onChange(items.map((item) => (item.key === key ? { ...item, label } : item)));
  const remove = (key) => onChange(items.filter((item) => item.key !== key));
  const move = (index, delta) => {
    const target = index + delta;
    if (target < 0 || target >= items.length) return;
    const next = [...items];
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  };

  return (
    <div className="program-editor-applicant-fields">
      <h3>지원자 정보 항목</h3>
      <p className="program-editor-help">
        모든 항목은 단답형 필수 항목입니다.{isTeam ? ' 팀 이름은 항상 받고, 아래 항목은 대표자와 팀원마다 각각 받습니다.' : ''}
      </p>
      <ul className="program-editor-applicant-list">
        {isTeam && (
          <li className="program-editor-applicant-row program-editor-applicant-row--fixed">
            <span className="program-editor-applicant-name">팀 이름</span>
            <span className="program-editor-applicant-tag">필수 · 고정</span>
          </li>
        )}
        {items.map((item, index) => (
          <li key={item.key} className="program-editor-applicant-row">
            <input
              type="text"
              className="ui-input"
              aria-label={`지원자 정보 항목 ${index + 1} 이름`}
              value={item.label}
              maxLength={100}
              onChange={(e) => update(item.key, e.target.value)}
              placeholder="항목 이름 (예: 학번)"
            />
            <button type="button" className="ui-button ui-button--ghost" aria-label={`지원자 정보 항목 ${index + 1} 위로`} disabled={index === 0} onClick={() => move(index, -1)}>↑</button>
            <button type="button" className="ui-button ui-button--ghost" aria-label={`지원자 정보 항목 ${index + 1} 아래로`} disabled={index === items.length - 1} onClick={() => move(index, 1)}>↓</button>
            <button type="button" className="ui-button ui-button--ghost program-editor-applicant-remove" aria-label={`지원자 정보 항목 ${index + 1} 삭제`} onClick={() => remove(item.key)}>삭제</button>
          </li>
        ))}
      </ul>
      {items.length === 0 && (
        <p className="program-editor-help">{isTeam ? '팀 이름만 받습니다.' : '받을 지원자 정보 항목이 없습니다.'}</p>
      )}
      <button type="button" className="ui-button ui-button--ghost program-editor-applicant-add" onClick={() => onChange([...items, newApplicantFieldItem()])}>
        + 항목 추가
      </button>
    </div>
  );
}
