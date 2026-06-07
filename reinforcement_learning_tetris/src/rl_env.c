#include "rl_env.h"

typedef struct {
    int aggregate_height;
    int max_height;
    int holes;
    int bumpiness;
    int used_columns;
    int partial_row_gaps;
    int row_fill_score;
} BoardStats;

static uint8_t clamp_u8(int value, int max_value) {
    if (value < 0) {
        return 0U;
    }
    if (value > max_value) {
        return (uint8_t)max_value;
    }
    return (uint8_t)value;
}

static int abs_int(int value) {
    return value < 0 ? -value : value;
}

static BoardStats board_stats(const TetrisGame *game) {
    BoardStats stats = {0, 0, 0, 0, 0, 0, 0};
    int heights[TETRIS_BOARD_WIDTH] = {0};
    int y;
    int x;

    for (x = 0; x < TETRIS_BOARD_WIDTH; x++) {
        int y;
        int seen_block = 0;

        for (y = 0; y < TETRIS_BOARD_HEIGHT; y++) {
            if (game->board[y][x] != 0U) {
                if (!seen_block) {
                    heights[x] = TETRIS_BOARD_HEIGHT - y;
                    seen_block = 1;
                }
            } else if (seen_block) {
                stats.holes++;
            }
        }

        stats.aggregate_height += heights[x];
        if (heights[x] > 0) {
            stats.used_columns++;
        }
        if (heights[x] > stats.max_height) {
            stats.max_height = heights[x];
        }
    }

    for (x = 0; x < TETRIS_BOARD_WIDTH - 1; x++) {
        stats.bumpiness += abs_int(heights[x] - heights[x + 1]);
    }

    for (y = 0; y < TETRIS_BOARD_HEIGHT; y++) {
        int filled = 0;
        for (x = 0; x < TETRIS_BOARD_WIDTH; x++) {
            if (game->board[y][x] != 0U) {
                filled++;
            }
        }
        if (filled > 0 && filled < TETRIS_BOARD_WIDTH) {
            stats.partial_row_gaps += (TETRIS_BOARD_WIDTH - filled);
        }
        stats.row_fill_score += filled * filled;
    }

    return stats;
}

static void accumulate_step(TetrisStepInfo *total, TetrisStepInfo step) {
    total->lines_cleared += step.lines_cleared;
    total->piece_locked = total->piece_locked || step.piece_locked;
    total->game_over = total->game_over || step.game_over;
}

static TetrisStepInfo run_placement_action(TetrisGame *game, int action_index) {
    TetrisStepInfo total = {0, 0, 0};
    int target_rotation;
    int target_x;
    int move_count;

    if (action_index < 0 || action_index >= RL_ACTION_COUNT) {
        return total;
    }

    target_rotation = action_index / RL_ACTION_COLUMNS;
    target_x = RL_ACTION_X_MIN + (action_index % RL_ACTION_COLUMNS);

    for (move_count = 0; move_count < RL_ACTION_ROTATIONS + 2 && !total.piece_locked && !total.game_over; move_count++) {
        int before_rotation;
        TetrisStepInfo step;
        if (game->rotation == target_rotation) {
            break;
        }
        before_rotation = game->rotation;
        step = tetris_apply_action(game, TETRIS_ACTION_ROTATE_CW);
        accumulate_step(&total, step);
        if (game->rotation == before_rotation) {
            break;
        }
    }

    for (move_count = 0; move_count < TETRIS_BOARD_WIDTH + 4 && !total.piece_locked && !total.game_over; move_count++) {
        int before_x;
        TetrisStepInfo step;
        TetrisAction move_action;
        if (game->x == target_x) {
            break;
        }
        move_action = (game->x < target_x) ? TETRIS_ACTION_RIGHT : TETRIS_ACTION_LEFT;
        before_x = game->x;
        step = tetris_apply_action(game, move_action);
        accumulate_step(&total, step);
        if (game->x == before_x) {
            break;
        }
    }

    if (!total.piece_locked && !total.game_over) {
        accumulate_step(&total, tetris_apply_action(game, TETRIS_ACTION_HARD_DROP));
    }
    return total;
}

