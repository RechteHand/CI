#include "tetris.h"

#include <fcntl.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <termios.h>
#include <time.h>
#include <unistd.h>

static struct termios g_original_termios;
static int g_original_flags = 0;
static int g_term_active = 0;

static void sleep_ms(long ms) {
    struct timespec req;
    req.tv_sec = ms / 1000;
    req.tv_nsec = (ms % 1000) * 1000000L;
    nanosleep(&req, NULL);
}

static void restore_terminal(void) {
    if (!g_term_active) {
        return;
    }
    tcsetattr(STDIN_FILENO, TCSANOW, &g_original_termios);
    fcntl(STDIN_FILENO, F_SETFL, g_original_flags);
    printf("\x1b[0m\x1b[?25h");
    fflush(stdout);
    g_term_active = 0;
}

static void handle_signal(int sig) {
    (void)sig;
    restore_terminal();
    _exit(1);
}

static int setup_terminal(void) {
    struct termios raw;
    if (tcgetattr(STDIN_FILENO, &g_original_termios) != 0) {
        return -1;
    }
    g_original_flags = fcntl(STDIN_FILENO, F_GETFL, 0);
    if (g_original_flags < 0) {
        return -1;
    }

    raw = g_original_termios;
    raw.c_lflag &= (tcflag_t)~(ICANON | ECHO);
    raw.c_cc[VMIN] = 0;
    raw.c_cc[VTIME] = 0;
    if (tcsetattr(STDIN_FILENO, TCSANOW, &raw) != 0) {
        return -1;
    }
    if (fcntl(STDIN_FILENO, F_SETFL, g_original_flags | O_NONBLOCK) != 0) {
        tcsetattr(STDIN_FILENO, TCSANOW, &g_original_termios);
        return -1;
    }

    g_term_active = 1;
    printf("\x1b[2J\x1b[H\x1b[?25l");
    fflush(stdout);
    return 0;
}

static const char *cell_color(uint8_t cell) {
    switch (cell) {
        case 1:
            return "\x1b[36m";
        case 2:
            return "\x1b[33m";
        case 3:
            return "\x1b[35m";
        case 4:
            return "\x1b[32m";
        case 5:
            return "\x1b[31m";
        case 6:
            return "\x1b[34m";
        case 7:
            return "\x1b[37m";
        default:
            return "\x1b[0m";
    }
}

static void render(const TetrisGame *game) {
    uint8_t board[TETRIS_BOARD_HEIGHT][TETRIS_BOARD_WIDTH];
    int y;
    int x;

    tetris_get_board_with_piece(game, board);
    printf("\x1b[H");
    printf("Tetris (ANSI, plain C)\n");
    printf("Score: %-8u Lines: %-6u Pieces: %-6u\n", game->score, game->total_lines, game->pieces_placed);
    printf("Hold: %-2s  Next: %s %s %s %s %s\n",
           tetris_piece_name(game->hold),
           tetris_piece_name(game->next[0]),
           tetris_piece_name(game->next[1]),
           tetris_piece_name(game->next[2]),
           tetris_piece_name(game->next[3]),
           tetris_piece_name(game->next[4]));
    printf("Controls: a/d move, s soft drop, w/z rotate, space hard drop, c hold, q quit\n\n");

    printf("+");
    for (x = 0; x < TETRIS_BOARD_WIDTH * 2; x++) {
        printf("-");
    }
    printf("+\n");

    for (y = 0; y < TETRIS_BOARD_HEIGHT; y++) {
        printf("|");
        for (x = 0; x < TETRIS_BOARD_WIDTH; x++) {
            uint8_t cell = board[y][x];
            if (cell == 0U) {
                printf("  ");
            } else {
                printf("%s[]\x1b[0m", cell_color(cell));
            }
        }
        printf("|\n");
    }

    printf("+");
    for (x = 0; x < TETRIS_BOARD_WIDTH * 2; x++) {
        printf("-");
    }
    printf("+\n");
    fflush(stdout);
}

