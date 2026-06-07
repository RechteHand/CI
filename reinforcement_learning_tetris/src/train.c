#include "rl_env.h"

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

/**
 * @brief Represents a single state-action entry in the Q-table.
 * 
 * Maps a unique state hash (`key`) to the Q-values of all possible actions
 * from that state. `used` serves as a quick flag for empty slot detection 
 * in the open-addressing hash table.
 */
typedef struct {
    uint64_t key;
    float q[RL_ACTION_COUNT];
    uint8_t used;
} QEntry;

/**
 * @brief Stores the full state-action space via an open-addressing hash table.
 * 
 * Capacity must always be a power of two to allow fast bitwise modulo operations.
 */
typedef struct {
    QEntry *entries;
    size_t capacity;
    size_t size;
} QTable;

/**
 * @brief Configuration parameters for parsing command line arguments and
 * directing the Q-learning episode loops.
 */
typedef struct {
    uint32_t episodes;
    uint32_t seed;
    uint32_t step_limit;
    uint32_t eval_episodes;
    uint32_t visual_every;
    uint32_t visual_delay_ms;
    uint32_t realtime_delay_ms;
    double alpha;
    double gamma;
    double epsilon;
    double epsilon_min;
    double epsilon_decay;
    int visual;
    int realtime_train;
    const char *load_path;
    const char *save_path;
} TrainConfig;

/* Global RNG state for pseudo-random deterministic number generation */
static uint32_t g_rng = 1U;

/**
 * @brief Generates the next deterministic 32-bit integer via xorshift algorithm.
 * Best practice choice for game loops/AI where performance bounds are tight 
 * compared to standard heavy PRNG implementations. 
 */
static uint32_t rng_next(void) {
    uint32_t x = g_rng;
    if (x == 0U) {
        x = 0xA341316CU;
    }
    x ^= x << 13;
    x ^= x >> 17;
    x ^= x << 5;
    g_rng = x;
    return x;
}

/**
 * @brief Returns a uniform double precision float between 0.0 and 1.0.
 */
static double rng_uniform(void) {
    return (double)(rng_next() >> 8) * (1.0 / 16777216.0);
}

/**
 * @brief Returns a random integer bounded within [0, limit-1].
 */
static int rng_int(int limit) {
    return (int)(rng_next() % (uint32_t)limit);
}

/**
 * @brief Pauses execution for a specified number of milliseconds.
 * Useful for visual rendering pacing.
 */
static void sleep_ms(long ms) {
    struct timespec req;
    req.tv_sec = ms / 1000;
    req.tv_nsec = (ms % 1000) * 1000000L;
    nanosleep(&req, NULL);
}

/**
 * @brief Integer hash function (using SplitMix64). Provides strong avalanche 
 * properties to uniquely scatter bit features across the QTable. 
 * Prevents clustering of structurally similar Tetris states.
 */
static uint64_t hash_u64(uint64_t x) {
    x += 0x9E3779B97F4A7C15ULL;
    x = (x ^ (x >> 30)) * 0xBF58476D1CE4E5B9ULL;
    x = (x ^ (x >> 27)) * 0x94D049BB133111EBULL;
    return x ^ (x >> 31);
}

/**
 * @brief Computes the next power of two for a given size.
 * Useful to ensure Q-table capacity works seamlessly with bitwise logical AND (`& (capacity - 1)`).
 */
static size_t next_power_of_two(size_t n) {
    size_t p = 1;
    while (p < n) {
        p <<= 1;
    }
    return p;
}

/**
 * @brief Allocates heap memory for the Q-table up to `capacity_hint` items.
 */
static int qtable_init(QTable *table, size_t capacity_hint) {
    table->capacity = next_power_of_two(capacity_hint);
    table->size = 0;
    table->entries = (QEntry *)calloc(table->capacity, sizeof(QEntry));
    return table->entries == NULL ? -1 : 0;
}

