#ifndef TETRIS_H
#define TETRIS_H

#include <stdint.h>

#define TETRIS_BOARD_WIDTH 10
#define TETRIS_BOARD_HEIGHT 20
#define TETRIS_NEXT_QUEUE 5

typedef enum {
    TETROMINO_I = 0,
    TETROMINO_O = 1,
    TETROMINO_T = 2,
    TETROMINO_S = 3,
    TETROMINO_Z = 4,
    TETROMINO_J = 5,
    TETROMINO_L = 6,
    TETROMINO_NONE = 255
} Tetromino;

typedef enum {
    TETRIS_ACTION_NONE = 0,
    TETRIS_ACTION_LEFT = 1,
    TETRIS_ACTION_RIGHT = 2,
    TETRIS_ACTION_ROTATE_CW = 3,
    TETRIS_ACTION_ROTATE_CCW = 4,
    TETRIS_ACTION_SOFT_DROP = 5,
    TETRIS_ACTION_HARD_DROP = 6,
    TETRIS_ACTION_HOLD = 7
} TetrisAction;

typedef struct {
    int lines_cleared;
    int piece_locked;
    int game_over;
} TetrisStepInfo;

typedef struct {
    uint8_t board[TETRIS_BOARD_HEIGHT][TETRIS_BOARD_WIDTH];

    Tetromino current;
    int rotation;
    int x;
    int y;

    Tetromino hold;
    uint8_t can_hold;
    Tetromino next[TETRIS_NEXT_QUEUE];

    uint8_t bag[7];
    int bag_index;
    uint32_t rng_state;

    uint32_t score;
    uint32_t total_lines;
    uint32_t pieces_placed;

    int gravity_frames;
    int gravity_counter;
    int lock_delay_frames;
    int lock_delay_counter;

    uint8_t game_over;
} TetrisGame;

void tetris_init(TetrisGame *game, uint32_t seed);
void tetris_reset(TetrisGame *game, uint32_t seed);
void tetris_set_timing(TetrisGame *game, int gravity_frames, int lock_delay_frames);
TetrisStepInfo tetris_apply_action(TetrisGame *game, TetrisAction action);
TetrisStepInfo tetris_tick(TetrisGame *game);
void tetris_get_board_with_piece(const TetrisGame *game, uint8_t out[TETRIS_BOARD_HEIGHT][TETRIS_BOARD_WIDTH]);
const char *tetris_piece_name(Tetromino piece);

#endif
