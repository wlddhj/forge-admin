package com.forge.framework.mybatis.handler;

import com.forge.framework.mybatis.holder.CryptoUtilsHolder;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 敏感字段加密类型处理器
 * 写入时加密，读取时解密
 *
 * @author standadmin
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class EncryptTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        // 写入时加密
        String encrypted = CryptoUtilsHolder.get().encrypt(parameter);
        ps.setString(i, encrypted);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        // 读取时解密
        String value = rs.getString(columnName);
        if (value == null) {
            return null;
        }
        return CryptoUtilsHolder.get().decrypt(value);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        if (value == null) {
            return null;
        }
        return CryptoUtilsHolder.get().decrypt(value);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        if (value == null) {
            return null;
        }
        return CryptoUtilsHolder.get().decrypt(value);
    }
}