void rl_env_init(RLEnv *env, uint32_t seed) {
    tetris_init(&env->game, seed);
    tetris_set_timing(&env->game, 1, 4);
    env->last_lines_cleared = 0;
    env->step_count = 0;
    env->step_limit = 3000;
}

void rl_env_reset(RLEnv *env, uint32_t seed) {
    tetris_reset(&env->game, seed);
    tetris_set_timing(&env->game, 1, 4);
    env->last_lines_cleared = 0;
    env->step_count = 0;
}

int rl_action_count(void) {
    return RL_ACTION_COUNT;
}

TetrisAction rl_action_from_index(int action_index) {
    (void)action_index;
    return TETRIS_ACTION_NONE;
}

RLStateFeatures rl_env_features(const RLEnv *env) {
    RLStateFeatures features;
    BoardStats stats = board_stats(&env->game);

    features.max_height = clamp_u8(stats.max_height, 15);
    features.holes = clamp_u8(stats.holes, 31);
    features.bumpiness = clamp_u8(stats.bumpiness, 31);
    features.cleared_last = clamp_u8(env->last_lines_cleared, 7);
    features.current_piece = (env->game.current == TETROMINO_NONE) ? 7U : (uint8_t)env->game.current;
    features.next_piece = (uint8_t)env->game.next[0];
    features.hold_piece = (env->game.hold == TETROMINO_NONE) ? 7U : (uint8_t)env->game.hold;
    features.can_hold = env->game.can_hold ? 1U : 0U;
    features.piece_x = clamp_u8(env->game.x + 2, 31);
    features.piece_y = clamp_u8(env->game.y + 4, 31);
    features.piece_rotation = clamp_u8(env->game.rotation, 3);
    return features;
}

double rl_env_step(RLEnv *env, int action_index, int *done) {
    TetrisStepInfo action_step;
    BoardStats before = board_stats(&env->game);
    BoardStats after;
    int total_lines_cleared;
    int terminal;
    double reward;

    action_step = run_placement_action(&env->game, action_index);

    total_lines_cleared = action_step.lines_cleared;
    after = board_stats(&env->game);
    env->last_lines_cleared = total_lines_cleared;
    env->step_count++;

    reward = 0.0;
    switch (total_lines_cleared) {
        case 1:
            reward += 150.0;
            break;
        case 2:
            reward += 360.0;
            break;
        case 3:
            reward += 640.0;
            break;
        case 4:
            reward += 1100.0;
            break;
        default:
            break;
    }
    reward += (double)(before.aggregate_height - after.aggregate_height) * 1.00;
    reward += (double)(before.holes - after.holes) * 18.00;
    reward += (double)(before.bumpiness - after.bumpiness) * 1.25;
    reward += (double)(before.partial_row_gaps - after.partial_row_gaps) * 1.10;
    reward += (double)(after.row_fill_score - before.row_fill_score) * 0.05;

    reward -= (double)after.holes * 4.00;
    reward -= (double)after.aggregate_height * 0.25;
    reward -= (double)after.bumpiness * 0.15;
    reward -= (double)after.max_height * 1.80;
    if (after.max_height >= 16) {
        reward -= (double)(after.max_height - 15) * 14.0;
    }

    terminal = action_step.game_over;
    if (env->step_count >= env->step_limit) {
        terminal = 1;
    }
    if (action_step.game_over) {
        reward -= 450.0;
    }

    if (done != 0) {
        *done = terminal;
    }
    return reward;
}

int rl_env_heuristic_action(const RLEnv *env) {
    int action_index;
    int best_action = 0;
    int initialized = 0;
    double best_reward = 0.0;

    for (action_index = 0; action_index < RL_ACTION_COUNT; action_index++) {
        RLEnv sim = *env;
        int done = 0;
        double reward = rl_env_step(&sim, action_index, &done);

        if (!initialized || reward > best_reward) {
            best_reward = reward;
            best_action = action_index;
            initialized = 1;
        }
    }

    return best_action;
}
