# The three cutaways of Imaginary Technique: Purple (the Toji fight), built from nothing in Blender and rendered with
# Cycles: a golden sky bursting with light, leaves holding drops of water in that light, and two drops - one turning
# blue (lapse) and one red (reversal) - running together into purple. Played full screen in the cutscene at 30 fps.
#
#   python toji_inserts.py <job> <jobs> <outdir> [--preview]
#
# Frames are written as <outdir>/<shot>_f%04d.png, every job taking every <jobs>-th frame of the three shots.
import bpy, math, random, sys, os
from mathutils import Vector, Euler

argv = sys.argv[1:]
JOB, JOBS, OUT = int(argv[0]), int(argv[1]), argv[2]
PREVIEW = '--preview' in argv
ONLY = None
for a in argv:
    if a.startswith('--only='):
        ONLY = a[7:].split(',')
W, H = (480, 270) if PREVIEW else (960, 540)
FPS = 30
SHOTS = {'sky': 96, 'leaves': 150, 'drops': 147}
# the drops shot, frame by frame (30 fps from tick 592: frame = (tick - 592) * 1.5)
BLUE_ON, RED_ON, SLIDE, TOUCH = 15, 34, 82, 140

GOLD = (1.0, 0.72, 0.28)
DEEP = (0.9, 0.42, 0.08)


def ease(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'
    sc.cycles.device = 'CPU'
    sc.cycles.samples = 10 if PREVIEW else 28
    sc.cycles.use_adaptive_sampling = True
    sc.cycles.use_denoising = True
    sc.cycles.denoiser = 'OPENIMAGEDENOISE'
    sc.cycles.max_bounces = 8
    sc.cycles.glossy_bounces = 4
    sc.cycles.transmission_bounces = 8
    sc.cycles.transparent_max_bounces = 16
    sc.render.resolution_x, sc.render.resolution_y, sc.render.resolution_percentage = W, H, 100
    sc.render.fps = FPS
    sc.render.film_transparent = False
    sc.render.image_settings.file_format = 'PNG'
    sc.render.image_settings.color_mode = 'RGB'
    sc.view_settings.view_transform = 'Standard'
    sc.view_settings.look = 'None'
    sc.view_settings.exposure = -0.2
    return sc


def node_mat(name):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new('ShaderNodeOutputMaterial')
    return m, nt, out


def emission_mat(name, color, strength, alpha_gradient=False):
    """Light that adds (emission over transparent); with alpha_gradient it fades out along the object's local +Y."""
    m, nt, out = node_mat(name)
    em = nt.nodes.new('ShaderNodeEmission')
    em.inputs['Color'].default_value = (*color, 1)
    em.inputs['Strength'].default_value = strength
    tr = nt.nodes.new('ShaderNodeBsdfTransparent')
    add = nt.nodes.new('ShaderNodeAddShader')
    nt.links.new(em.outputs[0], add.inputs[0])
    nt.links.new(tr.outputs[0], add.inputs[1])
    if alpha_gradient:
        # strength falls off along the ray (generated Y) and across it (generated X)
        tc = nt.nodes.new('ShaderNodeTexCoord')
        sep = nt.nodes.new('ShaderNodeSeparateXYZ')
        nt.links.new(tc.outputs['Generated'], sep.inputs[0])
        along = nt.nodes.new('ShaderNodeMath'); along.operation = 'SUBTRACT'; along.inputs[0].default_value = 1.0
        nt.links.new(sep.outputs['Y'], along.inputs[1])
        pw = nt.nodes.new('ShaderNodeMath'); pw.operation = 'POWER'; pw.inputs[1].default_value = 1.6
        nt.links.new(along.outputs[0], pw.inputs[0])
        cx = nt.nodes.new('ShaderNodeMath'); cx.operation = 'SUBTRACT'; cx.inputs[1].default_value = 0.5
        nt.links.new(sep.outputs['X'], cx.inputs[0])
        ab = nt.nodes.new('ShaderNodeMath'); ab.operation = 'ABSOLUTE'
        nt.links.new(cx.outputs[0], ab.inputs[0])
        side = nt.nodes.new('ShaderNodeMath'); side.operation = 'MULTIPLY_ADD'
        side.inputs[1].default_value = -2.0; side.inputs[2].default_value = 1.0
        nt.links.new(ab.outputs[0], side.inputs[0])
        sq = nt.nodes.new('ShaderNodeMath'); sq.operation = 'POWER'; sq.inputs[1].default_value = 2.0
        nt.links.new(side.outputs[0], sq.inputs[0])
        mul = nt.nodes.new('ShaderNodeMath'); mul.operation = 'MULTIPLY'
        nt.links.new(pw.outputs[0], mul.inputs[0])
        nt.links.new(sq.outputs[0], mul.inputs[1])
        mul2 = nt.nodes.new('ShaderNodeMath'); mul2.operation = 'MULTIPLY'; mul2.inputs[1].default_value = strength
        nt.links.new(mul.outputs[0], mul2.inputs[0])
        nt.links.new(mul2.outputs[0], em.inputs['Strength'])
    nt.links.new(add.outputs[0], out.inputs['Surface'])
    return m, em


def world_gold(top, horizon, strength=1.0, sun_dir=Vector((0, 1, 0.25)), sun_color=(1, 0.86, 0.55), sun_power=6.0, sun_tight=0.985):
    """A sky that is all light: a gradient from horizon to top, and a hot core where the sun sits."""
    w = bpy.data.worlds.new('gold')
    bpy.context.scene.world = w
    w.use_nodes = True
    nt = w.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new('ShaderNodeOutputWorld')
    tc = nt.nodes.new('ShaderNodeTexCoord')
    norm = nt.nodes.new('ShaderNodeVectorMath'); norm.operation = 'NORMALIZE'
    nt.links.new(tc.outputs['Generated'], norm.inputs[0])
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    nt.links.new(norm.outputs[0], sep.inputs[0])
    ramp = nt.nodes.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].position = 0.0
    ramp.color_ramp.elements[0].color = (*horizon, 1)
    ramp.color_ramp.elements[1].position = 0.7
    ramp.color_ramp.elements[1].color = (*top, 1)
    nt.links.new(sep.outputs['Z'], ramp.inputs[0])
    dot = nt.nodes.new('ShaderNodeVectorMath'); dot.operation = 'DOT_PRODUCT'
    nt.links.new(norm.outputs[0], dot.inputs[0])
    dot.inputs[1].default_value = tuple(sun_dir.normalized())
    mr = nt.nodes.new('ShaderNodeMapRange')
    mr.inputs['From Min'].default_value = sun_tight
    mr.inputs['From Max'].default_value = 1.0
    nt.links.new(dot.outputs['Value'], mr.inputs['Value'])
    pw = nt.nodes.new('ShaderNodeMath'); pw.operation = 'POWER'; pw.inputs[1].default_value = 2.0
    nt.links.new(mr.outputs[0], pw.inputs[0])
    sunk = nt.nodes.new('ShaderNodeMath'); sunk.operation = 'MULTIPLY'; sunk.inputs[1].default_value = sun_power
    nt.links.new(pw.outputs[0], sunk.inputs[0])
    mix = nt.nodes.new('ShaderNodeMix'); mix.data_type = 'RGBA'; mix.blend_type = 'ADD'; mix.clamp_result = False
    mix.inputs['Factor'].default_value = 1.0
    nt.links.new(ramp.outputs['Color'], mix.inputs[6])
    sc_ = nt.nodes.new('ShaderNodeMix'); sc_.data_type = 'RGBA'; sc_.blend_type = 'MULTIPLY'; sc_.clamp_result = False
    sc_.inputs['Factor'].default_value = 1.0
    sc_.inputs[6].default_value = (*sun_color, 1)
    comb = nt.nodes.new('ShaderNodeCombineXYZ')
    for k in ('X', 'Y', 'Z'):
        nt.links.new(sunk.outputs[0], comb.inputs[k])
    nt.links.new(comb.outputs[0], sc_.inputs[7])
    nt.links.new(sc_.outputs[2], mix.inputs[7])
    bg = nt.nodes.new('ShaderNodeBackground')
    bg.inputs['Strength'].default_value = strength
    nt.links.new(mix.outputs[2], bg.inputs['Color'])
    nt.links.new(bg.outputs[0], out.inputs['Surface'])
    return w


