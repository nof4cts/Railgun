# Renders every 2nd frame (30 fps from the 60 fps scene) of the Open cutscene, one slice per job.
import bpy, sys, time
argv = sys.argv[1:]
blend, job, jobs, out = argv[0], int(argv[1]), int(argv[2]), argv[3]
bpy.ops.wm.open_mainfile(filepath=blend)
sc = bpy.context.scene
sc.render.engine = 'CYCLES'
sc.render.resolution_x, sc.render.resolution_y, sc.render.resolution_percentage = 1280, 720, 100
sc.cycles.device = 'CPU'
sc.cycles.samples = 12
sc.cycles.use_adaptive_sampling = True
sc.cycles.use_denoising = True
sc.cycles.denoiser = 'OPENIMAGEDENOISE'
sc.render.use_persistent_data = True
sc.render.image_settings.file_format = 'PNG'
sc.render.image_settings.color_mode = 'RGB'
# The ending, remade: no explosion from the arrow at all. The golem never falls: from the shot on he stands where he
# was, lifeless (head sunk, arms hanging, his inner light gone out), wrapped in fire that grows until it rages over him.
for o in bpy.data.objects:
    if o.name.startswith(('VFX_blast', 'VFX_blob')) or o.name == 'L_blast':
        if o.animation_data:
            o.animation_data_clear()
        o.hide_render = True
        o.hide_viewport = True
SHOT = 2323
sc.frame_set(SHOT - 1)
golem = [o for o in bpy.data.objects if o.name.startswith('GOLEM')]
pose = {o.name: (o.location.copy(), o.rotation_euler.copy(), o.scale.copy()) for o in golem}
for o in golem:
    if o.animation_data:
        o.animation_data_clear()
    loc, rot, scl = pose[o.name]
    o.location, o.rotation_euler, o.scale = loc, rot, scl
import math
hp = bpy.data.objects.get('GOLEM_head_pivot')
if hp:
    hp.rotation_euler.x += math.radians(38)
for side, sgn in (('GOLEM_shoulder_L', 1), ('GOLEM_shoulder_R', -1)):
    s_ = bpy.data.objects.get(side)
    if s_:
        s_.rotation_euler = (0.0, 0.0, 0.0)
        s_.rotation_euler.y = math.radians(6 * sgn)
for m in bpy.data.materials:
    if m.name in ('GOLEM_eye',) and m.node_tree:
        for n in m.node_tree.nodes:
            if n.type == 'EMISSION':
                n.inputs['Strength'].default_value = 0.0
    if m.name == 'GOLEM_basalt' and m.node_tree:
        for n in m.node_tree.nodes:
            if n.type == 'EMISSION':
                n.inputs['Strength'].default_value = 0.6
for o in bpy.data.objects:
    if o.name in ('GOLEM_core_glow', 'GOLEM_eye_slit', 'GOLEM_shard_ring') or o.name.startswith('GOLEM_shard'):
        o.hide_render = True
# the fire: every bonfire gathered onto the golem, burning from the moment of the shot and growing past his head
gr = bpy.data.objects['GOLEM_root'].matrix_world.translation.copy()
fires = sorted([o for o in bpy.data.objects if o.name.startswith('VFX_bonfire')], key=lambda o: o.name)
for i, o in enumerate(fires):
    o.animation_data_clear()
    a = i * 2.399963
    r = 0.35 + 1.25 * ((i * 37) % 100) / 100.0
    lift = 0.0 if i % 3 else 1.1 + (i % 5) * 0.35
    o.location = (gr.x + math.cos(a) * r, gr.y + math.sin(a) * r, gr.z + lift)
    o.hide_render = True
    o.keyframe_insert('hide_render', frame=SHOT - 1)
    o.hide_render = False
    o.keyframe_insert('hide_render', frame=SHOT + (i % 6))
    base = 1.3 + 0.9 * (1 - r / 1.6)
    o.scale = (0.05, 0.05, 0.05)
    o.keyframe_insert('scale', frame=SHOT + (i % 6))
    o.scale = (base, base, base * 1.6)
    o.keyframe_insert('scale', frame=SHOT + 40 + (i % 9) * 3)
    o.scale = (base * 1.25, base * 1.25, base * 2.3)
    o.keyframe_insert('scale', frame=SHOT + 200)
FROM = 2323
frames = list(range(sc.frame_start, sc.frame_end + 1, 2))
todo = [(i, f) for i, f in enumerate(frames) if f >= FROM]
for k, (i, f) in enumerate(todo):
    if k % jobs != job:
        continue
    sc.frame_set(f)
    sc.render.filepath = f"{out}/f{i:05d}.png"
    t = time.time()
    bpy.ops.render.render(write_still=True)
    print(f"[render] job {job} frame {f} -> {i} in {time.time() - t:.1f}s", flush=True)
