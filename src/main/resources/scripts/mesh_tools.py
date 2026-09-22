#!/usr/bin/env python3
"""
Mesh printability tools for InnerStyle — invoked by the Java backend.

Usage:
    mesh_tools.py analyze <input>
        -> prints a JSON object describing printability.
    mesh_tools.py repair  <input> <output>
        -> repairs the mesh (watertight) into <output>, prints {"before":..,"after":..}.
    mesh_tools.py base    <input> <output> [shape] [heightRatio] [marginRatio] [color] [sigSpec]
        -> adds a base under the model (optionally engraving a signature), prints {"ok":..}.

Depends on: trimesh, pymeshfix, numpy (analyze/repair/base) plus matplotlib, shapely,
manifold3d, mapbox_earcut (signature engraving on the base).

Notes:
- Vertices are welded first (merge_vertices); GLB/OBJ exporters often split vertices for
  normals/UVs, which would otherwise make every edge look like a boundary.
- "holes" counts connected boundary loops; "nonManifoldEdges" counts edges shared by >2 faces.
- repair rebuilds the mesh's vertices/faces from scratch (pymeshfix), which drops all colour —
  the texture is re-attached afterwards by nearest-neighbour UV transfer from the pre-repair
  model, or the "repaired" model would export as a plain grey/white lump. That transfer is capped
  (size guard + a hard wall-clock budget) and always best-effort: past either limit it just skips,
  same as if no texture had been found, rather than risk an API-gateway timeout.
"""

import gc
import json
import sys
import time

import numpy as np
import trimesh
import pymeshfix


def load(path):
    mesh = trimesh.load(path, force="mesh")
    if isinstance(mesh, trimesh.Scene):
        mesh = trimesh.util.concatenate(tuple(mesh.geometry.values()))
    mesh.merge_vertices()
    return mesh


# Above this vertex count, skip the repair texture-transfer pass (it's an O(n*m) nearest-neighbour
# search) rather than risk a slow repair or an OOM on a constrained container — the repaired model
# just exports untextured in that case, same as before this fix.
MAX_REPAIR_TEXTURE_VERTS = 60_000


def _load_scene_with_texture(path):
    """Load `path` as a Scene (process=False, so vertex order matches the glTF accessors) with its
    UVs recovered and base-color texture captured. Used by `repair`, which otherwise has no visual
    info to carry through pymeshfix's full vertex/face rebuild."""
    scene = trimesh.load(path, process=False)
    if isinstance(scene, trimesh.Trimesh):
        scene = trimesh.Scene(scene)
    _attach_gltf_uvs(scene, path)
    dom = _dominant_texture(scene) or _external_texture_image(path)
    return scene, dom


def _combined_vertices_uv(scene):
    """Flatten every geometry in `scene` into one (vertices, uv-or-None) pair, in the scene's own
    per-geometry vertex order. trimesh.util.concatenate drops UVs when geometries don't share one
    visual type (a Meshy figure's body/hair/clothes primitives usually don't), so this is done by
    hand instead. `uv` is None unless every geometry has one UV row per vertex."""
    verts, uvs = [], []
    have_uv = True
    for geom in scene.geometry.values():
        v = np.asarray(geom.vertices)
        verts.append(v)
        uv = getattr(getattr(geom, "visual", None), "uv", None)
        if uv is not None and len(uv) == len(v):
            uvs.append(np.asarray(uv))
        else:
            have_uv = False
    vertices = np.concatenate(verts, axis=0) if verts else np.zeros((0, 3))
    uv_arr = np.concatenate(uvs, axis=0) if (have_uv and uvs) else None
    return vertices, uv_arr


def _dedupe_points(points, values, decimals=5):
    """Collapse near-duplicate 3D points (e.g. per-face-corner vertex splits for UVs/normals in a
    GLB export) down to one representative each, keeping whichever duplicate's row in `values` is
    kept. Shrinks a nearest-neighbour search space back toward the mesh's true (welded) vertex
    count instead of its export-inflated one."""
    if len(points) == 0:
        return points, values
    _, idx = np.unique(np.round(points, decimals), axis=0, return_index=True)
    return points[idx], values[idx]


def _mesh_from_scene(scene):
    """Merge every geometry in `scene` into one welded Trimesh -- the same result as
    load()'s `force="mesh"` path, but reused from an already-loaded scene so `repair` only parses
    the source file (and decodes its texture image) once instead of twice."""
    verts, faces = [], []
    voffset = 0
    for geom in scene.geometry.values():
        v = np.asarray(geom.vertices)
        verts.append(v)
        faces.append(np.asarray(geom.faces) + voffset)
        voffset += len(v)
    mesh = trimesh.Trimesh(
        vertices=np.concatenate(verts, axis=0) if verts else np.zeros((0, 3)),
        faces=np.concatenate(faces, axis=0) if faces else np.zeros((0, 3), dtype=np.int64),
        process=False,
    )
    mesh.merge_vertices()
    return mesh


