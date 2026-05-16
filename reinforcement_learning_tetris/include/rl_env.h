#ifndef RL_ENV_H
#define RL_ENV_H

#include <stdint.h>
#include "tetris.h"

#define RL_ACTION_ROTATIONS 4
#define RL_ACTION_X_MIN (-2)
#define RL_ACTION_X_MAX (TETRIS_BOARD_WIDTH - 1)
#define RL_ACTION_COLUMNS (RL_ACTION_X_MAX - RL_ACTION_X_MIN + 1)
#define RL_ACTION_COUNT (RL_ACTION_COLUMNS * RL_ACTION_ROTATIONS)

typedef struct {
    uint8_t max_height;
    uint8_t holes;
    uint8_t bumpiness;
    uint8_t cleared_last;
    uint8_t current_piece;
    uint8_t next_piece;
    uint8_t hold_piece;
    uint8_t can_hold;
    uint8_t piece_x;
    uint8_t piece_y;
    uint8_t piece_rotation;
} RLStateFeatures;

typedef struct {
    TetrisGame game;
    int last_lines_cleared;
    uint32_t step_count;
    uint32_t step_limit;
} RLEnv;

void rl_env_init(RLEnv *env, uint32_t seed);
void rl_env_reset(RLEnv *env, uint32_t seed);
RLStateFeatures rl_env_features(const RLEnv *env);
double rl_env_step(RLEnv *env, int action_index, int *done);
int rl_action_count(void);
TetrisAction rl_action_from_index(int action_index);
int rl_env_heuristic_action(const RLEnv *env);

#endif
