/* Kleiner Renderer fuer die Blockbench-Cosmetics des Kollegen Clients.
   Liest .bbmodel + Textur aus dem Mod-Jar und baut daraus Three.js-Geometrie.
   Die Modelle werden anhand ihrer Kategorie neben dem Skin platziert. */
(function () {
  const READY = "kollegen3d-ready";
  const cache = new Map();
  let THREE = null;

  function onReady(fn) {
    if (window.THREE) fn();
    else window.addEventListener(READY, fn, { once: true });
  }

  /* Eine Boxflaeche als Dreieck: Positionen + UV. */
  function pushFace(pos, uvs, idx, corners, uvr, res) {
    const base = pos.length / 3;
    for (let i = 0; i < 4; i++) {
      pos.push(corners[i][0], corners[i][1], corners[i][2]);
    }
    const [u1, v1, u2, v2] = uvr;
    // Blockbench rechnet die Textur von oben links; WebGL von unten links.
    let du = (u2 - u1) / res;
    let dv = (v2 - v1) / res;
    uvs.push(u1 / res, 1 - v1 / res - dv);
    uvs.push(u1 / res, 1 - v1 / res);
    uvs.push(u1 / res + du, 1 - v1 / res - dv);
    uvs.push(u1 / res + du, 1 - v1 / res);
    idx.push(base, base + 1, base + 2, base, base + 2, base + 3);
  }

  function faceCorners(f, x0, y0, z0, x1, y1, z1) {
    switch (f) {
      case "north": return [[x0, y1, z0], [x0, y1, z1], [x1, y1, z1], [x1, y1, z0]];
      case "south": return [[x1, y0, z1], [x1, y0, z0], [x0, y0, z0], [x0, y0, z1]];
      case "east": return [[x1, y0, z1], [x1, y1, z1], [x1, y1, z0], [x1, y0, z0]];
      case "west": return [[x0, y0, z0], [x0, y1, z0], [x0, y1, z1], [x0, y0, z1]];
      case "up": return [[x0, y1, z1], [x1, y1, z1], [x1, y1, z0], [x0, y1, z0]];
      default: return [[x0, y0, z0], [x1, y0, z0], [x1, y0, z1], [x0, y0, z1]];
    }
  }

  function buildGeometry(bb) {
    const res = (bb.resolution && bb.resolution.width) || 64;
    const pos = [], uvs = [], idx = [];
    for (const el of bb.elements || []) {
      if (el.type && el.type !== "cube") continue;
      const [x0, y0, z0] = el.from;
      const [x1, y1, z1] = el.to;
      const inflate = el.inflate || 0;
      const faces = el.faces || {};
      for (const f of ["north", "south", "east", "west", "up", "down"]) {
        const fd = faces[f];
        if (!fd || !fd.uv) continue;
        const corners = faceCorners(f,
          Math.min(x0, x1) - inflate, Math.min(y0, y1) - inflate, Math.min(z0, z1) - inflate,
          Math.max(x0, x1) + inflate, Math.max(y0, y1) + inflate, Math.max(z0, z1) + inflate);
        pushFace(pos, uvs, idx, corners, fd.uv, res);
      }
    }
    if (!pos.length) return null;
    const g = new THREE.BufferGeometry();
    g.setAttribute("position", new THREE.Float32BufferAttribute(pos, 3));
    g.setAttribute("uv", new THREE.Float32BufferAttribute(uvs, 2));
    g.setIndex(idx);
    g.computeVertexNormals();
    return g;
  }

  /* Baut die Szene im nebenstehenden Canvas auf. */
  function render(canvas, assets) {
    if (!window.THREE || !canvas || !assets || !assets.length) return;
    canvas.innerHTML = "";
    const w = canvas.clientWidth || 300;
    const h = canvas.clientHeight || 420;
    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0a0c10);

    const camera = new THREE.PerspectiveCamera(45, w / h, 0.1, 500);
    camera.position.set(0, 0.4, 2.2);

    scene.add(new THREE.AmbientLight(0xffffff, 0.75));
    const key = new THREE.DirectionalLight(0xffffff, 0.9);
    key.position.set(2, 3, 4);
    scene.add(key);

    let maxY = 1;
    const placed = [];
    for (const a of assets) {
      const geo = buildGeometry(a.model);
      if (!geo) continue;
      const mat = new THREE.MeshLambertMaterial({
        map: a.texture || null,
        transparent: true,
        alphaTest: 0.04,
        side: THREE.DoubleSide,
      });
      const mesh = new THREE.Mesh(geo, mat);
      const sc = a.scale || 0.035;
      mesh.scale.setScalar(sc);
      // Ursprung der Mitte, damit die Platzierung greift
      geo.computeBoundingBox();
      const bb2 = geo.boundingBox;
      const cx = (bb2.min.x + bb2.max.x) / 2;
      const cy = (bb2.min.y + bb2.max.y) / 2;
      const cz = (bb2.min.z + bb2.max.z) / 2;
      geo.translate(-cx, -cy, -cz);
      mesh.position.set(a.x || 0, a.y || 0, a.z || 0);
      scene.add(mesh);
      placed.push(mesh);
      maxY = Math.max(maxY, Math.abs(a.y || 0));
    }

    let renderer;
    try {
      renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false });
    } catch (e) {
      return;
    }
    renderer.setSize(w, h, false);
    canvas.appendChild(renderer.domElement);

    let raf = 0;
    const t0 = performance.now();
    const loop = () => {
      const t = (performance.now() - t0) / 1000;
      for (const m of placed) m.rotation.y = Math.sin(t * 0.8) * 0.6 + t * 0.25;
      renderer.render(scene, camera);
      raf = requestAnimationFrame(loop);
    };
    loop();
    canvas.__kollegen3dStop = () => cancelAnimationFrame(raf);
  }

  const ANCHORS = {
    head_accessory: { x: 0, y: 1.15, z: 0, s: 0.030 },
    face_accessory: { x: 0, y: 0.72, z: 0.5, s: 0.030 },
    shoulder_accessory: { x: 0.75, y: 0.55, z: 0, s: 0.032 },
    back_accessory: { x: 0, y: 0.15, z: -0.55, s: 0.036 },
    torso_accessory: { x: 0, y: 0.15, z: 0.5, s: 0.034 },
    legs_accessory: { x: 0.55, y: -0.75, z: -0.2, s: 0.034 },
    shield: { x: -0.7, y: 0.25, z: 0.2, s: 0.032 },
    cape: { x: 0, y: 0.45, z: -0.5, s: 0.038 },
    aura: { x: 0, y: 0.1, z: 0, s: 0.040 },
  };

  async function loadAsset(path) {
    if (cache.has(path)) return cache.get(path);
    const p = (async () => {
      const res = await window.__kginvoke("kollegen_mod_asset", { file: path });
      if (!res || res.error) return null;
      let model = null;
      if (res.bbmodel) {
        try { model = JSON.parse(res.bbmodel); } catch (e) { model = null; }
      }
      let texture = null;
      const loader = res.texture || res.data;
      if (loader) {
        try {
          texture = await new Promise((ok, no) => {
            new THREE.TextureLoader().load(loader, ok, undefined, no);
          });
        } catch (e) { texture = null; }
      }
      return model ? { model, texture } : null;
    })();
    cache.set(path, p);
    return p;
  }

  /* Sammelt die ausgeruesteten Modelle und zeichnet sie neben den Skin. */
  window.kollegen3dRenderEquipped = async function (canvas, equipped, catalog) {
    onReady(async () => {
      const byId = {};
      for (const it of catalog || []) byId[it.id] = it;
      const jobs = [];
      for (const cat of Object.keys(equipped || {})) {
        const id = equipped[cat];
        if (!id) continue;
        const it = byId[id];
        if (!it || !it.data || !it.data.model) continue;
        jobs.push({ cat, it });
      }
      if (!jobs.length) {
        if (canvas) canvas.innerHTML = "";
        return;
      }
      const assets = [];
      for (const j of jobs) {
        const loaded = await loadAsset(j.it.data.model);
        if (!loaded) continue;
        const a = Object.assign({}, ANCHORS[j.cat] || { x: 0, y: 0, z: 0, s: 0.032 });
        assets.push({
          model: loaded.model, texture: loaded.texture,
          x: a.x, y: a.y, z: a.z, scale: a.s,
        });
      }
      if (canvas) render(canvas, assets);
    });
  };
})();