def _nearest_uv(src_vertices, src_uv, dst_vertices, chunk=200, time_budget=15.0):
    """Nearest-neighbour UV transfer, numpy-only (no scipy dependency to add). For every point in
    `dst_vertices`, copies the UV of the closest point in `src_vertices` (by squared distance —
    sqrt isn't needed just to compare). Chunked so the pairwise distance matrix never fully
    materialises for a 10k+ vertex figure. Bailing out past `time_budget` seconds (caught by the
    caller, which just exports untextured) matters more here than finishing: this runs inside a
    repair request the API gateway will 504 if it takes too long, whatever the mesh size."""
    src = np.asarray(src_vertices, dtype=np.float32)
    dst = np.asarray(dst_vertices, dtype=np.float32)
    out = np.empty((len(dst), src_uv.shape[1]), dtype=src_uv.dtype)
    deadline = time.monotonic() + time_budget
    for i in range(0, len(dst), chunk):
        if time.monotonic() > deadline:
            raise TimeoutError("nearest_uv exceeded its time budget")
        block = dst[i:i + chunk]
        d = ((block[:, None, :] - src[None, :, :]) ** 2).sum(axis=2)
        out[i:i + chunk] = src_uv[np.argmin(d, axis=1)]
    return out


def count_holes(boundary_edges):
    if len(boundary_edges) == 0:
        return 0
    parent = {}

    def find(x):
        parent.setdefault(x, x)
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    for a, b in boundary_edges:
        parent[find(int(a))] = find(int(b))
    return len({find(int(a)) for a, b in boundary_edges})


def analyze(mesh):
    uniq, counts = np.unique(mesh.edges_sorted, axis=0, return_counts=True)
    boundary = uniq[counts == 1]
    return {
        "watertight": bool(mesh.is_watertight),
        "volume": float(abs(mesh.volume)),
        "triangles": int(len(mesh.faces)),
        "boundaryEdges": int(len(boundary)),
        "holes": int(count_holes(boundary)),
        "nonManifoldEdges": int((counts > 2).sum()),
    }


def parse_color(color):
    """Parse a #RRGGBB / RRGGBB hex string into an RGBA uint8 array; default neutral grey."""
    default = np.array([200, 200, 200, 255], dtype=np.uint8)
    if not color:
        return default
    s = str(color).strip().lstrip("#")
    if len(s) != 6:
        return default
    try:
        r = int(s[0:2], 16)
        g = int(s[2:4], 16)
        b = int(s[4:6], 16)
    except ValueError:
        return default
    return np.array([r, g, b, 255], dtype=np.uint8)


def make_base(shape, radius, height, color=None):
    """A base primitive whose thickness runs along +Y (model up-axis), centred at the origin."""
    s = (shape or "cylinder").lower()
    if s == "square":
        base = trimesh.creation.box(extents=[radius * 2.0, height, radius * 2.0])
    elif s == "hexagon":
        base = trimesh.creation.cylinder(radius=radius, height=height, sections=6)
        base.apply_transform(trimesh.transformations.rotation_matrix(np.pi / 2.0, [1, 0, 0]))
    else:  # cylinder
        base = trimesh.creation.cylinder(radius=radius, height=height, sections=64)
        base.apply_transform(trimesh.transformations.rotation_matrix(np.pi / 2.0, [1, 0, 0]))
    base.visual.vertex_colors = parse_color(color)
    return base


def _text_polygon(text):
    """Build a shapely (Multi)Polygon of `text` honouring glyph holes (e.g. inside of 'o','a').

    Uses matplotlib to turn the string into glyph outlines, then XORs the contours together so
    the even-odd fill rule carves the holes correctly. DejaVu Sans (matplotlib's default) covers
    Vietnamese diacritics. Returns a polygon centred-ish at the origin in a ~1-unit tall box.
    """
    from matplotlib.textpath import TextPath
    from matplotlib.font_manager import FontProperties
    from shapely.geometry import Polygon

    fp = FontProperties(family="DejaVu Sans", weight="bold")
    tp = TextPath((0, 0), text, size=1.0, prop=fp)
    geom = None
    for loop in tp.to_polygons():
        if len(loop) < 3:
            continue
        poly = Polygon(loop)
        if not poly.is_valid:
            poly = poly.buffer(0)
        if poly.is_empty:
            continue
        geom = poly if geom is None else geom.symmetric_difference(poly)
    return geom


def _strokes_polygon(strokes, pen_width):
    """Build a polygon from hand-drawn signature strokes (a list of polylines).

    Each stroke is a list of [x, y] points (canvas space, y pointing DOWN). We thicken every
    stroke into a ribbon with round caps/joins (shapely buffer) and union them, then flip Y so the
    signature is upright. A single-point stroke becomes a dot. `pen_width` is in the same units as
    the points (e.g. normalised [0,1] coords -> ~0.03).
    """
    from shapely.geometry import LineString, Point
    from shapely.ops import unary_union
    import shapely.affinity as affinity

    r = max(float(pen_width), 1e-4) / 2.0
    pieces = []
    for stroke in strokes or []:
        pts = [(float(p[0]), float(p[1])) for p in stroke if len(p) >= 2]
        if len(pts) >= 2:
            pieces.append(LineString(pts).buffer(r, cap_style=1, join_style=1))
        elif len(pts) == 1:
            pieces.append(Point(pts[0]).buffer(r))
    if not pieces:
        return None
    geom = unary_union(pieces)
    # Canvas Y is down; flip to Y-up so the engraving shares the text path's orientation.
    return affinity.scale(geom, xfact=1.0, yfact=-1.0, origin=(0, 0))


def _signature_polygon(spec):
    """Resolve a signature spec ({type: 'text'|'strokes', ...}) into a shapely (Multi)Polygon."""
    if (spec.get("type") or "text") == "strokes":
        return _strokes_polygon(spec.get("strokes"), spec.get("penWidth", 0.04))
    text = (spec.get("text") or "").strip()
    return _text_polygon(text) if text else None


