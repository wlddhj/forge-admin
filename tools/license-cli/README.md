# 插件 License 离线签发工具

零第三方依赖，JDK 21 单文件直跑。**本目录任何产物不含私钥**；私钥离线保管（密码管理器/加密盘），严禁进入仓库或随部署分发。

## 一次性：生成密钥对

```bash
cd tools/license-cli
java KeyGen.java
# 私钥 PKCS#8 (Base64) → 离线保存为 private-key.b64
# 公钥 X.509 (Base64) → forge-module-plugin/src/main/resources/plugin-license-public.key
```

> 密钥对一经投用不要更换（换钥=全部存量 License 失效）；确需更换时：新公钥随版发布 + 重新签发全部 License。

## 签发 License

### 1. 编写 payload.json

```json
{
  "licenseKey": "LIC-20261113-0001",
  "pluginCodes": ["demo"],
  "tenantCode": "tenant-a",
  "licensee": "某某科技有限公司",
  "issuedAt": "2026-11-13T00:00:00+08:00",
  "notBefore": "2026-11-13T00:00:00+08:00",
  "notAfter": "2027-11-13T00:00:00+08:00",
  "scopes": ["user:write"],
  "extra": {}
}
```

字段约束：

| 字段 | 必填 | 说明 |
|------|------|------|
| licenseKey | 是 | 全局唯一编号，重复导入会被底座拒绝 |
| pluginCodes | 是 | 授权插件编码数组（一码多插件；原子性：任一未注册则整个文件拒绝） |
| tenantCode | 是 | 绑定租户 `sys_tenant.code`（登录标识，全局唯一稳定） |
| licensee | 是 | 被授权方公司主体名（仅展示，不参与强校验） |
| issuedAt / notBefore / notAfter | 是 | ISO-8601 含时区偏移；notAfter 当日仍有效 |
| scopes | 否 | 内置数据 scope，必须 ⊆ 对应插件 `declaredScopes()` 声明集，否则导入被拒 |
| extra | 否 | 扩展位（未来按版本售卖等，本期底座不校验） |

### 2. 签发

```bash
java IssueLicense.java payload.json private-key.b64 tenant-a-demo-2027.lic
```

输出 license 文件（`{"payload":"<Base64>","signature":"<Base64>"}`）+ canonical 化的 payload 明文供人工核对。

### 3. 导入

管理后台 → 插件管理 → 租户授权抽屉 → License 导入（上传 .lic 文件）。

## canonical 序列化说明

签发端对 payload 做 canonical 化（对象 key 字典序、紧凑无空白）后签名；验签端（`forge-spring-boot-starter-plugin` 的 `LicenseVerifier`）对 Base64 解码出的**原始字节**验签，不重排序——两端不依赖同一套 JSON 库，验签零歧义。

## 在线签发（页面，双模式）

底座配置 `forge.plugin.license.private-key`（Base64 PKCS#8，生产建议 jasypt `ENC(...)` 加密）后，License 管理页出现「在线签发」表单，与 CLI 产物完全互通：

```yaml
forge:
  plugin:
    license:
      private-key: ENC(xxxxxx)   # jasypt 加密；dev 可明文 Base64
```

- **自营/内部部署**：配置私钥 → 页面直接签发（可签发并落库生效，或仅下载 .lic）。
- **私有化交付**：**不配置**（私钥绝不进客户部署）→ 页面仅导入，签发只走本 CLI。

jasypt 加密生成（PWD 为你的盐密）：`java -cp jasypt-1.9.3.jar org.jasypt.intf.cli.JasyptPBEStringEncryptionCLI input="<私钥Base64>" password=PWD algorithm=PBEWITHHMACSHA512ANDAES_256`
