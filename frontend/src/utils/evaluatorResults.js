import { rankResults } from './resultScoring.js';

// 평가 결과의 '심사위원별' 보기에서 쓰는 계산.
// 입력은 GET /api/programs/{id}/evaluation-results 응답(팀별 evaluations[]) 그대로이며,
// 최종 제출(SUBMITTED)된 심사만 점수로 다룬다. 임시저장 점수는 노출하지 않는다.

const isSubmitted = (evaluation) => evaluation.submission?.status === 'SUBMITTED';
const toNumber = (value) => (value == null || value === '' ? null : Number(value));
const mean = (values) => (values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : null);

export const evaluatorLabel = (evaluation) => evaluation.evaluatorName || evaluation.evaluatorEmail || '심사위원';

// 결과에 등장하는 심사위원 목록(배정 기준). 이름순.
export function listEvaluators(results) {
  const byId = new Map();
  results.forEach((team) => (team.evaluations ?? []).forEach((evaluation) => {
    const current = byId.get(evaluation.evaluatorId)
      ?? { evaluatorId: evaluation.evaluatorId, name: evaluatorLabel(evaluation), assignedCount: 0, submittedCount: 0 };
    current.assignedCount += 1;
    if (isSubmitted(evaluation)) current.submittedCount += 1;
    byId.set(evaluation.evaluatorId, current);
  }));
  return [...byId.values()].sort((a, b) => a.name.localeCompare(b.name, 'ko'));
}

export const EVALUATOR_SORTS = { rank: '최종 순위순', score: '본인 총점순' };

// 심사위원 한 명이 배정받은 팀별 심사 행과 요약.
// othersAvg / diff는 같은 팀에 최종 제출한 '다른' 심사위원만으로 계산한다.
export function evaluatorReview(results, evaluatorId, sort = 'rank') {
  const ranks = new Map(rankResults(results, 'ALL').map((team) => [team.teamId, team.rank]));
  const rows = results.flatMap((team) => {
    const evaluations = team.evaluations ?? [];
    const mine = evaluations.find((evaluation) => evaluation.evaluatorId === evaluatorId);
    if (!mine) return [];
    const submitted = isSubmitted(mine);
    const others = evaluations.filter((evaluation) => evaluation.evaluatorId !== evaluatorId && isSubmitted(evaluation));
    const othersAvg = mean(others.map((evaluation) => toNumber(evaluation.submission.totalScore)).filter((value) => value != null));
    const total = submitted ? toNumber(mine.submission.totalScore) : null;
    const scores = new Map(submitted ? (mine.submission.scores ?? []).map((score) => [score.criterionId, toNumber(score.score)]) : []);
    const othersScores = (criterionId) => mean(others
      .map((evaluation) => toNumber(evaluation.submission.scores?.find((score) => score.criterionId === criterionId)?.score))
      .filter((value) => value != null));
    return [{
      team,
      rank: ranks.get(team.teamId) ?? null,
      submitted,
      total,
      scores,
      othersScores,
      comment: submitted ? mine.submission.oneLineComment?.trim() ?? '' : '',
      othersAvg,
      diff: total != null && othersAvg != null ? total - othersAvg : null,
    }];
  });

  rows.sort((a, b) => {
    if (a.submitted !== b.submitted) return a.submitted ? -1 : 1;
    if (sort === 'score' && a.total !== b.total) return (b.total ?? -Infinity) - (a.total ?? -Infinity);
    if (a.rank !== b.rank) return (a.rank ?? Infinity) - (b.rank ?? Infinity);
    return a.team.teamName.localeCompare(b.team.teamName, 'ko');
  });

  const done = rows.filter((row) => row.submitted && row.total != null);
  const totals = done.map((row) => row.total);
  return {
    rows,
    summary: {
      assignedCount: rows.length,
      submittedCount: rows.filter((row) => row.submitted).length,
      average: mean(totals),
      diff: mean(done.filter((row) => row.diff != null).map((row) => row.diff)),
      min: totals.length ? Math.min(...totals) : null,
      max: totals.length ? Math.max(...totals) : null,
      totals,
    },
  };
}

// 기준별 득점률(배점 대비 %). 이 심사위원과 다른 심사위원 평균을 같은 팀들(본인이 제출한 팀) 기준으로 비교한다.
export function criterionRates(rows, criteria) {
  const done = rows.filter((row) => row.submitted);
  return criteria.map((criterion) => {
    const max = Number(criterion.maxScore);
    const mine = mean(done.map((row) => row.scores.get(criterion.id)).filter((value) => value != null));
    const others = mean(done.map((row) => row.othersScores(criterion.id)).filter((value) => value != null));
    const rate = (value) => (value == null || !max ? null : (value / max) * 100);
    return { criterion, mine: rate(mine), others: rate(others) };
  });
}
