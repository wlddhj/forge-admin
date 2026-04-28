<template>
  <div class="social-login">
    <el-divider>
      <span class="divider-text">其他登录方式</span>
    </el-divider>
    <div class="social-buttons">
      <el-tooltip content="微信登录" placement="top">
        <el-button
          class="social-btn wechat-btn"
          circle
          @click="handleSocialLogin('wechat')"
        >
          <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
            <path d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 0 1 .213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 0 0 .167-.054l1.903-1.114a.864.864 0 0 1 .717-.098 10.16 10.16 0 0 0 2.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178A1.17 1.17 0 0 1 4.623 7.17c0-.651.52-1.18 1.162-1.18zm5.813 0c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178 1.17 1.17 0 0 1-1.162-1.178c0-.651.52-1.18 1.162-1.18zm3.845 4.006c-3.285 0-5.951 2.275-5.951 5.08 0 2.804 2.666 5.079 5.951 5.079.682 0 1.34-.095 1.964-.276a.71.71 0 0 1 .58.08l1.46.852a.28.28 0 0 0 .135.043.237.237 0 0 0 .234-.238c0-.058-.024-.116-.04-.173l-.3-1.13a.48.48 0 0 1 .174-.542c1.488-1.09 2.456-2.753 2.456-4.595 0-2.805-2.666-5.08-5.951-5.08h-.712zm-1.633 2.598c.52 0 .942.428.942.956a.949.949 0 0 1-.942.957.949.949 0 0 1-.942-.957c0-.528.422-.956.942-.956zm4.716 0c.52 0 .942.428.942.956a.949.949 0 0 1-.942.957.949.949 0 0 1-.942-.957c0-.528.422-.956.942-.956z"/>
          </svg>
        </el-button>
      </el-tooltip>
      <el-tooltip content="钉钉登录" placement="top">
        <el-button
          class="social-btn dingtalk-btn"
          circle
          @click="handleSocialLogin('dingtalk')"
        >
          <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
            <path d="M12 0C5.373 0 0 5.373 0 12s5.373 12 12 12 12-5.373 12-12S18.627 0 12 0zm5.562 12.152l-3.89 1.26s-.427.166-.304.49l.756 1.983s.213.567-.138.785c-.35.218-.735-.064-.735-.064l-2.574-1.87s-.277-.177-.558 0l-2.574 1.87s-.384.282-.735.064c-.35-.218-.138-.785-.138-.785l.756-1.983s.123-.324-.304-.49l-3.89-1.26s-.547-.177-.448-.611c.1-.434.636-.41.636-.41l4.09-.082s.381-.017.492-.39l1.274-3.838s.174-.536.625-.536.625.536.625.536l1.274 3.838c.11.373.493.39.493.39l4.089.082s.536-.024.636.41c.099.434-.448.611-.448.611z"/>
          </svg>
        </el-button>
      </el-tooltip>
    </div>
  </div>
</template>

<script setup lang="ts">
const handleSocialLogin = (source: string) => {
  // 打开新窗口进行 OAuth2 授权
  const width = 600
  const height = 500
  const left = (window.screen.width - width) / 2
  const top = (window.screen.height - height) / 2
  window.open(
    `/api/auth/social/authorize/${source}`,
    `${source}_login`,
    `width=${width},height=${height},left=${left},top=${top},menubar=0,scrollbars=1,resizable=1,status=1,titlebar=0,toolbar=0`
  )
}
</script>

<style scoped lang="scss">
.social-login {
  margin-top: 10px;

  .divider-text {
    font-size: 12px;
    color: var(--el-text-color-placeholder);
  }

  .social-buttons {
    display: flex;
    justify-content: center;
    gap: 20px;
  }

  .social-btn {
    width: 44px;
    height: 44px;
    border: 1px solid var(--el-border-color);
    transition: all 0.3s;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    }

    &:focus {
      outline: none;
    }
  }

  .wechat-btn {
    color: #07c160;
    border-color: #07c160;

    &:hover {
      background-color: #07c160;
      color: #fff;
    }
  }

  .dingtalk-btn {
    color: #0089ff;
    border-color: #0089ff;

    &:hover {
      background-color: #0089ff;
      color: #fff;
    }
  }
}
</style>