def engrave_signature(base, geom, radius, base_h, depth_ratio, raised):
    """Carve (or raise) a signature polygon on the SIDE WALL of the base primitive.

    Done on the low-poly base only (never the full model), so the boolean stays cheap. The base is
    centred at the origin with its thickness along Y in [-base_h/2, +base_h/2] and its footprint in
    the XZ plane; the front-facing side wall is at z = +radius. The signature reads upright when the
    base is viewed head-on from the front (+Z), like an engraved nameplate on the pedestal — no need
    to flip the base over to see it.
    """
    import shapely.affinity as affinity

    if geom is None or geom.is_empty:
        return base

    minx, miny, maxx, maxy = geom.bounds
    w, h = maxx - minx, maxy - miny
    if w <= 0 or h <= 0:
        return base

    # Fit the signature onto the visible side wall: cap its height to ~60% of the wall height
    # (base_h) and its width to ~80% of the base width, whichever is tighter, keeping the aspect
    # ratio. Then centre it on the wall (origin at y = 0).
    s = min((base_h * 0.6) / h, (radius * 2.0 * 0.8) / w)
    if s <= 0:
        return base
    geom = affinity.scale(geom, xfact=s, yfact=s, origin=(0, 0))
    minx, miny, maxx, maxy = geom.bounds
    geom = affinity.translate(geom, xoff=-(minx + maxx) / 2.0, yoff=-(miny + maxy) / 2.0)

    depth = max(base_h * float(depth_ratio), base_h * 0.1)
    parts = list(geom.geoms) if geom.geom_type == "MultiPolygon" else [geom]
    meshes = [trimesh.creation.extrude_polygon(p, height=depth) for p in parts if p.area > 0]
    if not meshes:
        return base
    text_mesh = trimesh.util.concatenate(meshes)

    # extrude_polygon already builds the glyphs upright in the XY plane (X = horizontal,
    # Y = vertical) extruded along +Z. That is exactly a front-facing plaque on the +Z side wall,
    # so no rotation or mirroring is needed — it reads correctly when viewed from the front (+Z).
    if raised:
        # Stand the text proud of the wall: z in [radius, radius + depth].
        text_mesh.apply_translation([0.0, 0.0, radius])
        return base.union(text_mesh, engine="manifold")
    # Recess the text into the wall from its outer surface: z in [radius - depth, radius].
    text_mesh.apply_translation([0.0, 0.0, radius - depth])
    return base.difference(text_mesh, engine="manifold")


def _apply_base_color_texture(scene, image_path):
    """Re-bind an external base-color map onto the scene's UV-mapped geometries.

    Meshy GLBs reference their base-color texture by CDN URL rather than embedding it, so a trimesh
    round-trip (add base / strip base) drops the map and the exported model renders untextured. By
    re-applying the downloaded base-color image onto each geometry's existing UVs we keep the figure
    textured through the mesh op. Geometries without UVs (e.g. our vertex-coloured base) are skipped.
    Best-effort: any failure leaves the geometry untouched. Returns True if a map was applied.
    """
    if not image_path:
        return False
    try:
        from PIL import Image
        img = Image.open(image_path).convert("RGBA")
    except Exception:  # noqa: BLE001 -- missing/unreadable texture just means "no embed"
        return False
    applied = False
    for geom in scene.geometry.values():
        vis = getattr(geom, "visual", None)
        uv = getattr(vis, "uv", None)
        if uv is None or len(uv) == 0:
            continue
        try:
            geom.visual = trimesh.visual.TextureVisuals(uv=np.asarray(uv), image=img)
            applied = True
        except Exception:  # noqa: BLE001
            pass
    return applied


def _geom_image(vis):
    """Return a geometry's real embedded base-color image, or None. Ignores the tiny placeholder
    (e.g. 2x2) trimesh fabricates for map-less PBR materials so we don't mistake it for a texture."""
    if vis is None:
        return None
    img = None
    mat = getattr(vis, "material", None)
    if mat is not None:
        img = getattr(mat, "baseColorTexture", None)
    if img is None:
        img = getattr(vis, "image", None)
    if img is not None and min(getattr(img, "size", (0, 0))) >= 8:
        return img
    return None


def _dominant_texture(scene):
    """The first real embedded base-color image in the scene (Meshy models use a single atlas), or
    None. Captured before processing so it can be re-bound if a later step (e.g. concatenate, which
    drops textures) strips the model's map and leaves it white."""
    for geom in scene.geometry.values():
        img = _geom_image(getattr(geom, "visual", None))
        if img is not None:
            return img
    return None


def _gltf_json(path):
    """Parse the glTF JSON out of a .glb (binary) or .gltf file. Returns the dict or None."""
    import struct
    try:
        with open(path, "rb") as fh:
            data = fh.read()
    except Exception:  # noqa: BLE001
        return None
    if data[:4] == b"glTF":  # binary GLB container: 12-byte header then length-prefixed chunks
        off = 12
        while off + 8 <= len(data):
            clen, ctype = struct.unpack("<II", data[off:off + 8])
            off += 8
            chunk = data[off:off + clen]
            off += clen
            if ctype == 0x4E4F534A:  # 'JSON'
                try:
                    return json.loads(chunk.decode("utf-8"))
                except Exception:  # noqa: BLE001
                    return None
        return None
    try:
        return json.loads(data.decode("utf-8"))
    except Exception:  # noqa: BLE001
        return None


