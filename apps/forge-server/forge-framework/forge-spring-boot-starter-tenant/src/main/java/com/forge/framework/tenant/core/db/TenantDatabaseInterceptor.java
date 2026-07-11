package com.forge.framework.tenant.core.db;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.forge.framework.tenant.config.TenantProperties;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.schema.Column;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TenantDatabaseInterceptor implements TenantLineHandler {

    private static final String TENANT_ID_COLUMN = "tenant_id";

    private final Set<String> ignoreTables;

    public TenantDatabaseInterceptor(TenantProperties properties) {
        this.ignoreTables = new HashSet<>();
        properties.getIgnoreTables().forEach(t -> {
            ignoreTables.add(t.toLowerCase());
            ignoreTables.add(t.toUpperCase());
        });
        ignoreTables.add("DUAL");
    }

    @Override
    public Expression getTenantId() {
        return new LongValue(TenantContextHolder.getRequiredTenantId());
    }

    @Override
    public String getTenantIdColumn() {
        return TENANT_ID_COLUMN;
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return TenantContextHolder.isIgnore() || CollUtil.contains(ignoreTables, tableName);
    }

    @Override
    public boolean ignoreInsert(List<Column> columns, String tenantIdColumn) {
        // 平台超管调用 setIgnore(true) 时，跳过 INSERT 自动填 tenantId
        return TenantContextHolder.isIgnore();
    }
}