def camera(loc, look, lens=35, fstop=None, focus=None):
    cd = bpy.data.cameras.new('cam')
    cd.lens = lens
    cd.clip_start = 0.01
    cd.clip_end = 4000
    if fstop:
        cd.dof.use_dof = True
        cd.dof.aperture_fstop = fstop
        cd.dof.focus_distance = focus
    cam = bpy.data.objects.new('cam', cd)
    bpy.context.scene.collection.objects.link(cam)
    bpy.context.scene.camera = cam
    aim(cam, loc, look)
    return cam


def aim(cam, loc, look):
    cam.location = Vector(loc)
    d = Vector(look) - Vector(loc)
    cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()


def ico(name, loc, r, sub=3, mat=None):
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=sub, radius=r, location=loc)
    o = bpy.context.active_object
    o.name = name
    if mat:
        o.data.materials.append(mat)
    bpy.ops.object.shade_smooth()
    return o


def uv_sphere(name, loc, r, mat=None, seg=48, rings=24):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=seg, ring_count=rings, radius=r, location=loc)
    o = bpy.context.active_object
    o.name = name
    if mat:
        o.data.materials.append(mat)
    bpy.ops.object.shade_smooth()
    return o


# ------------------------------------------------------------------------------------------------- the golden sky

def cloud_mat():
    """Cloud mass seen against the sun: dark umber-gold in its body, its edges burning bright where the light grazes."""
    m, nt, out = node_mat('cloud')
    lw = nt.nodes.new('ShaderNodeLayerWeight'); lw.inputs['Blend'].default_value = 0.35
    ramp = nt.nodes.new('ShaderNodeValToRGB')
    e = ramp.color_ramp.elements
    e[0].position = 0.0; e[0].color = (0.10, 0.035, 0.008, 1)
    e[1].position = 0.6; e[1].color = (0.26, 0.10, 0.02, 1)
    el = e.new(0.84); el.color = (0.85, 0.42, 0.08, 1)
    el2 = e.new(0.96); el2.color = (1.0, 0.85, 0.45, 1)
    nt.links.new(lw.outputs['Facing'], ramp.inputs[0])
    noise = nt.nodes.new('ShaderNodeTexNoise'); noise.inputs['Scale'].default_value = 3.0; noise.inputs['Detail'].default_value = 8
    mixn = nt.nodes.new('ShaderNodeMix'); mixn.data_type = 'RGBA'; mixn.blend_type = 'MULTIPLY'; mixn.inputs['Factor'].default_value = 0.35
    nt.links.new(ramp.outputs['Color'], mixn.inputs[6])
    nt.links.new(noise.outputs['Color'], mixn.inputs[7])
    em = nt.nodes.new('ShaderNodeEmission'); em.inputs['Strength'].default_value = 1.0
    nt.links.new(mixn.outputs[2], em.inputs['Color'])
    nt.links.new(em.outputs[0], out.inputs['Surface'])
    return m


