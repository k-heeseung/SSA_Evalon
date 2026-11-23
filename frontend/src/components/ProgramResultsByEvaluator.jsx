import { Button, TableScroll } from './ui';
import { EVALUATOR_SORTS, criterionRates } from '../utils/evaluatorResults';
import './ProgramResultsByEvaluator.css';

const fmt = (value) => (value == null ? '—' : Number(value).toFixed(1));
const score = (value) => (value == null ? '—' : String(Number(value)));
const signed = (value) => (value == null ? '—' : `${value > 0 ? '+' : value < 0 ? '−' : '±'}${Math.abs(value).toFixed(1)}`);
const BUCKETS = [
  { label: '<60', min: 0, max: 60 }, { label: '60', min: 60, max: 70 }, { label: '70', min: 70, max: 80 },
  { label: '80', min: 80, max: 90 }, { label: '90+', min: 90, max: Infinity },
];

// 평가 결과 탭의 '심사위원별' 보기. 심사위원 한 명이 배정받은 팀을 어떻게 심사했는지 보여준다.
// 데이터·선택 상태·대화상자·내보내기는 ProgramResultsPanel이 소유하고, 이 컴포넌트는 표시만 한다.
export default function ProgramResultsByEvaluator({
  evaluator, review, criteriaQuery, sort, selectedTeamId,
  exportPending, exportError, onSort, onOpenTeam, onExport,
}) {
  if (!evaluator) return <p className="program-results-message">배정된 심사위원이 없습니다. 심사 배정 탭에서 심사위원을 배정해주세요.</p>;

  const { rows, summary } = review;
  const criteria = criteriaQuery.data ?? [];
  const rates = criterionRates(rows, criteria);
  const buckets = BUCKETS.map((bucket) => ({ ...bucket, count: summary.totals.filter((total) => total >= bucket.min && total < bucket.max).length }));
  const maxBucket = Math.max(1, ...buckets.map((bucket) => bucket.count));
  const cards = [
    { label: '심사 제출 완료', value: `${summary.submittedCount} / ${summary.assignedCount}팀` },
    { label: '평균 총점', value: fmt(summary.average) },
    { label: '다른 심사위원 대비', value: summary.diff == null ? '—' : `${signed(summary.diff)}점` },
    { label: '점수 폭', value: summary.min == null ? '—' : `${score(summary.min)} ~ ${score(summary.max)}점` },
  ];

  return <>
    <div className="grid grid-cols-2 xl:grid-cols-4 gap-2 mb-2">
      {cards.map((item) => <div key={item.label} className="bg-white rounded-xl border border-gray-200 p-4"><p className="text-xs text-gray-500 mb-1">{item.label}</p><p className="text-2xl font-bold text-gray-900">{item.value}</p></div>)}
    </div>
    <div className="evaluator-results-layout grid grid-cols-1 xl:grid-cols-4 gap-2">
      <section className="program-results-ranking xl:col-span-3" aria-labelledby="evaluator-results-title">
        <div className="program-results-ranking-heading">
          <span id="evaluator-results-title">{evaluator.name} 심사위원 심사 결과 <small>· {summary.submittedCount}팀 제출</small></span>
          <div className="program-results-filters" role="group" aria-label="정렬">
            {Object.entries(EVALUATOR_SORTS).map(([key, label]) => <Button key={key} variant="ghost" aria-pressed={sort === key} onClick={() => onSort(key)}>{label}</Button>)}
          </div>
        </div>
        <TableScroll className="program-results-table-scroll">
          <table className="program-results-table evaluator-results-table">
            <thead><tr>
              <th scope="col" className="evaluator-results-rank">순위</th><th scope="col" className="evaluator-results-team-cell">팀</th>
              {criteria.map((criterion) => <th key={criterion.id} scope="col" className="is-number evaluator-results-criterion" title={criterion.name}>
                <span className="evaluator-results-criterion-name">{criterion.name}</span><small>/ {Number(criterion.maxScore)}점</small>
              </th>)}
              <th scope="col" className="is-number">총점</th>
            </tr></thead>
            <tbody>
              {!rows.length && <tr><td colSpan={3 + criteria.length} className="program-results-message">배정된 팀이 없습니다.</td></tr>}
              {rows.map((row) => {
                const open = (event) => { event.stopPropagation(); onOpenTeam(row.team); };
                return <tr key={row.team.teamId} onClick={(event) => { event.currentTarget.querySelector('button')?.focus(); onOpenTeam(row.team); }}
                  className={`${selectedTeamId === row.team.teamId ? 'is-selected' : ''} ${row.submitted ? '' : 'is-unsubmitted'}`}>
                  <td className="program-results-rank evaluator-results-rank">{row.rank ?? '—'}</td>
                  <td className="evaluator-results-team-cell">
                    <button type="button" className="program-results-team-link" aria-haspopup="dialog" title={row.team.teamName} onClick={open}>{row.team.teamName}</button>
                    {!row.submitted && <span className="program-results-review-count">미제출</span>}
                    {row.comment && <span className="evaluator-results-comment" title={row.comment}>“{row.comment}”</span>}
                  </td>
                  {criteria.map((criterion) => <td key={criterion.id} className="is-number">{row.submitted ? score(row.scores.get(criterion.id)) : '—'}</td>)}
                  <td className="is-number program-results-final">{row.submitted ? score(row.total) : '미제출'}</td>
                </tr>;
              })}
            </tbody>
          </table>
        </TableScroll>
      </section>

      <aside className="program-results-sidebar" aria-label="점수 분포와 내보내기">
        <section className="bg-white rounded-xl border border-gray-200 p-4" aria-labelledby="evaluator-distribution-title">
          <h3 id="evaluator-distribution-title" className="text-sm font-semibold text-gray-700 mb-1">총점 분포</h3>
          <p className="program-results-method-rule">{evaluator.name} · 제출 {summary.totals.length}팀 기준</p>
          <div className="flex items-end justify-between gap-2 h-24" role="img" aria-label={buckets.map((bucket) => `${bucket.label}점 구간 ${bucket.count}팀`).join(', ')}>
            {buckets.map((bucket) => <div key={bucket.label} className="flex-1 flex flex-col items-center gap-1.5 h-full justify-end"><span className="text-xs text-gray-600">{bucket.count || ''}</span><div className="w-full bg-blue-500 rounded-t" style={{ height: `${(bucket.count / maxBucket) * 100}%`, minHeight: bucket.count ? '4px' : '0' }} /><span className="text-xs text-gray-500">{bucket.label}</span></div>)}
          </div>
        </section>

        <section className="bg-white rounded-xl border border-gray-200 p-4" aria-labelledby="evaluator-criteria-title">
          <h3 id="evaluator-criteria-title" className="text-sm font-semibold text-gray-700 mb-1">기준별 득점률</h3>
          {criteriaQuery.isPending ? <p className="program-results-method-rule" role="status">평가 기준을 불러오는 중입니다…</p>
            : criteriaQuery.isError ? <p className="program-results-method-rule" role="alert">평가 기준을 불러오지 못했습니다. <Button variant="ghost" onClick={() => criteriaQuery.refetch()}>다시 시도</Button></p>
            : !criteria.length ? <p className="program-results-method-rule">등록된 평가 기준이 없습니다.</p>
            : <>
              <div className="evaluator-results-legend"><span><i className="is-mine" />{evaluator.name}</span><span><i className="is-others" />다른 심사위원 평균</span></div>
              {rates.map(({ criterion, mine, others }) => <div key={criterion.id} className="evaluator-results-rate">
                <div><span>{criterion.name} <small>/ {Number(criterion.maxScore)}점</small></span><output><b>{mine == null ? '—' : `${Math.round(mine)}%`}</b> · {others == null ? '—' : `${Math.round(others)}%`}</output></div>
                <span className="evaluator-results-track is-mine"><i style={{ width: `${mine ?? 0}%` }} /></span>
                <span className="evaluator-results-track is-others"><i style={{ width: `${others ?? 0}%` }} /></span>
              </div>)}
            </>}
        </section>

        <section className="program-results-export bg-white rounded-xl border border-gray-200 p-5" aria-labelledby="evaluator-export-title">
          <h3 id="evaluator-export-title" className="text-sm font-semibold text-gray-700 mb-1">내보내기 옵션</h3>
          <p className="text-xs text-gray-500 mb-4">현재 선택: <strong>{evaluator.name}</strong></p>
          {exportPending && <p role="status" className="text-xs text-blue-600 mb-3">파일을 준비하고 있습니다…</p>}
          {exportError && <p role="alert" className="text-xs text-red-600 mb-3">{exportError}</p>}
          <div className="flex items-center rounded-lg border border-gray-100 hover:border-gray-200">
            <Button variant="ghost" onClick={onExport} disabled={exportPending || criteriaQuery.isPending} className="flex-1 min-w-0 flex items-center gap-3 px-3 py-3 rounded-lg border-0 hover:bg-gray-50 text-left">
              <span className="w-9 h-9 rounded-lg flex items-center justify-center shrink-0 bg-blue-50 text-blue-600"><svg aria-hidden="true" className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/></svg></span>
              <span className="flex-1 min-w-0"><span className="block text-sm font-medium text-gray-800">{evaluator.name} 심사위원 심사표</span><span className="block text-xs text-gray-500">Excel</span></span>
              <svg aria-hidden="true" className="w-4 h-4 text-gray-300 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"/></svg>
            </Button>
          </div>
        </section>
      </aside>
    </div>
  </>;
}