/**
 * @brief Frees the Q-table and invalidates object references to prevent use-after-free
 * or memory-leak issues.
 */
static void qtable_free(QTable *table) {
    free(table->entries);
    table->entries = NULL;
    table->capacity = 0;
    table->size = 0;
}

/**
 * @brief Internal probing to find exactly where to place an entry 
 * (Linear Probing for hash collisions handling).
 */
static QEntry *qtable_find_slot(QEntry *entries, size_t capacity, uint64_t key) {
    size_t mask = capacity - 1;
    size_t idx = (size_t)(hash_u64(key) & mask);
    while (entries[idx].used && entries[idx].key != key) {
        idx = (idx + 1) & mask;
    }
    return &entries[idx];
}

/**
 * @brief Resizes and re-hashes the Q-table contents once load factor bounds are crossed.
 */
static int qtable_grow(QTable *table) {
    size_t i;
    size_t new_capacity = table->capacity * 2;
    QEntry *new_entries = (QEntry *)calloc(new_capacity, sizeof(QEntry));
    if (new_entries == NULL) {
        return -1;
    }

    for (i = 0; i < table->capacity; i++) {
        if (table->entries[i].used) {
            QEntry *slot = qtable_find_slot(new_entries, new_capacity, table->entries[i].key);
            *slot = table->entries[i];
        }
    }
    free(table->entries);
    table->entries = new_entries;
    table->capacity = new_capacity;
    return 0;
}

/**
 * @brief Fetches a pre-existing state pointer or initializes a fresh zero-ed entry.
 * It will allocate larger memory pools if table capacity limits 70% threshold.
 */
static QEntry *qtable_get_or_create(QTable *table, uint64_t key) {
    QEntry *entry;
    if ((table->size + 1) * 10 > table->capacity * 7) {
        if (qtable_grow(table) != 0) {
            return NULL;
        }
    }
    entry = qtable_find_slot(table->entries, table->capacity, key);
    if (!entry->used) {
        memset(entry, 0, sizeof(*entry));
        entry->used = 1;
        entry->key = key;
        table->size++;
    }
    return entry;
}

/**
 * @brief Read-only query for a QEntry struct given a hash key. Returns NULL if not found.
 */
static const QEntry *qtable_get(const QTable *table, uint64_t key) {
    size_t mask = table->capacity - 1;
    size_t idx = (size_t)(hash_u64(key) & mask);
    while (table->entries[idx].used) {
        if (table->entries[idx].key == key) {
            return &table->entries[idx];
        }
        idx = (idx + 1) & mask;
    }
    return NULL;
}

/**
 * @brief Determines the action yielding the absolute highest Q-value given a loaded State.
 * Includes explicit tie-breaking using random uniform choice preventing biased repetition loops.
 */
static int best_action_from_entry(const QEntry *entry) {
    int a;
    int best_action = 0;
    float best_value = entry->q[0];
    int ties = 1;
    for (a = 1; a < RL_ACTION_COUNT; a++) {
        float value = entry->q[a];
        if (value > best_value + 1e-6f) {
            best_value = value;
            best_action = a;
            ties = 1;
        } else if (value >= best_value - 1e-6f && value <= best_value + 1e-6f) {
            ties++;
            if (rng_int(ties) == 0) {
                best_action = a;
            }
        }
    }
    return best_action;
}

/**
 * @brief Obtains the highest continuous Q-value for estimating the Temporal Difference (TD) target.
 */
static float max_q(const QEntry *entry) {
    int a;
    float best = entry->q[0];
    for (a = 1; a < RL_ACTION_COUNT; a++) {
        if (entry->q[a] > best) {
            best = entry->q[a];
        }
    }
    return best;
}

/**
 * @brief Resolves CLI output colors given a specific cell byte mapping.
 */
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

/**
 * @brief Renders the visual ASCII state representation of the Tetris board in terminal.
 */