def cloud(name, loc, size, mat, rnd):
    """A heap of displaced spheres."""
    parts = []
    for i in range(rnd.randint(8, 14)):
        off = Vector((rnd.uniform(-1.4, 1.4) * size, rnd.uniform(-0.4, 0.4) * size, rnd.uniform(-0.25, 0.3) * size))
        o = ico(f'{name}_{i}', Vector(loc) + off, size * rnd.uniform(0.3, 0.6), 4, mat)
        o.scale = (1.3, 0.8, 0.55)
        tex = bpy.data.textures.new(f'{name}_t{i}', 'CLOUDS')
        tex.noise_scale = size * 0.6
        tex.noise_depth = 2
        d = o.modifiers.new('d', 'DISPLACE'); d.texture = tex; d.strength = size * 0.18; d.mid_level = 0.5
        parts.append(o)
    return parts


def build_sky():
    sc = reset()
    sc.view_settings.exposure = -0.6
    sun = Vector((0, 260, 70))
    world_gold(top=(0.85, 0.36, 0.04), horizon=(1.0, 0.7, 0.2), strength=1.0, sun_dir=sun, sun_power=40.0, sun_tight=0.95)
    rnd = random.Random(7)
    clouds = []
    # a deck of real cloud (a volume shaped by noise) between the lens and the sun, lit from behind so its body is dark
    # and its edges burn; a hole opens over the sun as the shot goes on
    vm, vnt, vout = node_mat('cloudvol')
    tc = vnt.nodes.new('ShaderNodeTexCoord')
    mp = vnt.nodes.new('ShaderNodeMapping')
    vnt.links.new(tc.outputs['Object'], mp.inputs['Vector'])
    mp.inputs['Scale'].default_value = (1.0, 2.2, 3.0)
    nz = vnt.nodes.new('ShaderNodeTexNoise')
    nz.inputs['Scale'].default_value = 3.2
    nz.inputs['Detail'].default_value = 7.0
    nz.inputs['Roughness'].default_value = 0.58
    vnt.links.new(mp.outputs['Vector'], nz.inputs['Vector'])
    # the hole: distance from the deck's centre, in its own space, pushes the density down
    ln = vnt.nodes.new('ShaderNodeVectorMath'); ln.operation = 'LENGTH'
    sq = vnt.nodes.new('ShaderNodeVectorMath'); sq.operation = 'MULTIPLY'; sq.inputs[1].default_value = (1.0, 0.0, 1.6)
    vnt.links.new(tc.outputs['Object'], sq.inputs[0]); vnt.links.new(sq.outputs[0], ln.inputs[0])
    hole = vnt.nodes.new('ShaderNodeMapRange')
    hole.inputs['From Min'].default_value = 0.0
    hole.inputs['To Min'].default_value = -0.35
    hole.inputs['To Max'].default_value = 0.0
    vnt.links.new(ln.outputs['Value'], hole.inputs['Value'])
    hole.name = 'hole'
    hole.inputs['From Max'].default_value = 0.35
    add = vnt.nodes.new('ShaderNodeMath'); add.operation = 'ADD'
    vnt.links.new(nz.outputs['Fac'], add.inputs[0]); vnt.links.new(hole.outputs['Result'], add.inputs[1])
    dens = vnt.nodes.new('ShaderNodeMapRange')
    dens.inputs['From Min'].default_value = 0.5
    dens.inputs['From Max'].default_value = 0.6
    dens.inputs['To Min'].default_value = 0.0
    dens.inputs['To Max'].default_value = 1.6
    vnt.links.new(add.outputs[0], dens.inputs['Value'])
    pv = vnt.nodes.new('ShaderNodeVolumePrincipled')
    pv.inputs['Color'].default_value = (1.0, 0.62, 0.25, 1)
    pv.inputs['Anisotropy'].default_value = 0.55
    vnt.links.new(dens.outputs['Result'], pv.inputs['Density'])
    vnt.links.new(pv.outputs[0], vout.inputs['Volume'])
    bpy.ops.mesh.primitive_cube_add(size=1, location=(0, 200, 70))
    deck = bpy.context.active_object
    deck.scale = (520, 40, 260)
    deck.data.materials.append(vm)
    clouds.append(([deck], Vector((0, 0, 0))))
    sunl = bpy.data.lights.new('sunback', 'SUN')
    sunl.energy = 14.0
    sunl.color = (1.0, 0.8, 0.5)
    sunl.angle = math.radians(1.5)
    so = bpy.data.objects.new('sunback', sunl)
    bpy.context.scene.collection.objects.link(so)
    so.rotation_euler = (Vector((0, 0, 0)) - sun).to_track_quat('-Z', 'Y').to_euler()
    sc.cycles.volume_step_rate = 4.0
    sc.cycles.volume_max_steps = 256
    sc.cycles.volume_bounces = 1
    # the light breaking out round the edges: rays fanning from the sun
    rays = []
    rm, _ = emission_mat('ray', (1.0, 0.78, 0.35), 1.6, alpha_gradient=True)
    for i in range(30):
        bpy.ops.mesh.primitive_plane_add(size=1)
        p = bpy.context.active_object
        p.data.materials.append(rm)
        # plane from y=-0.5..0.5; generated Y runs 0 at the sun end to 1 at the tip
        for v in p.data.vertices:
            v.co.y += 0.5
            v.co.x *= (0.1 if v.co.y < 0.5 else 1.0)
        a = i / 30 * math.tau + rnd.uniform(-0.08, 0.08)
        length = rnd.uniform(160, 300)
        width = rnd.uniform(6, 22)
        p.location = sun + Vector((0, -30, 0))
        p.rotation_euler = Euler((math.radians(90), a, 0), 'XYZ')
        p.scale = (width, length, 1)
        rays.append((p, a, length, width, rnd.uniform(0, 6)))
    # the hot core
    core_m, core_em = emission_mat('core', (1.0, 0.92, 0.7), 3.0)
    core = uv_sphere('core', sun, 8, core_m)
    cam = camera((0, -10, 8), (0, 200, 62), lens=26)
    for f in range(SHOTS['sky']):
        pass
    return sc, cam, clouds, rays, core, core_em, sun


