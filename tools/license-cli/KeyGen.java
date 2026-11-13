import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * 插件 License 密钥对生成工具（一次性使用）。
 *
 * 用法：java KeyGen.java
 * 输出：PKCS#8 私钥与 X.509 公钥（Base64）。
 *
 * 私钥离线保管（密码管理器/加密盘），严禁进入任何仓库；
 * 公钥部署到 forge-module-plugin 的 classpath:plugin-license-public.key
 * 或配置项 forge.plugin.license.public-key。
 */
public class KeyGen {

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        System.out.println("===== 私钥 PKCS#8（离线保管，勿入仓库/勿分发） =====");
        System.out.println(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        System.out.println();
        System.out.println("===== 公钥 X.509（部署为 plugin-license-public.key） =====");
        System.out.println(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
    }
}
