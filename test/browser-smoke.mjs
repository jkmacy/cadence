// Browser-level smoke test for Cadence's temporary PWA presentation.
// Platform test harness only; scheduling rules live in Lisp sources.
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { existsSync, readFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright';

const root = path.resolve('public');
const types = {'.html':'text/html; charset=utf-8', '.css':'text/css; charset=utf-8',
  '.mjs':'text/javascript; charset=utf-8', '.js':'text/javascript; charset=utf-8',
  '.json':'application/json', '.webmanifest':'application/manifest+json',
  '.svg':'image/svg+xml', '.png':'image/png'};
const server = createServer((req,res)=>{
  const requested = new URL(req.url,'http://localhost').pathname;
  const file = path.resolve(root, '.' + requested, requested.endsWith('/') ? 'index.html' : '');
  if (!file.startsWith(root + path.sep) || !existsSync(file)) {res.writeHead(404);res.end('Not found');return;}
  const body=readFileSync(file);
  res.setHeader('Content-Type',types[path.extname(file)] || 'application/octet-stream');
  res.end(body);
});
await new Promise((ok)=>server.listen(8123,'127.0.0.1',ok));
const browser=await chromium.launch({headless:true});
const errors=[];
try {
  const mobile=await browser.newContext({viewport:{width:412,height:915},deviceScaleFactor:2,isMobile:true,hasTouch:true});
  const page=await mobile.newPage();
  page.on('pageerror',e=>errors.push('mobile: '+e.message));
  await page.goto('http://127.0.0.1:8123/',{waitUntil:'networkidle'});
  assert.equal(await page.locator('#screen-today').isVisible(),true,'Today default');
  assert.equal(await page.locator('.bottom-nav').isVisible(),true,'mobile navigation visible');
  await page.locator('#quickTitle').fill('Finish reading');
  await page.locator('#quickForm button[type=submit]').click();
  await page.getByText('Finish reading',{exact:true}).first().waitFor();
  const record=await page.evaluate(()=>JSON.parse(localStorage.getItem('cadence-local-private-v1')));
  assert(record.items.some(x=>x.title==='Finish reading'),'local data preserved');
  await page.locator('.bottom-nav [data-screen="calendar"]').click();
  assert.equal(await page.locator('#screen-calendar').isVisible(),true,'calendar opens');
  await page.locator('.bottom-nav [data-screen="plan"]').click();
  assert.equal(await page.locator('#screen-plan').isVisible(),true,'plan opens');
  await page.locator('.bottom-nav [data-screen="apps"]').click();
  assert.equal(await page.locator('#screen-apps').isVisible(),true,'apps open');
  assert.equal(await page.locator('#screen-apps a[href="https://jkmacy.github.io/solar-clock/"]').count(),1,'solar-clock link');
  await page.locator('.bottom-nav [data-screen="today"]').click();
  await page.reload({waitUntil:'networkidle'});
  await page.getByText('Finish reading',{exact:true}).first().waitFor();
  mkdirSync('test-results',{recursive:true});
  await page.screenshot({path:'test-results/cadence-2-mobile.png',fullPage:true});
  await mobile.close();
  const desktop=await browser.newContext({viewport:{width:1366,height:900}});
  const dpage=await desktop.newPage();
  dpage.on('pageerror',e=>errors.push('desktop: '+e.message));
  await dpage.goto('http://127.0.0.1:8123/',{waitUntil:'networkidle'});
  assert.equal(await dpage.locator('.sidebar').isVisible(),true,'desktop sidebar');
  await dpage.locator('.sidebar [data-screen="calendar"]').click();
  assert.equal(await dpage.locator('#screen-calendar').isVisible(),true,'desktop calendar');
  await dpage.locator('.sidebar [data-screen="today"]').click();
  await dpage.screenshot({path:'test-results/cadence-2-desktop.png',fullPage:true});
  await desktop.close();
  assert.deepEqual(errors,[],'no browser JS exceptions');
  console.log('Cadence 2 mobile/desktop browser smoke passed.');
} finally {
  await browser.close();
  await new Promise(ok=>server.close(ok));
}
