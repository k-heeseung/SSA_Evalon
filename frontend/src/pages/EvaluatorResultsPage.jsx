import { useNavigate } from 'react-router-dom';
import { getEvaluatorAssignments } from '../api/evaluations';
import { queryKeys } from '../query/keys';
import { useLiveApiQuery } from '../query/useApiQuery';
import { formatProgramPeriod } from '../utils/programSchedule';

// 매니저 '결과·내보내기' 목록(ResultsPage)과 같은 상태 표기
const STATUS_CONFIG = {
  ACTIVE: { label: '평가 진행', badge: 'bg-blue-100 text-blue-700' },
  INACTIVE: { label: '평가 완료', badge: 'bg-green-100 text-green-700' },
};
const WAITING = { label: '평가 전', badge: 'bg-gray-100 text-gray-500' };

// 심사위원 '결과 조회' 메뉴. 결과 조회 권한이 있는 배정(프로그램)만 모은다.
// 평가가 시작된 프로그램(ACTIVE / INACTIVE)만 열 수 있고, 카드를 누르면 매니저 '평가 결과' 탭과 같은 화면이 열린다.
export default function EvaluatorResultsPage() {
  const navigate = useNavigate();
  const { data: assignments = [], isPending, isError, refetch } = useLiveApiQuery(
    queryKeys.evaluatorAssignments,
    () => getEvaluatorAssignments(),
  );
  const rows = assignments
    .filter((assignment) => assignment.canViewResults)
    .sort((a, b) => Number(b.resultsAvailable) - Number(a.resultsAvailable)
      || (a.programStatus === b.programStatus ? 0 : a.programStatus === 'ACTIVE' ? -1 : 1));

  return (
    <div className="h-full overflow-auto">
      <div className="ui-page">
        <div className="flex items-center gap-2 text-sm text-gray-500 mb-2">
          <span>평가ON</span>
          <span>›</span>
          <span className="text-gray-600 font-medium">결과 조회</span>
        </div>
        <div className="ui-card mb-2">
          <h1 className="ui-page-title font-bold text-gray-900 mb-0.5">평가 결과</h1>
          <p className="text-sm text-gray-600">
            결과 조회 권한을 받은 프로그램입니다. 평가가 시작된 프로그램부터 결과를 볼 수 있습니다.
          </p>
        </div>

        {isPending ? (
          <p className="text-gray-500 py-10 text-center">로딩 중...</p>
        ) : isError ? (
          <div role="alert" className="text-center py-16 bg-white rounded-xl border border-gray-200">
            <p className="text-red-600 mb-3">결과 조회 목록을 불러오지 못했습니다.</p>
            <button type="button" onClick={() => refetch()} className="ui-button ui-button--ghost px-4 py-2 border border-gray-200 rounded-lg text-sm">다시 시도</button>
          </div>
        ) : rows.length === 0 ? (
          <div className="text-center py-16 bg-white rounded-xl border border-gray-200">
            <p className="text-gray-500">결과 조회 권한을 받은 프로그램이 없습니다.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 xl:grid-cols-2 gap-2">
            {rows.map((assignment) => (
              <ResultCard
                key={assignment.id}
                assignment={assignment}
                onOpen={() => navigate(`/evaluator/results/${assignment.id}`)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function ResultCard({ assignment, onOpen }) {
  const status = assignment.resultsAvailable ? (STATUS_CONFIG[assignment.programStatus] ?? STATUS_CONFIG.ACTIVE) : WAITING;
  const body = (
    <>
      <div className="flex items-start justify-between gap-3 mb-4">
        <div className="min-w-0">
          <div className="flex items-center gap-2 mb-0.5 flex-wrap">
            <h2 className="font-bold text-gray-900 text-base break-words">{assignment.programTitle}</h2>
            <span className={`text-xs px-2 py-0.5 rounded-full font-medium flex-shrink-0 ${status.badge}`}>{status.label}</span>
          </div>
          {assignment.canEvaluate && <span className="text-xs text-blue-700">이 프로그램은 심사도 맡고 있습니다</span>}
        </div>
        {assignment.resultsAvailable && (
          <svg aria-hidden="true" className="w-4 h-4 text-gray-300 flex-shrink-0 mt-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 5l7 7-7 7" />
          </svg>
        )}
      </div>
      <div className="rounded-lg bg-violet-50/60 px-3 py-2.5">
        <p className="text-xs text-violet-600 mb-1">평가 기간</p>
        <p className="text-sm font-medium text-gray-700">{formatProgramPeriod(assignment, 'evaluation')}</p>
      </div>
      {!assignment.resultsAvailable && (
        <p className="mt-3 text-xs text-gray-500">평가가 시작되면 결과를 볼 수 있습니다.</p>
      )}
    </>
  );

  if (!assignment.resultsAvailable) {
    return <div className="ui-card bg-white rounded-xl border border-gray-200 p-5 opacity-70">{body}</div>;
  }
  return (
    <button
      type="button"
      onClick={onOpen}
      className="ui-card text-left w-full bg-white rounded-xl border border-gray-200 p-5 cursor-pointer hover:shadow-md transition-shadow"
    >
      {body}
    </button>
  );
}
