# Bakes the fire effects of the user's fire_arrow_open.blend for the in-game Open (no cutscene): every part's base
# geometry once (before the FIRE_dough_flow wobble, which the game redoes in a shader), and per tick (every 3rd frame
# of the 60 fps scene) its placement, visibility and, for curves, how much of it is revealed.
import bpy, sys, struct, gzip, math, mathutils
argv=sys.argv[1:]
blend, out = argv[0], argv[1]
bpy.ops.wm.open_mainfile(filepath=blend)
sc=bpy.context.scene
MAT={'VFX_arrow_core':0,'VFX_fire_hot':0,'VFX_fireball':0,'VFX_arrow_wing':1,'VFX_blast':1,'VFX_fire_ribbon':1,'VFX_tongue':1,'VFX_arrowhead':2,'VFX_spark':3,'VFX_ember':3,'VFX_cube_glow':4}
def pick(prefixes, exclude=()):
    return [o for o in sc.objects if o.name.startswith(prefixes) and not o.name.startswith(exclude) and o.type in ('MESH','CURVE')]
SEQ=[
 ('armfire', (433,523), pick(('VFX_armfire','VFX_armstream','VFX_armspark')), 'hero'),
 ('fists',   (1441,1549), pick(('VFX_arm_column','VFX_arm_flare','VFX_arm_spark','VFX_fist_burst','VFX_fist_spark','VFX_fist_swirl')), 'hero'),
 ('draw',    (1675,1771), pick(('VFX_draw_streak','VFX_draw_collapse','VFX_arrow_'), ('VFX_arrow_root',)), 'hero'),
 ('arrow',   (2100,2280), pick(('VFX_arrow_',), ('VFX_arrow_root',)), 'arrow'),
 ('blast',   (2323,2905), pick(('VFX_blast','VFX_blob','VFX_bonfire')), 'blast'),
]
def anchor(kind):
    if kind=='hero': return bpy.data.objects['HERO_root'].matrix_world.inverted()
    if kind=='arrow': return bpy.data.objects['VFX_arrow_root'].matrix_world.inverted()
    return mathutils.Matrix.Translation((0,-36.4,0))
def visible(o):
    p=o
    while p is not None:
        if p.hide_render: return False
        p=p.parent
    return True
def gn(o):
    for m in o.modifiers:
        if m.type=='NODES': return m
def params(o):
    m=gn(o)
    if not m: return [0,0,1,0,0,0,0,0,0]
    i=m.properties.inputs
    g=lambda n,d=0.0: getattr(i,n,d)
    f=tuple(g('Flow',(0,0,0)))
    return [g('Amp'),g('Swell'),g('Scale',1.0),g('Speed'),g('Rise'),float(g('Subdiv',0)),f[0],f[1],f[2]]
def base(o, prm):
    """Local geometry before the wobble: (verts[(pos,nrm,u)], tris)."""
    m=gn(o)
    if m: m.show_viewport=False; m.show_render=False
    tmp=None; saved=None
    if o.type=='CURVE':
        d=o.data; saved=(d.bevel_factor_start,d.bevel_factor_end); d.bevel_factor_start=0.0; d.bevel_factor_end=1.0
    elif int(prm[5])>0:
        tmp=o.modifiers.new('bake_sub','SUBSURF'); tmp.levels=min(2,int(prm[5])); tmp.render_levels=tmp.levels
    dg=bpy.context.evaluated_depsgraph_get(); dg.update()
    ev=o.evaluated_get(dg); me=ev.to_mesh()
    vs=[]; tris=[]; mid=1
    if me is not None:
        me.calc_loop_triangles()
        vs=[(v.co.copy(), v.normal.copy()) for v in me.vertices]
        tris=[tuple(t.vertices) for t in me.loop_triangles]
    if ev.material_slots and ev.material_slots[0].material: mid=MAT.get(ev.material_slots[0].material.name,1)
    ev.to_mesh_clear()
    us=[-1.0]*len(vs)
    if o.type=='CURVE' and vs:
        sp=o.data.splines[0]; pts=[p.co.xyz for p in sp.points] if sp.type=='POLY' else [p.co for p in sp.bezier_points]
        cum=[0.0]
        for a,b in zip(pts,pts[1:]): cum.append(cum[-1]+(b-a).length)
        L=max(cum[-1],1e-6)
        for k,(p,n) in enumerate(vs):
            best=(1e18,0.0)
            for s in range(len(pts)-1):
                a,b=pts[s],pts[s+1]; ab=b-a; t=0.0 if ab.length_squared<1e-12 else max(0.0,min(1.0,(p-a).dot(ab)/ab.length_squared))
                dd=(a+ab*t-p).length_squared
                if dd<best[0]: best=(dd,(cum[s]+t*ab.length)/L)
            us[k]=best[1]
    if saved: o.data.bevel_factor_start,o.data.bevel_factor_end=saved
    if tmp: o.modifiers.remove(tmp)
    if m: m.show_viewport=True; m.show_render=True
    return [(p,n,u) for (p,n),u in zip(vs,us)], tris, mid
blob=bytearray()
def w(fmt,*a): blob.extend(struct.pack('<'+fmt,*a))
blob.extend(b'OPN2'); w('i',len(SEQ))
for name,(a,b),objs,kind in SEQ:
    frames=list(range(a,b+1,3))
    sc.frame_set(a)
    parts=[]
    for o in objs:
        prm=params(o); vs,tris,mid=base(o,prm)
        if tris: parts.append((o,prm,vs,tris,mid))
    nb=name.encode(); w('i',len(nb)); blob.extend(nb); w('ii',len(frames),len(parts))
    nt=0
    for o,prm,vs,tris,mid in parts:
        w('BB',1 if o.type=='CURVE' else 0, mid); w('9f',*prm); w('ii',len(vs),len(tris)); nt+=len(tris)
        for p,n,u in vs: w('3f3bf',p.x,p.y,p.z,*[max(-127,min(127,int(round(c*127)))) for c in n],u)
        for t in tris: w('3i',*t)
    for fr in frames:
        sc.frame_set(fr); inv=anchor(kind)
        for o,prm,vs,tris,mid in parts:
            if not visible(o): w('B',0); continue
            M=inv@o.matrix_world
            w('B',1); w('12f',*[M[r][c] for r in range(3) for c in range(4)])
            if o.type=='CURVE': w('2f',o.data.bevel_factor_start,o.data.bevel_factor_end)
            else: w('2f',0.0,1.0)
    print(name,'frames',len(frames),'parts',len(parts),'base tris',nt, flush=True)
open(out,'wb').write(gzip.compress(bytes(blob),9))
print('bytes raw',len(blob),'gz',len(gzip.compress(bytes(blob),9)))
