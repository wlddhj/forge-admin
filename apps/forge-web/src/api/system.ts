import request, { PageResult } from '@/utils/request'
import type {
  User, UserQuery, UserRequest,
  Role, RoleQuery, RoleRequest,
  Menu, MenuTree, MenuRequest,
  Dept, DeptTree, DeptRequest,
  OperationLog, OperationLogQuery,
  DictData, DictDataQuery, Config, ConfigQuery, ConfigRequest,
  Position, PositionQuery, PositionRequest,
  Attachment, AttachmentQuery,
  Job, JobQuery, JobRequest,
  JobLog, JobLogQuery
} from '@/types/system'

// ==================== 用户管理 ====================

export function getUserList(params: UserQuery) {
  return request.get<PageResult<User>>('/system/user/list', { params }).then(res => res.data)
}

export function getUser(id: number) {
  return request.get<User>(`/system/user/${id}`).then(res => res.data)
}

export function addUser(data: UserRequest) {
  return request.post('/system/user', data)
}

export function updateUser(data: UserRequest) {
  return request.put('/system/user', data)
}

export function deleteUser(ids: number[]) {
  return request.delete(`/system/user/${ids.join(',')}`)
}

export function updateUserStatus(id: number, status: number) {
  return request.put(`/system/user/${id}/status`, null, { params: { status } })
}

export function resetPassword(id: number) {
  return request.put(`/system/user/${id}/reset-password`)
}

export function exportUsers(params: UserQuery) {
  return request.get('/system/user/export', { params, responseType: 'blob' }).then(res => {
    const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = '用户列表.xlsx'
    link.click()
    URL.revokeObjectURL(link.href)
  })
}

// ==================== 角色管理 ====================

export function getRoleList(params: RoleQuery) {
  return request.get<PageResult<Role>>('/system/role/list', { params }).then(res => res.data)
}

export function getAllRoles() {
  return request.get<Role[]>('/system/role/all').then(res => res.data)
}

export function getRole(id: number) {
  return request.get<Role>(`/system/role/${id}`).then(res => res.data)
}

export function addRole(data: RoleRequest) {
  return request.post('/system/role', data)
}

export function updateRole(data: RoleRequest) {
  return request.put('/system/role', data)
}

export function deleteRole(ids: number[]) {
  return request.delete(`/system/role/${ids.join(',')}`)
}

export function updateRoleStatus(id: number, status: number) {
  return request.put(`/system/role/${id}/status`, null, { params: { status } })
}

export function getRoleMenus(id: number) {
  return request.get<number[]>(`/system/role/${id}/menus`).then(res => res.data)
}

export function assignRoleMenus(id: number, menuIds: number[]) {
  return request.put(`/system/role/${id}/menus`, menuIds)
}

export function exportRoles(params: RoleQuery) {
  return request.get('/system/role/export', { params, responseType: 'blob' }).then(res => {
    const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = '角色列表.xlsx'
    link.click()
    URL.revokeObjectURL(link.href)
  })
}

// ==================== 菜单管理 ====================

export function getMenuList(params?: { menuName?: string; status?: number }) {
  return request.get<Menu[]>('/system/menu/list', { params }).then(res => res.data)
}

export function getMenuTree() {
  return request.get<MenuTree[]>('/system/menu/tree').then(res => res.data)
}

export function getMenu(id: number) {
  return request.get<Menu>(`/system/menu/${id}`).then(res => res.data)
}

export function addMenu(data: MenuRequest) {
  return request.post('/system/menu', data)
}

export function updateMenu(data: MenuRequest) {
  return request.put('/system/menu', data)
}

export function deleteMenu(id: number) {
  return request.delete(`/system/menu/${id}`)
}

// ==================== 部门管理 ====================

export function getDeptList(params?: { deptName?: string; status?: number }) {
  return request.get<Dept[]>('/system/dept/list', { params }).then(res => res.data)
}

export function getDeptTree() {
  return request.get<DeptTree[]>('/system/dept/tree').then(res => res.data)
}

export function getDept(id: number) {
  return request.get<Dept>(`/system/dept/${id}`).then(res => res.data)
}

export function addDept(data: DeptRequest) {
  return request.post('/system/dept', data)
}

export function updateDept(data: DeptRequest) {
  return request.put('/system/dept', data)
}

