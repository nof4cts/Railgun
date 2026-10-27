"""Blockstates, models, lang, sounds.json and data files. Run from the project root."""
import json, os
A = 'src/main/resources/assets/ironlung'
D = 'src/main/resources/data/ironlung'
def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f: json.dump(obj, f, indent=2)

M = 'ironlung:block/'
FACING_Y = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
blocks = []

for b in ['hull_plate', 'hull_riveted', 'hull_seam', 'bolted_panel', 'grate_floor']:
    w(f'{A}/models/block/{b}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': M + b}})
    w(f'{A}/blockstates/{b}.json', {'variants': {'': {'model': M + b}}})
    w(f'{A}/models/item/{b}.json', {'parent': M + b}); blocks.append(b)

w(f'{A}/models/block/warning_plate.json', {'parent': 'minecraft:block/orientable',
  'textures': {'front': M + 'warning_plate', 'side': M + 'hull_plate', 'top': M + 'hull_plate'}})
w(f'{A}/blockstates/warning_plate.json', {'variants': {f'facing={f}': {'model': M + 'warning_plate', 'y': r} for f, r in FACING_Y.items()}})
w(f'{A}/models/item/warning_plate.json', {'parent': M + 'warning_plate'}); blocks.append('warning_plate')

# pipe
faces_side = {'texture': '#side', 'uv': [4, 0, 12, 16]}
w(f'{A}/models/block/pipe.json', {'parent': 'minecraft:block/block', 'textures': {'side': M + 'pipe', 'end': M + 'pipe_end', 'particle': M + 'pipe'},
  'elements': [
    {'from': [4, 0, 4], 'to': [12, 16, 12], 'faces': {
        'north': faces_side, 'south': faces_side, 'east': faces_side, 'west': faces_side,
        'up': {'texture': '#end', 'uv': [4, 4, 12, 12]}, 'down': {'texture': '#end', 'uv': [4, 4, 12, 12]}}},
    {'from': [3, 1, 3], 'to': [13, 3, 13], 'faces': {k: {'texture': '#side', 'uv': [0, 0, 16, 2]} for k in ['north', 'south', 'east', 'west']} | {'up': {'texture': '#end'}, 'down': {'texture': '#end'}}},
    {'from': [3, 13, 3], 'to': [13, 15, 13], 'faces': {k: {'texture': '#side', 'uv': [0, 0, 16, 2]} for k in ['north', 'south', 'east', 'west']} | {'up': {'texture': '#end'}, 'down': {'texture': '#end'}}},
  ]})
w(f'{A}/blockstates/pipe.json', {'variants': {'axis=y': {'model': M + 'pipe'}, 'axis=z': {'model': M + 'pipe', 'x': 90}, 'axis=x': {'model': M + 'pipe', 'x': 90, 'y': 90}}})
w(f'{A}/models/item/pipe.json', {'parent': M + 'pipe'}); blocks.append('pipe')

# cage lamp
for s in ('on', 'off'):
    w(f'{A}/models/block/cage_lamp_{s}.json', {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
      'textures': {'lamp': M + f'cage_lamp_{s}', 'stem': M + 'console_side', 'particle': M + 'console_side'},
      'elements': [
        {'from': [4.5, 6, 4.5], 'to': [11.5, 13, 11.5], 'shade': False, 'faces': {k: {'texture': '#lamp', 'uv': [0, 0, 16, 16]} for k in ['north', 'south', 'east', 'west', 'up', 'down']}},
        {'from': [7, 13, 7], 'to': [9, 16, 9], 'faces': {k: {'texture': '#stem', 'uv': [7, 0, 9, 3]} for k in ['north', 'south', 'east', 'west', 'up', 'down']}},
        {'from': [5, 15, 5], 'to': [11, 16, 11], 'faces': {k: {'texture': '#stem'} for k in ['north', 'south', 'east', 'west', 'up', 'down']}},
      ]})
w(f'{A}/blockstates/cage_lamp.json', {'variants': {'lit=true': {'model': M + 'cage_lamp_on'}, 'lit=false': {'model': M + 'cage_lamp_off'}}})
w(f'{A}/models/item/cage_lamp.json', {'parent': M + 'cage_lamp_on'}); blocks.append('cage_lamp')

# controls
controls = {'helm': True, 'camera': True, 'chart': False, 'breaker': True, 'ballast': True, 'pressure_gauge': True,
            'depth_gauge': True, 'proximity': True, 'sealant': False, 'hatch': False}
