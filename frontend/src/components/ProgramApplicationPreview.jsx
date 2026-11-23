import { useRef, useState } from 'react';
import FieldInput from './FieldInput';

// applicantFields: 지원자 정보 항목 편집 상태({ key, label }[]). null이면 예전 고정 항목 방식으로 보여 준다.
const legacyApplicantLabels = (participantType) => (participantType === 'TEAM'
  ? ['지원자 이름', '연락처', '이메일', '팀 이름', '팀원 정보']
  : ['지원자 이름', '연락처', '이메일']);

export default function ProgramApplicationPreview({ title, participantType, applicantFields, fields, attachmentsEnabled, maxAttachments }) {
  const dialog = useRef(null);
  const [answers, setAnswers] = useState({});
  return <>
    <button type="button" className="ui-button program-editor-preview-button" onClick={() => dialog.current.showModal()}>지원자 화면 미리보기</button>
    <dialog ref={dialog} className="program-editor-preview" aria-labelledby="program-preview-title">
      <h2 id="program-preview-title">{title.trim() || '프로그램'} 지원서</h2>
      <p className="program-editor-help">입력 내용을 확인하는 미리보기이며 실제 지원서는 저장되지 않습니다.</p>
      <div className="program-editor-preview-fields">
        {Array.isArray(applicantFields) ? <>
          {participantType === 'TEAM' && <label>팀 이름<span className="program-editor-required">필수</span><input className="ui-input" placeholder="팀 이름 입력" /></label>}
          {participantType === 'TEAM' && applicantFields.some((item) => item.label.trim()) && <p className="program-editor-help">아래 항목은 대표자와 팀원마다 각각 받습니다.</p>}
          {applicantFields.filter((item) => item.label.trim()).map((item) => <label key={item.key}>{item.label.trim()}<span className="program-editor-required">필수</span><input className="ui-input" placeholder={`${item.label.trim()} 입력`} /></label>)}
        </> : legacyApplicantLabels(participantType).map((name) => <label key={name}>{name}<input className="ui-input" placeholder={`${name} 입력`} /></label>)}
        {fields.map((field, index) => <div key={field.id}><div className="program-editor-input-label">{field.label || `항목 ${index + 1}`}{field.required && <span className="program-editor-required">필수</span>}</div>
          <FieldInput field={{ fieldId: `preview-${field.id}`, label: field.label || `항목 ${index + 1}`, fieldType: field.type, options: (field.options || []).filter((option) => option.trim()) }} value={answers[field.id] || ''} onChange={(value) => setAnswers((previous) => ({ ...previous, [field.id]: value }))} />
        </div>)}
        {attachmentsEnabled && <p className="program-editor-help">PDF 첨부 · 최대 {Number(maxAttachments) || 1}개 (미리보기에서는 업로드하지 않습니다.)</p>}
      </div>
      <button type="button" className="ui-button" onClick={() => dialog.current.close()}>닫기</button>
    </dialog>
  </>;
}