def _texture_diag(path):
    """Diagnostic summary of where a model's colour lives. Logged by the backend so we can tell,
    from real Meshy output, why a figure renders white: embedded image, external URL, vertex colours,
    or nothing at all. Best-effort — never raises."""
    diag = {"embedded": [], "externalUris": [], "vertexColors": False, "geoms": 0}
    try:
        scene = trimesh.load(path)
        if isinstance(scene, trimesh.Trimesh):
            scene = trimesh.Scene(scene)
        diag["geoms"] = len(scene.geometry)
        for geom in scene.geometry.values():
            vis = getattr(geom, "visual", None)
            img = _geom_image(vis)
            if img is not None:
                diag["embedded"].append(list(img.size))
            vc = getattr(vis, "vertex_colors", None) if vis is not None else None
            if vc is not None and len(np.asarray(vc)) > 0:
                uniq = len(np.unique(np.asarray(vc).reshape(-1, np.asarray(vc).shape[-1]), axis=0))
                if uniq > 1:
                    diag["vertexColors"] = True
    except Exception:  # noqa: BLE001
        pass
    try:
        gltf = _gltf_json(path)
        for img in (gltf.get("images", []) if gltf else []) or []:
            u = img.get("uri", "") if isinstance(img, dict) else ""
            if isinstance(u, str) and u.startswith(("http://", "https://")):
                diag["externalUris"].append(u[:120])
    except Exception:  # noqa: BLE001
        pass
    return diag


def _gltf_json_bin(path):
    """Parse a .glb/.gltf into (json dict, binary-chunk bytes-or-None). The BIN chunk holds the
    geometry buffers for a binary GLB; data-URI buffers are decoded on demand in _read_accessor."""
    import struct
    try:
        with open(path, "rb") as fh:
            data = fh.read()
    except Exception:  # noqa: BLE001
        return None, None
    if data[:4] == b"glTF":
        off = 12
        js = None
        binc = None
        while off + 8 <= len(data):
            clen, ctype = struct.unpack("<II", data[off:off + 8])
            off += 8
            chunk = data[off:off + clen]
            off += clen
            if ctype == 0x4E4F534A:      # 'JSON'
                try:
                    js = json.loads(chunk.decode("utf-8"))
                except Exception:  # noqa: BLE001
                    return None, None
            elif ctype == 0x004E4942:    # 'BIN\0'
                binc = chunk
        return js, binc
    try:
        return json.loads(data.decode("utf-8")), None
    except Exception:  # noqa: BLE001
        return None, None


# glTF componentType -> (numpy dtype, byte size)
_GLTF_CT = {5126: ("<f4", 4), 5123: ("<u2", 2), 5121: ("<u1", 1), 5125: ("<u4", 4), 5122: ("<i2", 2)}
_GLTF_NCOMP = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}


def _read_accessor(js, binc, idx):
    """Read a glTF accessor (idx) into an (N, comps) float32 array. Handles the BIN chunk or a
    base64 data-URI buffer; normalises integer component types. Returns None on any problem."""
    import base64
    try:
        acc = js["accessors"][idx]
        bv = js["bufferViews"][acc["bufferView"]]
        buf = js["buffers"][bv["buffer"]]
        data = binc
        uri = buf.get("uri")
        if data is None and isinstance(uri, str) and uri.startswith("data:"):
            data = base64.b64decode(uri.split(",", 1)[1])
        if data is None:
            return None
        off = bv.get("byteOffset", 0) + acc.get("byteOffset", 0)
        comps = _GLTF_NCOMP[acc["type"]]
        dt, size = _GLTF_CT[acc["componentType"]]
        arr = np.frombuffer(data, dtype=np.dtype(dt), count=acc["count"] * comps,
                            offset=off).reshape(acc["count"], comps).astype(np.float32)
        if acc.get("normalized") and size in (1, 2):
            arr = arr / float(np.iinfo({1: np.uint8, 2: np.uint16}[size]).max)
        return arr
    except Exception:  # noqa: BLE001
        return None


def _gltf_primitive_uvs(path):
    """Per-primitive (vertexCount, TEXCOORD_0 array) for every primitive that carries UVs. Used to
    recover UVs that trimesh discards when a primitive has UVs but no material (Meshy figures)."""
    js, binc = _gltf_json_bin(path)
    if not js:
        return []
    out = []
    for mesh in js.get("meshes", []) or []:
        for prim in mesh.get("primitives", []) or []:
            attrs = prim.get("attributes", {}) if isinstance(prim, dict) else {}
            if "TEXCOORD_0" in attrs and "POSITION" in attrs:
                uv = _read_accessor(js, binc, attrs["TEXCOORD_0"])
                if uv is not None:
                    out.append((int(js["accessors"][attrs["POSITION"]]["count"]), uv))
    return out


def _attach_gltf_uvs(scene, src):
    """Meshy figure GLBs carry UVs but no material, so trimesh loads them as ColorVisuals and drops
    the UVs — leaving nowhere to bind the (out-of-band) base-color map. Recover TEXCOORD_0 from the
    raw glTF and re-attach it (matching primitives to geometries by vertex count) so the texture can
    be applied. Requires the scene be loaded with process=False so vertex order matches the accessor.
    Best-effort — never raises."""
    try:
        uvs = _gltf_primitive_uvs(src)
    except Exception:  # noqa: BLE001
        return
    if not uvs:
        return
    for geom in scene.geometry.values():
        vis = getattr(geom, "visual", None)
        existing = getattr(vis, "uv", None)
        if existing is not None and len(existing):
            continue  # already has UVs
        n = len(geom.vertices)
        match = next((arr for (cnt, arr) in uvs if cnt == n and len(arr) == n), None)
        if match is None:
            continue
        try:
            geom.visual = trimesh.visual.TextureVisuals(uv=np.asarray(match))
        except Exception:  # noqa: BLE001
            pass


