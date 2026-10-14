(function () {
  'use strict';
  var DEG = Math.PI / 180;
  var modelCache = {};
  var templateCache = {};
  var renderer = null;
  var rendererOK = null;
  var mounts = [];
  var rafOn = false;
  var lastT = 0;

  function ready() {
    if (rendererOK !== null) return rendererOK;
    try {
      if (typeof THREE === 'undefined') { rendererOK = false; return false; }
      var test = document.createElement('canvas');
      var gl = test.getContext('webgl') || test.getContext('experimental-webgl');
      if (!gl) { rendererOK = false; return false; }
      rendererOK = true;
      return true;
    } catch (e) {
      rendererOK = false;
      return false;
    }
  }

  function ensureRenderer(w, h) {
    if (!renderer) {
      renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
      renderer.outputEncoding = THREE.sRGBEncoding;
    }
    var W = Math.max(2, Math.round(w)), H = Math.max(2, Math.round(h));
    if (renderer.domElement.width !== W * 1 || renderer.domElement.height !== H * 1) {
      renderer.setSize(W, H, false);
      renderer.setPixelRatio(1);
    }
    return renderer;
  }

  function keyFor(item) {
    try {
      if (!item || !item.data || !item.data.texture || !item.data.model) return null;
      var cats = ['cape', 'shield', 'back_accessory', 'head_accessory', 'face_accessory', 'aura', 'shoulder_accessory', 'torso_accessory', 'legs_accessory'];
      if (cats.indexOf(item.category) < 0) return null;
      var m = String(item.data.texture).match(/^kollegen:cosmetics\/([^/]+)\/(.+)\.png$/);
      if (!m) return null;
      return m[1] + '_' + m[2];
    } catch (e) {
      return null;
    }
  }

  function loadImage(src, cors) {
    return new Promise(function (res, rej) {
      var im = new Image();
      if (cors) im.crossOrigin = 'anonymous';
      im.onload = function () { res(im); };
      im.onerror = rej;
      im.src = src;
    });
  }
  var skinCache = {};
  function skinTexture(uuid) {
    var id = String(uuid || '').replace(/-/g, '');
    if (!id) return Promise.resolve(null);
    if (skinCache[id] !== undefined) return Promise.resolve(skinCache[id]);
    return loadImage('https://crafatar.com/skins/' + id + '?overlay', true).then(function (im) {
      if (!im || !im.width) throw new Error('bad skin');
      var t = makeTex(im);
      skinCache[id] = t;
      return t;
    }, function () {
      skinCache[id] = null;
      return null;
    });
  }

  function loadModel(key) {
    if (modelCache[key]) return Promise.resolve(modelCache[key]);
    var p = Promise.all([
      fetch('cosmetics/' + key + '.json').then(function (r) { if (!r.ok) throw new Error('no json'); return r.json(); }),
      loadImage('cosmetics/' + key + '.png')
    ]).then(function (arr) {
      var entry = { mod: arr[0], img: arr[1], tex: null };
      modelCache[key] = entry;
      return entry;
    });
    modelCache[key] = null;
    return p.then(function (e) { return e; }, function (err) { delete modelCache[key]; throw err; });
  }

  function makeTex(img) {
    var t = new THREE.Texture(img);
    t.needsUpdate = true;
    t.magFilter = THREE.NearestFilter;
    t.minFilter = THREE.NearestFilter;
    t.encoding = THREE.sRGBEncoding;
    return t;
  }

  function setUV(geo, rects, W, H) {
    var uv = geo.attributes.uv;
    var order = ['east', 'west', 'up', 'down', 'south', 'north'];
    order.forEach(function (f, idx) {
      var rc = (rects && rects[f]) || [0, 0, 0, 0];
      var u0 = rc[0], v0 = rc[1], u1 = rc[2], v1 = rc[3];
      var b = idx * 4;
      uv.setXY(b, u0 / W, 1 - v0 / H);
      uv.setXY(b + 1, u1 / W, 1 - v0 / H);
      uv.setXY(b + 2, u0 / W, 1 - v1 / H);
      uv.setXY(b + 3, u1 / W, 1 - v1 / H);
    });
    uv.needsUpdate = true;
  }

  function boxUV(u, v, w, h, d) {
    return {
      east: [u, v + d, u + d, v + d + h],
      north: [u + d, v + d, u + d + w, v + d + h],
      west: [u + d + w, v + d, u + 2 * d + w, v + d + h],
      south: [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
      up: [u + d + w, v + d, u + d, v],
      down: [u + d + 2 * w, v + d, u + d + w, v]
    };
  }

  function buildCosmeticGroup(entry) {
    var mod = entry.mod;
    var W = (mod.res && mod.res[0]) || 64, H = (mod.res && mod.res[1]) || 64;
    if (!entry.tex) entry.tex = makeTex(entry.img);
    var mat = new THREE.MeshStandardMaterial({ map: entry.tex, roughness: 0.55, metalness: 0.08, transparent: true, alphaTest: 0.4, side: THREE.DoubleSide });
    var byUuid = {};
    mod.elements.forEach(function (e) { if (e.uuid) byUuid[e.uuid] = e; });
    var root = new THREE.Group();
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
    var lastGroupOrigin = [0, 0, 0];
    var groups = {};
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
      groups[g.name] = go;
      var prev = lastGroupOrigin;
      lastGroupOrigin = o;
      (g.children || []).forEach(function (c) { buildGroup(c, go); });
      lastGroupOrigin = prev;
    }
    (mod.groups || []).forEach(function (g) { buildGroup(g, root); });
    return root;
  }

  function steveTexture() {
    var c = document.createElement('canvas');
    c.width = 64; c.height = 64;
    var g = c.getContext('2d');
    g.fillStyle = '#bd8260';
    g.fillRect(0, 0, 64, 64);
    g.fillStyle = '#ffffff';
    g.fillRect(5, 6, 2, 2); g.fillRect(9, 6, 2, 2);
    g.fillStyle = '#2b4bd3';
    g.fillRect(6, 6, 1, 2); g.fillRect(9, 6, 1, 2);
    g.fillStyle = '#7a4a32';
    g.fillRect(6, 10, 4, 1);
    g.fillStyle = '#3b7d8c';
    g.fillRect(20, 20, 8, 12); g.fillRect(36, 20, 8, 12); g.fillRect(4, 20, 4, 12); g.fillRect(52, 20, 4, 12);
    g.fillStyle = '#2c3a68';
    g.fillRect(4, 20, 4, 12); g.fillRect(20, 20, 4, 12);
    g.fillRect(4, 20, 4, 12); g.fillRect(20, 20, 4, 12);
    g.fillStyle = '#333a4d';
    g.fillRect(0, 16, 16, 16); g.fillRect(16, 48, 16, 16); g.fillRect(0, 48, 16, 16);
    g.fillStyle = '#2c3a68';
    g.fillRect(4, 20, 4, 12); g.fillRect(20, 20, 4, 12);
    var t = new THREE.CanvasTexture(c);
    t.magFilter = THREE.NearestFilter;
    t.minFilter = THREE.NearestFilter;
    t.encoding = THREE.sRGBEncoding;
    return t;
  }
  var steveTexSingleton = null;

  function buildPlayer(skinTex) {
    var mat = new THREE.MeshStandardMaterial({ map: skinTex || steveTextureMat(), roughness: 0.85 });
    var parts = [
      [-4, 24, -4, 8, 8, 8, 8, 8], [-4, 12, -2, 8, 12, 4, 20, 20],
      [4, 12, -2, 4, 12, 4, 44, 20], [-8, 12, -2, 4, 12, 4, 36, 52],
      [0, 0, -2, 4, 12, 4, 4, 20], [-4, 0, -2, 4, 12, 4, 20, 52]
    ];
    parts.forEach(function (p) {
      var x = p[0], y = p[1], z = p[2], w = p[3], h = p[4], d = p[5], u = p[6], v = p[7];
      var geo = new THREE.BoxGeometry(w, h, d);
      setUV(geo, boxUV(u, v, w, h, d), 64, 64);
      var m = new THREE.Mesh(geo, mat);
      m.position.set(x + w / 2, y + h / 2, z + d / 2);
      g.add(m);
    });
    return g;
  }

  function baseScene() {
    var scene = new THREE.Scene();
    scene.add(new THREE.HemisphereLight(0xe8eeff, 0x14161f, 0.95));
    var key = new THREE.DirectionalLight(0xffffff, 0.85);
    key.position.set(-25, 55, 35);
    scene.add(key);
    var rim = new THREE.DirectionalLight(0x88aaff, 0.35);
    rim.position.set(30, 20, -30);
    scene.add(rim);
    return scene;
  }

  function frameCamera(camera, center, radius, aspect) {
    var fov = camera.fov * DEG;
    var dist = (radius / Math.tan(fov / 2)) * 1.18;
    var dir = new THREE.Vector3(0.55, 0.34, -1).normalize();
    camera.position.copy(center).addScaledVector(dir, dist);
    camera.aspect = aspect;
    camera.lookAt(center);
    camera.updateProjectionMatrix();
  }

  function thumb(item, w, h) {
    var key = keyFor(item);
    if (!key) return Promise.resolve(null);
    return loadModel(key).then(function (entry) {
      var W = w || 220, H = h || 124;
      var r = ensureRenderer(W, H);
      var scene = baseScene();
      var stage = new THREE.Group();
      var cos = buildCosmeticGroup(entry);
      stage.add(cos);
      scene.add(stage);
      var box = new THREE.Box3().setFromObject(cos);
      if (box.isEmpty()) return null;
      var center = box.getCenter(new THREE.Vector3());
      var radius = box.getSize(new THREE.Vector3()).length() / 2;
      var camera = new THREE.PerspectiveCamera(34, W / H, 0.1, 500);
      frameCamera(camera, center, Math.max(2, radius), W / H);
      r.render(scene, camera);
      var url = r.domElement.toDataURL('image/png');
      scene.traverse(function (o) { if (o.geometry) o.geometry.dispose(); });
      return url;
    }, function () { return null; });
  }

  function tickLoop() {
    if (!mounts.length) { rafOn = false; return; }
    rafOn = true;
    requestAnimationFrame(tickLoop);
    var now = (typeof performance !== 'undefined' && performance.now) ? performance.now() : Date.now();
    var dt = Math.min(0.1, (now - (lastT || now)) / 1000);
    lastT = now;
    mounts.slice().forEach(function (m) {
      try {
        if (!m.el.isConnected) { unmount(m); return; }
        var w = m.el.clientWidth || 320, h = m.el.clientHeight || 300;
        var r = ensureRenderer(w, h);
        m.stage.rotation.y += dt * 0.8;
        var cw = r.domElement.width, ch = r.domElement.height;
        if (m.canvas.width !== cw || m.canvas.height !== ch) { m.canvas.width = cw; m.canvas.height = ch; }
        m.camera.aspect = w / h;
        m.camera.updateProjectionMatrix();
        r.render(m.scene, m.camera);
        var ctx = m.canvas.getContext('2d');
        ctx.clearRect(0, 0, m.canvas.width, m.canvas.height);
        ctx.drawImage(r.domElement, 0, 0, m.canvas.width, m.canvas.height);
      } catch (e) {
        unmount(m);
      }
    });
  }

  function mount(el, item) {
    var key = keyFor(item);
    if (!key || !el) return null;
    var handle = { el: el, key: key, canvas: null, scene: null, camera: null, stage: null, done: false };
    el.innerHTML = '';
    var cv = document.createElement('canvas');
    cv.style.width = '100%';
    cv.style.height = '100%';
    cv.style.display = 'block';
    el.appendChild(cv);
    handle.canvas = cv;
    loadModel(key).then(function (entry) {
      if (handle.done || !el.isConnected) return;
      var scene = baseScene();
      var stage = new THREE.Group();
      stage.add(buildPlayer());
      stage.add(buildCosmeticGroup(entry));
      scene.add(stage);
      var camera = new THREE.PerspectiveCamera(34, 1, 0.1, 500);
      var center = new THREE.Vector3(0, 16, 0);
      frameCamera(camera, center, 21, 1);
      handle.scene = scene;
      handle.camera = camera;
      handle.stage = stage;
      mounts.push(handle);
      lastT = 0;
      if (!rafOn) tickLoop();
    }, function () {
      if (!handle.done) el.innerHTML = '<div class="store-msg">Keine Vorschau.</div>';
    });
    return function () { unmount(handle); };
  }

  function unmount(handle) {
    handle.done = true;
    var i = mounts.indexOf(handle);
    if (i >= 0) mounts.splice(i, 1);
    try {
      if (handle.scene) handle.scene.traverse(function (o) { if (o.geometry) o.geometry.dispose(); });
    } catch (e) {}
  }

  function unmountAll() {
    mounts.slice().forEach(unmount);
  }

  window.Cosmetic3D = {
    ready: ready,
    keyFor: keyFor,
    thumb: thumb,
    mount: mount,
    unmount: unmountAll
  };
})();
