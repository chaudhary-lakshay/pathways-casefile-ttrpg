import { expect, test, type Locator, type Page } from '@playwright/test'

// The owner's validation list: no backend, 390 px, the frame on desktop,
// progress that survives a reload, and a one-shot that a double tap charges once.

/** Fails the test on any request that leaves this origin. */
function onlyLocal(page: Page) {
  const foreign: string[] = []
  page.on('request', (r) => {
    const url = new URL(r.url())
    if (url.protocol === 'data:' || url.protocol === 'blob:') return
    if (url.hostname !== 'localhost') foreign.push(r.url())
  })
  return foreign
}

const fp = (page: Page) => page.locator('.pool-count').nth(1)

/**
 * Two taps inside one frame (6ei): both clicks in one task, so React has not
 * re-rendered between them and only the handler's own guard can stop the second.
 */
const doubleTap = (target: Locator) => target.evaluate((el: HTMLElement) => { el.click(); el.click() })

test.describe('on a phone, 390 px', () => {
  test.use({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })

  test('full screen, no frame, nothing wider than the screen, nothing fetched from elsewhere', async ({ page }) => {
    const foreign = onlyLocal(page)
    await page.goto('./')
    await expect(page.getByRole('heading', { name: 'Characters' })).toBeVisible()
    await expect(page.getByTestId('phone')).toHaveCount(0)
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)
    await page.getByText('Beat Cop').first().click()
    await expect(page.getByRole('heading', { name: 'Beat Cop' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)
    for (const route of ['#/c/Beat%20Cop/pathway', '#/glossary', '#/credits']) {
      await page.goto(`./${route}`)
      await expect(page.locator('h1, .pathway-tile').first()).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)
    }
    expect(foreign).toEqual([])
  })

  test('a double tap on Drink drinks one potion and rolls one PCR', async ({ page }) => {
    await page.goto('./#/c/Beat%20Cop/pathway')
    await page.getByText('Fool').first().click()
    await doubleTap(page.locator('.drink-button'))
    await expect(page.locator('.drink-button')).toContainText('Drink and roll')
    await expect(page.locator('.sequence-tile.selected')).toContainText('Seer')
    await doubleTap(page.locator('.drink-button'))
    await page.locator('.dialog').getByRole('button', { name: 'OK' }).click()
    await page.getByRole('button', { name: 'Back to sheet' }).click()
    await page.getByRole('button', { name: 'More' }).click()
    await page.getByRole('menuitem', { name: 'Roll log' }).click()
    await expect(page.locator('.dialog').getByText('Potion Consumption Roll')).toHaveCount(1)
  })

  test('a lost point survives a reload', async ({ page }) => {
    await page.goto('./#/c/Beat%20Cop')
    await page.getByRole('button', { name: 'Lose 1 HP' }).click()
    await expect(page.locator('.pool-count').first()).toHaveAttribute('aria-label', '9 of 10')
    await page.reload()
    await expect(page.locator('.pool-count').first()).toHaveAttribute('aria-label', '9 of 10')
  })

  test('a double tap on Roll throws once and burns once', async ({ page }) => {
    await page.goto('./#/c/Beat%20Cop')
    await expect(fp(page)).toHaveAttribute('aria-label', '9 of 9')
    await page.getByText('Guns (Pistol)').first().click()
    await page.getByRole('button', { name: /One more Burn FP/ }).click()
    await doubleTap(page.locator('.dialog .ink-button'))
    await expect(page.locator('.dialog .stamp')).toBeVisible()
    await page.getByRole('button', { name: 'Close' }).click()
    await expect(fp(page)).toHaveAttribute('aria-label', '8 of 9')
    await page.getByRole('button', { name: 'More' }).click()
    await page.getByRole('menuitem', { name: 'Roll log' }).click()
    await expect(page.locator('.dialog').getByText('Guns (Pistol)')).toHaveCount(1)
  })
})

test.describe('on a desktop', () => {
  test.use({ viewport: { width: 1280, height: 900 } })

  test('the app sits in an Android frame', async ({ page }) => {
    const foreign = onlyLocal(page)
    await page.goto('./')
    const phone = page.getByTestId('phone')
    await expect(phone).toBeVisible()
    const box = (await phone.boundingBox())!
    expect(box.width).toBeLessThan(500)
    await expect(phone.getByRole('heading', { name: 'Characters' })).toBeVisible()
    expect(foreign).toEqual([])
  })
})
