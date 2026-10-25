#!/usr/bin/env python3
import os
import subprocess
import sys
import threading
import time


class BuildRunner:
    def __init__(self, project_root):
        self.project_root = os.path.abspath(project_root)
        self.lock = threading.Lock()
        self.state = 'idle'  # idle, running, done, failed
        self.steps = []
        self.progress = 0.0
        self.message = ''
        self.exitCode = None
        self.startedAt = None
        self.finishedAt = None
        self._thread = None

    def snapshot(self):
        with self.lock:
            return {
                'state': self.state,
                'steps': [dict(s) for s in self.steps],
                'progress': float(self.progress),
                'message': self.message,
                'exitCode': self.exitCode,
                'startedAt': self.startedAt,
                'finishedAt': self.finishedAt,
            }

    def start(self):
        with self.lock:
            if self.state == 'running':
                raise RuntimeError('already running')
            # Reset
            self.state = 'running'
            self.steps = [
                {'name': 'stats', 'state': 'pending', 'log': ''},
                {'name': 'art', 'state': 'pending', 'log': ''},
            ]
            self.progress = 0.0
            self.message = ''
            self.exitCode = None
            self.startedAt = time.time()
            self.finishedAt = None
        t = threading.Thread(target=self._run, daemon=True)
        self._thread = t
        t.start()
        return True

    def _run(self):
        try:
            # Step 1: stats
            self._run_step(0, [sys.executable, 'tools/statgen/stats2java.py', 'art/stats.json'])
            if self.state == 'failed':
                return
            # Step 2: art
            self._run_step(1, [sys.executable, 'tools/artgen/svg2java.py', '--manifest', 'art/art.json'])
            if self.state == 'failed':
                return
            with self.lock:
                self.state = 'done'
                self.message = 'done'
                self.progress = 1.0
                self.finishedAt = time.time()
        except Exception as e:
            with self.lock:
                self.state = 'failed'
                self.message = str(e)
                self.exitCode = -1
                self.finishedAt = time.time()

    def _run_step(self, idx, cmd):
        with self.lock:
            self.steps[idx]['state'] = 'running'
            self.steps[idx]['log'] = ''
        env = os.environ.copy()
        try:
            proc = subprocess.Popen(
                cmd,
                cwd=self.project_root,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                env=env,
                text=True,
                bufsize=1,
            )
        except Exception as e:
            with self.lock:
                self.steps[idx]['state'] = 'failed'
                self.steps[idx]['log'] = str(e)
                self.state = 'failed'
                self.message = f'{self.steps[idx]["name"]} failed'
                self.exitCode = -1
                self.finishedAt = time.time()
            return

        log_lines = []
        expected_assets = self._expected_asset_count()
        arrow_count = 0
        while True:
            line = proc.stdout.readline()
            if line == '' and proc.poll() is not None:
                break
            if line:
                log_lines.append(line)
                if ' -> ' in line:
                    arrow_count += 1
                with self.lock:
                    self.steps[idx]['log'] = ''.join(log_lines)
                # Update partial progress
                self._update_progress(idx, arrow_count, expected_assets)
            else:
                time.sleep(0.001)

        rc = proc.returncode
        with self.lock:
            self.steps[idx]['log'] = ''.join(log_lines)
        if rc != 0:
            with self.lock:
                self.steps[idx]['state'] = 'failed'
                self.state = 'failed'
                self.message = f'{self.steps[idx]["name"]} exited {rc}'
                self.exitCode = rc
                self.finishedAt = time.time()
            return
        else:
            with self.lock:
                self.steps[idx]['state'] = 'done'
            self._update_progress(idx, arrow_count, expected_assets, finished=True)

    def _expected_asset_count(self):
        try:
            import json
            art_path = os.path.join(self.project_root, 'art', 'art.json')
            with open(art_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
            assets = data.get('assets', [])
            if isinstance(assets, list):
                return len(assets)
        except Exception:
            pass
        return None

    def _update_progress(self, step_idx, arrow_count, expected_assets, finished=False):
        with self.lock:
            if finished:
                # Finished step counts fully
                step_contribution = 1.0
            else:
                if expected_assets is not None and expected_assets > 0:
                    step_contribution = min(arrow_count / expected_assets, 0.99)
                else:
                    step_contribution = 0.5
            # Progress is mean over 2 steps
            # Compute based on current step states
            total = 0.0
            for i, s in enumerate(self.steps):
                if s['state'] == 'done':
                    total += 1.0
                elif s['state'] == 'running' and i == step_idx:
                    if finished:
                        total += 1.0
                    else:
                        if expected_assets is not None and expected_assets > 0:
                            total += min(arrow_count / expected_assets, 0.99)
                        else:
                            total += 0.5
                elif s['state'] == 'running':
                    # keep previous estimate
                    pass
                else:
                    total += 0.0
            self.progress = total / 2.0


def create_runner(root):
    return BuildRunner(root)