def frame_sky(state, f):
    sc, cam, clouds, rays, core, core_em, sun = state
    u = f / (SHOTS['sky'] - 1)
    k = ease(u)
    # the lens rises into it; the clouds open; the light swells
    aim(cam, (0, -10 + 40 * k, 8 + 10 * k), (math.sin(u * 2) * 4, 200, 62 + 6 * k))
    cam.data.lens = 26 - 4 * k
    for parts, out in clouds:
        for o in parts:
            m = o.data.materials[0]
            h = m.node_tree.nodes.get('hole')
            if h:
                h.inputs['From Max'].default_value = 0.08 + 0.3 * k
            o.location = Vector((u * 8, 200, 70))
    for p, a, length, width, ph in rays:
        p.rotation_euler = Euler((math.radians(90), a + u * 0.12, 0), 'XYZ')
        p.scale = (width * (0.6 + 0.6 * k) * (1 + 0.15 * math.sin(f * 0.2 + ph)), length * (0.55 + 0.6 * k), 1)
    core_em.inputs['Strength'].default_value = 3 + 6 * k


# ------------------------------------------------------------------------------------------------ leaves and drops

def leaf_mat(name, tint=(0.07, 0.32, 0.02)):
    m, nt, out = node_mat(name)
    tc = nt.nodes.new('ShaderNodeTexCoord')
    sep = nt.nodes.new('ShaderNodeSeparateXYZ'); nt.links.new(tc.outputs['UV'], sep.inputs[0])
    # the midrib and veins: lighter lines along and slanting off the centre
    cx = nt.nodes.new('ShaderNodeMath'); cx.operation = 'SUBTRACT'; cx.inputs[1].default_value = 0.5
    nt.links.new(sep.outputs['X'], cx.inputs[0])
    ab = nt.nodes.new('ShaderNodeMath'); ab.operation = 'ABSOLUTE'; nt.links.new(cx.outputs[0], ab.inputs[0])
    rib = nt.nodes.new('ShaderNodeMath'); rib.operation = 'LESS_THAN'; rib.inputs[1].default_value = 0.012
    nt.links.new(ab.outputs[0], rib.inputs[0])
    vein_in = nt.nodes.new('ShaderNodeMath'); vein_in.operation = 'MULTIPLY_ADD'; vein_in.inputs[1].default_value = 0.6
    nt.links.new(ab.outputs[0], vein_in.inputs[0]); nt.links.new(sep.outputs['Y'], vein_in.inputs[2])
    vein_s = nt.nodes.new('ShaderNodeMath'); vein_s.operation = 'MULTIPLY'; vein_s.inputs[1].default_value = 14.0
    nt.links.new(vein_in.outputs[0], vein_s.inputs[0])
    vein_f = nt.nodes.new('ShaderNodeMath'); vein_f.operation = 'FRACT'; nt.links.new(vein_s.outputs[0], vein_f.inputs[0])
    vein = nt.nodes.new('ShaderNodeMath'); vein.operation = 'LESS_THAN'; vein.inputs[1].default_value = 0.06
    nt.links.new(vein_f.outputs[0], vein.inputs[0])
    lines = nt.nodes.new('ShaderNodeMath'); lines.operation = 'MAXIMUM'
    nt.links.new(rib.outputs[0], lines.inputs[0]); nt.links.new(vein.outputs[0], lines.inputs[1])
    noise = nt.nodes.new('ShaderNodeTexNoise'); noise.inputs['Scale'].default_value = 9
    base = nt.nodes.new('ShaderNodeMix'); base.data_type = 'RGBA'
    base.inputs[6].default_value = (*tint, 1)
    base.inputs[7].default_value = (tint[0] * 1.6 + 0.08, tint[1] * 1.25 + 0.1, tint[2] * 1.2, 1)
    nt.links.new(noise.outputs['Fac'], base.inputs['Factor'])
    col = nt.nodes.new('ShaderNodeMix'); col.data_type = 'RGBA'
    col.inputs[7].default_value = (0.3, 0.55, 0.1, 1)
    nt.links.new(base.outputs[2], col.inputs[6]); nt.links.new(lines.outputs[0], col.inputs['Factor'])
    bsdf = nt.nodes.new('ShaderNodeBsdfPrincipled')
    bsdf.inputs['Roughness'].default_value = 0.32
    nt.links.new(col.outputs[2], bsdf.inputs['Base Color'])
    # sunlight through the leaf
    tr = nt.nodes.new('ShaderNodeBsdfTranslucent')
    nt.links.new(col.outputs[2], tr.inputs['Color'])
    mx = nt.nodes.new('ShaderNodeMixShader'); mx.inputs['Fac'].default_value = 0.45
    nt.links.new(bsdf.outputs[0], mx.inputs[1]); nt.links.new(tr.outputs[0], mx.inputs[2])
    nt.links.new(mx.outputs[0], out.inputs['Surface'])
    return m


