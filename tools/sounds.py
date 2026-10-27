import numpy as np, subprocess, os, sys
SR=44100
OUT=sys.argv[1]; GORE=sys.argv[2]; EERIE=sys.argv[3]
rng=np.random.default_rng(13)
def ogg(name, x, sr=SR):
    x=np.asarray(x,dtype=np.float64); x=x/ (np.max(np.abs(x))+1e-9)*0.95
    raw=(x*32767).astype(np.int16).tobytes()
    subprocess.run(['ffmpeg','-v','error','-y','-f','s16le','-ar',str(sr),'-ac','1','-i','-','-c:a','libvorbis','-q:a','5',f'{OUT}/{name}.ogg'],input=raw,check=True)
def cut(src,name,start,dur,extra='',mono=True):
    af=f'afade=t=in:d=0.02,afade=t=out:st={max(dur-0.08,0)}:d=0.08'+(','+extra if extra else '')
    subprocess.run(['ffmpeg','-v','error','-y','-ss',str(start),'-t',str(dur),'-i',src,'-ac','1' if mono else '2','-ar','44100','-af',af,'-c:a','libvorbis','-q:a','5',f'{OUT}/{name}.ogg'],check=True)
def t(d): return np.arange(int(SR*d))/SR
def env(n,a,r):
    e=np.ones(n); A=int(a*SR); R=int(r*SR)
    if A: e[:A]=np.linspace(0,1,A)
    if R: e[-R:]*=np.linspace(1,0,R)**2
    return e
def lp(x,c):  # one-pole lowpass
    a=np.exp(-2*np.pi*c/SR); y=np.empty_like(x); s=0.
    for i,v in enumerate(x): s=(1-a)*v+a*s; y[i]=s
    return y
def bp(x,lo,hi): return lp(x,hi)-lp(lp(x,lo),lo)
def noise(d): return rng.standard_normal(int(SR*d))
def dist(x,k): return np.tanh(x*k)
# --- gore clips from the user's GoreBox file
gore=[(0.30,0.56),(1.68,0.41),(2.95,1.99),(5.55,1.20),(7.43,0.99),(9.17,0.83)]
for i,(s,d) in enumerate(gore): cut(GORE,f'gore_{i+1}',s,d)
cut(GORE,'rip_tear',2.95,1.99,'asetrate=44100*0.85,aresample=44100')
cut(GORE,'gush_1',5.55,1.20,'asetrate=44100*0.75,aresample=44100')
cut(GORE,'gush_2',7.43,0.99,'asetrate=44100*0.8,aresample=44100')
# --- music / stings from the eerie compilation (stereo streams, non-positional)
cut(EERIE,'music_stalk',150,68,mono=False)
cut(EERIE,'music_hunt',220,60,mono=False)
cut(EERIE,'music_nightmare',285,60,mono=False)
cut(EERIE,'finisher_sting',108,7,mono=False)
cut(EERIE,'emerge_sting',820,10,mono=False)
# --- synthesized layers
for v in range(3):  # breathing: layered low rasp, inhale then exhale
    d=2.6; n=noise(d); tt=t(d)
    rasp=bp(n,150+40*v,900)*(1+0.6*np.sin(2*np.pi*(38+7*v)*tt))
    shape=np.where(tt<1.1,np.sin(np.pi*tt/1.1),0)+np.where(tt>1.3,np.sin(np.pi*np.clip((tt-1.3)/1.2,0,1))*1.3,0)
    growl=np.sin(2*np.pi*(52+9*v)*tt+2*np.sin(2*np.pi*3*tt))*0.4
    ogg(f'breath_{v+1}',dist((rasp+growl*(tt>1.3))*shape,2.5))
for v in range(4):  # wet organic steps
    d=0.35; tt=t(d)
    thump=np.sin(2*np.pi*(70-60*tt)*tt)*np.exp(-tt*18)
    squelch=bp(noise(d),300,2500)*np.exp(-tt*12)*(1+np.sin(2*np.pi*(25+5*v)*tt))
    ogg(f'step_wet_{v+1}',thump*1.2+squelch*0.7)
for v in range(3):  # metallic scraping
    d=2.2+v*0.4; tt=t(d); n=noise(d)
    x=sum(bp(n,f,f*1.04)*np.sin(2*np.pi*0.7*tt+k) for k,f in enumerate([1800+200*v,2650,3900,5200]))
    x*=(0.6+0.4*np.abs(np.sin(2*np.pi*(5+v)*tt)))*env(len(tt),0.2,0.6)
    ogg(f'scrape_{v+1}',x)