def _external_texture_image(path):
    """When a model references its base-color map by URL (Meshy's GLBs do this and trimesh then
    drops it), pull the first http(s) image URI out of the glTF and download it so the texture can
    be re-embedded. Best-effort: any parse/network/decode failure returns None (model stays as-is).
    """
    gltf = _gltf_json(path)
    if not gltf:
        return None
    uri = None
    for img in gltf.get("images", []) or []:
        u = img.get("uri", "") if isinstance(img, dict) else ""
        if isinstance(u, str) and u.startswith(("http://", "https://")):
            uri = u
            break
    if not uri:
        return None
    try:
        import io
        import urllib.request
        from PIL import Image
        with urllib.request.urlopen(uri, timeout=30) as resp:  # noqa: S310 -- trusted Meshy CDN
            raw = resp.read()
        return Image.open(io.BytesIO(raw)).convert("RGBA")
    except Exception:  # noqa: BLE001
        return None


def _apply_missing_texture(scene, img):
    """Bind `img` onto UV-mapped geometries that have lost their map. Geometries that still carry a
    real texture, or have no UVs (e.g. a vertex-coloured base), are left untouched. Name-independent,
    so it works even after debase rebuilds the scene with renamed geometries."""
    if img is None:
        return
    for geom in scene.geometry.values():
        vis = getattr(geom, "visual", None)
        uv = getattr(vis, "uv", None)
        if uv is None or len(uv) == 0:
            continue
        if _geom_image(vis) is not None:
            continue
        try:
            geom.visual = trimesh.visual.TextureVisuals(uv=np.asarray(uv), image=img)
        except Exception:  # noqa: BLE001
            pass


def _export_with_texture(scene, out, texture_path, fallback_img=None):
    """Embed the base-color map then export to GLB. An external `texture_path` (Meshy's CDN map)
    wins; otherwise re-bind `fallback_img` — a texture captured before processing — onto any geometry
    that lost its map, so a trimesh round-trip never strips the model's colour."""
    applied = _apply_base_color_texture(scene, texture_path)
    if not applied:
        _apply_missing_texture(scene, fallback_img)
    scene.export(out)


def _strip_innerstyle_base(scene):
    """Remove any previously-baked base (geometry named 'innerstyle_base') from a scene.

    Lets the base be changed/removed in place: re-adding a base first strips the old one (so they
    never stack), and the 'strip' command removes it entirely. Returns the number removed.
    """
    names = [n for n in list(scene.geometry.keys()) if "innerstyle_base" in str(n).lower()]
    for n in names:
        try:
            scene.delete_geometry(n)
        except Exception:  # noqa: BLE001
            pass
    return len(names)


def add_base(src, out, shape, height_ratio, margin_ratio, color=None, signature_spec=None,
             texture_path=None):
    """Load the model as a scene (keeps textures), drop a base under it, and export GLB.

    `signature_spec` (optional dict) describes a signature to engrave on the side wall of the base:
      {"type": "strokes", "strokes": [[[x,y],...],...], "penWidth": 0.04, "depthRatio": 0.35,
       "raised": false}  -- or {"type": "text", "text": "...", ...}.
    `texture_path` (optional) is the model's base-color map (Meshy references it externally, so it
    must be re-embedded or the export loses its texture -- see _apply_base_color_texture).
    Returns True if a signature was applied (False if none requested or engraving failed -- the
    plain base is still produced so the operation never fails just because of the signature).
    """
    # process=False keeps the vertex order so recovered UVs line up with the glTF accessors.
    scene = trimesh.load(src, process=False)
    if isinstance(scene, trimesh.Trimesh):
        scene = trimesh.Scene(scene)
    # Recover UVs that trimesh drops for material-less primitives (Meshy figures), so the base-color
    # map has something to bind to.
    _attach_gltf_uvs(scene, src)
    # Replace, never stack: drop any base baked by a previous run before measuring/adding.
    _strip_innerstyle_base(scene)
    # Remember the source texture (embedded, else the map the GLB references by URL) so it survives
    # the round-trip, then re-bind the (external) base-color map on top when Java supplied one.
    captured = _dominant_texture(scene) or _external_texture_image(src)
    applied = _apply_base_color_texture(scene, texture_path)
    bounds = scene.bounds  # [[minx,miny,minz],[maxx,maxy,maxz]]
    if bounds is None:
        raise ValueError("empty scene")
    min_y = float(bounds[0][1])
    size_x = float(bounds[1][0] - bounds[0][0])
    size_z = float(bounds[1][2] - bounds[0][2])
    height = float(bounds[1][1] - bounds[0][1])
    cx = float((bounds[0][0] + bounds[1][0]) / 2.0)
    cz = float((bounds[0][2] + bounds[1][2]) / 2.0)
    base_h = max(height * float(height_ratio), max(height, 1.0) * 1e-3)
    radius = (max(size_x, size_z) / 2.0) * (1.0 + float(margin_ratio))
    if radius <= 0:
        radius = max(size_x, size_z, 1.0) / 2.0
    base = make_base(shape, radius, base_h, color)

    signature_applied = False
    if signature_spec:
        try:
            geom = _signature_polygon(signature_spec)
            if geom is not None and not geom.is_empty:
                base = engrave_signature(
                    base, geom, radius, base_h,
                    float(signature_spec.get("depthRatio", 0.35)),
                    bool(signature_spec.get("raised", False)))
                signature_applied = True
        except Exception:  # noqa: BLE001 -- never fail the base just because the signature did
            signature_applied = False

    # Sit the base directly under the model, centred on its footprint.
    base.apply_translation([cx, min_y - base_h / 2.0, cz])
    scene.add_geometry(base, geom_name="innerstyle_base")
    # Safety net: if no external map was applied, re-bind the captured source texture onto any
    # geometry that lost its map during the round-trip (the vertex-coloured base is left untouched).
    if not applied:
        _apply_missing_texture(scene, captured)
    scene.export(out)
    return signature_applied


