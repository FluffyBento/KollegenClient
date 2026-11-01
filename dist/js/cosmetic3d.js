/* Cosmetic3D – zeigt Kollegen-Cosmetics an einem Spieler.
 *
 *  - thumb(item)            -> Promise<dataURL>  Store-Vorschau (Spieler traegt das Item)
 *  - mount(el, item)        -> unmount-fn        drehbare Vorschau (Store-Detailfenster)
 *  - mountPlayer(canvas, o) -> Controller        Spieler mit echtem Skin + ausgeruesteten Items
 *
 * Performance:
 *  - EIN gemeinsamer WebGL-Renderer fuer alles (Ergebnis wird in 2D-Canvases kopiert),
 *    dadurch keine verlorenen Kontexte.
 *  - Jedes Cosmetic wird einmalig zu EINER Geometrie zusammengefuehrt (1 Draw-Call statt
 *    hunderter Boxen) und danach gecacht.
 *  - Animierte Ansichten rendern nur, wenn sie sichtbar sind (IntersectionObserver),
 *    der Tab im Vordergrund ist und mit max. ~30 FPS.
 *  - Thumbnails laufen ueber eine Queue (max. 1 pro Frame).
 */
(function () {
  'use strict';
  var DEG = Math.PI / 180;

  /* ---------- three.js-Kompatibilitaet (r13x und r150+) ----------
     index.html laedt zwei three.js-Versionen (klassisch + Modul). Damit nie Objekte
     verschiedener Versionen gemischt werden, wird THREE genau EINMAL lokal gebunden. */
  var THREE = null;
  function bind() {
    if (!THREE && typeof window.THREE !== 'undefined' && window.THREE) THREE = window.THREE;
    return !!THREE;
  }
  function rev() { return parseInt(THREE && THREE.REVISION, 10) || 0; }
  function lightK() { return rev() >= 155 ? Math.PI : 1; }
  function srgb(tex) {
    if (THREE.SRGBColorSpace && ('colorSpace' in tex)) tex.colorSpace = THREE.SRGBColorSpace;
    else if (THREE.sRGBEncoding) tex.encoding = THREE.sRGBEncoding;
  }

  var renderer = null;
  var rendererOK = null;

  function ready() {
    if (rendererOK !== null) return rendererOK;
    try {
      if (!bind()) { rendererOK = false; return false; }
      var test = document.createElement('canvas');
      var gl = test.getContext('webgl') || test.getContext('experimental-webgl');
      rendererOK = !!gl;
      return rendererOK;
    } catch (e) {
      rendererOK = false;
      return false;
    }
  }

  function ensureRenderer(w, h) {
    if (!renderer) {
      renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
      if (THREE.SRGBColorSpace && ('outputColorSpace' in renderer)) renderer.outputColorSpace = THREE.SRGBColorSpace;
      else if (THREE.sRGBEncoding) renderer.outputEncoding = THREE.sRGBEncoding;
      renderer.setClearColor(0x000000, 0);
      renderer.setPixelRatio(1);
      renderer.domElement.addEventListener('webglcontextlost', function (e) {
        e.preventDefault();
        renderer = null; // naechster Frame legt einen neuen an
      }, false);
    }
    var W = Math.max(2, Math.round(w)), H = Math.max(2, Math.round(h));
    if (renderer.domElement.width !== W || renderer.domElement.height !== H) renderer.setSize(W, H, false);
    return renderer;
  }

  /* ---------- Item -> Modell-Schluessel ---------- */
  var MODEL_CATS = ['cape', 'shield', 'back_accessory', 'head_accessory', 'face_accessory', 'aura', 'shoulder_accessory', 'torso_accessory', 'legs_accessory', 'gloves', 'gekauft'];

  function keyFor(item) {
    try {
      if (!item || !item.data || !item.data.texture || !item.data.model) return null;
      if (MODEL_CATS.indexOf(item.category) < 0) return null;
      var m = String(item.data.texture).match(/^kollegen:cosmetics\/([^/]+)\/(.+)\.png$/);
      if (!m) return null;
      return m[1] + '_' + m[2];
    } catch (e) {
      return null;
    }
  }

  function petKind(item) {
    return (item && item.data && item.data.kind) || String((item && item.id) || '').replace(/^pet_/, '');
  }

  /* ---------- Laden ---------- */
  function loadImage(src, cors) {
    return new Promise(function (res, rej) {
      var im = new Image();
      if (cors) im.crossOrigin = 'anonymous';
      im.onload = function () { res(im); };
      im.onerror = function () { rej(new Error('img')); };
      im.src = src;
    });
  }

  // Bilder ueber fetch->Blob laden: so bleibt das Canvas/WebGL-Texture "sauber" (kein CORS-Taint).
  function fetchImage(url) {
    return fetch(url).then(function (r) {
      if (!r.ok) throw new Error('http ' + r.status);
      return r.blob();
    }).then(function (b) {
      var u = URL.createObjectURL(b);
      return loadImage(u).then(function (im) { URL.revokeObjectURL(u); return im; },
        function (e) { URL.revokeObjectURL(u); throw e; });
    });
  }

  function makeTex(src) {
    var t = new THREE.Texture(src);
    t.needsUpdate = true;
    t.magFilter = THREE.NearestFilter;
    t.minFilter = THREE.NearestFilter;
    t.generateMipmaps = false;
    srgb(t);
    return t;
  }

  /* ---------- Skin ---------- */
  function paintMannequin() {
    var c = document.createElement('canvas');
    c.width = 64; c.height = 64;
    var g = c.getContext('2d');
    var skin = '#c99876', hair = '#3b2a1e', shirt = '#35a5b8', pants = '#37479a', shoe = '#4a4a52';
    function box(u, v, w, h, d, col) {
      g.fillStyle = col;
      g.fillRect(u + d, v, 2 * w, d);
      g.fillRect(u, v + d, 2 * d + 2 * w, h);
    }
    box(0, 0, 8, 8, 8, skin);
    g.fillStyle = hair;
    g.fillRect(8, 0, 8, 8); g.fillRect(24, 8, 8, 8);
    g.fillRect(0, 8, 8, 3); g.fillRect(16, 8, 8, 3); g.fillRect(8, 8, 8, 2);
    g.fillStyle = '#ffffff'; g.fillRect(9, 12, 2, 1); g.fillRect(13, 12, 2, 1);
    g.fillStyle = '#3a4fb0'; g.fillRect(10, 12, 1, 1); g.fillRect(13, 12, 1, 1);
    g.fillStyle = '#8a5a40'; g.fillRect(11, 14, 2, 1);
    box(16, 16, 8, 12, 4, shirt);
    box(40, 16, 4, 12, 4, shirt); g.fillStyle = skin; g.fillRect(40, 28, 16, 4);
    box(32, 48, 4, 12, 4, shirt); g.fillStyle = skin; g.fillRect(32, 60, 16, 4);
    box(0, 16, 4, 12, 4, pants); g.fillStyle = shoe; g.fillRect(0, 28, 16, 4);
    box(16, 48, 4, 12, 4, pants); g.fillStyle = shoe; g.fillRect(16, 60, 16, 4);
    return c;
  }

  var mannequinInfo = null;
  function getMannequin() {
    if (!mannequinInfo) mannequinInfo = skinInfo(paintMannequin());
    return mannequinInfo;
  }

  function skinInfo(src) {
    var W = src.width, H = src.height;
    var s = W / 64;
    var legacy = H < W; // 64x32
    var slim = false;
    if (!legacy) {
      try {
        var cv = document.createElement('canvas');
        cv.width = W; cv.height = H;
        var cx = cv.getContext('2d');
        cx.drawImage(src, 0, 0);
        var d = cx.getImageData(Math.round(54 * s), Math.round(20 * s), Math.max(1, Math.round(2 * s)), Math.max(1, Math.round(12 * s))).data;
        var sum = 0;
        for (var i = 3; i < d.length; i += 4) sum += d[i];
        slim = sum === 0;
      } catch (e) { slim = false; }
    }
    return { tex: makeTex(src), W: W, H: H, s: s, legacy: legacy, slim: slim };
  }

  var skinCache = {};
  function loadSkin(url) {
    if (!url) return Promise.resolve(getMannequin());
    if (skinCache[url]) return skinCache[url];
    var p = fetchImage(url).catch(function () { return loadImage(url, true); })
      .then(function (im) { return skinInfo(im); })
      .catch(function () { return null; })
      .then(function (info) {
        if (info) return info;
        delete skinCache[url];
        return Object.assign({}, getMannequin(), { failed: true });
      });
    skinCache[url] = p;
    return p;
  }

  var capeCache = {};
  function loadCape(url) {
    if (!url) return Promise.resolve(null);
    if (capeCache[url]) return capeCache[url];
    var p = fetchImage(url).catch(function () { return loadImage(url, true); })
      .then(function (im) { return { tex: makeTex(im), W: im.width, H: im.height, s: im.width / 64 }; })
      .catch(function () { delete capeCache[url]; return null; });
    capeCache[url] = p;
    return p;
  }

  /* ---------- Geometrie-Helfer ---------- */
  var FACE_ORDER = ['east', 'west', 'up', 'down', 'south', 'north'];

  // Flaeche -> [u0,v0,u1,v1] oder null (Blockbench: {uv:[..],texture:0}; auch reine Arrays erlaubt)
  function faceRect(fd) {
    if (!fd) return null;
    if (typeof fd.texture === 'number' && fd.texture < 0) return null;
    var rc = Array.isArray(fd) ? fd : fd.uv;
    if (!rc || rc.length < 4) return null;
    for (var i = 0; i < 4; i++) if (typeof rc[i] !== 'number' || !isFinite(rc[i])) return null;
    return rc;
  }

  // Setzt die UVs und entfernt Flaechen ohne UV aus dem Index (statt sie falsch zu texturieren).
  function setUV(geo, rects, W, H) {
    var uv = geo.attributes.uv;
    var keep = [];
    FACE_ORDER.forEach(function (f, idx) {
      var rc = faceRect(rects && rects[f]);
      var b = idx * 4;
      if (!rc) { uv.setXY(b, 0, 0); uv.setXY(b + 1, 0, 0); uv.setXY(b + 2, 0, 0); uv.setXY(b + 3, 0, 0); return; }
      keep.push(idx);
      var u0 = rc[0], v0 = rc[1], u1 = rc[2], v1 = rc[3];
      uv.setXY(b, u0 / W, 1 - v0 / H);
      uv.setXY(b + 1, u1 / W, 1 - v0 / H);
      uv.setXY(b + 2, u0 / W, 1 - v1 / H);
      uv.setXY(b + 3, u1 / W, 1 - v1 / H);
    });
    uv.needsUpdate = true;
    if (keep.length < 6 && geo.index) {
      var src = geo.index.array, out = [];
      keep.forEach(function (f) { for (var i = 0; i < 6; i++) out.push(src[f * 6 + i]); });
      geo.setIndex(out);
    }
  }

  function boxUV(u, v, w, h, d, s, mirror) {
    var r = {
      east: [u, v + d, u + d, v + d + h],
      north: [u + d, v + d, u + d + w, v + d + h],
      west: [u + d + w, v + d, u + 2 * d + w, v + d + h],
      south: [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
      up: [u + d + w, v + d, u + d, v],
      down: [u + d + 2 * w, v + d, u + d + w, v]
    };
    function fl(rc) { return [rc[2], rc[1], rc[0], rc[3]]; }
    if (mirror) r = { east: fl(r.west), west: fl(r.east), north: fl(r.north), south: fl(r.south), up: fl(r.up), down: fl(r.down) };
    var out = {};
    Object.keys(r).forEach(function (k) { out[k] = r[k].map(function (n) { return n * s; }); });
    return out;
  }

  /* ---------- Spieler ---------- */
  function buildPlayer(info) {
    var g = new THREE.Group();
    var s = info.s, W = info.W, H = info.H, aw = info.slim ? 3 : 4, legacy = info.legacy;
    var base = new THREE.MeshStandardMaterial({ map: info.tex, roughness: 0.92, metalness: 0 });
    var over = new THREE.MeshStandardMaterial({ map: info.tex, roughness: 0.92, metalness: 0, transparent: true, alphaTest: 0.02, side: THREE.DoubleSide });
    // x,y,z, w,h,d, u,v, mirror, inflate, overlay
    var parts = [
      [-4, 24, -4, 8, 8, 8, 0, 0, false, 0, false],
      [-4, 24, -4, 8, 8, 8, 32, 0, false, 0.5, true],
      [-4, 12, -2, 8, 12, 4, 16, 16, false, 0, false],
      [4, 12, -2, aw, 12, 4, 40, 16, false, 0, false],
      [-4 - aw, 12, -2, aw, 12, 4, legacy ? 40 : 32, legacy ? 16 : 48, legacy, 0, false],
      [0, 0, -2, 4, 12, 4, 0, 16, false, 0, false],
      [-4, 0, -2, 4, 12, 4, legacy ? 0 : 16, legacy ? 16 : 48, legacy, 0, false]
    ];
    if (!legacy) {
      parts.push([-4, 12, -2, 8, 12, 4, 16, 32, false, 0.25, true]);
      parts.push([4, 12, -2, aw, 12, 4, 40, 32, false, 0.25, true]);
      parts.push([-4 - aw, 12, -2, aw, 12, 4, 48, 48, false, 0.25, true]);
      parts.push([0, 0, -2, 4, 12, 4, 0, 32, false, 0.25, true]);
      parts.push([-4, 0, -2, 4, 12, 4, 0, 48, false, 0.25, true]);
    }
    parts.forEach(function (p) {
      var inf = p[9];
      var geo = new THREE.BoxGeometry(p[3] + inf * 2, p[4] + inf * 2, p[5] + inf * 2);
      setUV(geo, boxUV(p[6], p[7], p[3], p[4], p[5], s, p[8]), W, H);
      var m = new THREE.Mesh(geo, p[10] ? over : base);
      m.position.set(p[0] + p[3] / 2, p[1] + p[4] / 2, p[2] + p[5] / 2);
      g.add(m);
    });
    return { group: g, dispose: function () { g.traverse(function (o) { if (o.geometry) o.geometry.dispose(); }); base.dispose(); over.dispose(); } };
  }

  function buildVanillaCape(c) {
    var geo = new THREE.BoxGeometry(10, 16, 1);
    var s = c.s;
    var r = {
      south: [1, 1, 11, 17], north: [12, 1, 22, 17],
      east: [0, 1, 1, 17], west: [11, 1, 12, 17],
      up: [1, 0, 11, 1], down: [11, 0, 21, 1]
    };
    var out = {};
    Object.keys(r).forEach(function (k) { out[k] = r[k].map(function (n) { return n * s; }); });
    setUV(geo, out, c.W, c.H);
    var mat = new THREE.MeshStandardMaterial({ map: c.tex, roughness: 0.9, transparent: true, alphaTest: 0.05, side: THREE.DoubleSide });
    var mesh = new THREE.Mesh(geo, mat);
    mesh.position.set(0, -8, 0.5);
    var piv = new THREE.Group();
    piv.position.set(0, 24, 2.1);
    piv.rotation.x = -8 * DEG;
    piv.add(mesh);
    return { group: piv, dispose: function () { geo.dispose(); mat.dispose(); } };
  }

  /* ---------- Cosmetic-Modell (Blockbench-JSON) -> eine Geometrie ---------- */
  var modelCache = {};
  function loadModel(key) {
    if (modelCache[key]) return modelCache[key];
    var p = Promise.all([
      fetch('cosmetics/' + key + '.json').then(function (r) { if (!r.ok) throw new Error('no json'); return r.json(); }),
      loadImage('cosmetics/' + key + '.png')
    ]).then(function (arr) { return { mod: arr[0], img: arr[1] }; });
    modelCache[key] = p;
    p.catch(function () { delete modelCache[key]; });
    return p;
  }

  function buildCosmeticGroup(mod, mat) {
    var W = (mod.res && mod.res[0]) || 64, H = (mod.res && mod.res[1]) || 64;
    var byUuid = {};
    (mod.elements || []).forEach(function (e) { if (e.uuid) byUuid[e.uuid] = e; });
    var root = new THREE.Group();
    var lastGroupOrigin = [0, 0, 0];
    function addBox(parent, e) {
      var sx = Math.max(0.01, e.to[0] - e.from[0]);
      var sy = Math.max(0.01, e.to[1] - e.from[1]);
      var sz = Math.max(0.01, e.to[2] - e.from[2]);
      var geo = new THREE.BoxGeometry(sx, sy, sz);
      setUV(geo, e.faces, W, H);
      var piv = new THREE.Group();
      piv.rotation.order = 'ZYX';
      var o = e.origin || [0, 0, 0], r = e.rot || [0, 0, 0];
      var go = lastGroupOrigin;
      piv.position.set(o[0] - go[0], o[1] - go[1], o[2] - go[2]);
      piv.rotation.set(r[0] * DEG, r[1] * DEG, r[2] * DEG);
      var mesh = new THREE.Mesh(geo, mat);
      mesh.position.set((e.from[0] + e.to[0]) / 2 - o[0], (e.from[1] + e.to[1]) / 2 - o[1], (e.from[2] + e.to[2]) / 2 - o[2]);
      piv.add(mesh);
      parent.add(piv);
    }
    function buildGroup(g, parent) {
      if (typeof g === 'string') {
        var e = byUuid[g];
        if (e) addBox(parent, e);
        return;
      }
      var go = new THREE.Group();
      go.rotation.order = 'ZYX';
      var o = g.origin || [0, 0, 0], r = g.rot || [0, 0, 0];
      var par = parent === root ? [0, 0, 0] : (parent.userData.origin || [0, 0, 0]);
      go.position.set(o[0] - par[0], o[1] - par[1], o[2] - par[2]);
      go.rotation.set(r[0] * DEG, r[1] * DEG, r[2] * DEG);
      go.userData.origin = o;
      parent.add(go);
      var prev = lastGroupOrigin;
      lastGroupOrigin = o;
      (g.children || []).forEach(function (c) { buildGroup(c, go); });
      lastGroupOrigin = prev;
    }
    (mod.groups || []).forEach(function (g) { buildGroup(g, root); });
    return root;
  }

  var mergedCache = {};
  // Ergebnis: { geo, mat, box } – wird von allen Szenen geteilt, nie disposen.
  function getMerged(key) {
    if (mergedCache[key]) return mergedCache[key];
    var p = loadModel(key).then(function (entry) {
      var tex = makeTex(entry.img);
      var mat = new THREE.MeshStandardMaterial({ map: tex, roughness: 0.55, metalness: 0.08, transparent: true, alphaTest: 0.4, side: THREE.DoubleSide });
      var root = buildCosmeticGroup(entry.mod, mat);
      root.updateMatrixWorld(true);
      var pos = [], nor = [], uv = [], idx = [], vbase = 0;
      root.traverse(function (o) {
        if (!o.isMesh) return;
        var g = o.geometry.clone();
        g.applyMatrix4(o.matrixWorld);
        var P = g.attributes.position, N = g.attributes.normal, T = g.attributes.uv, I = g.index, i;
        for (i = 0; i < P.count; i++) {
          pos.push(P.getX(i), P.getY(i), P.getZ(i));
          nor.push(N.getX(i), N.getY(i), N.getZ(i));
          uv.push(T.getX(i), T.getY(i));
        }
        for (i = 0; i < I.count; i++) idx.push(I.getX(i) + vbase);
        vbase += P.count;
        g.dispose();
        o.geometry.dispose();
      });
      if (!pos.length) throw new Error('empty model');
      var geo = new THREE.BufferGeometry();
      geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
      geo.setAttribute('normal', new THREE.Float32BufferAttribute(nor, 3));
      geo.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
      geo.setIndex(idx);
      geo.computeBoundingBox();
      return { geo: geo, mat: mat, box: geo.boundingBox.clone() };
    });
    mergedCache[key] = p;
    p.catch(function () { delete mergedCache[key]; });
    return p;
  }

  // Alte Schild-Modelle sind um den Ursprung zentriert -> an den Arm schieben.
  function placeOffset(item, box) {
    var off = new THREE.Vector3(0, 0, 0);
    if (item && item.category === 'shield') {
      var cx = (box.min.x + box.max.x) / 2;
      if (Math.abs(cx) < 2) { off.x = 9.6; off.z = -1.5; }
    }
    return off;
  }

  /* ---------- Szene ---------- */
  function baseScene() {
    var K = lightK();
    var scene = new THREE.Scene();
    scene.add(new THREE.HemisphereLight(0xe8eeff, 0x20222c, 0.95 * K));
    var key = new THREE.DirectionalLight(0xffffff, 0.9 * K);
    key.position.set(-25, 55, -35);
    scene.add(key);
    var rim = new THREE.DirectionalLight(0x88aaff, 0.4 * K);
    rim.position.set(30, 20, 30);
    scene.add(rim);
    return scene;
  }

  var PLAYER_BOX_MIN = [-8, 0, -4], PLAYER_BOX_MAX = [8, 32, 4];

  function unionBox(boxes) {
    var b = new THREE.Box3(new THREE.Vector3(PLAYER_BOX_MIN[0], PLAYER_BOX_MIN[1], PLAYER_BOX_MIN[2]),
      new THREE.Vector3(PLAYER_BOX_MAX[0], PLAYER_BOX_MAX[1], PLAYER_BOX_MAX[2]));
    boxes.forEach(function (x) { b.union(x); });
    return b;
  }

  // Kamera so setzen, dass die Box (bei Drehung) komplett sichtbar ist.
  function fitCamera(camera, box, aspect, rotating) {
    var size = box.getSize(new THREE.Vector3());
    var center = box.getCenter(new THREE.Vector3());
    var vf = camera.fov * DEG;
    var t = Math.tan(vf / 2);
    // Sehr breite Items (Fluegel) duerfen an den Spitzen minimal abgeschnitten werden,
    // damit der Spieler nicht winzig wird.
    var hExt = rotating ? Math.min(Math.max(size.x, size.z), 50) : size.x + size.z * 0.5;
    var dist = Math.max((size.y / 2) / t, (hExt / 2) / (t * aspect)) * 1.12 + size.z / 2;
    camera.aspect = aspect;
    camera.position.set(center.x, center.y + size.y * 0.05, center.z - dist);
    camera.lookAt(center);
    camera.updateProjectionMatrix();
  }

  /* ---------- Pets ---------- */
  // Pet-Bilder haben viel Rand -> auf den sichtbaren Bereich zuschneiden (quadratisch, 256px).
  var petCropCache = {};
  function petCropped(kind) {
    if (petCropCache[kind]) return petCropCache[kind];
    var p = loadImage('pets/' + kind + '.png').then(function (im) {
      var c = document.createElement('canvas');
      c.width = im.width; c.height = im.height;
      var g = c.getContext('2d');
      g.drawImage(im, 0, 0);
      var d = g.getImageData(0, 0, c.width, c.height).data;
      var x0 = c.width, y0 = c.height, x1 = -1, y1 = -1, x, y;
      for (y = 0; y < c.height; y++) {
        for (x = 0; x < c.width; x++) {
          if (d[(y * c.width + x) * 4 + 3] > 8) {
            if (x < x0) x0 = x; if (x > x1) x1 = x;
            if (y < y0) y0 = y; if (y > y1) y1 = y;
          }
        }
      }
      if (x1 < 0) return null;
      var side = Math.max(x1 - x0 + 1, y1 - y0 + 1) * 1.12;
      var S = 256;
      var o = document.createElement('canvas');
      o.width = S; o.height = S;
      var og = o.getContext('2d');
      og.imageSmoothingEnabled = false;
      og.drawImage(im, (x0 + x1 + 1) / 2 - side / 2, (y0 + y1 + 1) / 2 - side / 2, side, side, 0, 0, S, S);
      return { canvas: o, url: o.toDataURL('image/png') };
    }).catch(function () { delete petCropCache[kind]; return null; });
    petCropCache[kind] = p;
    return p;
  }

  var petTexCache = {};
  function loadPetTex(kind) {
    if (petTexCache[kind]) return petTexCache[kind];
    var p = petCropped(kind).then(function (r) {
      if (!r) return null;
      var t = new THREE.Texture(r.canvas);
      t.needsUpdate = true;
      srgb(t);
      return t;
    });
    petTexCache[kind] = p;
    return p;
  }

  /* ---------- Thumbnails (Queue) ---------- */
  var thumbQ = [], thumbBusy = false;

  function thumb(item, w, h) {
    return new Promise(function (res) {
      if (!bind()) { res(null); return; }
      thumbQ.push({ item: item, w: w, h: h, res: res });
      pumpThumbs();
    });
  }

  function pumpThumbs() {
    if (thumbBusy || !thumbQ.length) return;
    thumbBusy = true;
    requestAnimationFrame(function () {
      var j = thumbQ.shift();
      renderThumb(j.item, j.w || 256, j.h || 320).then(function (u) { j.res(u); }, function (err) { try { console.warn('[Cosmetic3D] Thumbnail fehlgeschlagen:', j.item && j.item.id, err && err.message); } catch (e) {} j.res(null); })
        .then(function () { thumbBusy = false; pumpThumbs(); });
    });
  }

  function renderThumb(item, W, H) {
    var key = keyFor(item);
    if (!key) return Promise.resolve(null);
    return Promise.all([getMerged(key), loadSkin(null)]).then(function (arr) {
      var m = arr[0], skin = arr[1];
      var r = ensureRenderer(W, H);
      var scene = baseScene();
      var stage = new THREE.Group();
      scene.add(stage);
      var pl = buildPlayer(skin);
      stage.add(pl.group);
      var mesh = new THREE.Mesh(m.geo, m.mat);
      var off = placeOffset(item, m.box);
      mesh.position.copy(off);
      stage.add(mesh);
      var cb = m.box.clone().translate(off);
      var cz = (cb.min.z + cb.max.z) / 2;
      var back = cz > 1.0 || item.category === 'cape' || item.category === 'gekauft' || item.category === 'back_accessory';
      var headCat = item.category === 'head_accessory' || item.category === 'face_accessory';
      var target;
      if (headCat) {
        target = new THREE.Box3(new THREE.Vector3(-5, 22, -5), new THREE.Vector3(5, 34, 5));
        target.union(cb);
      } else {
        target = unionBox([cb]);
      }
      stage.rotation.y = back ? Math.PI - 0.55 : 0.55;
      var camera = new THREE.PerspectiveCamera(30, W / H, 0.1, 600);
      fitCamera(camera, target, W / H, false);
      r.render(scene, camera);
      var url = r.domElement.toDataURL('image/png');
      pl.dispose();
      return url;
    });
  }

  /* ---------- Spieler-Ansicht (Cosmetics-Tab, Home, Modal) ---------- */
  var mounts = [];
  var rafOn = false;
  var lastFrame = 0;

  function kick() {
    if (rafOn) return;
    rafOn = true;
    requestAnimationFrame(tick);
  }

  function needsFrame(h) {
    if (h.done || !h.visible || h.hidden) return false;
    return h.animate || h.dirty;
  }

  function tick(now) {
    rafOn = false;
    if (document.hidden) { return; }
    var any = false;
    for (var i = 0; i < mounts.length; i++) if (needsFrame(mounts[i])) { any = true; break; }
    if (!any) return;
    rafOn = true;
    requestAnimationFrame(tick);
    if (now - lastFrame < 30) return; // ~30 FPS
    var dt = Math.min(0.1, (now - (lastFrame || now)) / 1000);
    lastFrame = now;
    mounts.slice().forEach(function (h) {
      try {
        if (!h.canvas.isConnected) { disposeMount(h); return; }
        if (!needsFrame(h)) return;
        drawMount(h, dt, now);
      } catch (e) {
        disposeMount(h);
      }
    });
  }

  document.addEventListener('visibilitychange', function () { if (!document.hidden) kick(); });

  function drawMount(h, dt, now) {
    var cv = h.canvas;
    var rc = cv.getBoundingClientRect();
    if (rc.width < 4 || rc.height < 4) { h.hidden = true; return; }
    var dpr = Math.min(window.devicePixelRatio || 1, 2);
    var W = Math.round(rc.width * dpr), H = Math.round(rc.height * dpr);
    if (cv.width !== W || cv.height !== H) { cv.width = W; cv.height = H; h.dirty = true; }
    if (h.animate && h.autoRotate && (now - h.lastInteract) > 2500) h.rot += dt * 0.7;
    h.stage.rotation.y = h.rot;
    if (h.pet) h.pet.position.y = h.petY + Math.sin(now / 520) * 0.9;
    if (h.refit || h.camAspect !== W / H) {
      fitCamera(h.camera, h.box, W / H, true);
      h.camAspect = W / H;
      h.refit = false;
    }
    var r = ensureRenderer(W, H);
    r.render(h.scene, h.camera);
    var ctx = cv.getContext('2d');
    ctx.clearRect(0, 0, W, H);
    ctx.drawImage(r.domElement, 0, 0, W, H);
    h.dirty = false;
  }

  function disposeMount(h) {
    if (h.done) return;
    h.done = true;
    var i = mounts.indexOf(h);
    if (i >= 0) mounts.splice(i, 1);
    try { if (h.io) h.io.disconnect(); } catch (e) {}
    try { if (h.player) h.player.dispose(); } catch (e) {}
    try { if (h.cape) h.cape.dispose(); } catch (e) {}
    try { if (h.pet && h.pet.material) h.pet.material.dispose(); } catch (e) {}
    if (h.onPointerDown) h.canvas.removeEventListener('pointerdown', h.onPointerDown);
  }

  function mountPlayer(canvas, opts) {
    opts = opts || {};
    if (!bind()) throw new Error('THREE fehlt');
    var h = {
      canvas: canvas, done: false, visible: true, hidden: false, dirty: true, refit: true,
      animate: opts.animate !== false, autoRotate: opts.autoRotate !== false,
      rot: opts.rot != null ? opts.rot : 0.55, lastInteract: -1e9, camAspect: 0,
      scene: baseScene(), stage: new THREE.Group(), itemsG: new THREE.Group(),
      camera: new THREE.PerspectiveCamera(30, 1, 0.1, 600),
      player: null, cape: null, pet: null, petY: 21, capeG: null,
      box: unionBox([]), skinUrl: null, capeUrl: null, itemKeys: '', gen: 0, items: []
    };
    h.scene.add(h.stage);
    h.stage.add(h.itemsG);
    canvas.style.cursor = 'grab';
    canvas.style.touchAction = 'pan-y';

    // Ziehen zum Drehen
    var drag = null;
    h.onPointerDown = function (e) {
      drag = { x: e.clientX, rot: h.rot };
      h.lastInteract = performance.now();
      try { canvas.setPointerCapture(e.pointerId); } catch (er) {}
      canvas.style.cursor = 'grabbing';
      var move = function (ev) {
        if (!drag) return;
        h.rot = drag.rot + (ev.clientX - drag.x) * 0.012;
        h.lastInteract = performance.now();
        h.dirty = true;
        kick();
      };
      var up = function () {
        drag = null;
        canvas.style.cursor = 'grab';
        canvas.removeEventListener('pointermove', move);
        canvas.removeEventListener('pointerup', up);
        canvas.removeEventListener('pointercancel', up);
      };
      canvas.addEventListener('pointermove', move);
      canvas.addEventListener('pointerup', up);
      canvas.addEventListener('pointercancel', up);
    };
    canvas.addEventListener('pointerdown', h.onPointerDown);

    if (typeof IntersectionObserver !== 'undefined') {
      h.io = new IntersectionObserver(function (ents) {
        var vis = ents.some(function (en) { return en.isIntersecting; });
        h.visible = vis;
        if (vis) { h.hidden = false; h.dirty = true; kick(); }
      });
      h.io.observe(canvas);
    }
    mounts.push(h);

    function recomputeBox() {
      var boxes = [];
      h.itemsG.children.forEach(function (m) {
        if (m.userData.box) boxes.push(m.userData.box.clone().translate(m.position));
      });
      if (h.capeG) boxes.push(new THREE.Box3(new THREE.Vector3(-5, 8, 2), new THREE.Vector3(5, 24, 4.5)));
      if (h.pet) boxes.push(new THREE.Box3(new THREE.Vector3(-19.5, 14, -13), new THREE.Vector3(-6.5, 28, 1)));
      h.box = unionBox(boxes);
      h.refit = true;
      h.dirty = true;
      kick();
    }

    function applyVanillaCape() {
      if (h.capeG) { h.stage.remove(h.capeG); try { h.cape.dispose(); } catch (e) {} h.capeG = null; h.cape = null; }
      var hasCosCape = h.items.some(function (it) { return it && it.category === 'cape' && keyFor(it); });
      if (!h.capeUrl || hasCosCape) { recomputeBox(); return; }
      var gen = h.gen;
      loadCape(h.capeUrl).then(function (c) {
        if (h.done || gen !== h.gen || !c) return;
        h.cape = buildVanillaCape(c);
        h.capeG = h.cape.group;
        h.stage.add(h.capeG);
        recomputeBox();
      });
    }

    function applyItems() {
      var gen = ++h.gen;
      // alte Meshes entfernen (Geometrie ist geteilt -> nicht disposen)
      while (h.itemsG.children.length) h.itemsG.remove(h.itemsG.children[0]);
      if (h.pet) { h.stage.remove(h.pet); try { h.pet.material.dispose(); } catch (e) {} h.pet = null; }
      var list = h.items || [];
      var pending = [];
      list.forEach(function (it) {
        var key = keyFor(it);
        if (key) {
          pending.push(getMerged(key).then(function (m) {
            if (h.done || gen !== h.gen) return;
            var mesh = new THREE.Mesh(m.geo, m.mat);
            mesh.position.copy(placeOffset(it, m.box));
            mesh.userData.box = m.box;
            h.itemsG.add(mesh);
          }, function () {}));
        } else if (it && it.category === 'pet') {
          pending.push(loadPetTex(petKind(it)).then(function (t) {
            if (h.done || gen !== h.gen || !t) return;
            var sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: t, transparent: true, depthWrite: false }));
            sp.scale.set(11, 11, 1);
            sp.position.set(-13, h.petY, -6);
            h.pet = sp;
            h.stage.add(sp);
          }));
        }
      });
      if (!pending.length) { applyVanillaCape(); return; }
      Promise.all(pending).then(function () {
        if (h.done || gen !== h.gen) return;
        applyVanillaCape();
      });
    }

    function applySkin() {
      var url = h.skinUrl;
      loadSkin(url).then(function (info) {
        if (h.done || url !== h.skinUrl) return;
        if (h.player) { h.stage.remove(h.player.group); h.player.dispose(); }
        h.player = buildPlayer(info);
        h.stage.add(h.player.group);
        h.dirty = true;
        kick();
        if (info.failed && opts.onSkinError) { try { opts.onSkinError(url); } catch (e) {} }
      });
    }

    h.skinUrl = opts.skin || null;
    h.capeUrl = opts.cape || null;
    h.items = opts.items || [];
    h.itemKeys = h.items.map(function (it) { return it && it.id; }).join('|');
    applySkin();
    applyItems();

    return {
      setItems: function (items) {
        var keys = (items || []).map(function (it) { return it && it.id; }).join('|');
        if (keys === h.itemKeys) return;
        h.itemKeys = keys;
        h.items = items || [];
        applyItems();
      },
      setSkin: function (url) { if ((url || null) === h.skinUrl) return; h.skinUrl = url || null; applySkin(); },
      setCape: function (url) { if ((url || null) === h.capeUrl) return; h.capeUrl = url || null; applyItems(); },
      dispose: function () { disposeMount(h); },
      isDisposed: function () { return h.done; }
    };
  }

  /* ---------- Modal-Vorschau (alter Einstieg) ---------- */
  var modalMounts = [];
  function mount(el, item) {
    var key = keyFor(item);
    if (!key || !el) return null;
    el.innerHTML = '';
    var cv = document.createElement('canvas');
    cv.style.width = '100%';
    cv.style.height = '100%';
    cv.style.display = 'block';
    el.appendChild(cv);
    var ctrl = mountPlayer(cv, { skin: null, items: [item], animate: true });
    modalMounts.push(ctrl);
    return function () { ctrl.dispose(); };
  }

  function unmountAll() {
    modalMounts.splice(0).forEach(function (c) { try { c.dispose(); } catch (e) {} });
  }

  window.Cosmetic3D = {
    ready: ready,
    keyFor: keyFor,
    petKind: petKind,
    petCropped: petCropped,
    thumb: thumb,
    mount: mount,
    mountPlayer: mountPlayer,
    unmount: unmountAll
  };
})();