def leaf(name, loc, rot, length, width, mat, bend=0.18, curl=0.1):
    """A leaf: a pointed oval, cupped along its rib and bowed along its length; UV x across, y along."""
    import bmesh
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    nx, ny = 14, 40
    verts = []
    uv_layer = bm.loops.layers.uv.new()
    for j in range(ny + 1):
        v = j / ny
        half = width * 0.5 * (math.sin(math.pi * min(1, v * 1.08)) ** 0.85) * (1 - 0.15 * v) + 0.002
        row = []
        for i in range(nx + 1):
            s = i / nx * 2 - 1
            x = s * half
            y = v * length
            z = -curl * (x / max(1e-4, width * 0.5)) ** 2 * width * 0.5 * 0 - bend * (v ** 2) * length + curl * abs(s) * half
            row.append(bm.verts.new((x, y, z)))
        verts.append(row)
    for j in range(ny):
        for i in range(nx):
            fc = bm.faces.new((verts[j][i], verts[j][i + 1], verts[j + 1][i + 1], verts[j + 1][i]))
            for lp in fc.loops:
                co = lp.vert.co
                lp[uv_layer].uv = (0.5 + co.x / width, co.y / length)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(o)
    o.data.materials.append(mat)
    for p in o.data.polygons:
        p.use_smooth = True
    sol = o.modifiers.new('s', 'SOLIDIFY'); sol.thickness = 0.004 * length
    o.location = loc
    o.rotation_euler = rot
    return o


def leaf_surface(o, u, v, length, width, bend=0.18, curl=0.1):
    """A point on the leaf's upper face (u across -1..1, v along 0..1), in world space."""
    half = width * 0.5 * (math.sin(math.pi * min(1, v * 1.08)) ** 0.85) * (1 - 0.15 * v)
    x = u * half
    y = v * length
    z = -bend * (v ** 2) * length + curl * abs(u) * half
    return o.matrix_world @ Vector((x, y, z + 0.002 * length))


def water_mat(name):
    """Water: clear glass with a little emission behind it that can be raised to make it glow."""
    m, nt, out = node_mat(name)
    gl = nt.nodes.new('ShaderNodeBsdfGlass')
    gl.inputs['IOR'].default_value = 1.33
    gl.inputs['Roughness'].default_value = 0.0
    gl.inputs['Color'].default_value = (1, 1, 1, 1)
    em = nt.nodes.new('ShaderNodeEmission')
    em.inputs['Color'].default_value = (1, 1, 1, 1)
    em.inputs['Strength'].default_value = 0.0
    add = nt.nodes.new('ShaderNodeAddShader')
    nt.links.new(gl.outputs[0], add.inputs[0]); nt.links.new(em.outputs[0], add.inputs[1])
    nt.links.new(add.outputs[0], out.inputs['Surface'])
    return m, gl, em