for v in range(4):  # unintelligible whispers (syllabic AM noise, some reversed)
    d=2.4; tt=t(d); n=noise(d)
    syl=np.clip(np.sin(2*np.pi*(3.1+0.6*v)*tt+np.sin(2*np.pi*0.9*tt)*2),0,1)**1.5
    w=(bp(n,1500,5500)*0.8+bp(n,600,1400)*0.4)*syl*env(len(tt),0.3,0.5)
    if v%2: w=w[::-1]
    ogg(f'whisper_{v+1}',w)
d=1.6; tt=t(d)  # sub-bass pulse
ogg('sub_bass',np.sin(2*np.pi*(38-6*tt)*tt)*np.exp(-tt*2.2)*env(len(tt),0.04,0.3))
d=0.9; tt=t(d)  # heartbeat
hb=np.zeros_like(tt)
for s in (0.0,0.28):
    m=tt>=s; u=tt[m]-s; hb[m]+=np.sin(2*np.pi*48*u)*np.exp(-u*14)*(1 if s==0 else 0.7)
ogg('heartbeat',hb)
for v in range(2):  # jumpscare scream
    d=1.8; tt=t(d)
    f=900*np.exp(-tt*0.9)+180+v*60
    ph=2*np.pi*np.cumsum(f)/SR
    x=sum(np.sign(np.sin(ph*m+k))*a for k,(m,a) in enumerate([(1,1),(1.007,0.8),(1.5,0.5),(0.5,0.6)]))
    x=dist(x*0.6+bp(noise(d),2000,8000)*1.4,3)*env(len(tt),0.005,0.9)
    ogg(f'scream_{v+1}',x)
d=3.0; tt=t(d)  # roar
f=70+25*np.sin(2*np.pi*0.4*tt); ph=2*np.pi*np.cumsum(f)/SR
roar=dist((np.sin(ph)+0.6*np.sin(2.01*ph)+0.4*np.sin(3.03*ph))*(1+0.5*np.sin(2*np.pi*23*tt))+bp(noise(d),200,1800)*1.2,4)*env(len(tt),0.25,1.0)
ogg('roar',roar)
d=1.6; tt=t(d)  # impact: bass drop + crack
ogg('impact',dist(np.sin(2*np.pi*np.cumsum(140*np.exp(-tt*4)+30)/SR)*np.exp(-tt*2.5)*1.6+bp(noise(d),1500,9000)*np.exp(-tt*40)*1.2,2))
for v in range(4):  # vertebra cracks / snaps
    d=0.25; tt=t(d)
    c=bp(noise(d),1200+400*v,9000)*np.exp(-tt*55)+np.sin(2*np.pi*(240+40*v)*tt)*np.exp(-tt*40)*0.5
    ogg(f'crack_{v+1}',dist(c*2,2))
d=1.4; tt=t(d)  # grab: whoosh + wet slap
ogg('grab',bp(noise(d),300,3000)*np.clip(np.sin(np.pi*tt/0.45),0,1)*(tt<0.45)+(tt>0.45)*(bp(noise(d),200,2500)+np.sin(2*np.pi*60*tt)*0.8)*np.exp(-np.clip(tt-0.45,0,9)*9))
d=2.0; tt=t(d)  # vanish distortion
ogg('vanish',dist(np.sin(2*np.pi*np.cumsum(400*np.exp(-tt*3)+40)/SR)*bp(noise(d),100,6000)*env(len(tt),0.02,0.8)*3,2))
d=4.0; tt=t(d)  # death gurgle/dissolve
ogg('death',dist(bp(noise(d),80,900)*(1+np.sin(2*np.pi*(11-2*tt)*tt))*np.exp(-tt*0.7)+np.sin(2*np.pi*np.cumsum(90-15*tt)/SR)*np.exp(-tt*0.9)*0.7,3))
for v in range(3):  # hurt shriek
    d=0.7; tt=t(d); f=500+200*v-300*tt; ph=2*np.pi*np.cumsum(f)/SR
    ogg(f'hurt_{v+1}',dist((np.sin(ph)+np.sign(np.sin(1.5*ph))*0.4+bp(noise(d),1000,6000))*env(len(tt),0.01,0.4),3))
print(sorted(os.listdir(OUT)))
