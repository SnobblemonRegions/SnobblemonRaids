"""Native-resolution HP strips and bounded pixel-light overlays for the approved Rift Crown.

The generated crown artwork is imported unchanged. These independent code-native
layers animate around it and leave its face, name and caption regions unobstructed.
"""
from pathlib import Path
from PIL import Image, ImageDraw

OUT=Path(__file__).resolve().parents[1]/'radar-client/src/main/resources/assets/cobbleraids_radar/textures/worldboss'


def hp(colors):
    image=Image.new('RGBA',(189,6));draw=ImageDraw.Draw(image)
    for y,color in enumerate(colors):draw.line((0,y,188,y),fill=color)
    return image


def lights():
    atlas=Image.new('RGBA',(360,96*16))
    left=[(94,50),(98,53),(102,58),(107,62),(113,64),(121,65),(116,58),(120,52)]
    pulse=[0,1,2,3,3,2,1,0]
    for critical in [False,True]:
        for phase in range(8):
            layer=Image.new('RGBA',(360,96));draw=ImageDraw.Draw(layer)
            bright=(255,160,177,210) if critical else (235,184,255,200)
            dim=(174,48,83,105) if critical else (143,61,207,100)
            for i in range(7):
                x=95+(i*24+phase*3)%168
                draw.line((x,68,x+2,68),fill=bright)
                draw.point((x-1,68),fill=dim)
            for offset in [0,4]:
                x,y=left[(phase+offset)%8]
                for side in [x,360-x]:
                    draw.point((side,y),fill=bright);draw.point((side,y+1),fill=dim)
            level=40+25*pulse[phase]
            for eye in [(174,32),(192,32)]:draw.point(eye,fill=(255,100,131,level))
            for at in [(159,17),(183,2),(205,18)]:draw.point(at,fill=(255,231,166,level))
            atlas.alpha_composite(layer,(0,96*(phase+(8 if critical else 0))))
    return atlas


if __name__=='__main__':
    OUT.mkdir(exist_ok=True,parents=True)
    strips={
        'rift_health':['#8ae8ac','#24c870','#24c870','#20bc64','#1ca558','#147443'],
        'rift_wounded':['#fff0a2','#eac640','#eac640','#ddb335','#bb8f2d','#866125'],
        'rift_critical':['#ffb3b3','#e95b64','#e95b64','#d84551','#b93746','#812d38']}
    for name,colors in strips.items():hp(colors).save(OUT/(name+'.png'))
    lights().save(OUT/'rift_crown_flair.png')
    for name in ['rift_crown','rift_crown_flair',*strips]:
        (OUT/(name+'.png.mcmeta')).write_text('{"texture":{"blur":false,"clamp":true}}\n')
    print('Rift Crown: original imported art, 189x6 health strips, 16 native 360x96 light frames')
