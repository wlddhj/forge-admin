package com.forge.admin.common.permission;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.schema.Table;
import org.springframework.stereotype.Component;

/**
 * 数据权限规则处理器
 *
 * 实现 MyBatis-Plus 的 MultiDataPermissionHandler 接口
 * 基于 shi9-boot 设计
 *
 * @author standadmin
 * @since 2026-03-04
 */
@Slf4j
@Component
public class DataPermissionRuleHandler implements MultiDataPermissionHandler {

    private final DataPermissionRuleFactory ruleFactory;

    public DataPermissionRuleHandler(DataPermissionRuleFactory ruleFactory) {
        this.ruleFactory = ruleFactory;
        log.info("[数据权限规则处理器] 初始化完成");
    }

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        // 默认关闭数据权限，只有使用 @DataPermission 注解时才启用
        var context = DataPermissionContextHolder.peek();
        if (context == null) {
            return null;
        }

        // 获取 Mapper 对应的数据权限规则
        var rules = ruleFactory.getEnabledRules(mappedStatementId);
        if (CollUtil.isEmpty(rules)) {
            return null;
        }

        // 获取表名
        String tableName = table.getName();

        // 生成条件
        Expression allExpression = null;
        for (var rule : rules) {
            // 判断表名是否匹配
            if (!rule.getTableNames().contains(tableName)) {
                continue;
            }

            // 单条规则的条件
            Expression oneExpression = rule.getExpression(tableName, table.getAlias());
            if (oneExpression == null) {
                continue;
            }

            // 拼接到 allExpression 中（使用 AND 连接）
            allExpression = allExpression == null ? oneExpression
                    : new AndExpression(allExpression, oneExpression);

            log.debug("[数据权限规则处理器] 规则 {} 生效 - 表: {}, 条件: {}",
                    rule.getClass().getSimpleName(), tableName, oneExpression);
        }

        return allExpression;
    }
}
