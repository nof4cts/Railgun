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
frames = list(range(sc.frame_start, sc.frame_end + 1, 2))
for i, f in enumerate(frames):
    if i % jobs != job:
        continue
    sc.frame_set(f)
    sc.render.filepath = f"{out}/f{i:05d}.png"
    t = time.time()
    bpy.ops.render.render(write_still=True)
    print(f"[render] job {job} frame {f} -> {i} in {time.time() - t:.1f}s", flush=True)