static void render_visual_frame(const TetrisGame *game,
                                uint32_t episode,
                                uint32_t step,
                                double reward,
                                double epsilon,
                                size_t states) {
    uint8_t board[TETRIS_BOARD_HEIGHT][TETRIS_BOARD_WIDTH];
    int y;
    int x;

    tetris_get_board_with_piece(game, board);
    printf("\x1b[H");
    printf("Visual RL checkpoint\n");
    printf("Episode: %-8u Step: %-6u Epsilon: %-7.4f States: %-7zu\n", episode, step, epsilon, states);
    printf("Score: %-8u Lines: %-6u Pieces: %-6u Reward: %9.2f\n",
           game->score,
           game->total_lines,
           game->pieces_placed,
           reward);
    printf("Hold: %-2s  Next: %s %s %s %s %s\n",
           tetris_piece_name(game->hold),
           tetris_piece_name(game->next[0]),
           tetris_piece_name(game->next[1]),
           tetris_piece_name(game->next[2]),
           tetris_piece_name(game->next[3]),
           tetris_piece_name(game->next[4]));

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

/**
 * @brief Compress RL-State objects explicitly into bit fields. 
 * Condensing features to a 64-bit integer acts essentially as the immutable State Hash key.
 */
static uint64_t features_to_key(RLStateFeatures f) {
    uint64_t key = 0;
    key = (key << 4) | (uint64_t)(f.max_height & 0x0FU);
    key = (key << 5) | (uint64_t)(f.holes & 0x1FU);
    key = (key << 5) | (uint64_t)(f.bumpiness & 0x1FU);
    key = (key << 3) | (uint64_t)(f.cleared_last & 0x07U);
    key = (key << 3) | (uint64_t)(f.current_piece & 0x07U);
    key = (key << 3) | (uint64_t)(f.next_piece & 0x07U);
    key = (key << 3) | (uint64_t)(f.hold_piece & 0x07U);
    key = (key << 1) | (uint64_t)(f.can_hold & 0x01U);
    key = (key << 5) | (uint64_t)(f.piece_x & 0x1FU);
    key = (key << 5) | (uint64_t)(f.piece_y & 0x1FU);
    key = (key << 2) | (uint64_t)(f.piece_rotation & 0x03U);
    return key;
}

/**
 * @brief General purpose wrapper for reading CLI uint parameters securely handling edge conditions.
 */
static int parse_uint32(const char *s, uint32_t *out) {
    char *end = NULL;
    unsigned long v = strtoul(s, &end, 10);
    if (end == s || *end != '\0') {
        return -1;
    }
    *out = (uint32_t)v;
    return 0;
}

/**
 * @brief General purpose wrapper for reading CLI float parameters dynamically.
 */
static int parse_double(const char *s, double *out) {
    char *end = NULL;
    double v = strtod(s, &end);
    if (end == s || *end != '\0') {
        return -1;
    }
    *out = v;
    return 0;
}

/**
 * @brief Display helper manual flag instructions to CLI environment.
 */
static void print_usage(const char *argv0) {
    printf("Usage: %s [options]\n", argv0);
    printf("Options:\n");
    printf("  --episodes N        Training episodes (default 5000)\n");
    printf("  --seed N            RNG seed (default 1)\n");
    printf("  --step-limit N      Max piece placements per episode (default 3000)\n");
    printf("  --alpha X           Learning rate (default 0.10)\n");
    printf("  --gamma X           Discount factor (default 0.99)\n");
    printf("  --epsilon X         Initial epsilon (default 0.35)\n");
    printf("  --epsilon-min X     Minimum epsilon (default 0.02)\n");
    printf("  --epsilon-decay X   Per-episode decay (default 0.9995)\n");
    printf("  --eval-episodes N   Greedy eval episodes (default 30)\n");
    printf("  --visual            Show periodic greedy policy gameplay during training\n");
    printf("  --visual-every N    Visual checkpoint frequency (default 500)\n");
    printf("  --visual-delay-ms N Delay per rendered step in ms (default 25)\n");
    printf("  --realtime-train    Render every training step (actual learning episodes)\n");
    printf("  --realtime-delay-ms N Delay per rendered training step in ms (default 25)\n");
    printf("  --load PATH         Load Q-table before training\n");
    printf("  --save PATH         Save Q-table after training (default qtable.bin)\n");
}

/**
 * @brief Parse argument mappings configuring execution scope parameter sizes.
 */
static int parse_args(int argc, char **argv, TrainConfig *cfg) {
    int i;
    cfg->episodes = 5000;
    cfg->seed = 1;
    cfg->step_limit = 3000;
    cfg->eval_episodes = 30;
    cfg->visual_every = 500;
    cfg->visual_delay_ms = 25;
    cfg->realtime_delay_ms = 25;
    cfg->alpha = 0.10;
    cfg->gamma = 0.99;
    cfg->epsilon = 0.35;
    cfg->epsilon_min = 0.02;
    cfg->epsilon_decay = 0.9995;
    cfg->visual = 0;
    cfg->realtime_train = 0;
    cfg->load_path = NULL;
    cfg->save_path = "qtable.bin";

    for (i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--episodes") == 0 && i + 1 < argc) {
            if (parse_uint32(argv[++i], &cfg->episodes) != 0) {
                return -1;
            }
        } else if (strcmp(argv[i], "--seed") == 0 && i + 1 < argc) {
            if (parse_uint32(argv[++i], &cfg->seed) != 0) {
                return -1;
            }
        } else if (strcmp(argv[i], "--step-limit") == 0 && i + 1 < argc) {
            if (parse_uint32(argv[++i], &cfg->step_limit) != 0) {
                return -1;
            }
        } else if (strcmp(argv[i], "--eval-episodes") == 0 && i + 1 < argc) {
            if (parse_uint32(argv[++i], &cfg->eval_episodes) != 0) {
                return -1;
            }
        } else if (strcmp(argv[i], "--visual") == 0) {
            cfg->visual = 1;
        } else if (strcmp(argv[i], "--visual-every") == 0 && i + 1 < argc) {
            if (parse_uint32(argv[++i], &cfg->visual_every) != 0 || cfg->visual_every == 0U) {
                return -1;
            }
        } else if (strcmp(argv[i], "--visual-delay-ms") == 0 && i + 1 < argc) {
            if (parse_uint32(argv[++i], &cfg->visual_delay_ms) != 0) {
                return -1;
            }
        } else if (strcmp(argv[i], "--realtime-train") == 0) {
            cfg->realtime_train = 1;
        } else if (strcmp(argv[i], "--realtime-delay-ms") == 0 && i + 1 < argc) {
            if (parse_uint32(argv[++i], &cfg->realtime_delay_ms) != 0) {
                return -1;
            }
        } else if (strcmp(argv[i], "--alpha") == 0 && i + 1 < argc) {
            if (parse_double(argv[++i], &cfg->alpha) != 0) {
                return -1;
            }
        } else if (strcmp(argv[i], "--gamma") == 0 && i + 1 < argc) {
            if (parse_double(argv[++i], &cfg->gamma) != 0) {
                return -1;
            }
        } else if (strcmp(argv[i], "--epsilon") == 0 && i + 1 < argc) {
            if (parse_double(argv[++i], &cfg->epsilon) != 0) {
                return -1;
            }
        } else if (strcmp(argv[i], "--epsilon-min") == 0 && i + 1 < argc) {
            if (parse_double(argv[++i], &cfg->epsilon_min) != 0) {
                return -1;
            }
        } else if (strcmp(argv[i], "--epsilon-decay") == 0 && i + 1 < argc) {
            if (parse_double(argv[++i], &cfg->epsilon_decay) != 0) {
                return -1;
            }
        } else if (strcmp(argv[i], "--load") == 0 && i + 1 < argc) {
            cfg->load_path = argv[++i];
        } else if (strcmp(argv[i], "--save") == 0 && i + 1 < argc) {
            cfg->save_path = argv[++i];
        } else if (strcmp(argv[i], "--help") == 0) {
            print_usage(argv[0]);
            exit(0);
        } else {
            return -1;
        }
    }
    return 0;
}

