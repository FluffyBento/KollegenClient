












(function () {
  "use strict";

  var LOAD_TIMEOUT = 9000;
  var stages = new WeakMap();

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined) e.textContent = text;
    return e;
  }

  function byId(list, id) {
    if (!id || !Array.isArray(list)) return null;
    for (var i = 0; i < list.length; i++) if (list[i] && list[i].id === id) return list[i];
    return null;
  }

  
  
  
  function tag(host, patch) {
    if (!host) return;
    var d = host._kd || (host._kd = {});
    if (patch) for (var k in patch) if (patch[k] !== undefined && patch[k] !== null) d[k] = patch[k];
    var eq = d.equipped || {};
    var cat = d.catalog || [];
    var badge = byId(cat, eq.badge);
    var nc = byId(cat, eq.name_color);
    var stil = byId(cat, eq.profil_stil);
    var sticker = byId(cat, eq.sticker);
    var title = byId(cat, eq.title);
    var accent = (nc && nc.data && nc.data.accent) || (stil && stil.data && stil.data.accent) || "#ffffff";

    host.textContent = "";
    if (title && title.data && title.data.text) {
      var t = el("div", "pstage-tag-title", title.data.text);
      t.style.color = accent;
      host.appendChild(t);
    }
    var row = el("div", "pstage-tag-row");
    if (badge && badge.data && badge.data.icon) {
      var b = el("span", "pstage-ico", badge.data.icon);
      b.style.color = badge.data.color || "#FFD700";
      row.appendChild(b);
    }
    var nm = el("span", "pstage-nm", d.name || "…");
    nm.style.color = accent;
    row.appendChild(nm);
    if (sticker && sticker.data && sticker.data.icon) {
      var s = el("span", "pstage-ico", sticker.data.icon);
      s.style.color = sticker.data.color || "#ffffff";
      row.appendChild(s);
    }
    host.appendChild(row);
    host.classList.toggle("is-empty", !d.name);
  }

  
  function state(stage, s) {
    stage.setAttribute("data-state", s);
  }

  function fallbackName(stage) {
    var tg = stage.querySelector(".pstage-tag");
    return (tg && tg._kd && tg._kd.name) || "";
  }

  function showFallback(stage) {
    var st = stages.get(stage);
    if (st && st.ctrl) return; 
    var fb = stage.querySelector(".pstage-fallback");
    if (!fb) {
      fb = el("div", "pstage-fallback");
      stage.insertBefore(fb, stage.firstChild);
    }
    fb.textContent = "";
    var nm = fallbackName(stage);
    if (nm && nm !== "…") {
      var img = new Image();
      img.alt = "";
      img.decoding = "async";
      img.src = "https://mc-heads.net/body/" + encodeURIComponent(nm) + "/400";
      img.onerror = function () { fb.classList.add("is-plain"); img.remove(); };
      fb.appendChild(img);
    } else {
      fb.classList.add("is-plain");
    }
    state(stage, "fallback");
  }

  function hideFallback(stage) {
    var fb = stage.querySelector(".pstage-fallback");
    if (fb) fb.remove();
  }

  
  function loading(stage) {
    if (!stage) return;
    var st = stages.get(stage) || {};
    stages.set(stage, st);
    state(stage, "loading");
    clearTimeout(st.timer);
    st.timer = setTimeout(function () {
      if (stage.getAttribute("data-state") === "loading") showFallback(stage);
    }, LOAD_TIMEOUT);
  }

  
  function attach(stage, ctrl) {
    if (!stage || !ctrl) return;
    var st = stages.get(stage) || {};
    stages.set(stage, st);
    st.ctrl = ctrl;
    clearTimeout(st.timer);
    var tg = stage.querySelector(".pstage-tag");
    var first = true;
    var lx = -1, ly = -1;
    ctrl.setOnFrame(function (p) {
      if (first) {
        first = false;
        hideFallback(stage);
        state(stage, "ready");
      }
      if (!tg) return;
      var x = Math.round(p.x * 1000) / 10;
      var y = Math.round(p.y * 1000) / 10;
      if (x === lx && y === ly) return;
      lx = x; ly = y;
      tg.style.left = x + "%";
      
      tg.style.top = Math.max(y, 9) + "%";
    });
    bindTools(stage, st);
    
    clearTimeout(st.timer);
    st.timer = setTimeout(function () {
      if (first) { st.ctrl = null; showFallback(stage); }
    }, LOAD_TIMEOUT);
  }

  function detach(stage) {
    var st = stages.get(stage);
    if (!st) return;
    clearTimeout(st.timer);
    st.ctrl = null;
  }

  function bindTools(stage, st) {
    var spin = stage.querySelector("[data-stage-spin]");
    var flip = stage.querySelector("[data-stage-flip]");
    var hint = stage.querySelector(".pstage-hint");
    if (spin) {
      var initOn = !!(st.ctrl && st.ctrl.isAutoRotate && st.ctrl.isAutoRotate());
      spin.setAttribute("aria-pressed", initOn ? "true" : "false");
    }
    if (st.toolsBound) return;
    st.toolsBound = true;
    if (spin) {
      spin.addEventListener("click", function () {
        if (!st.ctrl) return;
        var on = !st.ctrl.isAutoRotate();
        st.ctrl.setAutoRotate(on);
        spin.setAttribute("aria-pressed", on ? "true" : "false");
      });
    }
    if (flip) {
      flip.addEventListener("click", function () {
        if (!st.ctrl) return;
        
        var r = st.ctrl.getRot();
        var base = Math.PI;
        var target = Math.round(r / base) * base;
        if (Math.abs(target - r) < 0.2) target += base;
        st.ctrl.setAutoRotate(false);
        if (spin) spin.setAttribute("aria-pressed", "false");
        st.ctrl.turnTo(target);
      });
    }
    var canvas = stage.querySelector("canvas");
    if (canvas && hint) {
      canvas.addEventListener("pointerdown", function () { hint.classList.add("is-gone"); }, { once: true });
      setTimeout(function () { hint.classList.add("is-gone"); }, 12000);
    }
  }

  
  function thumb(item) {
    var cache = window.__c3dThumbCache || (window.__c3dThumbCache = {});
    if (!item || !item.id) return Promise.resolve(null);
    if (!window.Cosmetic3D || !window.Cosmetic3D.keyFor(item)) return Promise.resolve(null);
    if (!cache[item.id]) {
      cache[item.id] = window.Cosmetic3D.thumb(item).catch(function () { return null; });
    }
    return cache[item.id];
  }

  window.KStage = {
    tag: tag,
    loading: loading,
    attach: attach,
    detach: detach,
    showFallback: showFallback,
    hideFallback: hideFallback,
    thumb: thumb
  };
})();
