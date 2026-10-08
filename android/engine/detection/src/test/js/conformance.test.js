// Loads every conformance fixture in Chromium at its own URL, with
// evaluator.js injected before page scripts, as the WebView does.
import { after, before, test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const here = dirname(fileURLToPath(import.meta.url));
const contracts = join(here, "../../../../../../contracts");
const evaluatorSource = readFileSync(join(here, "../../main/assets/evaluator.js"), "utf8");
const readJson = (path) => JSON.parse(readFileSync(path, "utf8"));

let browser;
before(async () => {
  browser = await chromium.launch();
});
after(async () => {
  await browser?.close();
});

// Serves html for the page itself and blocks every other request, so
// fixtures never reach the network.
async function openFixture(url, html) {
  const page = await browser.newPage({ javaScriptEnabled: true });
  await page.addInitScript({ content: evaluatorSource });
  await page.route("**/*", (route) =>
    route.request().resourceType() === "document"
      ? route.fulfill({ contentType: "text/html; charset=utf-8", body: html })
      : route.abort(),
  );
  await page.goto(url, { waitUntil: "domcontentloaded" });
  return page;
}

test("chapter numbers", async () => {
  const cases = readJson(join(contracts, "fixtures/chapter-numbers.json"));
  const page = await openFixture("https://chapters.example/", "<!DOCTYPE html><title>x</title>");
  const got = await page.evaluate(
    (labels) => labels.map((l) => AsconEvaluator.parseChapterNumber(l)),
    cases.map((c) => c.label),
  );
  await page.close();
  cases.forEach((c, i) => assert.equal(got[i], c.number, `label ${JSON.stringify(c.label)}`));
});

test("url resolution", async () => {
  const { base, cases } = readJson(join(contracts, "fixtures/urls.json"));
  const page = await openFixture(base, "<!DOCTYPE html><title>x</title>");
  const got = await page.evaluate(
    ([raws, b]) => raws.map((r) => AsconEvaluator.resolveUrl(r, b)),
    [cases.map((c) => c.raw), base],
  );
  await page.close();
  cases.forEach((c, i) => assert.equal(got[i], c.url, `raw ${JSON.stringify(c.raw)}`));
});

const fixtureRoot = join(contracts, "fixtures/conformance");
for (const site of readdirSync(fixtureRoot)) {
  const dir = join(fixtureRoot, site);
  const rule = readJson(join(dir, "rule.json"));
  for (const c of readJson(join(dir, "cases.json"))) {
    test(`${site}: ${c.name}`, async () => {
      const page = await openFixture(c.url, readFileSync(join(dir, c.html), "utf8"));
      const got = await page.evaluate(
        ([r, url]) => AsconEvaluator.evaluate(r, url),
        [rule, c.url],
      );
      await page.close();
      assert.deepEqual(got, c.expected);
    });
  }
}
