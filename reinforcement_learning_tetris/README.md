# Tetris RL in Plain C

This project contains:

- A reusable Tetris engine (`src/tetris.c`)
- A ANSI terminal frontend (`bin/play`)
- A RL environment wrapper (`src/rl_env.c`)
- A tabular Q-learning trainer with save/load (`bin/train`)

## Build

```bash
make
```

## Play Tetris

```bash
make run-play
# or:
./bin/play --seed 123 --gravity 12 --lock-delay 18
```

Controls:

- `a` / `d`: move left/right
- `s`: soft drop
- `w` / `z`: rotate CW/CCW
- `space`: hard drop
- `c`: hold
- `q`: quit
- Arrow keys also work (left/right/down/up)

## Train Tabular Q-learning

```bash
./bin/train --episodes 5000 --seed 1 --save qtable.bin
```

Useful options:

- `--episodes N`
- `--step-limit N` (max piece placements per episode)
- `--alpha X`
- `--gamma X`
- `--epsilon X`
- `--epsilon-min X`
- `--epsilon-decay X`
- `--eval-episodes N`
- `--visual` (render periodic greedy-policy gameplay checkpoints)
- `--visual-every N` (default 500)
- `--visual-delay-ms N` (default 25)
- `--realtime-train` (render every training step while learning; much slower)
- `--realtime-delay-ms N` (default 25)
- `--load PATH`
- `--save PATH`

If you trained with an older build, start fresh without `--load` once (state encoding/model format changed to improve placement quality).

Example quick run (1k episodes):

```bash
./bin/train --episodes 1000 --save qtable.bin
```

Example visual training run (watch policy improve over checkpoints):

```bash
./bin/train --episodes 5000 --visual --visual-every 500 --visual-delay-ms 20 --eval-episodes 20 --save qtable.bin
```

Example real-time training run (shows the actual learning episodes step-by-step):

```bash
./bin/train --episodes 100 --realtime-train --realtime-delay-ms 30 --eval-episodes 5 --save qtable.bin
```

## Notes

- Board: 10x20
- Tetromino RNG: 7-bag with seed support
- Rotation: SRS-style kicks
- Gameplay includes hold, next queue, and lock delay
- RL actions are piece-placement decisions (target rotation + target column)
- RL objective includes line-clear rewards (with extra bonus for multi-line clears) plus board-quality shaping (height, holes/open fields, bumpiness, and width usage)
