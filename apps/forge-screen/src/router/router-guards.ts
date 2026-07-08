import { Router } from 'vue-router';
import { PageEnum } from '@/enums/pageEnum'
import { loginCheck } from '@/utils'

export function createRouterGuards(router: Router) {
  // 前置
  router.beforeEach(async (to, from, next) => {
    // http://localhost:3000/#/chart/preview/792622755697790976?t=123
    // 把外部动态参数放入window.route.params，后续API动态接口可以用window.route?.params?.t来拼接参数
    // @ts-ignore
    if (!window.route) window.route = {params: {}}
    // @ts-ignore
    Object.assign(window.route.params, to.query)

    const Loading = window['$loading'];
    Loading && Loading.start();
    const isErrorPage = router.getRoutes().findIndex((item) => item.name === to.name);
    if (isErrorPage === -1) {
      next({ name: PageEnum.ERROR_PAGE_NAME_404 })
      return
    }

    // forge-admin 集成的路由（chart/preview/edit）：跳过 goView 自己的登录检查，
    // 鉴权由 forge-admin URL token 或后端 API 白名单负责。
    const forgeRoutes = ['ChartHome', 'ChartPreview', 'ChartEdit']
    if (forgeRoutes.includes(to.name as string)) {
      next()
      return
    }

    // goView 自身的页面（project 首页等）：保持原有登录逻辑
    if (!loginCheck()) {
      if (to.name === PageEnum.BASE_LOGIN_NAME) {
        next()
        return
      }
      next({ name: PageEnum.BASE_LOGIN_NAME })
      return
    }
    next()
  })

  router.afterEach((to, _, failure) => {
    const Loading = window['$loading'];
    document.title = (to?.meta?.title as string) || document.title;
    Loading && Loading.finish();
  })

  // 错误
  router.onError((error) => {
    console.log(error, '路由错误');
  });
}