def drop(name, at, r, mat, normal=Vector((0, 0, 1))):
    o = uv_sphere(name, at, r, mat, 48, 24)
    o.scale = (1.0, 1.0, 0.62)
    o.location = at + normal * r * 0.5
    return o


def bokeh(rnd, centre, count, spread, depth, mat_hot, mat_warm):
    for i in range(count):
        p = centre + Vector((rnd.uniform(-spread, spread), rnd.uniform(depth * 0.6, depth * 1.4), rnd.uniform(-spread * 0.6, spread * 0.6)))
        uv_sphere(f'bk{i}', p, rnd.uniform(0.04, 0.16) * depth / 10, mat_hot if rnd.random() < 0.4 else mat_warm, 12, 6)


def sun_lamp(rot, color, energy, angle=2.0):
    ld = bpy.data.lights.new('sun', 'SUN')
    ld.energy = energy
    ld.color = color
    ld.angle = math.radians(angle)
    lo = bpy.data.objects.new('sun', ld)
    bpy.context.scene.collection.objects.link(lo)
    lo.rotation_euler = rot
    return lo


def build_leaves():
    sc = reset()
    sc.view_settings.exposure = -0.9
    world_gold(top=(0.6, 0.2, 0.01), horizon=(0.95, 0.5, 0.08), strength=1.0, sun_dir=Vector((0.2, 1, 0.35)), sun_power=4, sun_tight=0.9)
    # back light through the leaves and a soft warm fill from the lens side
    sun_lamp(Euler((math.radians(-70), 0, math.radians(185))), (1.0, 0.8, 0.45), 6.0)
    sun_lamp(Euler((math.radians(40), 0, math.radians(20))), (1.0, 0.75, 0.45), 1.2, 30)
    rnd = random.Random(3)
    hot, _ = emission_mat('hot', (1.0, 0.82, 0.45), 4.0)
    warm, _ = emission_mat('warm', (1.0, 0.5, 0.08), 2.5)
    green, _ = emission_mat('greenbk', (0.25, 0.5, 0.05), 0.8)
    bokeh(rnd, Vector((0, 0, 0.4)), 160, 7, 8, hot, warm)
    for i in range(24):
        uv_sphere(f'gb{i}', Vector((rnd.uniform(-6, 6), rnd.uniform(5, 10), rnd.uniform(-2.5, 2.5))), rnd.uniform(0.1, 0.25), green, 12, 6)
    water, _, _ = water_mat('water')
    lm = [leaf_mat('leaf0'), leaf_mat('leaf1', (0.12, 0.38, 0.02)), leaf_mat('leaf2', (0.05, 0.25, 0.03))]
    # a twig across the frame, leaves along it either side, tipped toward the lens so the drops on them show
    a0, a1 = Vector((-1.6, 0.4, -0.55)), Vector((1.5, 0.1, 0.55))
    bpy.ops.mesh.primitive_cylinder_add(radius=0.018, depth=(a1 - a0).length, location=(a0 + a1) / 2)
    tw = bpy.context.active_object
    tw.rotation_euler = (a1 - a0).to_track_quat('Z', 'Y').to_euler()
    bm_, bnt, bout = node_mat('bark')
    bb = bnt.nodes.new('ShaderNodeBsdfPrincipled'); bb.inputs['Base Color'].default_value = (0.12, 0.07, 0.03, 1)
    bnt.links.new(bb.outputs[0], bout.inputs['Surface'])
    tw.data.materials.append(bm_)
    leaves = []
    along = (a1 - a0).normalized()
    base_yaw = math.atan2(along.y, along.x) - math.pi / 2
    for i in range(9):
        k = (i + 0.5) / 9
        at = a0.lerp(a1, k)
        side = 1 if i % 2 == 0 else -1
        yaw = base_yaw + side * math.radians(rnd.uniform(48, 70))
        tilt = math.radians(rnd.uniform(-38, -22))
        length = rnd.uniform(0.75, 1.0)
        width = length * rnd.uniform(0.4, 0.48)
        rot = Euler((tilt, rnd.uniform(-0.15, 0.15), yaw), 'XYZ')
        o = leaf(f'leaf{i}', at, rot, length, width, lm[i % 3])
        leaves.append((o, rot, rnd.uniform(0, 6), length, width))
    bpy.context.view_layer.update()
    drops = []
    for li, (o, rot, ph, length, width) in enumerate(leaves):
        for d in range(rnd.randint(2, 4)):
            u, v = rnd.uniform(-0.5, 0.5), rnd.uniform(0.3, 0.7)
            r = rnd.uniform(0.04, 0.065) * length
            dr = drop(f'd{li}_{d}', leaf_surface(o, u, v, length, width), r, water)
            dr.parent = o
            dr.matrix_parent_inverse = o.matrix_world.inverted()
            drops.append(dr)
    cam = camera((0.2, -2.2, 1.3), (0, 0.2, 0), lens=45, fstop=2.8, focus=2.6)
    return sc, cam, leaves


