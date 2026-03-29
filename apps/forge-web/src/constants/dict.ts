/**
 * 数据字典类型常量
 * 统一维护所有数据字典类型
 */
export const DICT_TYPE = {
  /** 用户状态：0=禁用 1=正常 */
  SYS_NORMAL_DISABLE: 'sys_normal_disable',

  /** 用户性别：0=男 1=女 2=未知 */
  SYS_USER_SEX: 'sys_user_sex',

  /** 显示隐藏：0=隐藏 1=显示 */
  SYS_SHOW_HIDE: 'sys_show_hide',

  /** 任务状态：0=正常 1=暂停 2=完成 */
  SYS_JOB_STATUS: 'sys_job_status',

  /** 任务分组 */
  SYS_JOB_GROUP: 'sys_job_group',

  /** 通知类型 */
  SYS_NOTICE_TYPE: 'sys_notice_type',

  /** 通知状态 */
  SYS_NOTICE_STATUS: 'sys_notice_status',

  /** 通用状态：0=失败 1=成功（登录状态等） */
  SYS_COMMON_STATUS: 'sys_common_status',

  /** 文件存储类型 */
  SYS_STORAGE_TYPE: 'sys_storage_type',

  /** 成功/失败：0=失败 1=成功（与 sys_common_status 相同） */
  SYS_SUCCESS_FAIL: 'sys_common_status',

  /** 数据权限范围 */
  SYS_DATA_SCOPE: 'sys_data_scope',

  /** 配置参数类型 */
  SYS_CONFIG_TYPE: 'sys_config_type',

  /** 操作类型 */
  SYS_OPERATION_TYPE: 'sys_oper_type',

  /** 标签样式类型 */
  SYS_TAG_TYPE: 'sys_tag_type'
} as const
