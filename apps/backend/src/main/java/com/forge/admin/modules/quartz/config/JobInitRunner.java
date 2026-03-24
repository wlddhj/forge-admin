package com.forge.admin.modules.quartz.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.forge.admin.common.annotation.DataPermission;
import com.forge.admin.modules.quartz.service.ScheduleService;
import com.forge.admin.modules.system.entity.SysJob;
import com.forge.admin.modules.system.mapper.SysJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 系统启动时加载定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobInitRunner implements ApplicationRunner {

    private final SysJobMapper sysJobMapper;
    private final ScheduleService scheduleService;

    @Override
    @DataPermission(enable = false) // 启动时禁用数据权限
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始加载定时任务...");

        // 查询所有状态为正常的任务
        LambdaQueryWrapper<SysJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysJob::getStatus, 1);

        List<SysJob> jobs = sysJobMapper.selectList(wrapper);

        for (SysJob job : jobs) {
            try {
                scheduleService.scheduleJob(job);
                log.info("加载定时任务: {} - {}", job.getJobName(), job.getCronExpression());
            } catch (Exception e) {
                log.error("加载定时任务失败: {}", job.getJobName(), e);
            }
        }

        log.info("定时任务加载完成，共加载 {} 个任务", jobs.size());
    }
}
