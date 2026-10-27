import numpy as np, math, os, sys
from PIL import Image
R=sys.argv[1]  # resources assets dir
JAVA_OUT=sys.argv[2]
rng=np.random.default_rng(666)
P=[]  # (name,parent,pivot,rot,boxes[(mat,x,y,z,w,h,d)])
def part(n,p,piv,rot=(0,0,0),boxes=()): P.append((n,p,piv,rot,list(boxes)))
part('root',None,(0,24,0))
part('hips','root',(0,-26,0),(0,0,0),[('F',-3.5,-1,-2.5,7,4,5),('B',-1,-1,2.5,2,3,1)])
part('torso','hips',(0,-1,0),(0.25,0,0),[('D',-2.5,-7,-2,5,7,4),('B',-0.75,-7,2,1.5,7,1),('B',-2.8,-5,-1.5,0.6,3,3)])
part('chest','torso',(0,-7,0),(0.45,0,0),[('F',-5,-12,-3,10,12,6),('B',-4.6,-10,-3.6,9.2,1,1),('B',-4.6,-7.5,-3.6,9.2,1,1),('B',-4.2,-5,-3.6,8.4,1,1),('B',-3.6,-2.6,-3.4,7.2,1,1),('G',-6.5,-13.5,-2,4,4,4),('G',3,-5,-3.8,3,3,2),('B',-5.5,-12.5,-2,11,1.5,4)])
part('neck','chest',(0,-12,-1),(-0.2,0,0),[('D',-1.2,-9,-1.2,2.4,9,2.4),('B',-0.6,-9,1.0,1.2,9,1)])
part('head','neck',(0,-9,0),(0.1,0,0),[('B',-3,-6,-3,6,6,8),('F',-2.5,-4.5,-7,5,3.5,4),('B',-1,-8,-1,2,2,6),('B',-3.5,-7,1,1,3,1),('B',2.5,-7.5,2,1,3.5,1),('T',-2.4,-1,-6.9,4.8,1.2,0.8),('T',-2.6,-1,-6,0.6,1.2,4),('T',2.0,-1,-6,0.6,1.2,4)])
part('jaw','head',(0,-1,-1),(0.2,0,0),[('F',-2.6,0,-6.5,5.2,1.6,7),('T',-2.3,-1,-6.2,4.6,1,0.7),('T',-2.4,-1,-5.5,0.6,1,4.5),('T',1.8,-1,-5.5,0.6,1,4.5)])
part('jaw_inner','head',(0,-1.5,-2),(0,0,0),[('D',-1.5,-0.5,-4,3,1,4),('T',-1.4,-1.3,-4,2.8,0.8,0.6)])
for n,piv,s in [('eye_1',(-1.8,-3.6,-5.3),1.4),('eye_2',(1.9,-3.2,-5.4),1.2),('eye_3',(-2.7,-5.0,-3.0),1.1),('eye_4',(2.6,-5.3,-2.2),0.9),('eye_5',(0.3,-5.9,-4.2),1.0),('eye_6',(-0.8,-2.4,-7.1),0.8)]:
    part(n,'head',piv,(0,0,0),[('E',-s/2,-s/2,-s/2,s,s,s)])
prev='chest'
for i in range(8):
    w=2.2*(1-0.08*i)
    part(f'spine_{i+1}',prev,(0,-11.5,3.2) if i==0 else (0,-3,0),(-0.5 if i==0 else -0.28,0,0),[('B',-w/2,-3,-w/2,w,3,w),('B',-0.4,-2.6,w/2,0.8,0.8,2.5-0.2*i)])
    prev=f'spine_{i+1}'
for tn,piv,rot in [('tendril_a',(-2.5,-4,3),(0.5,0,0.3)),('tendril_b',(3,-8,3),(0.9,0,-0.4))]:
    prev='chest'
    for i in range(4):
        w=1.2-0.2*i
        bx=[('F' if i==0 else 'D',-w/2,0,-w/2,w,5,w)]
        if i==3: bx.append(('C',-0.3,5,-0.3,0.6,2,0.6))
        part(f'{tn}{i+1}',prev,piv if i==0 else (0,5,0),rot if i==0 else (0.3,0,0),bx); prev=f'{tn}{i+1}'
