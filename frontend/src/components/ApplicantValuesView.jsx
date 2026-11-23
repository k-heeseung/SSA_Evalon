import { applicantValue } from '../utils/applicantFields';

// 매니저가 정한 지원자 정보 항목으로 제출된 값을 보여 준다. (읽기 전용)
// 개인 지원은 항목별 값 하나씩, 팀 지원은 대표자와 팀원마다 한 묶음씩 보여 준다.
// fields: [{ id, label }], info: applicantInfo({ values, members: [{ values }] })
function PersonValues({ fields, values }) {
  return (
    <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-2">
      {fields.map((field) => (
        <div key={field.id} className="min-w-0">
          <dt className="text-xs text-gray-500">{field.label}</dt>
          <dd className="text-sm text-gray-800 break-all">{applicantValue(values, field.id) || '—'}</dd>
        </div>
      ))}
    </dl>
  );
}

export default function ApplicantValuesView({ fields, info, isTeam }) {
  if (!fields.length) {
    return <p className="text-sm text-gray-500">{isTeam ? '팀 이름 외에 받은 지원자 정보가 없습니다.' : '받은 지원자 정보가 없습니다.'}</p>;
  }
  if (!isTeam) return <PersonValues fields={fields} values={info?.values} />;

  const members = Array.isArray(info?.members) ? info.members : [];
  return (
    <div className="space-y-3">
      <div className="text-xs font-medium text-gray-500">팀 구성원 {1 + members.length}명</div>
      <div className="rounded-lg border border-gray-100 p-3">
        <div className="mb-2 text-xs font-medium text-blue-600">대표자</div>
        <PersonValues fields={fields} values={info?.values} />
      </div>
      {members.map((member, index) => (
        <div key={index} className="rounded-lg border border-gray-100 p-3">
          <div className="mb-2 text-xs font-medium text-gray-500">팀원 {index + 1}</div>
          <PersonValues fields={fields} values={member?.values} />
        </div>
      ))}
    </div>
  );
}
