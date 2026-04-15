package com.forge.admin.common.utils;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Excel 导出工具类
 *
 * @author standadmin
 */
@Slf4j
public class ExcelUtils {

    private ExcelUtils() {
    }

    /**
     * 导出 Excel 到响应流
     * 先写入内存缓冲区，成功后再写入响应流，避免写入失败时响应已提交无法返回错误信息
     *
     * @param response  HTTP 响应
     * @param fileName  文件名（不含扩展名）
     * @param sheetName Sheet 名称
     * @param clazz     数据类
     * @param data      数据列表
     */
    public static <T> void export(HttpServletResponse response, String fileName, String sheetName,
                                  Class<T> clazz, List<T> data) {
        try {
            // 先写入内存缓冲区
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            EasyExcel.write(baos, clazz)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet(sheetName)
                    .doWrite(data);

            // 写入成功后，再设置响应头并输出
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encodedFileName + ".xlsx");
            response.setContentLength(baos.size());
            baos.writeTo(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("导出Excel失败", e);
            if (e.getCause() != null) {
                log.error("导出Excel失败 - 根因", e.getCause());
            }
            throw new RuntimeException("导出Excel失败: " + e.getMessage()
                    + (e.getCause() != null ? ", 原因: " + e.getCause().getMessage() : ""));
        }
    }

    /**
     * 从上传文件中读取 Excel 数据
     *
     * @param file  上传的 Excel 文件
     * @param clazz 数据类
     * @return 数据列表
     */
    public static <T> List<T> read(MultipartFile file, Class<T> clazz) {
        try {
            return EasyExcel.read(file.getInputStream(), clazz, null)
                    .autoCloseStream(false)
                    .doReadAllSync();
        } catch (Exception e) {
            log.error("读取Excel失败", e);
            throw new RuntimeException("读取Excel失败: " + e.getMessage());
        }
    }

    /**
     * 导出 Excel 到响应流（使用默认 Sheet 名称）
     *
     * @param response HTTP 响应
     * @param fileName 文件名（不含扩展名）
     * @param clazz    数据类
     * @param data     数据列表
     */
    public static <T> void export(HttpServletResponse response, String fileName,
                                  Class<T> clazz, List<T> data) {
        export(response, fileName, "Sheet1", clazz, data);
    }
}
