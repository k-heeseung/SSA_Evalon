import api from './client';

export const getPrograms = () => api.get('/programs');
export const getOpenPrograms = () => api.get('/programs/open');
// 프로그램 편집 화면의 전체 상태(기본 정보·평가 기준·지원 양식 항목)를 한 트랜잭션으로 저장한다.
// 응답: { program, criteria, formFields }. publish: true면 같은 요청에서 개설까지 한다.
export const createProgram = (data) => api.post('/programs', data);
export const updateProgram = (programId, data) => api.put(`/programs/${programId}`, data);
export const deleteProgram = (programId) => api.delete(`/programs/${programId}`);

export const getTeams = (programId) => api.get(`/programs/${programId}/teams`);
export const getTeam = (programId, teamId) => api.get(`/programs/${programId}/teams/${teamId}`);
export const getTeamApplication = (teamId) => api.get(`/teams/${teamId}/application`);
export const updateTeamApplication = (teamId, data) => api.put(`/teams/${teamId}/application`, data);
export const updateTeam = (programId, teamId, data) => api.put(`/programs/${programId}/teams/${teamId}`, data);
export const deleteTeam = (programId, teamId) => api.delete(`/programs/${programId}/teams/${teamId}`);
export const changeTeamStatus = (programId, teamId, data) => api.post(`/programs/${programId}/teams/${teamId}/status`, data);
export const updateTeamBonus = (programId, teamId, data) => api.post(`/programs/${programId}/teams/${teamId}/bonus`, data);

export const getTeamMembers = (teamId) => api.get(`/teams/${teamId}/members`);
export const addTeamMember = (teamId, data) => api.post(`/teams/${teamId}/members`, data);
export const removeTeamMember = (teamId, memberId) => api.delete(`/teams/${teamId}/members/${memberId}`);
export const lookupUser = (email) => api.get('/users/lookup', { params: { email } });

export const getFormFields = (programId) => api.get(`/programs/${programId}/form-fields`);

export const getCriteria = (programId) => api.get(`/programs/${programId}/criteria`);

export const getAssignmentsByProgram = (programId) => api.get(`/programs/${programId}/assignments`);
export const getEvaluatorsByProgram = (programId) => api.get(`/programs/${programId}/evaluators`);
export const createAssignment = (programId, data) => api.post(`/programs/${programId}/assignments`, data);
export const cancelAssignment = (assignmentId) => api.delete(`/assignments/${assignmentId}`);

export const getEvaluationResults = (programId) => api.get(`/programs/${programId}/evaluation-results`);
export const getEvaluationResultsPdf = (programId, onlyPassed = false, method = 'ALL') =>
  api.get(`/programs/${programId}/evaluation-results/pdf`, { params: { onlyPassed, method }, responseType: 'blob' });
