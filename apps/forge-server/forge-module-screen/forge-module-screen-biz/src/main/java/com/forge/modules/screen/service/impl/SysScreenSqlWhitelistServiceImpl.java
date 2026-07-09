package com.forge.modules.screen.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.modules.screen.entity.SysScreenSqlWhitelist;
import com.forge.modules.screen.mapper.SysScreenSqlWhitelistMapper;
import com.forge.modules.screen.service.SysScreenSqlWhitelistService;
import org.springframework.stereotype.Service;

/**
 * 大屏 SQL 白名单 Service 实现
 *
 * @author standadmin
 */
@Service
public class SysScreenSqlWhitelistServiceImpl
        extends ServiceImpl<SysScreenSqlWhitelistMapper, SysScreenSqlWhitelist>
        implements SysScreenSqlWhitelistService {
}
