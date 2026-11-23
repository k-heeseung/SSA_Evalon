import api from './client';

// 매니저 계정 관리 (최고 관리자 전용). 계정 생성/삭제는 공용 /users 엔드포인트를 사용한다.
export const getManagers = () => api.get('/coordinators');
// sendInvitation: true면 서버가 계정 생성과 같은 트랜잭션에서 안내 메일을 발송 대기열에 넣는다.
export const createManager = (data) => api.post('/users', { ...data, role: 'MANAGER' });
export const deleteManager = (userId) => api.delete(`/users/${userId}`);
export const getManagerInvitations = () => api.get('/manager-invitations');
export const retryManagerInvitation = (id) => api.post(`/managers/${id}/invitation/retry`);