def _looks_like_base(pmin, pmax, gmin, gmax):
    """Heuristic: is this component the flat pedestal a Meshy figure stands on?

    A base is (a) thin vertically, (b) sitting at the very bottom of the model, (c) entirely
    within the lower part of the model, and (d) broad relative to the whole footprint. The model
    up-axis is Y (matches add_base). Deliberately conservative so a real figure is never mistaken
    for a base — when in doubt we keep the part.
    """
    total_h = max(float(gmax[1] - gmin[1]), 1e-9)
    foot_all = max(float(gmax[0] - gmin[0]), float(gmax[2] - gmin[2]), 1e-9)
    vert = float(pmax[1] - pmin[1])
    foot = max(float(pmax[0] - pmin[0]), float(pmax[2] - pmin[2]))
    sits_at_bottom = (float(pmin[1]) - float(gmin[1])) <= 0.05 * total_h
    is_thin = vert <= 0.22 * total_h
    in_lower_band = (float(pmax[1]) - float(gmin[1])) <= 0.40 * total_h
    is_broad = foot >= 0.45 * foot_all
    return sits_at_bottom and is_thin and in_lower_band and is_broad


def _connected_face_components(mesh):
    """Group a mesh's faces into connected components using face adjacency + union-find.

    Avoids trimesh's mesh.split(), which needs a graph engine (scipy/networkx) we don't ship.
    Returns a list of face-index lists (one per component).
    """
    n = len(mesh.faces)
    parent = list(range(n))

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    for a, b in mesh.face_adjacency:
        ra, rb = find(int(a)), find(int(b))
        if ra != rb:
            parent[ra] = rb

    groups = {}
    for i in range(n):
        groups.setdefault(find(i), []).append(i)
    return list(groups.values())


def _section_radius(mesh, y, cx, cz):
    """Radial half-width of the model's horizontal cross-section at height y (95th pct of the
    section outline's distance from the central axis). Density-independent — works even when the
    mesh has few vertices between rings. Returns None on failure, 0.0 for an empty slice."""
    try:
        from trimesh.intersections import mesh_plane
        segs = mesh_plane(mesh, plane_normal=[0.0, 1.0, 0.0], plane_origin=[0.0, float(y), 0.0])
    except Exception:  # noqa: BLE001
        return None
    if segs is None or len(segs) == 0:
        return 0.0
    pts = np.asarray(segs).reshape(-1, 3)
    r = np.sqrt((pts[:, 0] - cx) ** 2 + (pts[:, 2] - cz) ** 2)
    return float(np.percentile(r, 95)) if len(r) else 0.0


def _detect_base_cut_y(mesh):
    """Find the Y height at which a fused pedestal ends, or None if there's no clear base.

    The model up-axis is Y. A base is a flat disc at the very bottom: wide (>=60% of the model's
    widest cross-section) and ending with a sharp width drop within the bottom 25% of the height.
    Conservative — returns None whenever the bottom isn't an obvious disc, so a real figure (or an
    already base-less model) is never cut.
    """
    v = mesh.vertices
    if v is None or len(v) == 0:
        return None
    ymin, ymax = float(v[:, 1].min()), float(v[:, 1].max())
    total_h = ymax - ymin
    if total_h <= 1e-9:
        return None
    cx = 0.5 * (float(v[:, 0].min()) + float(v[:, 0].max()))
    cz = 0.5 * (float(v[:, 2].min()) + float(v[:, 2].max()))
    r_all = np.sqrt((v[:, 0] - cx) ** 2 + (v[:, 2] - cz) ** 2)
    overall_r = float(np.percentile(r_all, 95))
    if overall_r <= 1e-9:
        return None

    bands = 40
    ys = np.linspace(ymin + 0.01 * total_h, ymin + 0.35 * total_h, bands)
    radii = []
    for y in ys:
        rad = _section_radius(mesh, y, cx, cz)
        if rad is None:
            return None  # sectioning unavailable — don't risk a blind cut
        radii.append(rad)
    radii = np.array(radii)

    base_r = float(np.percentile(radii[:3], 95))  # width right above the floor
    if base_r < 0.6 * overall_r:
        return None  # bottom isn't notably wide → no disc base

    cut_y = None
    for i in range(bands):
        if radii[i] < 0.6 * base_r:
            cut_y = float(ys[i])
            break
    if cut_y is None:
        return None
    if (cut_y - ymin) > 0.25 * total_h:
        return None  # the "base" would be too tall to be a base
    return cut_y


# Peak-memory guard for the fused-base passes (submesh/concatenate roughly double memory). Above
# this face count the heavy passes are skipped so a constrained container isn't OOM-killed. ~500k
# tris keeps typical Meshy figures well inside the fast path; tune down if the container has little
# RAM, up if it has plenty.
MAX_DEBASE_FACES = 500_000


