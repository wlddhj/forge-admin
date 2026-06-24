package com.forge.modules.workflow.config;

/**
 * FlowLong 工作流扩展组件配置说明
 *
 * 所有扩展组件通过 @Component/@Service 注解自动注册，无需在此显式配置：
 *
 * 监听器（继承 FlowLong EventListener）：
 * - CompositeTaskListener (@Component("taskListener")) - 复合任务监听器
 * - InstanceEventListener (@Component("instanceListener")) - 实例监听器
 *
 * 处理器（实现 FlowLong 接口）：
 * - ForgeTaskAccessStrategy (@Component) - 任务访问策略
 * - PermissionTaskCreateInterceptor (@Component) - 任务创建拦截器
 * - TaskTriggerHandler (@Component) - 任务触发器处理器
 * - TaskReminderHandler (@Component) - 任务提醒处理器
 *
 * 业务处理器（不实现 FlowLong 接口）：
 * - BpmTaskCandidateListener (@Service) - 候选人处理器
 * - TaskNotificationListener (@Service) - 通知处理器
 *
 * FlowLong 自动配置通过 @Autowired(required = false) 注入以上组件
 * 由于 @ConditionalOnMissingBean，我们的 Bean 会优先注册
 *
 * @author forge-admin
 */
// @Configuration - 不需要，所有组件通过注解自动注册
public class FlowLongWorkflowConfig {
    // 无需显式 Bean 注册
}