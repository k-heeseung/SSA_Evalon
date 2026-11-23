import { applicantValue } from '../utils/applicantFields';

// 매니저가 정한 지원자 정보 항목의 입력 화면. (지원서 작성·팀 정보 수정·매니저 지원 정보 수정에서 공유)
// 모든 항목은 단답형 필수다. 팀 지원이면 팀 이름을 받고, 항목을 대표자와 팀원마다 한 줄씩 받는다.
//
// values: 본인(대표자) 값 { [fieldId]: string }
// members: 팀원 행 [{ values: { [fieldId]: string } }]
const INPUT_CLASS =
  'ui-input w-full min-w-0 px-3 py-2 border border-gray-200 rounded-lg text-base focus:ring-2 focus:ring-blue-500 outline-none transition-shadow disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-default';

function RowInputs({ fields, values, onChange, disabled, rowLabel }) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
      {fields.map((field) => (
        <label key={field.id} className="block min-w-0">
          <span className="block text-xs font-medium text-gray-500 mb-1">{field.label}</span>
          <input
            type="text"
            value={applicantValue(values, field.id)}
            onChange={(e) => onChange({ ...(values || {}), [field.id]: e.target.value })}
            placeholder={field.label}
            aria-label={`${rowLabel} ${field.label}`}
            autoComplete="off"
            disabled={disabled}
            className={INPUT_CLASS}
          />
        </label>
      ))}
    </div>
  );
}

export default function CustomApplicantInputs({
  fields, isTeam, teamName, onTeamNameChange, values, onValuesChange, members, onMembersChange, disabled = false,
}) {
  if (!isTeam) {
    if (fields.length === 0) return null;
    return <RowInputs fields={fields} values={values} onChange={onValuesChange} disabled={disabled} rowLabel="지원자" />;
  }

  const updateMember = (index, next) => onMembersChange(members.map((m, i) => (i === index ? { ...m, values: next } : m)));
  const removeMember = (index) => onMembersChange(members.filter((_, i) => i !== index));
  const addMember = () => onMembersChange([...members, { values: {} }]);

  return (
    <div className="space-y-4">
      <label className="block">
        <span className="block text-xs font-medium text-gray-500 mb-1">팀 이름</span>
        <input
          type="text"
          value={teamName}
          onChange={(e) => onTeamNameChange(e.target.value)}
          placeholder="팀 이름을 입력하세요"
          autoComplete="off"
          disabled={disabled}
          className={INPUT_CLASS}
        />
      </label>

      {fields.length > 0 && (
        <>
          <div className="rounded-lg border border-gray-100 p-4">
            <div className="mb-3 text-xs font-medium text-blue-600">대표자·본인</div>
            <RowInputs fields={fields} values={values} onChange={onValuesChange} disabled={disabled} rowLabel="대표자" />
          </div>

          {members.map((member, index) => (
            <div key={index} className="rounded-lg border border-gray-100 p-4">
              <div className="mb-3 flex items-center justify-between gap-2">
                <span className="text-xs font-medium text-gray-500">팀원 {index + 1}</span>
                <button
                  type="button"
                  onClick={() => removeMember(index)}
                  disabled={disabled}
                  className="ui-button ui-button--ghost text-xs text-gray-500 hover:text-red-500 cursor-pointer disabled:opacity-40 disabled:cursor-default"
                >
                  삭제
                </button>
              </div>
              <RowInputs fields={fields} values={member.values} onChange={(next) => updateMember(index, next)} disabled={disabled} rowLabel={`팀원 ${index + 1}`} />
            </div>
          ))}

          <button
            type="button"
            onClick={addMember}
            disabled={disabled}
            className="ui-button ui-button--ghost flex items-center gap-1.5 text-sm text-blue-600 hover:text-blue-700 font-medium cursor-pointer transition-colors disabled:opacity-40 disabled:cursor-default"
          >
            + 팀원 추가
          </button>
        </>
      )}
    </div>
  );
}
