package com.forge.modules.workflow.framework;

import com.aizuda.bpm.engine.FlowLongIdGenerator;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.stereotype.Component;

/**
 * FlowLong 引擎主键生成器——替换默认透传实现为雪花 ID
 *
 * 根因：引擎全部 insert 路径（任务/历史任务/参与者/实例/定义）显式调用生成器取 id，
 * 而 DefaultFlowLongIdGenerator 为空操作（null 进 null 出）；叠加应用全局
 * mybatis-plus id-type=auto（MP 对 null id 不填充），flw_ 表 id 完全依赖 MySQL
 * 各表独立的自增序列——子流程 callProcess 归档与常规任务归档的序列交叠即主键冲突。
 *
 * 本 Bean 经引擎 starter 的 @ConditionalOnMissingBean 接管：id 为空时生成雪花，
 * 非空原样返回（保留引擎"复活历史任务沿用原 id"的语义）。
 * flw_ 表 AUTO_INCREMENT 属性保留不动（显式雪花 id 插入不受影响）。
 *
 * @author forge-admin
 */
@Component
public class FlowLongSnowflakeIdGenerator implements FlowLongIdGenerator {

    @Override
    public Long getId(Long id) {
        return id != null ? id : IdWorker.getId();
    }
}