export function deleteDept(id: number) {
  return request.delete(`/system/dept/${id}`)
}

// ==================== 操作日志 ====================

export function getOperationLogList(params: OperationLogQuery) {
  return request.get<PageResult<OperationLog>>('/system/operation-log/list', { params }).then(res => res.data)
}

export function getOperationLog(id: number) {
  return request.get<OperationLog>(`/system/operation-log/${id}`).then(res => res.data)
}

export function clearOperationLogs() {
  return request.delete('/system/operation-log/clear')
}

export function exportOperationLogs(params: OperationLogQuery) {
  return request.get('/system/operation-log/export', { params, responseType: 'blob' }).then(res => {
    const blob = new Blob([res as any], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = '操作日志.xlsx'
    link.click()
    URL.revokeObjectURL(link.href)
  })
}

// ==================== 字典管理 ====================

export function getDictTypeList(params: { dictName?: string; dictType?: string; status?: number; pageNum: number; pageSize: number }) {
  return request.get<PageResult<DictType>>('/system/dict-type/list', { params }).then(res => res.data)
}

export function getAllDictTypes() {
  return request.get<DictType[]>('/system/dict-type/all').then(res => res.data)
}

export function getDictType(id: number) {
  return request.get<DictType>(`/system/dict-type/${id}`).then(res => res.data)
}

export function addDictType(data: { dictName: string; dictType: string; status?: number; remark?: string }) {
  return request.post('/system/dict-type', data)
}

export function updateDictType(data: { id: number; dictName: string; dictType: string; status?: number; remark?: string }) {
  return request.put('/system/dict-type', data)
}

export function deleteDictType(ids: number[]) {
  return request.delete(`/system/dict-type/${ids.join(',')}`)
}

export function getDictDataByType(dictType: string) {
  return request.get<DictData[]>(`/system/dict-data/type/${dictType}`).then(res => res.data)
}

export function getDictDataList(params: DictDataQuery) {
  return request.get<PageResult<DictData>>('/system/dict-data/list', { params }).then(res => res.data)
}

export function addDictData(data: { dictType: string; dictLabel: string; dictValue: string; dictSort?: number; status?: number; remark?: string }) {
  return request.post('/system/dict-data', data)
}

export function updateDictData(data: { id: number; dictType: string; dictLabel: string; dictValue: string; dictSort?: number; status?: number; remark?: string }) {
  return request.put('/system/dict-data', data)
}

export function deleteDictData(ids: number[]) {
  return request.delete(`/system/dict-data/${ids.join(',')}`)
}

// ==================== 系统配置 ====================

export function getConfigList(params: ConfigQuery) {
  return request.get<PageResult<Config>>('/system/config/list', { params }).then(res => res.data)
}

export function getConfigByKey(key: string) {
  return request.get<string>(`/system/config/key/${key}`).then(res => res.data)
}

export function addConfig(data: ConfigRequest) {
  return request.post('/system/config', data)
}

export function updateConfig(data: ConfigRequest) {
  return request.put('/system/config', data)
}

export function deleteConfig(ids: number[]) {
  return request.delete(`/system/config/${ids.join(',')}`)
}

// ==================== 岗位管理 ====================

export function getPositionList(params: PositionQuery) {
  return request.get<PageResult<Position>>('/system/position/list', { params }).then(res => res.data)
}

export function getAllPositions() {
  return request.get<Position[]>('/system/position/all').then(res => res.data)
}

export function getPosition(id: number) {
  return request.get<Position>(`/system/position/${id}`).then(res => res.data)
}

export function addPosition(data: PositionRequest) {
  return request.post('/system/position', data)
}

export function updatePosition(data: PositionRequest) {
  return request.put('/system/position', data)
}

export function deletePosition(ids: number[]) {
  return request.delete(`/system/position/${ids.join(',')}`)
}

export function updatePositionStatus(id: number, status: number) {
  return request.put(`/system/position/${id}/status`, null, { params: { status } })
}

// ==================== 附件管理 ====================

export function getAttachmentList(params: AttachmentQuery) {
  return request.get<PageResult<Attachment>>('/system/attachment/list', { params }).then(res => res.data)
}

export function getAttachment(id: number) {
  return request.get<Attachment>(`/system/attachment/${id}`).then(res => res.data)
}

export function deleteAttachment(ids: number[]) {
  return request.delete(`/system/attachment/${ids.join(',')}`)
}

// ==================== Dashboard ====================

export function getDashboardStats() {
  return request.get<DashboardStats>('/system/dashboard/stats').then(res => res.data)
}

export interface DashboardStats {
  userCount: number
  roleCount: number
  menuCount: number
  logCount: number
  deptCount: number
  positionCount: number
  dictCount: number
  configCount: number
}

// ==================== 登录日志 ====================

export interface LoginLog {
  id: number
  username: string
  loginIp: string
  loginLocation: string
  browser: string
  os: string
  status: number
  msg: string
  loginTime: string
}

export interface LoginLogQuery {
  pageNum: number
  pageSize: number
  username?: string
  loginIp?: string
  status?: number
  startTime?: string
  endTime?: string
}

export function getLoginLogList(params: LoginLogQuery) {
  return request.get<PageResult<LoginLog>>('/system/login-log/list', { params }).then(res => res.data)
}

export function clearLoginLogs() {
  return request.delete('/system/login-log/clear')
}

export function exportLoginLogs(params: LoginLogQuery) {
  return request.get('/system/login-log/export', { params, responseType: 'blob' }).then(res => {
    const blob = new Blob([res as any], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = '登录日志.xlsx'
    link.click()
    URL.revokeObjectURL(link.href)
  })
}

// ==================== 在线用户 ====================

export interface OnlineUser {
  tokenId: string
  userId: number
  username: string
  nickname: string
  loginIp: string
  loginLocation: string
  browser: string
  os: string
  loginTime: number
  lastActiveTime: number
  ttl: number
  status: 'online' | 'idle'
}

export function getOnlineUsers() {
  return request.get<OnlineUser[]>('/system/online-user/list').then(res => res.data)
}

export function forceLogout(tokenId: string) {
  return request.delete(`/system/online-user/${tokenId}`)
}

// ==================== 定时任务 ====================

export function getJobList(params: JobQuery) {
  return request.get<PageResult<Job>>('/system/job/list', { params }).then(res => res.data)
}

export function getJob(id: number) {
  return request.get<Job>(`/system/job/${id}`).then(res => res.data)
}

export function addJob(data: JobRequest) {
  return request.post('/system/job', data)
}

export function updateJob(data: JobRequest) {
  return request.put('/system/job', data)
}

export function deleteJob(ids: number[]) {
  return request.delete(`/system/job/${ids.join(',')}`)
}

export function changeJobStatus(id: number, status: number) {
  return request.put(`/system/job/${id}/status`, null, { params: { status } })
}

export function runJobOnce(id: number) {
  return request.post(`/system/job/${id}/run`)
}

// ==================== 任务日志 ====================

export function getJobLogList(params: JobLogQuery) {
  return request.get<PageResult<JobLog>>('/system/job-log/list', { params }).then(res => res.data)
}

export function getJobLog(id: number) {
  return request.get<JobLog>(`/system/job-log/${id}`).then(res => res.data)
}

export function clearJobLogs(jobId?: number) {
  return request.delete('/system/job-log/clear', { params: { jobId } })
}

// ==================== 通知公告 ====================

export interface Notice {
  id: number
  noticeTitle: string
  noticeType: number
  noticeContent: string
  status: number
  createByName: string
  createTime: string
  updateTime: string
  remark: string
}

export interface NoticeQuery {
  pageNum: number
  pageSize: number
  noticeTitle?: string
  noticeType?: number
  status?: number
}

export interface NoticeRequest {
  id?: number
  noticeTitle: string
  noticeType: number
  noticeContent: string
  status?: number
  remark?: string
}

export function getNoticeList(params: NoticeQuery) {
  return request.get<PageResult<Notice>>('/system/notice/list', { params }).then(res => res.data)
}

export function getLatestNotices(limit?: number) {
  return request.get<Notice[]>('/system/notice/latest', { params: { limit } }).then(res => res.data)
}

export function getNotice(id: number) {
  return request.get<Notice>(`/system/notice/${id}`).then(res => res.data)
}

export function addNotice(data: NoticeRequest) {
  return request.post('/system/notice', data)
}

export function updateNotice(data: NoticeRequest) {
  return request.put('/system/notice', data)
}

export function deleteNotice(ids: number[]) {
  return request.delete(`/system/notice/${ids.join(',')}`)
}
