package com.forge.admin.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.forge.admin.common.permission.DataPermissionRuleHandler;
import com.forge.admin.common.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置
 *
 * @author standadmin
 */
@Configuration
@RequiredArgsConstructor
public class MybatisPlusConfig {

    private final DataPermissionRuleHandler dataPermissionRuleHandler;

    /**
     * MyBatis-Plus 拦截器配置
     *
     * 注意：拦截器顺序很重要！
     * 1. 数据权限拦截器（标准 DataPermissionInterceptor）必须最先执行，确保 COUNT 查询也被过滤
     * 2. 分页插件
     * 3. 乐观锁插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 数据权限插件（标准 DataPermissionInterceptor，使用 MultiDataPermissionHandler）
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(dataPermissionRuleHandler));
        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * 自动填充处理器
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                Long userId = SecurityUtils.getCurrentUserId();
                if (userId != null) {
                    this.strictInsertFill(metaObject, "createBy", Long.class, userId);
                    this.strictInsertFill(metaObject, "updateBy", Long.class, userId);
                }
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                Long userId = SecurityUtils.getCurrentUserId();
                if (userId != null) {
                    this.strictUpdateFill(metaObject, "updateBy", Long.class, userId);
                }
            }
        };
    }
}
