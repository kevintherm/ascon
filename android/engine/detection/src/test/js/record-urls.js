// Rewrites the expected URLs in contracts/fixtures/urls.json with what
// Chromium gives. To add a case, append {"raw": "..."} and run
// `npm run record-urls`, then review the diff.
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const here = dirname(fileURLToPath(import.meta.url));
const fixturePath = join(here, "../../../../../../contracts/fixtures/urls.json");
const evaluatorSource = readFileSync(join(here, "../../main/assets/evaluator.js"), "utf8");
const fixture = JSON.parse(readFileSync(fixturePath, "utf8"));

const browser = await chromium.launch();
const page = await browser.newPage();
await page.addInitScript({ content: evaluatorSource });
await page.route("**/*", (route) =>
  route.request().resourceType() === "document"
    ? route.fulfill({ contentType: "text/html", body: "<!DOCTYPE html><title>x</title>" })
    : route.abort(),
);
await page.goto(fixture.base);
const urls = await page.evaluate(
  ([raws, base]) => raws.map((r) => AsconEvaluator.resolveUrl(r, base)),
  [fixture.cases.map((c) => c.raw), fixture.base],
);
await browser.close();

fixture.cases = fixture.cases.map((c, i) => ({ raw: c.raw, url: urls[i] }));
writeFileSync(fixturePath, JSON.stringify(fixture, null, 2) + "\n");
console.log(`recorded ${urls.length} URLs`);