# holding arm (model -x)
part('rshoulder','chest',(-5.5,-10.5,0),(-0.6,0,0.12),[('F',-1.5,-1,-1.5,3,15,3),('B',-2.0,-1.5,-1,1,4,2),('G',-2.5,2,-1,2,3,2)])
part('rfore','rshoulder',(0,14,0),(-0.3,0,0),[('D',-1.2,0,-1.2,2.4,15,2.4),('B',-0.5,0,1.1,1,3,1)])
part('rhand','rfore',(0,15,0),(0,0,0),[('F',-1.5,0,-1.2,3,2,2.4)])
for i,px in enumerate([-1,0,1]):
    part(f'rf{i+1}','rhand',(px,1.8,-0.6 if px else -0.9),(-0.2,0,0),[('D',-0.4,0,-0.4,0.8,6,0.8)])
    part(f'rc{i+1}',f'rf{i+1}',(0,6,0),(0.3,0,0),[('C',-0.35,0,-0.7,0.7,4,0.7)])
# ripping arm (model +x), three-jointed and longer
part('lshoulder','chest',(5.5,-10.5,0.5),(-0.6,0,-0.1),[('F',-1.5,-1,-1.5,3,13,3),('G',0.5,-2,-1.5,3,3,3)])
part('lmid','lshoulder',(0,12,0),(0.2,0,0),[('D',-1.2,0,-1.2,2.4,9,2.4),('B',-0.5,-0.5,1,1,2,1.5)])
part('lfore','lmid',(0,9,0),(-0.4,0,0),[('D',-1,0,-1,2,11,2)])
part('lhand','lfore',(0,11,0),(0,0,0),[('F',-1.6,0,-1.2,3.2,2,2.4)])
for i,px in enumerate([-1.2,-0.4,0.4,1.2]):
    part(f'lf{i+1}','lhand',(px,1.8,-0.9 if abs(px)<1 else -0.6),(-0.15,0,0),[('D',-0.35,0,-0.35,0.7,7,0.7)])
    part(f'lc{i+1}',f'lf{i+1}',(0,7,0),(0.35,0,0),[('C',-0.3,0,-0.7,0.6,5,0.6)])
# legs
part('lthigh','root',(2.6,-26,0),(-0.25,0,-0.05),[('F',-1.5,0,-1.5,3,14,3),('B',-0.5,11,-2.3,1,3,1)])
part('lshin','lthigh',(0,13,0),(0.5,0,0),[('D',-1.2,0,-1.2,2.4,12,2.4)])
part('lfoot','lshin',(0,12,0),(-0.25,0,0),[('F',-1.5,0,-4,3,1.2,5),('C',-1.4,0.2,-5.5,0.6,1,1.5),('C',0.8,0.2,-5.5,0.6,1,1.5)])
part('rthigh','root',(-2.8,-26,0.4),(-0.1,0,0.08),[('D',-1.3,0,-1.3,2.6,14,2.6),('G',-1.8,4,-1,2,3,2)])
part('rshin','rthigh',(0,13,0),(0.35,0,0),[('F',-1.1,0,-1.1,2.2,12,2.2),('B',-0.4,2,1.0,0.8,4,1)])
part('rfoot','rshin',(0,12,0),(-0.25,0,0),[('F',-1.4,0,-4,2.8,1.2,5),('C',-1.3,0.2,-5.5,0.6,1,1.5),('C',0.7,0.2,-5.5,0.6,1,1.5)])

# ---- pack UVs (shelf packer) on 256x256
W=H=256
rects=[]
for pi,(n,p,piv,rot,bx) in enumerate(P):
    for bi,b in enumerate(bx):
        _,x,y,z,w,h,d=b
        rects.append((pi,bi,math.ceil(2*(d+w))+1,math.ceil(d+h)+1))
order=sorted(range(len(rects)),key=lambda i:-rects[i][3])
uv={}; cx=cy=0; rowh=0
for i in order:
    pi,bi,rw,rh=rects[i]
    if cx+rw>W: cx=0; cy+=rowh; rowh=0
    uv[(pi,bi)]=(cx,cy); cx+=rw; rowh=max(rowh,rh)
