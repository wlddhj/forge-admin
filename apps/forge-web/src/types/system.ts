// 用户
export interface User {
  id: number
  username: string
  nickname: string
  phone: string
  email: string
  avatar: string
  deptId: number
  deptName: string
  positionId: number
  positionName: string
  accountType: number
  status: number
  lastLoginTime: string
  lastLoginIp: string
  createTime: string
  roleIds: number[]
  roleNames: string[]
}

// 用户查询参数
export interface UserQuery {
  pageNum: number
  pageSize: number
  username?: string
  nickname?: string
  phone?: string
  status?: number
  deptId?: number
}

// 用户请求
export interface UserRequest {
  id?: number
  username: string
  nickname: string
  password?: string
  phone?: string
  email?: string
  avatar?: string
  deptId?: number
  positionId?: number
  accountType?: number
  status?: number
  roleIds?: number[]
}

// 角色
export interface Role {
  id: number
  roleName: string
  roleCode: string
  description: string
  isFixed: number
  status: number
  dataScope: string
  sortOrder: number
  deptIds?: number[]
  createTime: string
}

// 角色查询参数
export interface RoleQuery {
  pageNum: number
  pageSize: number
  roleName?: string
  roleCode?: string
  status?: number
}

// 角色请求
export interface RoleRequest {
  id?: number
  roleName: string
  roleCode: string
  description?: string
  isFixed?: number
  status?: number
  dataScope?: string
  deptIds?: number[]
  sortOrder?: number
}

// 数据权限范围枚举
export enum DataScope {
  ALL = '1',           // 全部数据权限
  CUSTOM = '2',        // 自定义数据权限
  DEPT = '3',          // 本部门数据权限
  DEPT_AND_CHILD = '4', // 本部门及以下数据权限
  SELF = '5'           // 仅本人数据权限
}

// 数据权限范围选项
export const DataScopeOptions = [
  { label: '全部数据权限', value: DataScope.ALL },
  { label: '自定义数据权限', value: DataScope.CUSTOM },
  { label: '本部门数据权限', value: DataScope.DEPT },
  { label: '本部门及以下数据权限', value: DataScope.DEPT_AND_CHILD },
  { label: '仅本人数据权限', value: DataScope.SELF }
]

// 菜单
export interface Menu {
  id: number
  menuName: string
  parentId: number
  routePath: string
  componentPath: string
  redirectPath: string
  icon: string
  sortOrder: number
  menuType: number
  permission: string
  status: number
  visible: number
  isExternal: number
  isCached: number
  createTime: string
}

// 菜单树
export interface MenuTree extends Menu {
  children?: MenuTree[]
}

// 菜单请求
export interface MenuRequest {
  id?: number
  menuName: string
  parentId?: number
  routePath?: string
  componentPath?: string
  redirectPath?: string
  icon?: string
  sortOrder?: number
  menuType?: number
  permission?: string
  status?: number
  visible?: number
  isExternal?: number
  isCached?: number
}

// 部门
export interface Dept {
  id: number
  deptName: string
  parentId: number
  ancestors: string
  leader: string
  email: string
  phone: string
  status: number
  sortOrder: number
  createTime: string
}

// 部门树
export interface DeptTree extends Dept {
  children?: DeptTree[]
}

// 部门请求
export interface DeptRequest {
  id?: number
  deptName: string
  parentId?: number
  leader?: string
  email?: string
  phone?: string
  status?: number
  sortOrder?: number
}

// 操作日志
export interface OperationLog {
  id: number
  title: string
  businessType: string
  requestMethod: string
  requestUrl: string
  operatorId: number
  operatorName: string
  deptName: string
  operateIp: string
  operateLocation: string
  requestParam: string
  jsonResult: string
  status: number
  errorMsg: string
  operateTime: string
  costTime: number
}

// 操作日志查询参数
export interface OperationLogQuery {
  pageNum: number
  pageSize: number
  title?: string
  operatorName?: string
  businessType?: string
  status?: number
  startTime?: string
  endTime?: string
}

// 字典类型
export interface DictType {
  id: number
  dictName: string
  dictType: string
  status: number
  isSystem: number
  remark: string
  createTime: string
}

// 字典数据
export interface DictData {
  id: number
  dictType: string
  dictLabel: string
  dictValue: string
  dictSort: number
  cssClass: string
  listClass: string
  status: number
  remark: string
  createTime: string
}

// 字典数据查询参数
export interface DictDataQuery {
  pageNum: number
  pageSize: number
  dictType?: string
  dictLabel?: string
  status?: number
}

// 岗位
export interface Position {
  id: number
  positionName: string
  positionCode: string
  deptId: number
  deptName: string
  sortOrder: number
  status: number
  createTime: string
}

// 岗位查询参数
export interface PositionQuery {
  pageNum: number
  pageSize: number
  positionName?: string
  status?: number
}

// 岗位请求
export interface PositionRequest {
  id?: number
  positionName: string
  positionCode: string
  deptId?: number
  sortOrder?: number
  status?: number
}

// 系统配置
export interface Config {
  id: number
  configName: string
  configKey: string
  configValue: string
  configType: string
  configGroup: string
  isSystem: number
  remark: string
  createTime: string
}

// 系统配置查询参数
export interface ConfigQuery {
  pageNum: number
  pageSize: number
  configName?: string
  configKey?: string
  configGroup?: string
  status?: number
}

// 系统配置请求
export interface ConfigRequest {
  id?: number
  configName: string
  configKey: string
  configValue?: string
  configType?: string
  configGroup?: string
  isSystem?: number
  remark?: string
}

// 附件
export interface Attachment {
  id: number
  fileName: string
  originalName: string
  filePath: string
  fileUrl: string
  fileSize: number
  fileType: string
  fileExtension: string
  storageType: string
  bizType: string
  bizId: number
  uploaderId: number
  uploaderName: string
  createTime: string
}

// 附件查询参数
export interface AttachmentQuery {
  pageNum: number
  pageSize: number
  fileName?: string
  fileType?: string
  storageType?: string
  uploaderName?: string
  startTime?: string
  endTime?: string
}

// 定时任务
export interface Job {
  id: number
  jobName: string
  jobGroup: string
  invokeTarget: string
  cronExpression: string
  status: number
  concurrent: number
  remark: string
  createTime: string
  nextValidTime?: string
}

// 定时任务查询参数
export interface JobQuery {
  pageNum: number
  pageSize: number
  jobName?: string
  jobGroup?: string
  status?: number
}

// 定时任务请求
export interface JobRequest {
  id?: number
  jobName: string
  jobGroup?: string
  invokeTarget: string
  cronExpression: string
  status?: number
  concurrent?: number
  remark?: string
}

// 任务执行日志
export interface JobLog {
  id: number
  jobId: number
  jobName: string
  jobGroup: string
  invokeTarget: string
  jobMessage: string
  status: number
  exceptionInfo: string
  startTime: string
  endTime: string
  duration: number
  createTime: string
}

// 任务日志查询参数
export interface JobLogQuery {
  pageNum: number
  pageSize: number
  jobId?: number
  jobName?: string
  status?: number
}

// 公告
export interface Notice {
  id: number
  noticeTitle: string
  noticeType: number
  noticeContent: string
  status: number
  isTop: number
  createTime: string
}

// 公告查询参数
export interface NoticeQuery {
  pageNum: number
  pageSize: number
  noticeTitle?: string
  noticeType?: number
  status?: number
}

// 公告请求
export interface NoticeRequest {
  id?: number
  noticeTitle: string
  noticeType: number
  noticeContent: string
  status?: number
  isTop?: number
}