/**
 * @brief Serializes the mapped QTable memory to disk via a binary byte dump format.
 * Prefixed with static 'TRQ2' header check validation block alongside total item counts tracking.
 */
static int qtable_save(const char *path, const QTable *table) {
    uint64_t count = (uint64_t)table->size;
    size_t i;
    FILE *fp = fopen(path, "wb");
    if (fp == NULL) {
        return -1;
    }

    if (fwrite("TRQ2", 1, 4, fp) != 4) {
        fclose(fp);
        return -1;
    }

    {
        uint32_t actions = RL_ACTION_COUNT;
        if (fwrite(&actions, sizeof(actions), 1, fp) != 1) {
            fclose(fp);
            return -1;
        }
    }

    if (fwrite(&count, sizeof(count), 1, fp) != 1) {
        fclose(fp);
        return -1;
    }

    for (i = 0; i < table->capacity; i++) {
        if (table->entries[i].used) {
            if (fwrite(&table->entries[i].key, sizeof(uint64_t), 1, fp) != 1) {
                fclose(fp);
                return -1;
            }
            if (fwrite(table->entries[i].q, sizeof(float), RL_ACTION_COUNT, fp) != RL_ACTION_COUNT) {
                fclose(fp);
                return -1;
            }
        }
    }

    fclose(fp);
    return 0;
}

