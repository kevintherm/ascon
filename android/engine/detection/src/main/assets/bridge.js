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
 *   page → app  {"type":"position","url":...,"page":n,"pageCount":m,"offset":f}
 *   app → page  {"type":"scroll","page":n,"pageCount":m,"offset":f}
 *   page → app  {"type":"tap","url":...,"href":link|null}
 *   page → app  {"type":"structure","url":...,"via":...,"features":[...]}
 *   app → page  {"type":"snapshot"}
 *   page → app  {"type":"snapshot","url":...,"chapter":n|null,"html":...}
 *
 * A position is sent on every chapter page: the page slot under the middle of the
 * screen, counted from 1, and how far down that slot the middle is, from 0 up to 1.
 * It is sent at once when the slot changes and again when scrolling stops. It saves
 * progress on a chapter read as it is, and opens the reader at the same page when the
 * user switches to it. A scroll from the app puts that place back in the middle of the
 * screen, scaled to this page's slots, as when the user resumes a chapter read as the
 * site shows it.
 *
 * A tap reports the link under the finger, or null, for the navigation guard: a tap
 * only lets the tab go to another site through the link that was tapped.
 *
 * A chapter page found without the site's own rule sends its structure once: its
 * generator meta tag, class names and parent>child tag pairs. The app turns them into
 * a fingerprint and may answer with new rules, borrowed from a site built the same way.
 *
 * When nothing can be borrowed and the user has AI detection, the app asks for a
 * snapshot: the page reduced to its skeleton, with scripts, styles, form fields and
 * most attributes removed and text cut short, for the backend to write a rule from.
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
  var lastFound = null;
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
    if (message && message.type === "scroll" && message.page > 0 && message.pageCount > 0) {
      var offset = message.offset > 0 && message.offset < 1 ? message.offset : 0;
      scrollTo(message.page, message.pageCount, offset);
    } else if (message && message.type === "snapshot") {
      sendSnapshot();
    } else if (message && message.type === "rules" && Array.isArray(message.rules)) {
      candidates = message.rules;
      // Before DOMContentLoaded the page is half parsed; that event runs detection.
      if (document.readyState !== "loading") schedule(0);
    }
  };

  // ---- Structure --------------------------------------------------------

  var MAX_FEATURES = 1000;
  // Class names with digits are usually generated per page or per build.
  var CLASS_NAME = /^[a-z][a-z_-]*$/i;
  var structureSent = false;

  function structure() {
    var seen = {};
    var features = [];
    function add(feature) {
      if (seen[feature] || features.length >= MAX_FEATURES) return;
      seen[feature] = true;
      features.push(feature);
    }
    var generator = document.querySelector('meta[name="generator" i]');
    if (generator && generator.content) {
      add("generator:" + generator.content.toLowerCase().replace(/[\d.]+/g, " ").replace(/\s+/g, " ").trim());
    }
    var all = document.body ? document.body.getElementsByTagName("*") : [];
    for (var i = 0; i < all.length && features.length < MAX_FEATURES; i++) {
      var el = all[i];
      add("tag:" + el.parentElement.tagName.toLowerCase() + ">" + el.tagName.toLowerCase());
      for (var j = 0; j < el.classList.length; j++) {
        if (CLASS_NAME.test(el.classList[j])) add("class:" + el.classList[j].toLowerCase());
      }
    }
    return features;
  }

  function sendStructure(url, found) {
    if (structureSent || found.via === "rule" || found.result.pageType !== "chapter") return;
    structureSent = true;
    send({ type: "structure", url: url, via: found.via, features: structure() });
  }

  // ---- Snapshot for AI detection ----------------------------------------

  // Elements that carry no detection signal, or hold what the user typed.
  var SNAPSHOT_DROP = {
    script: 1, style: 1, noscript: 1, template: 1, svg: 1, canvas: 1, iframe: 1, object: 1,
    embed: 1, video: 1, audio: 1, link: 1, input: 1, textarea: 1, select: 1
  };
  // Attributes rules read. Meta tags also keep name, property and content, for og:title.
  var SNAPSHOT_KEEP = {
    "class": 1, id: 1, href: 1, src: 1, "data-src": 1, "data-lazy-src": 1, srcset: 1,
    "data-srcset": 1, rel: 1
  };
  var SNAPSHOT_META = { name: 1, property: 1, content: 1 };
  var SNAPSHOT_TEXT = 80;
  var SNAPSHOT_CHARS = 80000;

  function shorten(text) {
    text = text.replace(/\s+/g, " ");
    return text.length > SNAPSHOT_TEXT ? text.slice(0, SNAPSHOT_TEXT) + "\u2026" : text;
  }

  function reduce(node) {
    var child = node.firstChild;
    while (child) {
      var next = child.nextSibling;
      if (child.nodeType === 8 || (child.nodeType === 1 && SNAPSHOT_DROP[child.localName])) {
        node.removeChild(child);
      } else if (child.nodeType === 3) {
        child.data = shorten(child.data);
      } else if (child.nodeType === 1) {
        var meta = child.localName === "meta";
        for (var i = child.attributes.length - 1; i >= 0; i--) {
          var attr = child.attributes[i];
          if (!SNAPSHOT_KEEP[attr.name] && !(meta && SNAPSHOT_META[attr.name])) {
            child.removeAttribute(attr.name);
          } else if (attr.name === "content") {
            attr.value = shorten(attr.value);
          }
        }
        reduce(child);
      }
      child = next;
    }
  }

  /** The page reduced for AI detection. It works on a copy in an inert document, which loads nothing. */
  function snapshot() {
    var inert = document.implementation.createHTMLDocument("");
    var root = inert.importNode(document.documentElement, true);
    reduce(root);
    return ("<!DOCTYPE html>" + root.outerHTML).slice(0, SNAPSHOT_CHARS);
  }

  function sendSnapshot() {
    var found = lastFound;
    if (!found || found.result.pageType !== "chapter" || !document.documentElement) return;
    send({ type: "snapshot", url: location.href, chapter: found.result.chapter, html: snapshot() });
  }

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

  /** Every JSON-LD node on the page, @graph members included. */
  function jsonLdNodes() {
    var nodes = [];
    var scripts = document.querySelectorAll('script[type="application/ld+json"]');
    for (var i = 0; i < scripts.length; i++) {
      var data;
      try {
        data = parse(scripts[i].textContent);
      } catch (e) {
        continue;
      }
      nodes = nodes.concat(data);
      if (data && Array.isArray(data["@graph"])) nodes = nodes.concat(data["@graph"]);
    }
    return nodes.filter(function (node) { return node && typeof node === "object"; });
  }

  function jsonLdTitle() {
    var nodes = jsonLdNodes();
    for (var j = 0; j < nodes.length; j++) {
      var node = nodes[j];
      if (hasType(node, ISSUE_TYPES) && nameOf(node.isPartOf)) return nameOf(node.isPartOf);
      if (hasType(node, SERIES_TYPES) && nameOf(node)) return nameOf(node);
    }
    return null;
  }

  function samePage(a, b) {
    return a.replace(/[?#].*$/, "").replace(/\/$/, "") === b.replace(/[?#].*$/, "").replace(/\/$/, "");
  }

  /**
   * The chapter number a JSON-LD chapter of a series gives, from its name or else its
   * position, for sites whose chapter URLs carry an id instead. A node that names another
   * URL is left out, since a single-page site can keep the last page's JSON-LD.
   */
  function jsonLdChapter(url) {
    var nodes = jsonLdNodes();
    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i];
      if (!hasType(node, ISSUE_TYPES) || !nameOf(node.isPartOf)) continue;
      if (typeof node.url === "string" && !samePage(evaluator.resolveUrl(node.url, url) || "", url)) continue;
      var m = typeof node.name === "string" && CHAPTER_NUMBER_IN_TITLE.exec(node.name);
      if (m) return evaluator.parseChapterNumber(m[1]);
      if (typeof node.position === "number" || typeof node.position === "string") {
        var n = evaluator.parseChapterNumber(String(node.position));
        if (n != null) return n;
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

  /** A slot holds an image or nothing; children with text are headings or notes. */
  function isSlot(child) {
    return child.tagName === "IMG" || child.querySelector("img") !== null || child.textContent.trim() === "";
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

  /** The container that holds the most images, with their URLs in document order. */
  function imageRun(base) {
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
    return groups.reduce(function (a, g) { return a && a.urls.length >= g.urls.length ? a : g; }, null);
  }

  /** The chapter's page images, or none when they look like a preview or a half-loaded reader. */
  function pageImages(base) {
    var best = imageRun(base);
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
    var chapter;
    if (here) {
      // The title can lag behind a single-page site's URL, so it wins only over an id.
      chapter = here.number >= MAX_CHAPTER ? chapterInTitle() : evaluator.parseChapterNumber(here.raw);
    } else {
      chapter = jsonLdChapter(url);
      if (chapter == null) return { pageType: "none" };
    }
    var around = here ? neighbors(here, url) : { next: null, previous: null };
    return {
      pageType: "chapter",
      series: null,
      title: cleanTitle(jsonLdTitle() || document.title || metaContent('meta[property="og:title"]')),
      chapterLabel: null,
      chapter: chapter,
      images: pageImages(url),
      next: around.next,
      previous: around.previous
    };
  }

  // ---- Position on a chapter page ----------------------------------

  var tracked = null; // { holder, slots, page, offset }
  var pendingScroll = null; // { page, pageCount, offset } until the slots are found
  var REPORT_MS = 300;
  var reportTimer = 0;
  // Images that load after a scroll change the heights above the place it went to, so
  // the place is held in the middle of the screen until the user touches the page or
  // HOLD_MS pass.
  var HOLD_MS = 5000;
  var held = null; // { observer, timer }
  var touchedSince = 0;

  function stopTracking() {
    release();
    clearTimeout(reportTimer);
    reportTimer = 0;
    tracked = null;
  }

  /** Reports where the reading line is among [holder]'s page slots, as it changes. */
  function track(holder) {
    if (tracked && tracked.holder === holder) return;
    stopTracking();
    var slots = [].filter.call(holder.children, isSlot);
    if (slots.length < MIN_PAGES) return;
    tracked = { holder: holder, slots: slots, page: 0, offset: 0 };
    report();
    if (pendingScroll) scrollTo(pendingScroll.page, pendingScroll.pageCount, pendingScroll.offset);
  }

  /**
   * Where the user reads: the middle of the screen, in the page's pixels. It is measured
   * on the screen, not the window, so the toolbar showing or hiding doesn't move it. A
   * page laid out wider than the screen has more of its pixels per screen pixel.
   */
  function readingLine() {
    var screen = window.screen;
    if (!screen || !screen.height || !screen.width) return window.innerHeight / 2;
    return screen.height * (window.innerWidth / screen.width) / 2;
  }

  /**
   * The slot under the reading line and how far down it the line is. Before the first
   * slot that is the first slot's top. Once the last slot's end is on screen, the last
   * slot if the user has scrolled into the slots, so a short last page still counts as
   * reached, and otherwise the first, as when the slots have no height yet.
   */
  function placeOf(slots) {
    var last = slots.length - 1;
    if (slots[last].getBoundingClientRect().bottom <= window.innerHeight + 1) {
      var into = slots[0].getBoundingClientRect().top < 0;
      return { page: into ? last + 1 : 1, offset: 0 };
    }
    var line = readingLine();
    for (var i = 0; ; i++) {
      var rect = slots[i].getBoundingClientRect();
      if (rect.bottom <= line && i < last) continue;
      var down = rect.height > 0 && rect.top < line ? (line - rect.top) / rect.height : 0;
      return { page: i + 1, offset: Math.min(down, 0.999) };
    }
  }

  function report() {
    reportTimer = 0;
    if (!tracked) return;
    var place = placeOf(tracked.slots);
    if (place.page === tracked.page && Math.abs(place.offset - tracked.offset) < 0.001) return;
    tracked.page = place.page;
    tracked.offset = place.offset;
    send({ type: "position", url: location.href, page: place.page, pageCount: tracked.slots.length, offset: place.offset });
  }

  // Captured, so a reader that scrolls inside its own box is followed too.
  window.addEventListener("scroll", function () {
    if (!tracked) return;
    // A new page is shown at once; a place inside one waits for the scroll to stop.
    if (placeOf(tracked.slots).page !== tracked.page) report();
    clearTimeout(reportTimer);
    reportTimer = setTimeout(report, REPORT_MS);
  }, { capture: true, passive: true });

  /** Puts page [page] of [pageCount], [offset] down it, in the middle of the screen, or waits for the slots. */
  function scrollTo(page, pageCount, offset) {
    if (!tracked) {
      pendingScroll = { page: page, pageCount: pageCount, offset: offset };
      return;
    }
    pendingScroll = null;
    var slots = tracked.slots;
    // The place in the whole chapter, scaled to this page's slots.
    var place = (page - 1 + offset) / pageCount * slots.length;
    var index = Math.min(slots.length - 1, Math.floor(place));
    hold(tracked.holder, slots[index], Math.min(place - index, 0.999));
  }

  function hold(holder, slot, offset) {
    release();
    touchedSince = 0;
    var until = Date.now() + HOLD_MS;
    function place() {
      if (touchedSince || Date.now() > until || !slot.isConnected) return release();
      slot.scrollIntoView({ block: "start" });
      var rect = slot.getBoundingClientRect();
      var by = rect.top + offset * rect.height - readingLine();
      if (Math.abs(by) >= 1) scrollerOf(slot).scrollBy(0, by);
    }
    place();
    var observer = new ResizeObserver(place);
    observer.observe(holder);
    observer.observe(document.documentElement);
    held = { observer: observer, timer: setTimeout(release, HOLD_MS) };
  }

  function release() {
    if (!held) return;
    held.observer.disconnect();
    clearTimeout(held.timer);
    held = null;
  }

  /** The box that scrolls [el]: its nearest scrolling ancestor, or the window. */
  function scrollerOf(el) {
    for (var e = el.parentElement; e && e !== document.body && e !== document.documentElement; e = e.parentElement) {
      var y = getComputedStyle(e).overflowY;
      if ((y === "auto" || y === "scroll") && e.scrollHeight > e.clientHeight) return e;
    }
    return window;
  }

  /** Tracks the position on a chapter page; stops on any other page. */
  function followPosition(found) {
    var r = found.result;
    var run = r.pageType === "chapter" ? imageRun(location.href) : null;
    if (run) track(run.holder);
    else stopTracking();
  }

  // ---- Evaluation -------------------------------------------------------

  /**
   * Cloudflare's "Just a moment" check stands in for the page at its URL until it
   * passes, then loads the real page. Nothing on it is the site's.
   */
  function challenged() {
    return "_cf_chl_opt" in window || !!document.getElementById("challenge-form");
  }

  /*
   * Candidates come in lookup order. A rule made for this domain is trusted
   * even when it says the page is neither a chapter nor a series page. A
   * built-in theme rule only counts when its marker element is present and
   * it recognizes the page.
   */
  function detect(url) {
    if (challenged()) return { via: "heuristic", result: { pageType: "none" } };
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
    lastFound = found;
    followPosition(found);
    var report = stringify({ type: "result", url: url, via: found.via, result: found.result });
    if (report !== lastReport) {
      lastReport = report;
      postMessage(report);
    }
    sendStructure(url, found);
  }

  function schedule(delay) {
    if (timer) return;
    timer = setTimeout(run, delay);
  }

  function urlChanged() {
    var url = location.href;
    if (url === lastUrl) return;
    lastUrl = url;
    // A new chapter in the same reader starts its position over.
    stopTracking();
    pendingScroll = null;
    watchUntil = Date.now() + WATCH_MS;
    schedule(SETTLE_MS);
  }

  // Registered before any page script, so a page cannot hide a tap from it. Pointerdown
  // comes before the click handlers that hijack taps.
  window.addEventListener("pointerdown", function (event) {
    touchedSince = Date.now();
    var link = event.target && event.target.closest ? event.target.closest("a[href]") : null;
    var href = link ? evaluator.resolveUrl(link.getAttribute("href"), location.href) : null;
    send({ type: "tap", url: location.href, href: href });
  }, true);

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
