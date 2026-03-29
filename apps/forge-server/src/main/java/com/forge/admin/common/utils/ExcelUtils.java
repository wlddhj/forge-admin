package com.forge.admin.common.utils;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

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
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encodedFileName + ".xlsx");

            EasyExcel.write(response.getOutputStream(), clazz)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet(sheetName)
                    .doWrite(data);
        } catch (Exception e) {
            log.error("导出Excel失败", e);
            throw new RuntimeException("导出Excel失败: " + e.getMessage());
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