assert cy+rowh<=H, cy+rowh
# ---- paint
base=np.zeros((H,W,4),np.uint8); glow=np.zeros((H,W,4),np.uint8)
def nz(h,w,s=1.0): return rng.random((h,w))*s
for pi,(n,p,piv,rot,bx) in enumerate(P):
    for bi,b in enumerate(bx):
        m,x,y,z,w,h,d=b; u,v=uv[(pi,bi)]
        rw=math.ceil(2*(d+w))+1; rh=math.ceil(d+h)+1
        N=nz(rh,rw)
        if m=='F':   # corrupted flesh: bruised grey-violet, crimson veins, translucent patches
            c=np.stack([45+N*35,22+N*18,32+N*22],-1)
            veins=(rng.random((rh,rw))>0.82)
            c[veins]=[120,10,14]
            a=np.where(rng.random((rh,rw))>0.85,185,255)
            g=veins&(rng.random((rh,rw))>0.4)
        elif m=='D':  # sinew, nearly black with red striations
            c=np.stack([20+N*18,8+N*8,10+N*10],-1)
            st=(np.arange(rw)[None,:]+np.arange(rh)[:,None]*0)%3==0
            c[st&(N>0.5)]=[90,8,10]; a=np.full((rh,rw),255); g=st&(N>0.8)
        elif m=='B':  # dirty cracked bone
            c=np.stack([170+N*50,155+N*40,120+N*30],-1)
            cr=rng.random((rh,rw))>0.8; c[cr]=[70,55,40]
            bl=rng.random((rh,rw))>0.9; c[bl]=[110,15,15]; a=np.full((rh,rw),255); g=np.zeros((rh,rw),bool)
        elif m=='G':  # pulsing crimson growths
            c=np.stack([150+N*80,10+N*20,20+N*20],-1); a=np.full((rh,rw),235); g=N>0.35
        elif m=='T':  # teeth: ivory with dark gaps
            c=np.stack([215+N*30,205+N*30,170+N*30],-1)
            gap=(np.arange(rw)%2==1)[None,:].repeat(rh,0); c[gap]=[60,30,25]; a=np.full((rh,rw),255); g=np.zeros((rh,rw),bool)
        elif m=='E':  # eyes: sickly yellow sclera, red pupil
            c=np.stack([230+N*20,200+N*30,60+N*30],-1); c[(N>0.55)]=[255,20,10]; a=np.full((rh,rw),255); g=np.ones((rh,rw),bool)
        elif m=='C':  # claws: glossy black-red
            c=np.stack([25+N*25,5+N*10,8+N*10],-1); c[N>0.85]=[140,140,150]; a=np.full((rh,rw),255); g=np.zeros((rh,rw),bool)
        base[v:v+rh,u:u+rw,:3]=np.clip(c,0,255); base[v:v+rh,u:u+rw,3]=a
        gc=np.zeros((rh,rw,4)); gc[g]=[255,30,20,255] if m!='E' else [255,240,120,255]
        if m=='E': gc[(N>0.55)]=[255,20,10,255]
        glow[v:v+rh,u:u+rw]=gc
os.makedirs(f'{R}/textures/entity',exist_ok=True)
Image.fromarray(base,'RGBA').save(f'{R}/textures/entity/abomination.png')
Image.fromarray(glow,'RGBA').save(f'{R}/textures/entity/abomination_glow.png')
# ---- java emission
def f(v): s=f'{v:.4f}'.rstrip('0').rstrip('.'); return (s if s not in('-0','') else '0')+'F'
L=[]
for pi,(n,p,piv,rot,bx) in enumerate(P):
    cb='CubeListBuilder.create()'
    for bi,b in enumerate(bx):
        m,x,y,z,w,h,d=b; u,v=uv[(pi,bi)]
        cb+=f'.texOffs({u}, {v}).addBox({f(x)}, {f(y)}, {f(z)}, {f(w)}, {f(h)}, {f(d)})'
    par='mesh.getRoot()' if p is None else f'p_{p}'
    L.append(f'        PartDefinition p_{n} = {par}.addOrReplaceChild("{n}", {cb}, PartPose.offsetAndRotation({f(piv[0])}, {f(piv[1])}, {f(piv[2])}, {f(rot[0])}, {f(rot[1])}, {f(rot[2])}));')
fields='\n'.join(f'    public final ModelPart {n};' for n,*_ in P)
ctor=[]
for n,p,*_ in P:
    ctor.append(f'        this.{n} = ' + ('modelRoot.getChild("root");' if p is None else f'this.{p}.getChild("{n}");'))
open(JAVA_OUT,'w').write('// GENERATED_FIELDS\n'+fields+'\n// GENERATED_CTOR\n'+'\n'.join(ctor)+'\n// GENERATED_LAYER\n'+'\n'.join(L)+'\n')
print('parts',len(P),'rows used',cy+rowh)