static TetrisAction map_key(unsigned char c, int *quit) {
    if (c == 'a' || c == 'A') {
        return TETRIS_ACTION_LEFT;
    }
    if (c == 'd' || c == 'D') {
        return TETRIS_ACTION_RIGHT;
    }
    if (c == 's' || c == 'S') {
        return TETRIS_ACTION_SOFT_DROP;
    }
    if (c == 'w' || c == 'W' || c == 'x' || c == 'X') {
        return TETRIS_ACTION_ROTATE_CW;
    }
    if (c == 'z' || c == 'Z') {
        return TETRIS_ACTION_ROTATE_CCW;
    }
    if (c == 'c' || c == 'C') {
        return TETRIS_ACTION_HOLD;
    }
    if (c == ' ') {
        return TETRIS_ACTION_HARD_DROP;
    }
    if (c == 'q' || c == 'Q') {
        *quit = 1;
    }
    return TETRIS_ACTION_NONE;
}

static void handle_input(TetrisGame *game, int *quit) {
    unsigned char c;
    ssize_t n;

    for (;;) {
        n = read(STDIN_FILENO, &c, 1);
        if (n <= 0) {
            break;
        }
        if (c == 27) {
            unsigned char seq[2];
            ssize_t n1 = read(STDIN_FILENO, &seq[0], 1);
            ssize_t n2 = read(STDIN_FILENO, &seq[1], 1);
            if (n1 == 1 && n2 == 1 && seq[0] == '[') {
                if (seq[1] == 'A') {
                    tetris_apply_action(game, TETRIS_ACTION_ROTATE_CW);
                } else if (seq[1] == 'B') {
                    tetris_apply_action(game, TETRIS_ACTION_SOFT_DROP);
                } else if (seq[1] == 'C') {
                    tetris_apply_action(game, TETRIS_ACTION_RIGHT);
                } else if (seq[1] == 'D') {
                    tetris_apply_action(game, TETRIS_ACTION_LEFT);
                }
            }
            continue;
        }

        {
            TetrisAction action = map_key(c, quit);
            if (action != TETRIS_ACTION_NONE) {
                tetris_apply_action(game, action);
            }
        }
    }
}

int main(int argc, char **argv) {
    TetrisGame game;
    uint32_t seed = (uint32_t)time(NULL);
    int gravity = 12;
    int lock_delay = 18;
    int quit = 0;
    int i;

    for (i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--seed") == 0 && i + 1 < argc) {
            seed = (uint32_t)strtoul(argv[++i], NULL, 10);
        } else if (strcmp(argv[i], "--gravity") == 0 && i + 1 < argc) {
            gravity = atoi(argv[++i]);
        } else if (strcmp(argv[i], "--lock-delay") == 0 && i + 1 < argc) {
            lock_delay = atoi(argv[++i]);
        } else if (strcmp(argv[i], "--help") == 0) {
            printf("Usage: %s [--seed N] [--gravity N] [--lock-delay N]\n", argv[0]);
            return 0;
        } else {
            fprintf(stderr, "Unknown option: %s\n", argv[i]);
            return 1;
        }
    }

    if (setup_terminal() != 0) {
        fprintf(stderr, "Failed to configure terminal.\n");
        return 1;
    }

    atexit(restore_terminal);
    signal(SIGINT, handle_signal);
    signal(SIGTERM, handle_signal);

    tetris_init(&game, seed);
    tetris_set_timing(&game, gravity, lock_delay);

    while (!quit && !game.game_over) {
        handle_input(&game, &quit);
        tetris_tick(&game);
        render(&game);
        sleep_ms(50);
    }

    render(&game);
    if (quit) {
        printf("Exited.\n");
    } else {
        printf("Game over. Final score: %u, lines: %u\n", game.score, game.total_lines);
    }
    return 0;
}
