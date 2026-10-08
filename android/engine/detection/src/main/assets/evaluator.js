/*
 * Ascon detection rule evaluator.
 *
 * Injected into every page at document start. Rules are data; this file is
 * the only code that interprets them and it never changes remotely. The Go
 * evaluator in backend/internal/adapter/evaluator follows the same contract,
 * contracts/README.md, and both must pass contracts/fixtures/conformance.
 */
(function () {
  "use strict";

  // Whitespace as the contract defines it: tab, LF, FF, CR, space, U+00A0.
  var WS_RUN = /[\t\n\f\r  ]+/g;
  var WS_ENDS = /^[\t\n\f\r  ]+|[\t\n\f\r  ]+$/g;

  var VOLUME_MARKER = /\b(?:volume|vol)\.?[\t\n\f\r  ]*\d+(?:\.\d+)?/g;
  var KEYWORD_NUMBER = /\b(?:chapter|chap|ch|episode|ep)[.\t\n\f\r  #:_-]*(\d+(?:[.,-]\d+)?)/;
  var WHOLE_NUMBER = /^\d+(?:[.,-]\d+)?$/;
  var FIRST_NUMBER = /\d+(?:[.,]\d+)?/;
  var DESCRIPTOR = /^(\d+(?:\.\d+)?)([wx])$/;
  // Forms Chromium accepts but the Go evaluator cannot parse. The contract
  // says both give no value.
  var BROKEN_ESCAPE = /%(?![0-9A-Fa-f]{2})/;
  var SCHEME_WITHOUT_SLASHES = /^https?:(?!\/\/)/i;

  function nonEmpty(s) {
    return s === "" ? null : s;
  }

  function trim(s) {
    return s.replace(WS_ENDS, "");
  }

  function collapse(s) {
    return nonEmpty(trim(s.replace(WS_RUN, " ")));
  }

  function normalizeNumber(n) {
    var parts = n.split(/[.,-]/);
    var whole = parts[0].replace(/^0+/, "") || "0";
    var frac = parts.length > 1 ? parts[1].replace(/0+$/, "") : "";
    return frac ? whole + "." + frac : whole;
  }

  function parseChapterNumber(label) {
    if (label == null) return null;
    var s = label
      .replace(/[A-Z]/g, function (c) { return c.toLowerCase(); })
      .replace(VOLUME_MARKER, " ");

    var m = KEYWORD_NUMBER.exec(s);
    if (m) return normalizeNumber(m[1]);

    var trimmed = trim(s);
    if (WHOLE_NUMBER.test(trimmed)) return normalizeNumber(trimmed);

    m = FIRST_NUMBER.exec(s);
    return m ? normalizeNumber(m[0]) : null;
  }

  function readValue(el, attribute) {
    if (attribute === "text") return collapse(el.textContent);
    var v = el.getAttribute(attribute);
    return v == null ? null : nonEmpty(trim(v));
  }

  function resolveUrl(raw, base) {
    if (raw == null) return null;
    var head = raw.split(/[?#]/)[0].replace(/\\/g, "/");
    if (BROKEN_ESCAPE.test(head) || SCHEME_WITHOUT_SLASHES.test(head)) return null;
    var u;
    try {
      u = new URL(raw, base);
    } catch (e) {
      return null;
    }
    if (u.protocol !== "http:" && u.protocol !== "https:") return null;
    u.hash = "";
    u.username = "";
    u.password = "";
    return u.href;
  }

  function largestCandidate(srcset) {
    var bestW = null, bestX = null, maxW = -1, maxX = -1;
    srcset.split(",").forEach(function (candidate) {
      var fields = trim(candidate).split(WS_RUN).filter(Boolean);
      if (fields.length === 0 || fields.length > 2) return;
      var size = 1, kind = "x";
      if (fields.length === 2) {
        var m = DESCRIPTOR.exec(fields[1]);
        if (!m) return;
        size = parseFloat(m[1]);
        kind = m[2];
      }
      if (kind === "w" && size > maxW) { bestW = fields[0]; maxW = size; }
      if (kind === "x" && size > maxX) { bestX = fields[0]; maxX = size; }
    });
    return maxW >= 0 ? bestW : maxX >= 0 ? bestX : null;
  }

  function matchUrl(pattern, url) {
    var m = new RegExp("^(?:" + pattern + ")$", "u").exec(url);
    if (!m) return null;
    return m.groups || {};
  }

  function group(groups, name) {
    var v = groups[name];
    return v == null ? null : nonEmpty(v);
  }

  function extract(doc, x) {
    if (!x) return null;
    var el = doc.querySelector(x.selector);
    if (!el) return null;
    var v = readValue(el, x.attribute || "text");
    if (v == null || !x.pattern) return v;
    var m = new RegExp(x.pattern, "u").exec(v);
    if (!m) return null;
    if (!m.groups || !("value" in m.groups)) throw new Error("pattern has no group named value");
    return m.groups.value == null ? null : nonEmpty(trim(m.groups.value));
  }

  function link(doc, l, base) {
    if (!l) return null;
    var el = doc.querySelector(l.selector);
    if (!el) return null;
    return resolveUrl(readValue(el, l.attribute || "href"), base);
  }

  function images(doc, im, base) {
    var attrs = im.attributes || ["src"];
    var urls = [];
    doc.querySelectorAll(im.selector).forEach(function (el) {
      for (var i = 0; i < attrs.length; i++) {
        var v = readValue(el, attrs[i]);
        if (attrs[i] === "srcset" && v != null) v = largestCandidate(v);
        var u = resolveUrl(v, base);
        if (u) {
          urls.push(u);
          return;
        }
      }
    });
    return urls;
  }

  function chapterPage(doc, p, groups, url) {
    var label = extract(doc, p.chapterLabel);
    var chapter = parseChapterNumber(label);
    if (chapter == null && groups.chapter != null) chapter = parseChapterNumber(groups.chapter);
    return {
      pageType: "chapter",
      series: group(groups, "series"),
      title: extract(doc, p.title),
      chapterLabel: label,
      chapter: chapter,
      images: images(doc, p.images, url),
      next: link(doc, p.next, url),
      previous: link(doc, p.previous, url)
    };
  }

  function seriesPage(doc, p, groups, url) {
    var chapters = [];
    doc.querySelectorAll(p.chapterLinks).forEach(function (el) {
      var u = resolveUrl(readValue(el, "href"), url);
      if (!u) return;
      var label = collapse(el.textContent);
      chapters.push({ url: u, label: label, number: parseChapterNumber(label) });
    });
    return {
      pageType: "series",
      series: group(groups, "series"),
      title: extract(doc, p.title),
      chapters: chapters
    };
  }

  /** Evaluates rule against the page at url. doc defaults to this page. */
  function evaluate(rule, url, doc) {
    doc = doc || document;
    var groups = matchUrl(rule.chapterPage.url, url);
    if (groups) return chapterPage(doc, rule.chapterPage, groups, url);
    if (rule.seriesPage) {
      groups = matchUrl(rule.seriesPage.url, url);
      if (groups) return seriesPage(doc, rule.seriesPage, groups, url);
    }
    return { pageType: "none" };
  }

  globalThis.AsconEvaluator = Object.freeze({
    evaluate: evaluate,
    parseChapterNumber: parseChapterNumber,
    resolveUrl: resolveUrl
  });
})();
