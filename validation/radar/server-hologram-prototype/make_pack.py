from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import json, math, struct, sys, zlib

def texture():
    rows=[]
    for y in range(64):
        row=bytearray([0])
        for x in range(64):
            tile=y//16
            edge=x%8 in (0,7) or y%8 in (0,7)
            row.extend([
                ((104,133,148,255) if edge else (39,51,65,255)),
                ((50,228,243,255) if edge else (9,83,99,255)),
                (255,174,46,255), (204,251,255,255),
            ][tile])
        rows.append(row)
    def chunk(name,data):
        return struct.pack('>I',len(data))+name+data+struct.pack('>I',zlib.crc32(name+data))
    return bytes.fromhex('89504e470d0a1a0a')+chunk(b'IHDR',struct.pack('>IIBBBBB',64,64,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows)))+chunk(b'IEND',b'')

def build_pack(path):
    def cube(origin,size,tile):
        return dict(origin=origin,size=size,uv=[0,tile*16])
    bones=[dict(name='projector',pivot=[0,0,0],cubes=[
        cube([-7,0,-6],[14,1.5,12],0),cube([-6,1.5,-5],[12,.65,10],0),
        cube([-7.2,1.8,-.25],[14.4,.16,.5],1),cube([-1,2.15,-1],[2,.45,2],1),
        cube([-4,.25,6],[8,1.1,.3],0),cube([-2,.5,6.3],[4,.3,.05],1)]),
        dict(name='terrain',parent='projector',pivot=[0,5.6,0],rotation=[-12,0,0])]
    animation={}
    for z in range(16):
        for x in range(16):
            i=z*16+x
            if math.hypot(x-7.5,z-7.5)>7.75:
                continue
            pivot=[(x-7.5)*.74,5.6,(z-7.5)*.74]
            name=f'column_{i}'
            origin=[pivot[0]-.355,pivot[1],pivot[2]-.355]
            bones.append(dict(name=name,parent='terrain',pivot=pivot,cubes=[cube(origin,[.71,1,.71],1)]))
            h=f'v.h{i}'
            animation[name]={'scale':[f'{h}>0?1:0',f'{h}*0.28',f'{h}>0?1:0']}
    for name,x,z,tile in [('you',.37,.37,3),('raid',2.8,-2,2)]+[
            (f'dot{i}',.37+(2.8-.37)*i/8,.37+(-2-.37)*i/8,3) for i in range(1,8)]:
        extent=.24 if name in ('you','raid') else .065
        pivot=[x,9.35,z]
        bones.append(dict(name=name,parent='terrain',pivot=pivot,cubes=[cube([x-extent,9.35,z-extent],[extent*2,extent*2,extent*2],tile)]))
        animation[name]={'scale':['v.markers']*3}
    with ZipFile(path,'w',ZIP_DEFLATED) as pack:
        def put(key,value):pack.writestr(key,json.dumps(value,separators=(',',':')))
        put('pack.mcmeta',{'pack':{'pack_format':34,'description':'Disposable Raid Radar 3D prototype'}})
        root='assets/radarprototype/bedrock/generic/'
        put(root+'models/projector.geo.json',{'format_version':'1.12.0','minecraft:geometry':[{
            'description':{'identifier':'geometry.projector','texture_width':64,'texture_height':64,'visible_bounds_width':3,'visible_bounds_height':2,'visible_bounds_offset':[0,.5,0]},
            'bones':bones}]})
        put(root+'animations/projector.animation.json',{'format_version':'1.8.0','animations':{'animation.projector.scan':{'loop':True,'animation_length':1,'bones':animation}}})
        put(root+'posers/projector.json',{'rootBone':'projector','transformedParts':[{'part':name,'scale':[1,1,1]} for name in animation],'poses':{'standing':{'poseTypes':['STAND','NONE'],'animations':["q.bedrock('projector', 'scan')"]}}})
        put(root+'variations/projector.json',{'species':'radarprototype:projector','order':0,'variations':[{'aspects':[],'model':'radarprototype:projector.geo','poser':'radarprototype:projector','texture':'radarprototype:textures/projector.png'}]})
        pack.writestr('assets/radarprototype/textures/projector.png',texture())

if __name__=='__main__':
    build_pack(Path(sys.argv[1]))
