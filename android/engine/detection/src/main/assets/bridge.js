/*
 * Ascon detection bridge.
 *
 * Injected at document start right after evaluator.js, in the main frame only.
 * It asks the app for the rules that apply to this page, evaluates them as the
 * page changes, and reports what it found. When no rule applies it falls back
 * to heuristics: JSON-LD, og:title, a chapter number in the URL and the
 * largest run of images.
 *
 * Messages are JSON strings.
 *   page → app  {"type":"page","url":...}
 *   app → page  {"type":"rules","rules":[{"via":...,"when":selector|null,"rule":{...}}]}
 *   page → app  {"type":"result","url":...,"via":"rule"|"builtin"|"heuristic","result":{...}}
 */
(function () {
  "use strict";

  if (window.top !== window) return;
  if (location.protocol !== "https:" && location.protocol !== "http:") return;

  // Keep private references, then remove both globals so page scripts, which run
  // later, can neither fingerprint Ascon nor swap the evaluator.
  var evaluator = globalThis.AsconEvaluator;
  var bridge = globalThis.asconBridge;
  try { delete globalThis.AsconEvaluator; } catch (e) { /* not configurable */ }
  try { delete globalThis.asconBridge; } catch (e) { /* not configurable */ }
  if (!evaluator || !bridge) return;

  var postMessage = bridge.postMessage.bind(bridge);
  var parse = JSON.parse;
  var stringify = JSON.stringify;

  // Pages that build themselves keep changing after load. Re-evaluate on DOM
  // changes for this long after each navigation, at most once per SETTLE_MS.
  var WATCH_MS = 15000;
  var SETTLE_MS = 400;

  var candidates = null;
  var lastReport = null;
  var lastUrl = null;
  var timer = 0;
  var watchUntil = 0;

  function send(message) {
    postMessage(stringify(message));
  }

  bridge.onmessage = function (event) {
    var message;
    try {
      message = parse(event.data);
    } catch (e) {
      return;
    }
    if (message && message.type === "rules" && Array.isArray(message.rules)) {
      candidates = message.rules;
      // Before DOMContentLoaded the page is half parsed; that event runs detection.
      if (document.readyState !== "loading") schedule(0);
    }
  };

  // ---- Heuristics -------------------------------------------------------

  var CHAPTER_IN_PATH = /(?:^|[\/_.-])(?:chapter|chap|ch|episode|ep)[-_./]?(\d+(?:[-.]\d+)?)(?=$|[\/_.-])/;
  var CHAPTER_NUMBER_IN_TITLE = /\b(?:chapter|chap|ch|episode|ep)\b\.?\s*#?(\d+(?:\.\d+)?)/i;
  var CHAPTER_IN_TITLE = /[\s\-–—|:,]*\b(?:chapter|chap|ch|episode|ep)\b\.?\s*#?\d[\s\S]*$/i;
  var TITLE_SEPARATOR = /\s+[|–—-]\s+/;
  var READ_PREFIX = /^read\s+/i;
  var ISSUE_TYPES = ["ComicIssue", "Chapter", "Episode"];
  var SERIES_TYPES = ["ComicSeries", "Book", "BookSeries", "CreativeWorkSeries"];

  function hasType(node, types) {
    var t = node["@type"];
    var list = Array.isArray(t) ? t : [t];
    return list.some(function (x) { return types.indexOf(x) >= 0; });
  }

  function nameOf(node) {
    if (!node) return null;
    if (typeof node === "string") return node;
    return typeof node.name === "string" ? node.name : null;
  }

  function jsonLdTitle() {
    var scripts = document.querySelectorAll('script[type="application/ld+json"]');
    for (var i = 0; i < scripts.length; i++) {
      var data;
      try {
        data = parse(scripts[i].textContent);
      } catch (e) {
        continue;
      }
      var nodes = [].concat(data);
      if (data && Array.isArray(data["@graph"])) nodes = nodes.concat(data["@graph"]);
      for (var j = 0; j < nodes.length; j++) {
        var node = nodes[j];
        if (!node || typeof node !== "object") continue;
        if (hasType(node, ISSUE_TYPES) && nameOf(node.isPartOf)) return nameOf(node.isPartOf);
        if (hasType(node, SERIES_TYPES) && nameOf(node)) return nameOf(node);
      }
    }
    return null;
  }

  function metaContent(selector) {
    var el = document.querySelector(selector);
    return el ? el.getAttribute("content") : null;
  }

  /** "Read Aztec Turning of Heaven Chapter 12 - MangaFire" gives "Aztec Turning of Heaven". */
  function cleanTitle(raw) {
    if (!raw) return null;
    var t = raw.replace(/\s+/g, " ").trim();
    t = t.split(TITLE_SEPARATOR).map(function (part) {
      return part.replace(CHAPTER_IN_TITLE, "").replace(READ_PREFIX, "").trim();
    }).filter(Boolean)[0] || "";
    return t === "" ? null : t;
  }

  // Pages of a chapter are images stacked in one container. Fewer than this
  // many is a preview or a gallery, not a chapter.
  var MIN_PAGES = 3;
  // Images narrower than this, by their width attribute, are icons.
  var MIN_WIDTH = 100;
  var LAZY_ATTRIBUTES = ["data-src", "data-lazy-src", "data-original"];

  function imageUrl(img, base) {
    for (var i = 0; i < LAZY_ATTRIBUTES.length; i++) {
      var lazy = evaluator.resolveUrl(img.getAttribute(LAZY_ATTRIBUTES[i]), base);
      if (lazy) return lazy;
    }
    var srcset = img.getAttribute("srcset");
    var best = srcset ? evaluator.resolveUrl(evaluator.largestCandidate(srcset), base) : null;
    return best || evaluator.resolveUrl(img.getAttribute("src"), base);
  }

  /** The element that holds an image and its siblings, skipping wrappers around one image. */
  function holderOf(img) {
    var el = img;
    while (el.parentElement && el.parentElement.children.length === 1) el = el.parentElement;
    return el.parentElement;
  }

  function directoryOf(url) {
    var path = new URL(url).pathname;
    return path.slice(0, path.lastIndexOf("/") + 1);
  }

  /**
   * Keeps the images that share the most common directory, when at least half do.
   * That drops a banner placed among the pages without hurting sites whose pages
   * each live in their own directory.
   */
  function sameDirectory(urls) {
    var counts = {};
    var top = null;
    urls.forEach(function (u) {
      var d = directoryOf(u);
      counts[d] = (counts[d] || 0) + 1;
      if (top === null || counts[d] > counts[top]) top = d;
    });
    if (counts[top] * 2 < urls.length) return urls;
    return urls.filter(function (u) { return directoryOf(u) === top; });
  }

  /**
   * Children with neither an image nor text. A reader that fills page slots only near
   * the page on screen has many; its images are then a few pages, not the chapter.
   */
  function emptySlots(holder) {
    var n = 0;
    for (var i = 0; i < holder.children.length; i++) {
      var child = holder.children[i];
      if (child.tagName !== "IMG" && !child.querySelector("img") && child.textContent.trim() === "") n++;
    }
    return n;
  }

  /** Image URLs from the container that holds the most images, in document order. */
  function pageImages(base) {
    var groups = [];
    var imgs = document.images;
    for (var i = 0; i < imgs.length; i++) {
      var img = imgs[i];
      var width = parseInt(img.getAttribute("width"), 10);
      if (width < MIN_WIDTH) continue;
      var url = imageUrl(img, base);
      var holder = url && holderOf(img);
      if (!holder) continue;
      var group = groups.filter(function (g) { return g.holder === holder; })[0];
      if (!group) groups.push(group = { holder: holder, urls: [] });
      if (group.urls.indexOf(url) < 0) group.urls.push(url);
    }
    var best = groups.reduce(function (a, g) { return a && a.urls.length >= g.urls.length ? a : g; }, null);
    if (!best || best.urls.length < MIN_PAGES || emptySlots(best.holder) >= best.urls.length) return [];
    var pages = sameDirectory(best.urls);
    return pages.length < MIN_PAGES ? [] : pages;
  }

  /** Where the chapter number sits in a URL, or null when it names no chapter. */
  function chapterInUrl(url) {
    var u;
    try {
      u = new URL(url);
    } catch (e) {
      return null;
    }
    var path = u.pathname.toLowerCase();
    var m = CHAPTER_IN_PATH.exec(path);
    if (!m) return null;
    return {
      before: u.origin + path.slice(0, m.index),
      after: path.slice(m.index + m[0].length),
      raw: m[1],
      number: parseFloat(evaluator.parseChapterNumber(m[1]))
    };
  }

  /**
   * The nearest chapters before and after, from links that differ from this page's URL
   * only in the chapter number. Those belong to the same series and language.
   */
  function neighbors(here, base) {
    var next = null, previous = null, nextN = Infinity, previousN = -Infinity;
    var links = document.querySelectorAll("a[href]");
    for (var i = 0; i < links.length; i++) {
      var url = evaluator.resolveUrl(links[i].getAttribute("href"), base);
      var c = url && chapterInUrl(url);
      if (!c || c.before !== here.before || c.after !== here.after || isNaN(c.number)) continue;
      if (c.number > here.number && c.number < nextN) { next = url; nextN = c.number; }
      if (c.number < here.number && c.number > previousN) { previous = url; previousN = c.number; }
    }
    return { next: next, previous: previous };
  }

  // No series runs to this many chapters; a number this large in a URL is an id.
  var MAX_CHAPTER = 5000;

  /** "Cinderelle - Chapter 6" gives "6". */
  function chapterInTitle() {
    var titles = [document.title, metaContent('meta[property="og:title"]')];
    for (var i = 0; i < titles.length; i++) {
      var m = titles[i] && CHAPTER_NUMBER_IN_TITLE.exec(titles[i]);
      if (m) return evaluator.parseChapterNumber(m[1]);
    }
    return null;
  }

  function heuristic(url) {
    var here = chapterInUrl(url);
    if (!here) return { pageType: "none" };
    var around = neighbors(here, url);
    return {
      pageType: "chapter",
      series: null,
      title: cleanTitle(jsonLdTitle() || document.title || metaContent('meta[property="og:title"]')),
      chapterLabel: null,
      // The title can lag behind a single-page site's URL, so it wins only over an id.
      chapter: here.number >= MAX_CHAPTER ? chapterInTitle() : evaluator.parseChapterNumber(here.raw),
      images: pageImages(url),
      next: around.next,
      previous: around.previous
    };
  }

  // ---- Evaluation -------------------------------------------------------

  /*
   * Candidates come in lookup order. A rule made for this domain is trusted
   * even when it says the page is neither a chapter nor a series page. A
   * built-in theme rule only counts when its marker element is present and
   * it recognizes the page.
   */
  function detect(url) {
    for (var i = 0; i < candidates.length; i++) {
      var c = candidates[i];
      try {
        if (c.when && !document.querySelector(c.when)) continue;
        var result = evaluator.evaluate(c.rule, url);
        if (c.via === "rule" || result.pageType !== "none") return { via: c.via, result: result };
      } catch (e) {
        // A broken rule never stops detection; the next candidate gets its turn.
      }
    }
    return { via: "heuristic", result: heuristic(url) };
  }

  function run() {
    timer = 0;
    if (candidates == null || !document.documentElement) return;
    var url = location.href;
    var found = detect(url);
    var report = stringify({ type: "result", url: url, via: found.via, result: found.result });
    if (report !== lastReport) {
      lastReport = report;
      postMessage(report);
    }
  }

  function schedule(delay) {
    if (timer) return;
    timer = setTimeout(run, delay);
  }

  function urlChanged() {
    var url = location.href;
    if (url === lastUrl) return;
    lastUrl = url;
    watchUntil = Date.now() + WATCH_MS;
    schedule(SETTLE_MS);
  }

  // Single-page sites change the URL without loading a new document.
  ["pushState", "replaceState"].forEach(function (name) {
    var original = history[name];
    history[name] = function () {
      var r = original.apply(this, arguments);
      urlChanged();
      return r;
    };
  });
  window.addEventListener("popstate", urlChanged);
  window.addEventListener("hashchange", urlChanged);
  document.addEventListener("DOMContentLoaded", function () {
    schedule(0);
    new MutationObserver(function () {
      if (Date.now() < watchUntil) schedule(SETTLE_MS);
    }).observe(document.documentElement, { childList: true, subtree: true });
  });
  window.addEventListener("load", function () { schedule(0); });

  lastUrl = location.href;
  watchUntil = Date.now() + WATCH_MS;
  send({ type: "page", url: lastUrl });
})();