def frame_leaves(state, f):
    sc, cam, leaves = state
    u = f / (SHOTS['leaves'] - 1)
    for o, rot, ph, length, width in leaves:
        o.rotation_euler = Euler((rot[0] + 0.03 * math.sin(f * 0.07 + ph), rot[1] + 0.02 * math.sin(f * 0.05 + ph), rot[2]), 'XYZ')
    # a slow drift across, the focus easing from the front leaf to the drops behind
    aim(cam, (0.45 - 0.9 * ease(u), -2.2 + 0.5 * u, 1.3 - 0.2 * u), (0.15 - 0.3 * u, 0.2, 0))
    cam.data.dof.focus_distance = 2.6 - 0.4 * ease(u)


def build_drops():
    sc = reset()
    sc.view_settings.exposure = -0.9
    world_gold(top=(0.6, 0.2, 0.01), horizon=(0.95, 0.5, 0.08), strength=1.0, sun_dir=Vector((0.1, 1, 0.3)), sun_power=4, sun_tight=0.9)
    sun_lamp(Euler((math.radians(-65), 0, math.radians(180))), (1.0, 0.8, 0.45), 5.0)
    sun_lamp(Euler((math.radians(45), 0, math.radians(15))), (1.0, 0.78, 0.5), 1.0, 30)
    rnd = random.Random(11)
    hot, _ = emission_mat('hot', (1.0, 0.82, 0.45), 3.5)
    warm, _ = emission_mat('warm', (1.0, 0.5, 0.08), 2.2)
    bokeh(rnd, Vector((0, 0, 0.2)), 120, 3, 4, hot, warm)
    L, Wd = 1.6, 0.7
    lf = leaf('leaf', (0, -0.6, 0), (math.radians(-6), 0, 0), L, Wd, leaf_mat('leafd'), bend=0.08, curl=0.06)
    lf2 = leaf('leafb', (0.55, 0.2, -0.25), (math.radians(-20), 0, math.radians(-40)), 1.2, 0.5, leaf_mat('leafe', (0.12, 0.45, 0.07)))
    bpy.context.view_layer.update()
    bm, bgl, bem = water_mat('blue')
    rm, rgl, rem = water_mat('red')
    pm, pgl, pem = water_mat('purple')
    v0 = 0.5
    a = leaf_surface(lf, -0.42, v0, L, Wd, 0.08, 0.06)
    b = leaf_surface(lf, 0.42, v0, L, Wd, 0.08, 0.06)
    mid = leaf_surface(lf, 0.0, v0, L, Wd, 0.08, 0.06)
    r = 0.055
    blue = drop('blue', a, r, bm)
    red = drop('red', b, r, rm)
    purple = drop('purple', mid, r * 1.25, pm)
    purple.hide_render = True
    # light inside each drop, coloured
    lights = {}
    for name, col, at in (('lb', (0.25, 0.55, 1.0), blue.location), ('lr', (1.0, 0.18, 0.12), red.location), ('lp', (0.75, 0.3, 1.0), purple.location)):
        ld = bpy.data.lights.new(name, 'POINT'); ld.color = col; ld.energy = 0.0; ld.shadow_soft_size = 0.03
        lo = bpy.data.objects.new(name, ld); bpy.context.scene.collection.objects.link(lo); lo.location = at
        lights[name] = lo
    # a halo plane round the merged drop: the burst
    flash_m, flash_em = emission_mat('flash', (0.85, 0.5, 1.0), 0.0)
    flash = uv_sphere('flash', purple.location, r * 1.6, flash_m, 32, 16)
    flash.hide_render = True
    cam = camera((0, -1.15, 0.55), (0, 0.05, -0.05), lens=85, fstop=2.0, focus=1.25)
    others = []
    for d in range(6):
        u_, v_ = rnd.uniform(-0.6, 0.6), rnd.uniform(0.72, 0.9)
        others.append(drop(f'o{d}', leaf_surface(lf, u_, v_, L, Wd, 0.08, 0.06), rnd.uniform(0.02, 0.04), water_mat(f'w{d}')[0]))
    return sc, cam, lf, (a, b, mid), (blue, red, purple, flash), (bem, rem, pem, flash_em), (bgl, rgl, pgl), lights