/**
 * @brief Restores the raw binary QTable backup to local heap mappings.
 */
static int qtable_load(const char *path, QTable *table) {
    char magic[4];
    uint32_t actions;
    uint64_t count;
    uint64_t i;
    FILE *fp = fopen(path, "rb");
    if (fp == NULL) {
        return -1;
    }

    if (fread(magic, 1, 4, fp) != 4 || memcmp(magic, "TRQ2", 4) != 0) {
        fclose(fp);
        return -1;
    }
    if (fread(&actions, sizeof(actions), 1, fp) != 1 || actions != RL_ACTION_COUNT) {
        fclose(fp);
        return -1;
    }
    if (fread(&count, sizeof(count), 1, fp) != 1) {
        fclose(fp);
        return -1;
    }

    for (i = 0; i < count; i++) {
        uint64_t key;
        float q[RL_ACTION_COUNT];
        QEntry *entry;
        if (fread(&key, sizeof(uint64_t), 1, fp) != 1) {
            fclose(fp);
            return -1;
        }
        if (fread(q, sizeof(float), RL_ACTION_COUNT, fp) != RL_ACTION_COUNT) {
            fclose(fp);
            return -1;
        }
        entry = qtable_get_or_create(table, key);
        if (entry == NULL) {
            fclose(fp);
            return -1;
        }
        memcpy(entry->q, q, sizeof(q));
    }

    fclose(fp);
    return 0;
}

/**
 * @brief Primary Reinforcement Learning policy selector utilizing Epsilon-Greedy logic.
 * Decides whether to exploit best-known variables versus attempting randomized exploring.
 * Will fallback to heuristic-driven defaults when entirely random.
 */
static int select_action(QTable *table, const RLEnv *env, uint64_t key, double epsilon) {
    const QEntry *entry = qtable_get(table, key);

    // Epsilon parameter acts as Exploration probability bounds
    if (rng_uniform() < epsilon) {
        if (rng_uniform() < 0.85) {
            return rl_env_heuristic_action(env); // Guided heuristic random jump
        }
        return rng_int(RL_ACTION_COUNT); // Chaos random jump
    }
    
    // Exploitation (Using knowledge learned thus far)
    if (entry == NULL) {
        return rl_env_heuristic_action(env);
    }
    return best_action_from_entry(entry);
}

