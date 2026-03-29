package com.forge.admin.modules.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统附件实体
 */
@Data
@TableName("sys_attachment")
public class SysAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文件名称 */
    private String fileName;

    /** 原始文件名 */
    private String originalName;

    /** 文件路径 */
    private String filePath;

    /** 文件URL */
    private String fileUrl;

    /** 文件大小(字节) */
    private Long fileSize;

    /** 文件类型(MIME类型) */
    private String fileType;

    /** 文件扩展名 */
    private String fileExtension;

    /** 存储类型(local/oss等) */
    private String storageType;

    /** 业务类型 */
    private String bizType;

    /** 业务ID */
    private Long bizId;

    /** 上传者ID */
    private Long uploaderId;

    /** 上传者名称 */
    private String uploaderName;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
