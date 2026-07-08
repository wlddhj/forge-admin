import { test, expect } from '@playwright/test'

test.describe('大屏渲染页', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
    await page.fill('input[name="username"]', 'admin')
    await page.fill('input[name="password"]', 'admin123')
    await page.click('button[type="submit"]')
    await page.waitForURL('**/dashboard')
  })

  test('已登录用户访问 /screen/operations 渲染大屏', async ({ page }) => {
    await page.goto('/screen/operations')
    await expect(page.locator('.screen-renderer')).toBeVisible({ timeout: 10_000 })
    await expect(page).toHaveScreenshot('render-operations.png', { maxDiffPixelRatio: 0.05 })
  })
})