/**
 * @brief Complete Exploitation model (100% Greedy action logic). Never randomizes arbitrarily.
 */
static int greedy_action(const QTable *table, const RLEnv *env, uint64_t key) {
    const QEntry *entry = qtable_get(table, key);
    if (entry != NULL) {
        return best_action_from_entry(entry);
    }
    return rl_env_heuristic_action(env);
}

/**
 * @brief Spawns discrete isolated testing scenarios to examine objective AI policy metrics without impacting existing Train variables.
 */
static double evaluate_policy(const QTable *table, uint32_t seed, uint32_t episodes, uint32_t step_limit) {
    RLEnv env;
    uint32_t ep;
    double sum_lines = 0.0;

    rl_env_init(&env, seed);
    env.step_limit = step_limit;

    for (ep = 0; ep < episodes; ep++) {
        int done = 0;
        rl_env_reset(&env, seed + 131U * (ep + 1U));
        env.step_limit = step_limit;

        while (!done) {
            RLStateFeatures f = rl_env_features(&env);
            uint64_t key = features_to_key(f);
            int action = greedy_action(table, &env, key);
            rl_env_step(&env, action, &done);
        }
        sum_lines += env.game.total_lines;
    }

    return sum_lines / (double)episodes;
}

/**
 * @brief Encapsulates a singular isolated test executing visually inside Console output constraints.
 */
static void visualize_policy_episode(const QTable *table,
                                     uint32_t seed,
                                     uint32_t step_limit,
                                     uint32_t episode,
                                     double epsilon,
                                     size_t states,
                                     uint32_t delay_ms) {
    RLEnv env;
    int done = 0;
    uint32_t step = 0;
    double total_reward = 0.0;

    rl_env_init(&env, seed);
    env.step_limit = step_limit;
    rl_env_reset(&env, seed);
    env.step_limit = step_limit;

    printf("\x1b[2J");
    while (!done) {
        RLStateFeatures f = rl_env_features(&env);
        uint64_t key = features_to_key(f);
        int action = greedy_action(table, &env, key);
        total_reward += rl_env_step(&env, action, &done);
        step++;
        render_visual_frame(&env.game, episode, step, total_reward, epsilon, states);
        if (delay_ms > 0U) {
            sleep_ms((long)delay_ms);
        }
    }
    printf("Visual checkpoint done | episode: %u | lines: %u | score: %u | reward: %.2f\n",
           episode,
           env.game.total_lines,
           env.game.score,
           total_reward);
}

/**
 * @brief Root execution mapping invoking the setup, train and teardown procedures asynchronously resolving the RL algorithms.
 */
