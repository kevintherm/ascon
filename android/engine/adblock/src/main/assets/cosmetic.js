// Ascon cosmetic filtering. Runs at document start in every main frame.
// Asks the app which elements this page hides, then sends the class and id names that
// appear on the page, in batches, so generic rules that name them can hide them too.
(function () {
  "use strict";
  var bridge = window.asconAdblock;
  if (!bridge || window.top !== window) return;

  var MAX_NAMES = 500;
  var BATCH_MS = 100;
  var style = null;
  var ready = false;
  var generic = true;
  var exceptions = [];
  var seen = new Set();
  var classes = [];
  var ids = [];
  var timer = null;

  // One rule per selector, so a selector this browser can't parse drops only itself.
  function hide(selectors) {
    if (!selectors || !selectors.length) return;
    if (!style || !style.isConnected) {
      style = document.createElement("style");
      (document.head || document.documentElement).appendChild(style);
    }
    style.textContent += selectors.map(function (s) { return s + "{display:none !important}"; }).join("\n") + "\n";
  }

  function note(kind, name) {
    var key = kind + name;
    if (!name || name.length > 100 || seen.has(key)) return;
    seen.add(key);
    (kind === "." ? classes : ids).push(name);
    if (!timer) timer = setTimeout(flush, BATCH_MS);
  }

  function noteElement(el) {
    if (el.nodeType !== 1) return;
    if (el.id) note("#", el.id);
    if (el.classList) for (var i = 0; i < el.classList.length; i++) note(".", el.classList[i]);
  }

  function noteTree(root) {
    noteElement(root);
    if (root.querySelectorAll) root.querySelectorAll("[class],[id]").forEach(noteElement);
  }

  function flush() {
    timer = null;
    if (!ready || !generic || (!classes.length && !ids.length)) return;
    var c = classes.splice(0, MAX_NAMES);
    var i = ids.splice(0, MAX_NAMES - c.length);
    bridge.postMessage(JSON.stringify({ type: "names", url: location.href, classes: c, ids: i, exceptions: exceptions }));
    if (classes.length || ids.length) timer = setTimeout(flush, BATCH_MS);
  }

  bridge.onmessage = function (event) {
    var reply;
    try { reply = JSON.parse(event.data); } catch (e) { return; }
    if (reply.page) {
      ready = true;
      generic = !reply.generichide;
      exceptions = reply.exceptions || [];
      if (!generic) { classes = []; ids = []; }
      flush();
    }
    hide(reply.hide);
  };

  new MutationObserver(function (records) {
    records.forEach(function (r) {
      if (r.type === "attributes") noteElement(r.target);
      else r.addedNodes.forEach(noteTree);
    });
  }).observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ["class", "id"] });
  document.addEventListener("DOMContentLoaded", function () { noteTree(document.documentElement); });

  bridge.postMessage(JSON.stringify({ type: "page", url: location.href }));
})();