for c, powerable in controls.items():
    side = M + ('hull_plate' if c == 'hatch' else 'console_side')
    top = M + ('hull_plate' if c == 'hatch' else 'console_top')
    if powerable:
        for s in ('on', 'off'):
            w(f'{A}/models/block/{c}_{s}.json', {'parent': 'minecraft:block/orientable', 'textures': {'front': M + f'{c}_{s}', 'side': side, 'top': top}})
        variants = {f'facing={f},powered={p}': {'model': M + f'{c}_{"on" if p == "true" else "off"}', 'y': r}
                    for f, r in FACING_Y.items() for p in ('true', 'false')}
        w(f'{A}/models/item/{c}.json', {'parent': M + f'{c}_on'})
    else:
        w(f'{A}/models/block/{c}.json', {'parent': 'minecraft:block/orientable', 'textures': {'front': M + c, 'side': side, 'top': top}})
        variants = {f'facing={f},powered={p}': {'model': M + c, 'y': r} for f, r in FACING_Y.items() for p in ('true', 'false')}
        w(f'{A}/models/item/{c}.json', {'parent': M + c})
    w(f'{A}/blockstates/{c}.json', {'variants': variants}); blocks.append(c)

w(f'{A}/models/item/dive_orders.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'ironlung:item/dive_orders'}})

# ---------------- lang
names = {'hull_plate': 'Rusted Hull Plate', 'hull_riveted': 'Riveted Hull Plate', 'hull_seam': 'Welded Hull Seam',
         'bolted_panel': 'Bolted Access Panel', 'grate_floor': 'Deck Grating', 'warning_plate': 'Warning Plate', 'pipe': 'Corroded Pipe',
         'cage_lamp': 'Cage Lamp', 'helm': 'Helm Console', 'camera': 'Camera Monitor', 'chart': 'Survey Chart', 'breaker': 'Power Breaker',
         'ballast': 'Ballast Controls', 'pressure_gauge': 'Pressure Gauge', 'depth_gauge': 'Depth Gauge', 'proximity': 'Proximity Panel',
         'sealant': 'Sealant Station', 'hatch': 'Sealed Hatch'}
lang = {f'block.ironlung.{k}': v for k, v in names.items()}
lang.update({
    'item.ironlung.dive_orders': 'Dive Orders',
    'item.ironlung.dive_orders.desc': 'Sealed survey orders. Use to begin a dive.',
    'itemGroup.ironlung': 'Iron Lung',
    'screen.ironlung.helm': 'Helm', 'screen.ironlung.camera': 'Camera', 'screen.ironlung.chart': 'Survey Chart',
    'screen.ironlung.summary': 'Dive Report',
    'screen.ironlung.surface': 'SURFACE', 'screen.ironlung.abort': 'ABORT DIVE', 'screen.ironlung.abort_confirm': 'CONFIRM ABORT',
    'ironlung.msg.dead_console': 'The instrument is dead.',
    'ironlung.msg.already_diving': 'You are already on a dive.',
    'ironlung.msg.no_power': 'NO POWER',
    'ironlung.msg.recharging': 'Flash recharging...',
    'ironlung.msg.all_logged': 'ALL SITES LOGGED. Surface from the chart when ready.',
    'ironlung.msg.incomplete': 'The survey is not complete.',
    'ironlung.msg.ascending': 'Ballast blown. Ascending.',
    'ironlung.msg.breaker_stuck': 'The breaker will not move.',
    'ironlung.msg.breaker_reset': 'Breaker closed. Power restoring...',
    'ironlung.msg.breaker_busy': 'Power is already restoring.',
    'ironlung.msg.breaker_ok': 'Breaker closed. Power nominal.',
    'ironlung.msg.gauge_dead': 'The needle rests at zero.',
    'ironlung.msg.pressure': 'PRESSURE %s ATM    HULL %s%%',
    'ironlung.msg.depth': 'DEPTH %s m    KEEL CLEARANCE %s m    RATED %s m',
    'ironlung.msg.proximity': 'FORE %s   STBD %s   AFT %s   PORT %s',
    'ironlung.msg.weld_start': 'Welding the seam. Stay at the station.',
    'ironlung.msg.weld_busy': 'Already welding.',
    'ironlung.msg.weld_empty': 'Sealant reservoir is empty.',
    'ironlung.msg.weld_none': 'No breaches found.',
    'ironlung.msg.weld_abandoned': 'Weld abandoned.',
    'ironlung.msg.weld_done': 'Seam sealed. %s charges remain.',
    'ironlung.msg.hatch0': 'The hatch is welded shut from the outside.',
    'ironlung.msg.hatch1': 'The metal is cold. Colder than the rest.',
    'ironlung.msg.hatch2': 'There is no handle on this side.',
    'ironlung.msg.hatch3': 'You hear nothing. You listen anyway.',
    'ironlung.cmd.not_diving': 'You are not on a dive.',
    'death.attack.ironlung.crushed': '%1$s was crushed by the deep',
    'death.attack.ironlung.crushed.player': '%1$s was crushed by the deep',
    'biome.ironlung.blood_sea': 'Blood Sea',
})
for k in ['ambient_drone', 'engine_hum', 'electric_buzz', 'hull_groan', 'hull_creak', 'hull_knock', 'hull_impact', 'hull_scrape',
          'pressure_hiss', 'power_down', 'power_up', 'breaker_clunk', 'camera_shutter', 'camera_develop', 'camera_servo',
          'proximity_ping', 'alarm', 'drip', 'weld', 'leviathan', 'static_burst', 'helm_click', 'implosion', 'distant_wail', 'hatch_knock']:
    lang[f'subtitles.ironlung.{k}'] = {
        'ambient_drone': 'The deep hums', 'engine_hum': 'Motor labours', 'electric_buzz': 'Wiring buzzes', 'hull_groan': 'Hull groans',
        'hull_creak': 'Metal creaks', 'hull_knock': 'Something knocks', 'hull_impact': 'Hull struck', 'hull_scrape': 'Something scrapes the hull',
        'pressure_hiss': 'Pressure hisses', 'power_down': 'Power fails', 'power_up': 'Power returns', 'breaker_clunk': 'Breaker thrown',
        'camera_shutter': 'Shutter fires', 'camera_develop': 'Film winds', 'camera_servo': 'Camera turns', 'proximity_ping': 'Proximity alarm',
        'alarm': 'Alarm sounds', 'drip': 'Something drips', 'weld': 'Torch sputters', 'leviathan': '...', 'static_burst': 'Static',
        'helm_click': 'Switch clicks', 'implosion': 'Hull collapses', 'distant_wail': 'Distant call', 'hatch_knock': 'Knocking at the hatch'}[k]
w(f'{A}/lang/en_us.json', lang)

# ---------------- data
w(f'{D}/dimension_type/iron_lung.json', {
    'ultrawarm': False, 'natural': False, 'coordinate_scale': 1.0, 'has_skylight': False, 'has_ceiling': True,
    'ambient_light': 0.0, 'fixed_time': 18000, 'monster_spawn_light_level': 0, 'monster_spawn_block_light_limit': 0,
    'piglin_safe': False, 'bed_works': False, 'respawn_anchor_works': False, 'has_raids': False,
    'logical_height': 256, 'min_y': 0, 'height': 256, 'infiniburn': '#minecraft:infiniburn_overworld', 'effects': 'ironlung:iron_lung'})
w(f'{D}/dimension/iron_lung.json', {'type': 'ironlung:iron_lung', 'generator': {'type': 'minecraft:flat', 'settings': {
    'biome': 'ironlung:blood_sea', 'layers': [], 'lakes': False, 'features': False, 'structure_overrides': []}}})
w(f'{D}/worldgen/biome/blood_sea.json', {
    'has_precipitation': False, 'temperature': 0.5, 'downfall': 0.0,
    'effects': {'fog_color': 0x140000, 'sky_color': 0, 'water_color': 0x3A0000, 'water_fog_color': 0x1A0000},
    'spawners': {}, 'spawn_costs': {}, 'carvers': {}, 'features': []})
w(f'{D}/damage_type/crushed.json', {'message_id': 'ironlung.crushed', 'scaling': 'never', 'exhaustion': 0.0})
for b in blocks:
    w(f'{D}/loot_tables/blocks/{b}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'ironlung:{b}'}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
w(f'{D}/recipes/dive_orders.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
    'ingredients': [{'item': 'minecraft:paper'}, {'item': 'minecraft:iron_ingot'}, {'item': 'minecraft:redstone'}, {'item': 'minecraft:ink_sac'}],
    'result': {'item': 'ironlung:dive_orders'}})
w('src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json', {'replace': False, 'values': [f'ironlung:{b}' for b in blocks]})
print('data ok', len(blocks), 'blocks')
