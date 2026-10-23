import numpy as np, sys
from PIL import Image, ImageFilter
out=sys.argv[1]; N=1200; OUT=600
y,x=np.mgrid[0:N,0:N].astype(np.float32); cx=cy=(N-1)/2
dx,dy=(x-cx)/(N/2),(y-cy)/(N/2); r=np.hypot(dx,dy); th=np.arctan2(dy,dx)
E=0.82*(1+0.028*np.sin(3*th+0.5)+0.022*np.sin(5*th+2.1)+0.014*np.sin(8*th+0.9)+0.008*np.sin(13*th+0.2))
d=r/E
phi=4*th+5.5*np.log(r+0.03)
v=np.sin(phi)+0.35*np.sin(2*phi+0.7)+0.15*np.sin(9*th-3*np.log(r+0.03))
s=0.5+0.5*np.clip(v/1.4,-1,1)
b=s*0.62+(1-d)*0.62
hexs=['#174d17','#2a7f22','#4fae2f','#8fd947','#c9f57a','#f4ffc8']
tones=np.array([[int(h[i:i+2],16) for i in (1,3,5)] for h in hexs],np.uint8)
idx=np.digitize(b,[0.32,0.48,0.62,0.78,0.98])
rgb=tones[idx]
# rim
rim=(d>0.885)&(d<=1.0); rgb[rim]=[0xb8,0xf0,0x60]
rim2=(d>0.975)&(d<=1.0); rgb[rim2]=[0x6c,0xc2,0x34]
inner=(d>0.85)&(d<=0.885); rgb[inner]=[0xe2,0xff,0x9c]
a=np.where(d<=1.0,255,0).astype(np.uint8)
img=np.dstack([rgb,a])
im=Image.fromarray(img,'RGBA')
# specks
rng=np.random.default_rng(7)
from PIL import ImageDraw
dr=ImageDraw.Draw(im)
for i in range(9):
    t=rng.uniform(0,2*np.pi); rr=rng.uniform(0.18,0.72)*0.8*(N/2); s2=rng.uniform(5,11)
    px,py=cx+rr*np.cos(t),cy+rr*np.sin(t)
    dr.ellipse([px-s2,py-s2,px+s2,py+s2],fill=(246,255,226,255))
im=im.resize((OUT,OUT),Image.LANCZOS)
im.save(out+'/portal-swirl.png',optimize=True)
# halo
g=np.clip(1-(r-0.70)/0.30,0,1)**2*np.where(r>0.6,1,1)
halo=np.dstack([np.full_like(r,0x94),np.full_like(r,0xcc),np.full_like(r,0x66),g*170]).astype(np.uint8)
Image.fromarray(halo,'RGBA').resize((OUT,OUT),Image.LANCZOS).save(out+'/portal-halo.png',optimize=True)
