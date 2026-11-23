import { Link, useParams } from 'react-router-dom';
import { getAssignmentResults, getAssignmentResultsPdf } from '../api/evaluations';
import ProgramResultsPanel from '../components/ProgramResultsPanel';
import { queryKeys } from '../query/keys';
import { useLiveApiQuery } from '../query/useApiQuery';
import { formatProgramPeriod } from '../utils/programSchedule';

// 403(권한 없음)·404(배정 해제)·409(평가 시작 전)는 다시 시도해도 같으므로 재시도하지 않는다.
const retryServerErrors = (count, error) => {
  const status = error?.response?.status;
  return (!status || status >= 500) && count < 2;
};

const ERROR_MESSAGES = {
  403: '이 프로그램의 결과 조회 권한이 없습니다. 권한이 바뀌었다면 매니저에게 문의하세요.',
  404: '배정이 해제되어 결과를 볼 수 없습니다.',
};

// 결과 조회 권한 심사위원의 결과 화면. 매니저 프로그램 상세의 '평가 결과' 탭(ProgramResultsPanel)만 읽기 전용으로 보여 준다.
// 합격/불합격 변경과 팀 상세(지원서·첨부) 이동은 없다.
export default function EvaluatorResultDetailPage() {
  const { assignmentId } = useParams();
  const query = useLiveApiQuery(
    queryKeys.assignmentResults(assignmentId),
    () => getAssignmentResults(assignmentId),
    { retry: retryServerErrors },
  );
  const bundle = query.data;
  const program = bundle?.program ?? null;
  const pick = (key) => ({
    data: bundle?.[key],
    isPending: query.isPending,
    isError: query.isError,
    refetch: query.refetch,
  });
  const source = {
    resultsQuery: pick('results'),
    criteriaQuery: pick('criteria'),
    downloadPdf: (onlyPassed, method) => getAssignmentResultsPdf(assignmentId, onlyPassed, method),
  };
  const status = query.error?.response?.status;
  const blocked = query.isError && !bundle && status && status < 500;
  const blockedMessage = ERROR_MESSAGES[status] ?? query.error?.response?.data?.message ?? '결과를 볼 수 없습니다.';

  return (
    <div className="h-full overflow-auto">
      <div className="ui-page">
        <div className="flex items-center gap-2 text-sm text-gray-500 mb-2">
          <span>평가ON</span>
          <span>›</span>
          <Link to="/evaluator/results" className="hover:text-gray-700">결과 조회</Link>
          <span>›</span>
          <span className="text-gray-600 font-medium truncate">{program?.title ?? '평가 결과'}</span>
        </div>
        <div className="ui-card mb-2 flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <h1 className="ui-page-title font-bold text-gray-900 mb-0.5 break-words">{program?.title ?? '평가 결과'}</h1>
            {program && <p className="text-sm text-gray-600">평가 기간 {formatProgramPeriod(program, 'evaluation')}</p>}
          </div>
          <span className="text-xs font-medium px-2 py-1 rounded-full bg-violet-50 text-violet-700 border border-violet-200">결과 조회 · 읽기 전용</span>
        </div>

        {blocked ? (
          <div role="alert" className="text-center py-16 bg-white rounded-xl border border-gray-200">
            <p className="text-gray-700 mb-4">{blockedMessage}</p>
            <Link to="/evaluator/results" className="ui-button ui-button--ghost inline-flex px-4 py-2 border border-gray-200 rounded-lg text-sm">결과 조회 목록으로</Link>
          </div>
        ) : (
          <ProgramResultsPanel programId={Number(assignmentId)} program={program} source={source} readOnly />
        )}
      </div>
    </div>
  );
}
