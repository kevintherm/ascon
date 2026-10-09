/*
 * Ascon detection bridge.
 *
 * Injected at document start right after evaluator.js, in the main frame only.
 * It asks the app for the rules that apply to this page, evaluates them as the
 * page changes, and reports what it found. When no rule applies it falls back
 * to heuristics: JSON-LD, og:title and a chapter number in the URL.
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

  var CHAPTER_IN_PATH = /(?:^|[\/_.-])(?:chapter|chap|ch|episode|ep)[-_.]?(\d+(?:[-.]\d+)?)(?=$|[\/_.-])/;
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

  function heuristic(url) {
    var path;
    try {
      path = new URL(url).pathname.toLowerCase();
    } catch (e) {
      return { pageType: "none" };
    }
    var m = CHAPTER_IN_PATH.exec(path);
    if (!m) return { pageType: "none" };
    return {
      pageType: "chapter",
      series: null,
      title: cleanTitle(jsonLdTitle() || metaContent('meta[property="og:title"]') || document.title),
      chapterLabel: null,
      chapter: evaluator.parseChapterNumber(m[1]),
      images: [],
      next: null,
      previous: null
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