def strip_generated_base(src, out, texture_path=None):
    """Remove the pedestal Meshy bakes under a figure, keeping the figure + its textures.

    Two passes: (1) if the base is its own named geometry in the scene, delete it (preserves the
    other geometries' materials/UVs); (2) otherwise, if the model is a single fused mesh, split it
    into connected components and drop the base-looking ones. If nothing clearly matches a base,
    the model is exported unchanged — we never risk mangling the figure. Returns the count removed.

    `texture_path` (optional) is the model's base-color map; Meshy references it externally, so it
    is re-embedded on export or the stored model would lose its texture (see
    _apply_base_color_texture).
    """
    # process=False keeps the vertex order so recovered UVs line up with the glTF accessors.
    scene = trimesh.load(src, process=False)
    if isinstance(scene, trimesh.Trimesh):
        scene = trimesh.Scene(scene)
    # Recover UVs trimesh drops for material-less primitives (Meshy figures carry UVs but no material
    # — the colour comes from a separate base-color map), so the map has UVs to bind to.
    _attach_gltf_uvs(scene, src)
    _strip_innerstyle_base(scene)
    # Capture the model's texture up-front: passes 2/3 may concatenate/rebuild the mesh, which drops
    # the embedded map and would leave the figure white. The capture lets us re-bind it on export.
    # If nothing is embedded, the GLB likely references the map by URL (Meshy) — fetch + embed that.
    dom = _dominant_texture(scene) or _external_texture_image(src)
    if not scene.geometry:
        _export_with_texture(scene, out, texture_path, dom)
        return 0

    gmin, gmax = scene.bounds[0], scene.bounds[1]

    # Pass 1: whole named geometries that look like a base.
    removed = 0
    for name in list(scene.geometry.keys()):
        geom = scene.geometry[name]
        gb = getattr(geom, "bounds", None)
        if gb is None:
            continue
        if _looks_like_base(gb[0], gb[1], gmin, gmax):
            try:
                scene.delete_geometry(name)
                removed += 1
            except Exception:  # noqa: BLE001
                pass
    if removed:
        _export_with_texture(scene, out, texture_path, dom)
        return removed

    # Passes 2 & 3 rebuild/duplicate the mesh (submesh + concatenate), which roughly doubles peak
    # memory. On a very high-poly figure that can exceed a constrained container's RAM and get the
    # process OOM-killed (exit 137). Above a safe face budget, skip these passes and keep Meshy's
    # base — the same end result as an OOM would leave, but clean instead of a hard kill.
    total_faces = sum(len(g.faces) for g in scene.geometry.values()
                      if getattr(g, "faces", None) is not None)
    if total_faces > MAX_DEBASE_FACES:
        _export_with_texture(scene, out, texture_path, dom)
        return 0

    # Pass 2: base fused into a single mesh — split by connectivity (no graph engine needed) and
    # drop base-looking components.
    names = list(scene.geometry.keys())
    if len(names) == 1:
        mesh = scene.geometry[names[0]]
        try:
            groups = _connected_face_components(mesh)
            parts = mesh.submesh(groups, append=False) if len(groups) >= 2 else []
        except Exception:  # noqa: BLE001
            parts = []
        if len(parts) >= 2:
            keep = [p for p in parts if not _looks_like_base(p.bounds[0], p.bounds[1], gmin, gmax)]
            dropped = len(parts) - len(keep)
            if dropped > 0 and keep:
                rebuilt = trimesh.Scene()
                for i, part in enumerate(keep):
                    rebuilt.add_geometry(part, geom_name=f"part_{i}")
                _export_with_texture(rebuilt, out, texture_path, dom)
                return dropped

    # Pass 3: base fused into the SAME connected mesh as the figure (Meshy often does this, so the
    # component split above can't separate it). Detect the flat disc by cross-section width and cut
    # it off with a horizontal plane, keeping the figure's original faces/UVs above the cut.
    geoms = list(scene.geometry.values())
    big = geoms[0] if len(geoms) == 1 else trimesh.util.concatenate(tuple(geoms))
    if big is not None and len(big.faces) > 0:
        cut_y = _detect_base_cut_y(big)
        if cut_y is not None:
            face_y = big.vertices[big.faces].mean(axis=1)[:, 1]
            keep = np.nonzero(face_y >= cut_y)[0]
            if 0 < len(keep) < len(big.faces):
                kept = big.submesh([keep], append=True)
                rebuilt = trimesh.Scene()
                rebuilt.add_geometry(kept, geom_name="figure")
                _export_with_texture(rebuilt, out, texture_path, dom)
                return 1

    # Nothing matched a base — leave the model exactly as it was (still re-embed its texture).
    _export_with_texture(scene, out, texture_path, dom)
    return 0


