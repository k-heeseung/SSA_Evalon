// 계정 안내 메일(심사위원·매니저) 발송 상태 표시. 상태·오류 코드는 백엔드 EvaluatorInvitationService와 같은 값이다.
const INVITATION_LABELS = {
  PENDING: '발송 대기', PROCESSING: '발송 중', RETRY_WAIT: '재시도 대기',
  SENT: '발송 요청 성공', FAILED: '발송 실패', CANCELLED: '발송 취소', EXPIRED: '발송 기한 만료',
};
const INVITATION_ERRORS = {
  AUTH_REQUIRED: '발신 계정 재연결 필요', PERMISSION_DENIED: '발신 계정 권한 확인 필요',
  RATE_LIMITED: '발송량 제한', DAILY_LIMIT: '일일 발송량 제한', GMAIL_UNAVAILABLE: '메일 서비스 일시 오류',
  NETWORK_ERROR: '네트워크 오류', NETWORK_INTERRUPTED: '네트워크 요청 중단',
  DECRYPTION_FAILED: '메일 설정 확인 필요', ACCOUNT_INACTIVE: '비활성 계정',
  ATTEMPTS_EXHAUSTED: '재시도 횟수 소진', INVALID_REQUEST: '메일 요청 확인 필요',
  INVALID_GMAIL_RESPONSE: '메일 서비스 응답 확인 필요', MESSAGE_ENCODING_ERROR: '메일 작성 오류',
};

export default function InvitationStatus({ invitation, pending, onRetry }) {
  if (!invitation) return <span className="text-gray-500">발송 이력 없음</span>;
  return <div className="space-y-1">
    <span className={invitation.status === 'FAILED' ? 'text-red-600' : 'text-gray-600'}>
      {INVITATION_LABELS[invitation.status] || invitation.status}
    </span>
    {invitation.errorCode && <p className="text-xs text-gray-500">{INVITATION_ERRORS[invitation.errorCode] || '발송 오류'}</p>}
    {invitation.nextAttemptAt && invitation.status === 'RETRY_WAIT' && <p className="text-xs text-gray-500">다음 시도: {new Date(invitation.nextAttemptAt).toLocaleString('ko-KR')}</p>}
    {invitation.retryAllowed && <button type="button" disabled={pending} onClick={(event) => { event.stopPropagation(); onRetry(); }} className="ui-button ui-button--ghost block text-xs text-blue-600 disabled:opacity-50">메일 재시도</button>}
  </div>;
}
