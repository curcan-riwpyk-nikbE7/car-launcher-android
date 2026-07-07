import numpy as np
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.art3d import Poly3DCollection

def remap(cx, cy, cz):
    return cx, cz, cy

def box(cx, cy, cz, hx, hy, hz):
    px, py, pz = remap(cx, cy, cz)
    x0,x1 = px-hx, px+hx
    y0,y1 = py-hz, py+hz
    z0,z1 = pz-hy, pz+hy
    verts = [
        [(x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)],
        [(x0,y0,z0),(x1,y0,z0),(x1,y1,z0),(x0,y1,z0)],
        [(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)],
        [(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)],
        [(x1,y0,z0),(x1,y1,z0),(x1,y1,z1),(x1,y0,z1)],
        [(x0,y0,z0),(x0,y1,z0),(x0,y1,z1),(x0,y0,z1)],
    ]
    return verts

BODY_HX = 0.85
BODY_HY = 0.22
WHEEL_R = 0.40
WHEEL_CY = -0.30
WHEEL_X = 1.00

def build_ax(ax, azim):
    ax.set_facecolor('black')
    def add_box(cx,cy,cz,hx,hy,hz,color,alpha=1.0):
        v = box(cx,cy,cz,hx,hy,hz)
        poly = Poly3DCollection(v, facecolor=color, edgecolor='k', linewidths=0.3, alpha=alpha)
        ax.add_collection3d(poly)

    add_box(0,0,0, BODY_HX,BODY_HY,2.0, (0.80,0.84,0.94))
    add_box(0,-0.02,1.55, BODY_HX-0.05,0.14,0.55, (0.72,0.77,0.88))
    add_box(0,0.30,-0.15, BODY_HX-0.2,0.20,1.0, (0.08,0.09,0.14))
    add_box(0,0.40,-1.78, BODY_HX-0.05,0.035,0.12, (0.20,0.90,1.0))
    add_box(0,-0.10,0, BODY_HX+0.02,0.02,2.02, (0.24,0.88,1.0))
    add_box(0.6,0.02,1.95, 0.16,0.07,0.05, (1,1,0.92))
    add_box(-0.6,0.02,1.95, 0.16,0.07,0.05, (1,1,0.92))
    add_box(0.62,0.06,-1.95, 0.18,0.06,0.05, (1,0.15,0.15))
    add_box(-0.62,0.06,-1.95, 0.18,0.06,0.05, (1,0.15,0.15))

    def wheel(cx,cy,cz,radius=WHEEL_R,width=0.22,color=(0.05,0.05,0.07)):
        px, py, pz = remap(cx, cy, cz)
        theta = np.linspace(0, 2*np.pi, 24)
        yy = py + radius*np.sin(theta)
        zz = pz + radius*np.cos(theta)
        x0 = px - width/2
        x1 = px + width/2
        verts=[]
        for i in range(len(theta)-1):
            verts.append([(x0,yy[i],zz[i]),(x0,yy[i+1],zz[i+1]),(x1,yy[i+1],zz[i+1]),(x1,yy[i],zz[i])])
        poly = Poly3DCollection(verts, facecolor=color, edgecolor='k', linewidths=0.2)
        ax.add_collection3d(poly)

    wheel(WHEEL_X,WHEEL_CY,1.32)
    wheel(-WHEEL_X,WHEEL_CY,1.32)
    wheel(WHEEL_X,WHEEL_CY,-1.32)
    wheel(-WHEEL_X,WHEEL_CY,-1.32)

    xx, yy = np.meshgrid(np.linspace(-2.5,2.5,2), np.linspace(-3,3,2))
    zz = np.full_like(xx, WHEEL_CY - WHEEL_R)
    ax.plot_surface(xx, yy, zz, color=(0.04,0.05,0.12), alpha=0.6)

    ax.set_xlim(-1.8,1.8)
    ax.set_ylim(-3,3)
    ax.set_zlim(-0.9,0.8)
    ax.set_box_aspect([3.6,6,1.7])
    ax.view_init(elev=14, azim=azim)
    ax.axis('off')

fig = plt.figure(figsize=(16, 8))
fig.patch.set_facecolor('black')
for i, azim in enumerate([0, 90, 180, 270]):
    ax = fig.add_subplot(2, 2, i+1, projection='3d')
    build_ax(ax, azim)
    ax.set_title(f"azim={azim}", color='white', fontsize=10)

plt.tight_layout()
plt.savefig('/home/user/car_check_final_4angles.png', dpi=100, facecolor='black')
print("saved")