def main():
    if len(sys.argv) < 3:
        print(json.dumps({"error": "usage: mesh_tools.py <analyze|repair|base|strip|debase> <input> [output]"}))
        sys.exit(2)

    cmd, src = sys.argv[1], sys.argv[2]

    if cmd == "base":
        if len(sys.argv) < 4:
            print(json.dumps({"error": "base requires output and options"}))
            sys.exit(2)
        out = sys.argv[3]
        shape = sys.argv[4] if len(sys.argv) > 4 else "cylinder"
        height_ratio = float(sys.argv[5]) if len(sys.argv) > 5 else 0.06
        margin_ratio = float(sys.argv[6]) if len(sys.argv) > 6 else 0.1
        color = sys.argv[7] if len(sys.argv) > 7 else None
        # Optional arg 8: path to a JSON signature spec (too big/structured for a CLI arg).
        signature_spec = None
        spec_path = sys.argv[8] if len(sys.argv) > 8 else None
        if spec_path:
            try:
                with open(spec_path, "r", encoding="utf-8") as fh:
                    signature_spec = json.load(fh)
            except Exception:  # noqa: BLE001 -- a bad spec just means "no signature"
                signature_spec = None
        # Optional arg 9: path to the base-color texture image to re-embed.
        texture_path = sys.argv[9] if len(sys.argv) > 9 and sys.argv[9] else None
        try:
            applied = add_base(src, out, shape, height_ratio, margin_ratio, color, signature_spec,
                               texture_path)
        except Exception as exc:  # noqa: BLE001
            print(json.dumps({"error": "base_failed", "detail": str(exc)}))
            sys.exit(1)
        print(json.dumps({"ok": True, "signature": bool(applied)}))
        return

    if cmd == "strip":
        if len(sys.argv) < 4:
            print(json.dumps({"error": "strip requires <output>"}))
            sys.exit(2)
        out = sys.argv[3]
        texture_path = sys.argv[4] if len(sys.argv) > 4 and sys.argv[4] else None
        try:
            scene = trimesh.load(src)
            if isinstance(scene, trimesh.Trimesh):
                scene = trimesh.Scene(scene)
            removed = _strip_innerstyle_base(scene)
            _export_with_texture(scene, out, texture_path)
        except Exception as exc:  # noqa: BLE001
            print(json.dumps({"error": "strip_failed", "detail": str(exc)}))
            sys.exit(1)
        print(json.dumps({"ok": True, "removed": removed}))
        return

    if cmd == "debase":
        if len(sys.argv) < 4:
            print(json.dumps({"error": "debase requires <output>"}))
            sys.exit(2)
        out = sys.argv[3]
        texture_path = sys.argv[4] if len(sys.argv) > 4 and sys.argv[4] else None
        src_diag = _texture_diag(src)
        try:
            removed = strip_generated_base(src, out, texture_path)
        except Exception as exc:  # noqa: BLE001
            print(json.dumps({"error": "debase_failed", "detail": str(exc)}))
            sys.exit(1)
        # Diagnostics (logged by the backend): where the colour was in Meshy's GLB and whether it
        # survived into the stored model — so a "white figure" report can be pinned to a real cause.
        print(json.dumps({"ok": True, "removed": removed,
                          "srcTex": src_diag, "outTex": _texture_diag(out)}))
        return

    if cmd == "repair":
        if len(sys.argv) < 4:
            print(json.dumps({"error": "repair requires <output>"}))
            sys.exit(2)
        out = sys.argv[3]
        # One parse of the source (as a textured scene) serves both the mesh pymeshfix repairs and
        # the texture repair re-attaches afterwards -- loading it a second time here would decode
        # the base-color image twice, needlessly doubling peak memory on top of pymeshfix's own
        # (already sizeable) footprint.
        try:
            scene, dom = _load_scene_with_texture(src)
            src_v, src_uv = _combined_vertices_uv(scene)
            if src_uv is not None:
                src_v, src_uv = _dedupe_points(src_v, src_uv)
            mesh = _mesh_from_scene(scene)
        except Exception as exc:  # noqa: BLE001
            print(json.dumps({"error": "load_failed", "detail": str(exc)}))
            sys.exit(1)
        before = analyze(mesh)
        try:
            fixer = pymeshfix.MeshFix(mesh.vertices.astype(np.float64), mesh.faces)
            fixer.repair()
            fixed = trimesh.Trimesh(vertices=fixer.points, faces=fixer.faces, process=False)
        except Exception as exc:  # noqa: BLE001
            print(json.dumps({"error": "repair_failed", "detail": str(exc)}))
            sys.exit(1)
        # Drop pymeshfix's own workspace and the pre-repair mesh before the texture pass -- neither
        # is needed past this point, and freeing them first keeps the nearest-neighbour transfer's
        # peak memory from stacking on top of pymeshfix's.
        del fixer, mesh
        gc.collect()

        # pymeshfix rebuilds vertices/faces from scratch and carries no colour with them — without
        # this, every repaired model exports as a flat grey/white lump regardless of how good the
        # geometry fix was. Re-attach the source texture via nearest-neighbour UV transfer.
        # Best-effort: any failure here just exports untextured, same as before this fix, rather
        # than failing the whole repair over a colour problem.
        try:
            if dom is not None and src_uv is not None and len(src_v) > 0 \
                    and len(fixed.vertices) <= MAX_REPAIR_TEXTURE_VERTS \
                    and len(src_v) <= MAX_REPAIR_TEXTURE_VERTS:
                uv = _nearest_uv(src_v, src_uv, fixed.vertices)
                fixed.visual = trimesh.visual.TextureVisuals(uv=uv, image=dom)
        except Exception:  # noqa: BLE001
            pass
        del scene, src_v, src_uv
        gc.collect()

        fixed.merge_vertices()
        fixed.export(out)
        print(json.dumps({"before": before, "after": analyze(fixed)}))
        return

    try:
        mesh = load(src)
    except Exception as exc:  # noqa: BLE001
        print(json.dumps({"error": "load_failed", "detail": str(exc)}))
        sys.exit(1)

    if cmd == "analyze":
        print(json.dumps(analyze(mesh)))
        return

    print(json.dumps({"error": "unknown command: " + cmd}))
    sys.exit(2)


if __name__ == "__main__":
    main()