def frame_drops(state, f):
    sc, cam, lf, (a, b, mid), (blue, red, purple, flash), (bem, rem, pem, flash_em), (bgl, rgl, pgl), lights = state
    r = 0.055
    up = Vector((0, 0, r * 0.5))
    # glow up: blue at "lapse", red at "reversal"
    kb = ease((f - BLUE_ON) / 8.0)
    kr = ease((f - RED_ON) / 8.0)
    bem.inputs['Color'].default_value = (0.05, 0.35, 1.0, 1)
    rem.inputs['Color'].default_value = (1.0, 0.04, 0.02, 1)
    pulse_b = 1 + 0.12 * math.sin(f * 0.6)
    pulse_r = 1 + 0.12 * math.sin(f * 0.6 + 2)
    bem.inputs['Strength'].default_value = 1.6 * kb * pulse_b
    rem.inputs['Strength'].default_value = 1.6 * kr * pulse_r
    bgl.inputs['Color'].default_value = (1 - 0.85 * kb, 1 - 0.5 * kb, 1, 1)
    rgl.inputs['Color'].default_value = (1, 1 - 0.9 * kr, 1 - 0.9 * kr, 1)
    lights['lb'].data.energy = 1.2 * kb
    lights['lr'].data.energy = 1.2 * kr
    # then they run together, faster as they close, wobbling
    s = max(0.0, min(1.0, (f - SLIDE) / float(TOUCH - SLIDE)))
    k = s ** 2.2
    pa = a.lerp(mid, k * 0.93) + up
    pb = b.lerp(mid, k * 0.93) + up
    wob = 0.06 * s * math.sin(f * 1.3)
    blue.location = pa
    red.location = pb
    blue.scale = (1.0 + wob + 0.15 * k, 1.0 - wob, 0.62 - 0.05 * k)
    red.scale = (1.0 - wob + 0.15 * k, 1.0 + wob, 0.62 - 0.05 * k)
    lights['lb'].location = pa
    lights['lr'].location = pb
    merged = f >= TOUCH
    blue.hide_render = merged
    red.hide_render = merged
    purple.hide_render = not merged
    flash.hide_render = True
    if merged:
        m = (f - TOUCH) / float(SHOTS['drops'] - TOUCH)
        purple.location = mid + up * 1.2
        pem.inputs['Color'].default_value = (0.6, 0.12, 1.0, 1)
        pem.inputs['Strength'].default_value = 2.5 + 14 * m ** 1.5
        purple.scale = Vector((1.0, 1.0, 0.62)) * (1 + 0.9 * m)
        lights['lp'].data.energy = 2 + 8 * m
        lights['lp'].location = purple.location
        flash.location = purple.location
        flash.scale = Vector((1, 1, 1)) * (1 + 5 * m ** 1.3)
        flash_em.inputs['Color'].default_value = (0.7, 0.3, 1.0, 1)
        flash_em.inputs['Strength'].default_value = 0.6 + 1.2 * m
    # the lens leans in as they close
    z = ease((f - SLIDE) / float(TOUCH - SLIDE + 6))
    aim(cam, (0.02 * math.sin(f * 0.04), -1.15 + 0.4 * z, 0.55 - 0.12 * z), (0, 0.05, -0.04))
    cam.data.dof.focus_distance = 1.25 - 0.38 * z


BUILD = {'sky': (build_sky, frame_sky), 'leaves': (build_leaves, frame_leaves), 'drops': (build_drops, frame_drops)}


def bloom(path):
    """A soft glow round everything bright, as a lens would (the compositor is not used)."""
    try:
        import numpy as np
        from PIL import Image, ImageFilter
    except ImportError:
        return
    im = Image.open(path).convert('RGB')
    a = np.asarray(im).astype(np.float32) / 255.0
    lum = a.max(axis=2, keepdims=True)
    bright = np.clip((lum - 0.8) / 0.2, 0, 1) * a
    b = Image.fromarray((bright * 255).astype(np.uint8))
    glow = np.zeros_like(a)
    for rad, w in ((W / 160, 0.35), (W / 50, 0.3), (W / 16, 0.2)):
        glow += np.asarray(b.filter(ImageFilter.GaussianBlur(rad))).astype(np.float32) / 255.0 * w
    out = 1 - (1 - a) * (1 - np.clip(glow, 0, 1))
    Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8)).save(path)


def main():
    os.makedirs(OUT, exist_ok=True)
    work = []
    for shot, n in SHOTS.items():
        if ONLY and shot not in ONLY:
            continue
        frames = sorted(set(list(range(0, n, 30)) + [n - 1, n - 6])) if PREVIEW else range(n)
        if PREVIEW and shot == 'drops':
            frames = [0, 30, 60, 100, 130, TOUCH, TOUCH + 3, n - 1]
        for f in frames:
            work.append((shot, f))
    mine = work[JOB::JOBS]
    current = None
    state = None
    for shot, f in mine:
        if shot != current:
            state = BUILD[shot][0]()
            current = shot
        BUILD[shot][1](state, f)
        sc = bpy.context.scene
        sc.render.filepath = os.path.join(OUT, f'{shot}_f{f:04d}.png')
        t0 = __import__('time').time()
        bpy.ops.render.render(write_still=True)
        bloom(sc.render.filepath)
        print(f'[render] {shot} {f} {__import__("time").time() - t0:.1f}s', flush=True)


main()