int main(int argc, char **argv) {
    TrainConfig cfg;
    QTable table;
    RLEnv env;
    uint32_t episode;
    double epsilon;
    double lines_window = 0.0;
    double reward_window = 0.0;
    double best_visual_eval = -1.0;

    if (parse_args(argc, argv, &cfg) != 0) {
        print_usage(argv[0]);
        return 1;
    }

    g_rng = cfg.seed ? cfg.seed : 1U;
    
    // Hard alloc hash pool representing QTable dictionary index space bounds
    if (qtable_init(&table, 1U << 18) != 0) {
        fprintf(stderr, "Failed to allocate Q-table.\n");
        return 1;
    }

    if (cfg.load_path != NULL) {
        if (qtable_load(cfg.load_path, &table) != 0) {
            fprintf(stderr, "Failed to load model from %s (missing file or incompatible format)\n", cfg.load_path);
            qtable_free(&table);
            return 1;
        }
        printf("Loaded model: %s (%zu states)\n", cfg.load_path, table.size);
    }

    rl_env_init(&env, cfg.seed);
    env.step_limit = cfg.step_limit;
    epsilon = cfg.epsilon;
    if (cfg.realtime_train) {
        printf("\x1b[2J");
    }

    for (episode = 1; episode <= cfg.episodes; episode++) {
        int done = 0;
        double episode_reward = 0.0;
        uint32_t training_step = 0U;

        rl_env_reset(&env, cfg.seed + 9973U * episode);
        env.step_limit = cfg.step_limit;

        while (!done) {
            RLStateFeatures state_f = rl_env_features(&env);
            RLStateFeatures next_f;
            uint64_t state_key = features_to_key(state_f);
            
            // Choose optimal selection choice bounded by arbitrary hyper parameters (Epsilon)
            int action = select_action(&table, &env, state_key, epsilon);
            double reward = rl_env_step(&env, action, &done);
            uint64_t next_key;
            QEntry *state_entry;
            QEntry *next_entry;
            double target;

            next_f = rl_env_features(&env);
            next_key = features_to_key(next_f);

            state_entry = qtable_get_or_create(&table, state_key);
            next_entry = qtable_get_or_create(&table, next_key);
            if (state_entry == NULL || next_entry == NULL) {
                fprintf(stderr, "Q-table allocation failure during training.\n");
                qtable_free(&table);
                return 1;
            }

            target = reward;
            if (!done) {
                target += cfg.gamma * (double)max_q(next_entry);
            }
            
            // Bellman Equation resolution combining existing Knowledge array parameters directly via Alpha updates (Temporal-Difference Logic bounds)
            state_entry->q[action] =
                (float)((double)state_entry->q[action] + cfg.alpha * (target - (double)state_entry->q[action]));

            episode_reward += reward;
            training_step++;
            if (cfg.realtime_train) {
                render_visual_frame(&env.game, episode, training_step, episode_reward, epsilon, table.size);
                if (cfg.realtime_delay_ms > 0U) {
                    sleep_ms((long)cfg.realtime_delay_ms);
                }
            }
        }

        lines_window += (double)env.game.total_lines;
        reward_window += episode_reward;

        if (episode % 100 == 0U) {
            printf("Episode %5u | avg_lines: %7.2f | avg_reward: %9.2f | epsilon: %.4f | states: %zu\n",
                   episode,
                   lines_window / 100.0,
                   reward_window / 100.0,
                   epsilon,
                   table.size);
            lines_window = 0.0;
            reward_window = 0.0;
        }

        if (cfg.visual && (episode % cfg.visual_every == 0U || episode == cfg.episodes)) {
            double eval_lines = 0.0;
            if (cfg.eval_episodes > 0U) {
                eval_lines = evaluate_policy(&table, cfg.seed + 3331U * episode, cfg.eval_episodes, cfg.step_limit);
                if (eval_lines > best_visual_eval) {
                    best_visual_eval = eval_lines;
                }
                printf("Visual checkpoint %u | greedy avg lines: %.2f | best so far: %.2f\n",
                       episode,
                       eval_lines,
                       best_visual_eval);
            }
            visualize_policy_episode(&table,
                                     cfg.seed + 7777U * episode,
                                     cfg.step_limit,
                                     episode,
                                     epsilon,
                                     table.size,
                                     cfg.visual_delay_ms);
        }

        if (epsilon > cfg.epsilon_min) {
            epsilon *= cfg.epsilon_decay;
            if (epsilon < cfg.epsilon_min) {
                epsilon = cfg.epsilon_min;
            }
        }
    }

    printf("Training finished. States learned: %zu\n", table.size);
    if (cfg.eval_episodes > 0U) {
        double eval_lines = evaluate_policy(&table, cfg.seed + 777U, cfg.eval_episodes, cfg.step_limit);
        printf("Greedy eval over %u episodes: avg lines %.2f\n", cfg.eval_episodes, eval_lines);
    }

    if (cfg.save_path != NULL) {
        if (qtable_save(cfg.save_path, &table) != 0) {
            fprintf(stderr, "Failed to save model to %s\n", cfg.save_path);
            qtable_free(&table);
            return 1;
        }
        printf("Saved model: %s\n", cfg.save_path);
    }

    qtable_free(&table);
    return 0;
}
