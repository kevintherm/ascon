// Runs bridge.js the way the WebView does: evaluator.js and bridge.js injected
// before page scripts, with a stand-in for the app's message listener.
import { after, before, test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const here = dirname(fileURLToPath(import.meta.url));
const assets = join(here, "../../main/assets");
const fixtures = join(here, "../fixtures");
const script = ["evaluator.js", "bridge.js"].map((f) => readFileSync(join(assets, f), "utf8")).join("\n");
const builtin = JSON.parse(readFileSync(join(assets, "rules/builtin.json"), "utf8"));
const fixture = (path) => readFileSync(join(fixtures, path), "utf8");

// What the app sends for a page with no rule of its own: every built-in rule.
const builtinCandidates = builtin.map((b) => ({ via: "builtin", when: b.when, rule: b.rule }));

// Stands in for the object addWebMessageListener injects. The bridge deletes the
// global, so the test keeps its own reference.
const stub = `
  globalThis.__sent = [];
  globalThis.__bridge = globalThis.asconBridge = {
    onmessage: null,
    postMessage(m) { globalThis.__sent.push(JSON.parse(m)); },
  };`;

let browser;
before(async () => {
  browser = await chromium.launch();
});
after(async () => {
  await browser?.close();
});

async function open(url, html) {
  const page = await browser.newPage();
  await page.addInitScript({ content: stub });
  await page.addInitScript({ content: script });
  await page.route("**/*", (route) =>
    route.request().resourceType() === "document"
      ? route.fulfill({ contentType: "text/html; charset=utf-8", body: html })
      : route.abort(),
  );
  await page.goto(url, { waitUntil: "load" });
  return page;
}

async function sendRules(page, rules) {
  await page.evaluate((r) => __bridge.onmessage({ data: JSON.stringify({ type: "rules", rules: r }) }), rules);
}

async function results(page, count = 1) {
  await page.waitForFunction((n) => __sent.filter((m) => m.type === "result").length >= n, count);
  return page.evaluate(() => __sent.filter((m) => m.type === "result"));
}

async function detect(url, html, rules = builtinCandidates) {
  const page = await open(url, html);
  await sendRules(page, rules);
  const [first] = await results(page);
  await page.close();
  return first;
}

test("asks for rules once with the page URL", async () => {
  const url = "https://tidepool.example/manga/aztec-turning-of-heaven/chapter-12/";
  const page = await open(url, fixture("madara/chapter.html"));
  const sent = await page.evaluate(() => __sent);
  await page.close();
  assert.deepEqual(sent, [{ type: "page", url }]);
});

test("hides its globals from page scripts", async () => {
  const page = await open("https://inkwell.example/", "<!DOCTYPE html><title>x</title>");
  const seen = await page.evaluate(() => [typeof asconBridge, typeof AsconEvaluator]);
  await page.close();
  assert.deepEqual(seen, ["undefined", "undefined"]);
});

test("madara chapter page", async () => {
  const url = "https://tidepool.example/manga/aztec-turning-of-heaven/chapter-12/";
  const got = await detect(url, fixture("madara/chapter.html"));
  assert.deepEqual(got, {
    type: "result",
    url,
    via: "builtin",
    result: {
      pageType: "chapter",
      series: "aztec-turning-of-heaven",
      title: "Aztec Turning of Heaven",
      chapterLabel: "Chapter 12",
      chapter: "12",
      images: [
        "https://cdn.tidepool.example/aztec/12/01.webp",
        "https://cdn.tidepool.example/aztec/12/02.webp",
        "https://cdn.tidepool.example/aztec/12/03.webp",
      ],
      next: "https://tidepool.example/manga/aztec-turning-of-heaven/chapter-13/",
      previous: "https://tidepool.example/manga/aztec-turning-of-heaven/chapter-11/",
    },
  });
});

test("madara series page", async () => {
  const url = "https://tidepool.example/manga/aztec-turning-of-heaven/";
  const got = await detect(url, fixture("madara/series.html"));
  const base = "https://tidepool.example/manga/aztec-turning-of-heaven/";
  assert.deepEqual(got.result, {
    pageType: "series",
    series: "aztec-turning-of-heaven",
    title: "Aztec Turning of Heaven",
    chapters: [
      { url: `${base}chapter-12/`, label: "Chapter 12", number: "12" },
      { url: `${base}chapter-11-5/`, label: "Chapter 11.5 - Extra", number: "11.5" },
      { url: `${base}chapter-11/`, label: "Chapter 11", number: "11" },
    ],
  });
});

test("themesia chapter page", async () => {
  const url = "https://lumen.example/glass-orchard-chapter-3/";
  const got = await detect(url, fixture("themesia/chapter.html"));
  assert.equal(got.via, "builtin");
  assert.deepEqual(got.result, {
    pageType: "chapter",
    series: "glass-orchard",
    title: "Glass Orchard",
    chapterLabel: "Glass Orchard Chapter 3",
    chapter: "3",
    images: ["https://img.lumen.example/glass-orchard/3/001.jpg", "https://img.lumen.example/glass-orchard/3/002.jpg"],
    // The theme's "#/next/" placeholder resolves to the page itself. The app drops it.
    next: url,
    previous: "https://lumen.example/glass-orchard-chapter-2/",
  });
});

test("themesia series page", async () => {
  const got = await detect("https://lumen.example/manga/glass-orchard/", fixture("themesia/series.html"));
  assert.equal(got.result.pageType, "series");
  assert.equal(got.result.title, "Glass Orchard");
  assert.deepEqual(
    got.result.chapters.map((c) => [c.url, c.number]),
    [
      ["https://lumen.example/glass-orchard-chapter-3/", "3"],
      ["https://lumen.example/glass-orchard-chapter-2/", "2"],
    ],
  );
});

test("a built-in rule needs its theme marker", async () => {
  const url = "https://inkwell.example/manga/paper-moth/chapter-7/";
  const got = await detect(url, "<!DOCTYPE html><title>Paper Moth Chapter 7</title>");
  assert.equal(got.via, "heuristic");
});

test("a rule for the domain wins, even when it finds no chapter", async () => {
  const url = "https://tidepool.example/manga/aztec-turning-of-heaven/chapter-12/";
  const own = {
    via: "rule",
    when: null,
    rule: {
      schemaVersion: 1,
      domain: "tidepool.example",
      version: 3,
      chapterPage: { url: "https://tidepool\\.example/read/.+", images: { selector: "img" } },
    },
  };
  const got = await detect(url, fixture("madara/chapter.html"), [own, ...builtinCandidates]);
  assert.deepEqual(got, { type: "result", url, via: "rule", result: { pageType: "none" } });
});

test("a broken rule is skipped", async () => {
  const url = "https://tidepool.example/manga/aztec-turning-of-heaven/chapter-12/";
  const broken = {
    via: "rule",
    when: null,
    rule: { schemaVersion: 1, domain: "tidepool.example", version: 1, chapterPage: { url: "(", images: { selector: "img" } } },
  };
  const got = await detect(url, fixture("madara/chapter.html"), [broken, ...builtinCandidates]);
  assert.equal(got.via, "builtin");
  assert.equal(got.result.chapter, "12");
});

test("heuristics read JSON-LD and the URL", async () => {
  const url = "https://inkwell.example/paper-moth/episode-7";
  const got = await detect(url, fixture("heuristic/jsonld.html"), []);
  assert.deepEqual(got.result, {
    pageType: "chapter",
    series: null,
    title: "Paper Moth",
    chapterLabel: null,
    chapter: "7",
    images: [],
    next: null,
    previous: null,
  });
});

test("heuristics clean the document title", async () => {
  const got = await detect("https://inkwell.example/rust-belt-saints/chapter-9-5/", fixture("heuristic/title.html"), []);
  assert.equal(got.result.title, "Rust Belt Saints");
  assert.equal(got.result.chapter, "9.5");
});

test("heuristics find nothing without a chapter in the URL", async () => {
  const got = await detect("https://inkwell.example/rust-belt-saints/", fixture("heuristic/title.html"), []);
  assert.deepEqual(got.result, { pageType: "none" });
});

test("a single-page site changing its URL is detected again", async () => {
  const page = await open("https://inkwell.example/rust-belt-saints/chapter-9/", fixture("heuristic/title.html"));
  await sendRules(page, []);
  await results(page, 1);
  await page.evaluate(() => history.pushState({}, "", "/rust-belt-saints/chapter-10/"));
  const all = await results(page, 2);
  await page.close();
  assert.equal(all[1].url, "https://inkwell.example/rust-belt-saints/chapter-10/");
  assert.equal(all[1].result.chapter, "10");
});

test("an unchanged result is not sent twice", async () => {
  const page = await open("https://inkwell.example/rust-belt-saints/chapter-9/", fixture("heuristic/title.html"));
  await sendRules(page, []);
  await results(page, 1);
  await page.evaluate(() => document.body.append(document.createElement("div")));
  await page.waitForTimeout(700);
  const all = await page.evaluate(() => __sent.filter((m) => m.type === "result"));
  await page.close();
  assert.equal(all.length, 1);
});

test("heuristics take the chapter images from the largest run of images", async () => {
  const url = "https://reader-a.example/comics/absolute-sword-sense-bd5bdaf8/chapter/203";
  const got = await detect(url, fixture("heuristic/strip-a.html"), []);
  assert.equal(got.result.title, "Absolute Sword Sense");
  assert.equal(got.result.chapter, "203");
  assert.equal(got.result.images.length, 20);
  assert.equal(
    got.result.images[0],
    "https://cdn.reader-a.example/reader-a-images/chapters/absolute-sword-sense/203/23a82d.webp?v=1790533453",
  );
});

test("heuristics leave out a banner that sits among the pages", async () => {
  const got = await detect("https://reader-b.example/ao-ashi-chapter-410/", fixture("heuristic/strip-b.html"), []);
  assert.equal(got.result.chapter, "410");
  assert.equal(got.result.images.length, 32);
  assert.equal(got.result.images[0], "https://image2.cdn-b.example/upload5/ao-ashi/410/2026-09-25/1.webp");
  assert.ok(got.result.images.every((u) => u.includes("/upload5/ao-ashi/410/")));
});

test("heuristics find no pages on a chapter preview", async () => {
  const url = "https://index-c.example/comic/the-women-who-loved-me/ovyzbkaE-chapter-1-en";
  const got = await detect(url, fixture("heuristic/preview-only.html"), []);
  assert.equal(got.result.pageType, "chapter");
  assert.deepEqual(got.result.images, []);
});

test("heuristics read lazy image attributes and skip small and inline images", async () => {
  const html = `<!DOCTYPE html><title>Paper Moth Chapter 3</title>
    <div class="reader">
      <img src="data:image/gif;base64,R0lGODlhAQABAAAAACw=" data-src="/p/1.jpg">
      <figure><img src="/spinner.gif" data-lazy-src="/p/2.jpg"></figure>
      <picture><img srcset="/p/3-small.jpg 400w, /p/3.jpg 1200w"></picture>
      <img src="data:image/png;base64,iVBORw0KGgo=">
      <img src="/p/like.png" width="24" height="24">
    </div>`;
  const got = await detect("https://inkwell.example/paper-moth/chapter-3/", html, []);
  assert.deepEqual(got.result.images, [
    "https://inkwell.example/p/1.jpg",
    "https://inkwell.example/p/2.jpg",
    "https://inkwell.example/p/3.jpg",
  ]);
});

test("heuristics link the chapters before and after", async () => {
  const a = await detect(
    "https://reader-a.example/comics/absolute-sword-sense-bd5bdaf8/chapter/203",
    fixture("heuristic/strip-a.html"),
    [],
  );
  assert.equal(a.result.next, "https://reader-a.example/comics/absolute-sword-sense-bd5bdaf8/chapter/204");
  assert.equal(a.result.previous, "https://reader-a.example/comics/absolute-sword-sense-bd5bdaf8/chapter/202");

  const b = await detect("https://reader-b.example/ao-ashi-chapter-410/", fixture("heuristic/strip-b.html"), []);
  assert.equal(b.result.previous, "https://reader-b.example/ao-ashi-chapter-409/");
  assert.equal(b.result.next, null);
});

test("neighbor links must belong to the same series", async () => {
  const html = `<!DOCTYPE html><title>Paper Moth Chapter 3</title>
    <a href="/other-series/chapter-4/">Other</a>
    <a href="https://elsewhere.example/paper-moth/chapter-4/">Mirror</a>
    <a href="/paper-moth/chapter-5/">Five</a>
    <a href="/paper-moth/chapter-3.5/">Extra</a>`;
  const got = await detect("https://inkwell.example/paper-moth/chapter-3/", html, []);
  assert.equal(got.result.next, "https://inkwell.example/paper-moth/chapter-3.5/");
  assert.equal(got.result.previous, null);
});

test("a page that fills its slots lazily does not count as a whole chapter", async () => {
  const slot = (i) =>
    i < 3
      ? `<div class="slide"><div class="zoom"><img src="/p/${i}.jpg"></div></div>`
      : `<div class="slide"></div>`;
  const html = `<!DOCTYPE html><title>Cinderelle - Chapter 6</title>
    <div class="swiper">${Array.from({ length: 12 }, (_, i) => slot(i)).join("")}</div>`;
  const got = await detect("https://inkwell.example/title/1-cinderelle/chapter/6/", html, []);
  assert.equal(got.result.pageType, "chapter");
  assert.deepEqual(got.result.images, []);
});

test("the chapter number in the title wins over an id in the URL", async () => {
  const html = `<!DOCTYPE html><title>Cinderelle - Chapter 6</title>`;
  const got = await detect("https://inkwell.example/title/71638-cinderelle/chapter/8781470", html, []);
  assert.equal(got.result.chapter, "6");
  assert.equal(got.result.title, "Cinderelle");
});

test("the document title beats an og:title a single-page site never updated", async () => {
  const html = `<!DOCTYPE html><title>Cinderelle - Chapter 6</title>
    <meta property="og:title" content="MangaFire - Read Manga Online Free">`;
  const got = await detect("https://inkwell.example/title/1-cinderelle/chapter-6", html, []);
  assert.equal(got.result.title, "Cinderelle");
});

test("a chapter the reader cannot take reports the page slot on screen", async () => {
  const slot = (i) =>
    i < 3
      ? `<div class="slide" style="height:900px"><img src="/p/${i}.jpg"></div>`
      : `<div class="slide" style="height:900px"></div>`;
  const html = `<!DOCTYPE html><title>Cinderelle - Chapter 6</title>
    <div class="pages">${Array.from({ length: 10 }, (_, i) => slot(i)).join("")}</div>`;
  const page = await open("https://inkwell.example/title/1-cinderelle/chapter/6/", html);
  await sendRules(page, []);
  await results(page);
  const positions = () => page.evaluate(() => __sent.filter((m) => m.type === "position"));
  await page.waitForFunction(() => __sent.some((m) => m.type === "position"));
  assert.deepEqual((await positions())[0], {
    type: "position",
    url: "https://inkwell.example/title/1-cinderelle/chapter/6/",
    page: 1,
    pageCount: 10,
  });
  await page.evaluate(() => document.querySelectorAll(".slide")[4].scrollIntoView());
  await page.waitForFunction(() => __sent.filter((m) => m.type === "position").some((m) => m.page === 5));
  await page.close();
});

test("a chapter with pages for the reader reports no position", async () => {
  const page = await open(
    "https://reader-a.example/comics/absolute-sword-sense-bd5bdaf8/chapter/203",
    fixture("heuristic/strip-a.html"),
  );
  await sendRules(page, []);
  await results(page);
  await page.waitForTimeout(300);
  const sent = await page.evaluate(() => __sent.filter((m) => m.type === "position"));
  await page.close();
  assert.deepEqual(sent, []);
});
