#!/usr/bin/env python3
import json
import os
import re
import shutil
import tempfile


class ModelError(Exception):
    pass


MOB_ID_RE = re.compile(r'^[a-z][a-z0-9_]*$')
JAVA_IDENT_RE = re.compile(r'^[A-Za-z_][A-Za-z0-9_]*$')
FIT_RE = re.compile(r'^\s*([0-9.]+)\s*[x,]\s*([0-9.]+)\s*$')


class Project:
    def __init__(self, root):
        self.root = os.path.abspath(root)
        self.art_dir = os.path.join(self.root, 'art')
        self.stats_path = os.path.join(self.art_dir, 'stats.json')
        self.art_path = os.path.join(self.art_dir, 'art.json')

    def load_stats(self):
        with open(self.stats_path, 'r', encoding='utf-8') as f:
            return json.load(f)

    def load_art(self):
        with open(self.art_path, 'r', encoding='utf-8') as f:
            return json.load(f)

    def save_stats(self, stats):
        # Validate
        errors = self.validate_stats(stats)
        if errors:
            raise ModelError(errors)
        # Atomic save with backup
        self._atomic_save(self.stats_path, stats)

    def save_art(self, art):
        """Atomically write art/art.json, keeping a .bak of the previous file."""
        self._atomic_save(self.art_path, art)

    def _atomic_save(self, path, data):
        # Create backup
        if os.path.exists(path):
            backup_path = path + '.bak'
            shutil.copy2(path, backup_path)
        # Write to temp file in same dir
        dirpath = os.path.dirname(path)
        fd, tmp_path = tempfile.mkstemp(dir=dirpath, prefix='.tmp_', suffix='.json')
        try:
            os.close(fd)
            with open(tmp_path, 'w', encoding='utf-8') as f:
                json.dump(data, f, indent=2)
                f.write('\n')
            # mkstemp creates 0600 files; keep the permissions the JSON
            # files had before (or a sane default for a shared checkout).
            mode = 0o644
            if os.path.exists(path):
                mode = os.stat(path).st_mode & 0o777
            os.chmod(tmp_path, mode)
            os.replace(tmp_path, path)
        except Exception:
            try:
                os.unlink(tmp_path)
            except Exception:
                pass
            raise

    def validate_stats(self, stats):
        errors = []
        try:
            player = stats.get('player', {})
            maxHp = player.get('maxHp')
            attack = player.get('attack')
            hurtInvuln = player.get('hurtInvuln')
            if not isinstance(maxHp, int) or isinstance(maxHp, bool) or maxHp < 1 or maxHp > 9999:
                errors.append({'field': 'player.maxHp', 'message': 'must be int 1..9999'})
            if not isinstance(attack, int) or isinstance(attack, bool) or attack < 0 or attack > 9999:
                errors.append({'field': 'player.attack', 'message': 'must be int 0..9999'})
            if not isinstance(hurtInvuln, (int, float)) or isinstance(hurtInvuln, bool) or hurtInvuln < 0 or hurtInvuln > 5:
                errors.append({'field': 'player.hurtInvuln', 'message': 'must be float 0..5'})
        except Exception as e:
            errors.append({'field': 'player', 'message': str(e)})

        try:
            mobs = stats.get('mobs', [])
            seen_ids = set()
            for i, mob in enumerate(mobs):
                mid = mob.get('id')
                if not isinstance(mid, str) or not MOB_ID_RE.match(mid):
                    errors.append({'field': f'mobs[{i}].id', 'message': 'must match ^[a-z][a-z0-9_]*$'})
                elif mid in seen_ids:
                    errors.append({'field': f'mobs[{i}].id', 'message': 'duplicate id'})
                else:
                    seen_ids.add(mid)
                for key in ['maxHp']:
                    val = mob.get(key)
                    if not isinstance(val, int) or isinstance(val, bool) or val < 1 or val > 9999:
                        errors.append({'field': f'mobs[{i}].{key}', 'message': 'must be int 1..9999'})
                for key in ['attack']:
                    val = mob.get(key)
                    if not isinstance(val, int) or isinstance(val, bool) or val < 0 or val > 9999:
                        errors.append({'field': f'mobs[{i}].{key}', 'message': 'must be int 0..9999'})
                for key in ['speed']:
                    val = mob.get(key)
                    if not isinstance(val, (int, float)) or isinstance(val, bool) or val < 0 or val > 2000:
                        errors.append({'field': f'mobs[{i}].{key}', 'message': 'must be float 0..2000'})
                for key in ['width', 'height']:
                    val = mob.get(key)
                    if not isinstance(val, (int, float)) or isinstance(val, bool) or val < 4 or val > 512:
                        errors.append({'field': f'mobs[{i}].{key}', 'message': 'must be float 4..512'})
        except Exception as e:
            errors.append({'field': 'mobs', 'message': str(e)})

        return errors

    def get_entities(self, stats, art):
        entities = []
        # Player
        player_stats = stats.get('player', {})
        player_asset = self._find_asset_for_entity(art, 'player')
        entities.append({
            'id': 'player',
            'kind': 'player',
            'name': 'Player',
            'stats': {
                'maxHp': player_stats.get('maxHp'),
                'attack': player_stats.get('attack'),
                'hurtInvuln': player_stats.get('hurtInvuln'),
            },
            'asset': player_asset
        })
        # Mobs
        for mob in stats.get('mobs', []):
            asset = self._find_asset_for_entity(art, mob.get('id'))
            entities.append({
                'id': mob.get('id'),
                'kind': 'mob',
                'name': mob.get('name'),
                'stats': {
                    'id': mob.get('id'),
                    'name': mob.get('name'),
                    'maxHp': mob.get('maxHp'),
                    'attack': mob.get('attack'),
                    'speed': mob.get('speed'),
                    'width': mob.get('width'),
                    'height': mob.get('height'),
                },
                'asset': asset
            })
        return entities

    def _find_asset_for_entity(self, art, entity_id):
        assets = art.get('assets', []) if isinstance(art, dict) else []
        for asset in assets:
            if isinstance(asset, dict) and asset.get('entity') == entity_id:
                # Return a copy of relevant fields
                return {
                    'src': asset.get('src'),
                    'class': asset.get('class'),
                    'fit': asset.get('fit'),
                    'anchor': asset.get('anchor'),
                    'precision': asset.get('precision'),
                    'entity': asset.get('entity'),
                }
        return None

    def update_asset(self, art_data, entity, src, class_name, fit, anchor, precision):
        errors = []
        if src is not None:
            # validate src is repo-relative .svg that exists
            if not isinstance(src, str) or not src.endswith('.svg'):
                errors.append({'field': 'src', 'message': 'must be repo-relative .svg'})
            else:
                full_path = os.path.join(self.root, src)
                if not os.path.exists(full_path):
                    errors.append({'field': 'src', 'message': 'file does not exist'})
                elif os.path.commonpath([os.path.abspath(full_path), self.root]) != self.root:
                    errors.append({'field': 'src', 'message': 'path escapes the project root'})
            if not isinstance(class_name, str) or not JAVA_IDENT_RE.match(class_name):
                errors.append({'field': 'class', 'message': 'must be Java identifier'})
            if not isinstance(fit, str) or not FIT_RE.match(fit):
                errors.append({'field': 'fit', 'message': 'must match WxH'})
            if anchor not in ['bottom-center', 'bottom-left', 'bottom-right', 'center', 'top-left']:
                errors.append({'field': 'anchor', 'message': 'invalid anchor'})
            # svg2java.py reads precision with int(); a null must never reach
            # the manifest, and the generator only accepts 0..6.
            if precision is None:
                precision = 2
            if not isinstance(precision, int) or isinstance(precision, bool) \
                    or precision < 0 or precision > 6:
                errors.append({'field': 'precision', 'message': 'must be int 0..6'})
        if errors:
            raise ModelError(errors)

        # Make a copy preserving unknown keys
        art = dict(art_data) if isinstance(art_data, dict) else {}
        # Ensure reserved keys not clobbered
        assets = list(art.get('assets', [])) if isinstance(art.get('assets'), list) else []
        # Remove existing asset for this entity
        new_assets = []
        for a in assets:
            if isinstance(a, dict) and a.get('entity') == entity:
                continue  # remove
            new_assets.append(dict(a))
        if src is not None:
            asset = {
                'src': src,
                'class': class_name,
                'fit': fit,
                'anchor': anchor,
                'precision': precision,
                'entity': entity,
            }
            new_assets.append(asset)
        art['assets'] = new_assets
        # Keep package/outDir if they exist
        if 'package' not in art and 'package' in art_data:
            art['package'] = art_data['package']
        if 'outDir' not in art and 'outDir' in art_data:
            art['outDir'] = art_data['outDir']
        return art

    def list_svgs(self):
        svgs = []
        for root, dirs, files in os.walk(self.root):
            # Skip hidden dirs and common build dirs
            dirs[:] = [d for d in dirs if not d.startswith('.') and d not in ['build', '__pycache__']]
            for fname in files:
                if fname.endswith('.svg'):
                    full = os.path.join(root, fname)
                    rel = os.path.relpath(full, self.root)
                    # Only include files under root, avoid going outside? rel should be fine
                    if not rel.startswith('..'):
                        svgs.append(rel)
        svgs.sort()
        if len(svgs) > 500:
            svgs = svgs[:500]
        return svgs
