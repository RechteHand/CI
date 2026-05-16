# Blackjack NN (Plain C) — Implementation Plan

Build a standalone C project in `black_jack_nn` that first simulates Blackjack hands and prints ASCII state snapshots, while adding a neural-network scaffold (no training yet).

## Locked scope

- Plain C only, no external ML libraries
- Rules: dealer stands on soft 17, no surrender, no split modeling yet
- State fields:
  - player total
  - usable ace flag
  - dealer upcard
  - terminal/bust flags
  - Hi-Lo running count
  - true count estimate
- Output mode: streamed states from simulated hand lifecycle (deal/play/dealer/resolve)
- Player policy: basic-strategy heuristic
- Shoe: 6 decks
- Reshuffle at 75% penetration
- Build: `Makefile` with `make`, `make run`, `make test`
- Testing now: runtime demo only
- Include NN scaffold now (state encoder + model structs/functions)

## Approach

1. Create project skeleton
   - `include/`, `src/`, `main.c`, `Makefile`
2. Implement Blackjack core
   - shoe/deck representation
   - Hi-Lo and true count tracking
   - hand scoring with usable ace
   - dealer S17 + reshuffle logic
3. Implement policy + state model
   - basic-strategy hit/stand logic
   - canonical state struct
4. Implement ASCII renderer
   - print readable state snapshots for each round step
5. Add NN scaffold
   - model/layer structs
   - initialization
   - forward-path placeholder + state vector encoder
6. Wire simulation in main
   - run configurable number of rounds with chosen defaults

## Execution todos

1. `setup-c-project` — create C skeleton and Makefile
2. `implement-blackjack-engine` — implement shoe/hand/round flow/counting
3. `implement-basic-strategy` — implement decision logic
4. `implement-state-ascii-output` — implement state struct + renderer
5. `add-nn-scaffold` — add NN structs + forward scaffold
6. `wire-main-simulation` — connect simulation loop and CLI defaults

## Notes

- First milestone is readable, correct ASCII state output from simulated hands.
- Training/backprop is intentionally deferred to the next milestone.
