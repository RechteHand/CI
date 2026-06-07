#include "tetris.h"

#include <string.h>

static const int8_t PIECE_CELLS[7][4][4][2] = {
    {
        {{0, 1}, {1, 1}, {2, 1}, {3, 1}},
        {{2, 0}, {2, 1}, {2, 2}, {2, 3}},
        {{0, 2}, {1, 2}, {2, 2}, {3, 2}},
        {{1, 0}, {1, 1}, {1, 2}, {1, 3}}
    },
    {
        {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
        {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
        {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
        {{1, 0}, {2, 0}, {1, 1}, {2, 1}}
    },
    {
        {{1, 0}, {0, 1}, {1, 1}, {2, 1}},
        {{1, 0}, {1, 1}, {2, 1}, {1, 2}},
        {{0, 1}, {1, 1}, {2, 1}, {1, 2}},
        {{1, 0}, {0, 1}, {1, 1}, {1, 2}}
    },
    {
        {{1, 0}, {2, 0}, {0, 1}, {1, 1}},
        {{0, 0}, {0, 1}, {1, 1}, {1, 2}},
        {{1, 1}, {2, 1}, {0, 2}, {1, 2}},
        {{1, 0}, {1, 1}, {2, 1}, {2, 2}}
    },
    {
        {{0, 0}, {1, 0}, {1, 1}, {2, 1}},
        {{1, 0}, {0, 1}, {1, 1}, {0, 2}},
        {{0, 1}, {1, 1}, {1, 2}, {2, 2}},
        {{2, 0}, {1, 1}, {2, 1}, {1, 2}}
    },
    {
        {{0, 0}, {0, 1}, {1, 1}, {2, 1}},
        {{1, 0}, {2, 0}, {1, 1}, {1, 2}},
        {{0, 1}, {1, 1}, {2, 1}, {2, 2}},
        {{1, 0}, {1, 1}, {0, 2}, {1, 2}}
    },
    {
        {{2, 0}, {0, 1}, {1, 1}, {2, 1}},
        {{1, 0}, {1, 1}, {1, 2}, {2, 2}},
        {{0, 1}, {1, 1}, {2, 1}, {0, 2}},
        {{0, 0}, {1, 0}, {1, 1}, {1, 2}}
    }
};

static const int8_t JLSTZ_KICKS_CW[4][5][2] = {
    {{0, 0}, {-1, 0}, {-1, 1}, {0, -2}, {-1, -2}},
    {{0, 0}, {1, 0}, {1, -1}, {0, 2}, {1, 2}},
    {{0, 0}, {1, 0}, {1, 1}, {0, -2}, {1, -2}},
    {{0, 0}, {-1, 0}, {-1, -1}, {0, 2}, {-1, 2}}
};

static const int8_t JLSTZ_KICKS_CCW[4][5][2] = {
    {{0, 0}, {1, 0}, {1, 1}, {0, -2}, {1, -2}},
    {{0, 0}, {1, 0}, {1, -1}, {0, 2}, {1, 2}},
    {{0, 0}, {-1, 0}, {-1, 1}, {0, -2}, {-1, -2}},
    {{0, 0}, {-1, 0}, {-1, -1}, {0, 2}, {-1, 2}}
};

static const int8_t I_KICKS_CW[4][5][2] = {
    {{0, 0}, {-2, 0}, {1, 0}, {-2, -1}, {1, 2}},
    {{0, 0}, {-1, 0}, {2, 0}, {-1, 2}, {2, -1}},
    {{0, 0}, {2, 0}, {-1, 0}, {2, 1}, {-1, -2}},
    {{0, 0}, {1, 0}, {-2, 0}, {1, -2}, {-2, 1}}
};

static const int8_t I_KICKS_CCW[4][5][2] = {
    {{0, 0}, {-1, 0}, {2, 0}, {-1, 2}, {2, -1}},
    {{0, 0}, {2, 0}, {-1, 0}, {2, 1}, {-1, -2}},
    {{0, 0}, {1, 0}, {-2, 0}, {1, -2}, {-2, 1}},
    {{0, 0}, {-2, 0}, {1, 0}, {-2, -1}, {1, 2}}
};

static uint32_t rng_next(uint32_t *state) {
    uint32_t x = *state;
    if (x == 0U) {
        x = 0x6D2B79F5U;
    }
    x ^= x << 13;
    x ^= x >> 17;
    x ^= x << 5;
    *state = x;
    return x;
}

static int random_bounded(uint32_t *state, int limit) {
    return (int)(rng_next(state) % (uint32_t)limit);
}

static int piece_collision(const TetrisGame *game, Tetromino piece, int rotation, int px, int py) {
    int i;
    for (i = 0; i < 4; i++) {
        int bx = px + PIECE_CELLS[piece][rotation][i][0];
        int by = py + PIECE_CELLS[piece][rotation][i][1];
        if (bx < 0 || bx >= TETRIS_BOARD_WIDTH || by >= TETRIS_BOARD_HEIGHT) {
            return 1;
        }
        if (by >= 0 && game->board[by][bx] != 0U) {
            return 1;
        }
    }
    return 0;
}

static int is_grounded(const TetrisGame *game) {
    return piece_collision(game, game->current, game->rotation, game->x, game->y + 1);
}

static int try_move(TetrisGame *game, int dx, int dy) {
    int nx = game->x + dx;
    int ny = game->y + dy;
    if (piece_collision(game, game->current, game->rotation, nx, ny)) {
        return 0;
    }
    game->x = nx;
    game->y = ny;
    return 1;
}

static int try_rotate(TetrisGame *game, int cw) {
    int next_rotation;
    int from;
    int i;
    const int8_t (*kicks)[2];

    if (game->current == TETROMINO_O) {
        return 1;
    }

    from = game->rotation;
    next_rotation = cw ? (from + 1) % 4 : (from + 3) % 4;

    if (game->current == TETROMINO_I) {
        kicks = cw ? I_KICKS_CW[from] : I_KICKS_CCW[from];
    } else {
        kicks = cw ? JLSTZ_KICKS_CW[from] : JLSTZ_KICKS_CCW[from];
    }

    for (i = 0; i < 5; i++) {
        int nx = game->x + kicks[i][0];
        int ny = game->y - kicks[i][1];
        if (!piece_collision(game, game->current, next_rotation, nx, ny)) {
            game->rotation = next_rotation;
            game->x = nx;
            game->y = ny;
            return 1;
        }
    }
    return 0;
}

static void refill_bag(TetrisGame *game) {
    int i;
    for (i = 0; i < 7; i++) {
        game->bag[i] = (uint8_t)i;
    }
    for (i = 6; i > 0; i--) {
        int j = random_bounded(&game->rng_state, i + 1);
        uint8_t tmp = game->bag[i];
        game->bag[i] = game->bag[j];
        game->bag[j] = tmp;
    }
    game->bag_index = 0;
}

static Tetromino draw_piece(TetrisGame *game) {
    if (game->bag_index >= 7) {
        refill_bag(game);
    }
    return (Tetromino)game->bag[game->bag_index++];
}

static Tetromino pop_next_piece(TetrisGame *game) {
    Tetromino piece = game->next[0];
    int i;
    for (i = 0; i < TETRIS_NEXT_QUEUE - 1; i++) {
        game->next[i] = game->next[i + 1];
    }
    game->next[TETRIS_NEXT_QUEUE - 1] = draw_piece(game);
    return piece;
}

static int spawn_piece(TetrisGame *game, Tetromino piece) {
    game->current = piece;
    game->rotation = 0;
    game->x = 3;
    game->y = -1;
    game->gravity_counter = 0;
    game->lock_delay_counter = game->lock_delay_frames;
    if (piece_collision(game, game->current, game->rotation, game->x, game->y)) {
        game->game_over = 1;
        return 0;
    }
    return 1;
}

static int spawn_next_piece(TetrisGame *game) {
    return spawn_piece(game, pop_next_piece(game));
}

static int clear_lines(TetrisGame *game) {
    int y;
    int write = TETRIS_BOARD_HEIGHT - 1;
    int lines = 0;
    for (y = TETRIS_BOARD_HEIGHT - 1; y >= 0; y--) {
        int x;
        int full = 1;
        for (x = 0; x < TETRIS_BOARD_WIDTH; x++) {
            if (game->board[y][x] == 0U) {
                full = 0;
                break;
            }
        }
        if (full) {
            lines++;
            continue;
        }
        if (write != y) {
            memcpy(game->board[write], game->board[y], TETRIS_BOARD_WIDTH);
        }
        write--;
    }

    while (write >= 0) {
        memset(game->board[write], 0, TETRIS_BOARD_WIDTH);
        write--;
    }
    return lines;
}

static void add_line_clear_score(TetrisGame *game, int lines) {
    switch (lines) {
        case 1:
            game->score += 100U;
            break;
        case 2:
            game->score += 300U;
            break;
        case 3:
            game->score += 500U;
            break;
        case 4:
            game->score += 800U;
            break;
        default:
            break;
    }
}

static TetrisStepInfo lock_current_piece(TetrisGame *game) {
    TetrisStepInfo step = {0, 1, 0};
    int i;
    for (i = 0; i < 4; i++) {
        int bx = game->x + PIECE_CELLS[game->current][game->rotation][i][0];
        int by = game->y + PIECE_CELLS[game->current][game->rotation][i][1];
        if (by < 0) {
            game->game_over = 1;
            step.game_over = 1;
            return step;
        }
        game->board[by][bx] = (uint8_t)(game->current + 1);
    }

    game->pieces_placed++;
    game->can_hold = 1;
    step.lines_cleared = clear_lines(game);
    game->total_lines += (uint32_t)step.lines_cleared;
    add_line_clear_score(game, step.lines_cleared);

    if (!spawn_next_piece(game)) {
        game->game_over = 1;
    }
    step.game_over = game->game_over ? 1 : 0;
    return step;
}

void tetris_init(TetrisGame *game, uint32_t seed) {
    tetris_reset(game, seed);
}

void tetris_reset(TetrisGame *game, uint32_t seed) {
    int i;
    memset(game, 0, sizeof(*game));
    game->rng_state = (seed == 0U) ? 1U : seed;
    game->hold = TETROMINO_NONE;
    game->can_hold = 1;
    game->gravity_frames = 12;
    game->lock_delay_frames = 18;
    game->bag_index = 7;

    for (i = 0; i < TETRIS_NEXT_QUEUE; i++) {
        game->next[i] = draw_piece(game);
    }
    spawn_next_piece(game);
}

void tetris_set_timing(TetrisGame *game, int gravity_frames, int lock_delay_frames) {
    game->gravity_frames = gravity_frames < 1 ? 1 : gravity_frames;
    game->lock_delay_frames = lock_delay_frames < 0 ? 0 : lock_delay_frames;
    if (game->lock_delay_counter > game->lock_delay_frames) {
        game->lock_delay_counter = game->lock_delay_frames;
    }
}

TetrisStepInfo tetris_apply_action(TetrisGame *game, TetrisAction action) {
    TetrisStepInfo step = {0, 0, 0};
    int moved = 0;

    if (game->game_over) {
        step.game_over = 1;
        return step;
    }

    switch (action) {
        case TETRIS_ACTION_LEFT:
            moved = try_move(game, -1, 0);
            break;
        case TETRIS_ACTION_RIGHT:
            moved = try_move(game, 1, 0);
            break;
        case TETRIS_ACTION_ROTATE_CW:
            moved = try_rotate(game, 1);
            break;
        case TETRIS_ACTION_ROTATE_CCW:
            moved = try_rotate(game, 0);
            break;
        case TETRIS_ACTION_SOFT_DROP:
            if (try_move(game, 0, 1)) {
                game->score += 1U;
            } else {
                if (game->lock_delay_counter > 0) {
                    game->lock_delay_counter--;
                }
                if (game->lock_delay_counter <= 0) {
                    return lock_current_piece(game);
                }
            }
            break;
        case TETRIS_ACTION_HARD_DROP: {
            int drop_distance = 0;
            while (try_move(game, 0, 1)) {
                drop_distance++;
            }
            game->score += (uint32_t)(drop_distance * 2);
            return lock_current_piece(game);
        }
        case TETRIS_ACTION_HOLD:
            if (game->can_hold) {
                Tetromino previous_hold = game->hold;
                game->hold = game->current;
                game->can_hold = 0;
                if (previous_hold == TETROMINO_NONE) {
                    spawn_next_piece(game);
                } else {
                    spawn_piece(game, previous_hold);
                }
            }
            break;
        case TETRIS_ACTION_NONE:
        default:
            break;
    }

    if (moved) {
        if (is_grounded(game)) {
            game->lock_delay_counter = game->lock_delay_frames;
        }
    }

    step.game_over = game->game_over ? 1 : 0;
    return step;
}

TetrisStepInfo tetris_tick(TetrisGame *game) {
    TetrisStepInfo step = {0, 0, 0};

    if (game->game_over) {
        step.game_over = 1;
        return step;
    }

    game->gravity_counter++;
    if (game->gravity_counter < game->gravity_frames) {
        return step;
    }
    game->gravity_counter = 0;

    if (try_move(game, 0, 1)) {
        game->lock_delay_counter = game->lock_delay_frames;
        return step;
    }

    if (game->lock_delay_counter > 0) {
        game->lock_delay_counter--;
    }
    if (game->lock_delay_counter <= 0) {
        return lock_current_piece(game);
    }

    step.game_over = game->game_over ? 1 : 0;
    return step;
}

void tetris_get_board_with_piece(const TetrisGame *game, uint8_t out[TETRIS_BOARD_HEIGHT][TETRIS_BOARD_WIDTH]) {
    int i;
    memcpy(out, game->board, sizeof(game->board));
    if (game->game_over || game->current == TETROMINO_NONE) {
        return;
    }
    for (i = 0; i < 4; i++) {
        int bx = game->x + PIECE_CELLS[game->current][game->rotation][i][0];
        int by = game->y + PIECE_CELLS[game->current][game->rotation][i][1];
        if (bx >= 0 && bx < TETRIS_BOARD_WIDTH && by >= 0 && by < TETRIS_BOARD_HEIGHT) {
            out[by][bx] = (uint8_t)(game->current + 1);
        }
    }
}

const char *tetris_piece_name(Tetromino piece) {
    switch (piece) {
        case TETROMINO_I:
            return "I";
        case TETROMINO_O:
            return "O";
        case TETROMINO_T:
            return "T";
        case TETROMINO_S:
            return "S";
        case TETROMINO_Z:
            return "Z";
        case TETROMINO_J:
            return "J";
        case TETROMINO_L:
            return "L";
        case TETROMINO_NONE:
        default:
            return "-";
    }
}
