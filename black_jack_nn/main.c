#include "blackjack.h"
#include "nn.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define DEFAULT_SIM_ROUNDS 5
#define DEFAULT_TRAIN_ROUNDS 50000
#define DEFAULT_PLAY_ROUNDS 5
#define DEFAULT_LEARNING_RATE 0.01f
#define DEFAULT_MODEL_PATH "model.nn"

typedef enum {
    MODE_SIMULATE = 0,
    MODE_TRAIN = 1,
    MODE_PLAY = 2
} AppMode;

typedef struct {
    const NNModel *model;
} SimulationContext;

typedef struct {
    NNModel *model;
    float learning_rate;
    long samples;
    float total_loss;
} TrainingContext;

typedef struct {
    const NNModel *model;
    int wins;
    int losses;
    int pushes;
} PlayContext;

typedef struct {
    AppMode mode;
    int rounds;
    float learning_rate;
    const char *model_path;
} CliConfig;

static int parse_positive_int(const char *value, int fallback) {
    if (value == NULL) {
        return fallback;
    }
    char *end = NULL;
    long parsed = strtol(value, &end, 10);
    if (end == value || *end != '\0' || parsed <= 0 || parsed > 10000000L) {
        return fallback;
    }
    return (int) parsed;
}

static float parse_positive_float(const char *value, float fallback) {
    if (value == NULL) {
        return fallback;
    }
    char *end = NULL;
    float parsed = strtof(value, &end);
    if (end == value || *end != '\0' || parsed <= 0.0f || parsed > 10.0f) {
        return fallback;
    }
    return parsed;
}

static void print_usage(const char *exe_name) {
    printf("Usage:\n");
    printf("  %s [rounds]\n", exe_name);
    printf("  %s simulate [rounds]\n", exe_name);
    printf("  %s train [rounds] [learning_rate] [model_path]\n", exe_name);
    printf("  %s play [rounds] [model_path]\n", exe_name);
}

static CliConfig parse_cli(int argc, char **argv) {
    CliConfig cfg;
    cfg.mode = MODE_SIMULATE;
    cfg.rounds = DEFAULT_SIM_ROUNDS;
    cfg.learning_rate = DEFAULT_LEARNING_RATE;
    cfg.model_path = DEFAULT_MODEL_PATH;

    if (argc < 2) {
        return cfg;
    }

    if (strcmp(argv[1], "simulate") == 0) {
        cfg.mode = MODE_SIMULATE;
        if (argc >= 3) {
            cfg.rounds = parse_positive_int(argv[2], DEFAULT_SIM_ROUNDS);
        }
        return cfg;
    }
    if (strcmp(argv[1], "train") == 0) {
        cfg.mode = MODE_TRAIN;
        cfg.rounds = DEFAULT_TRAIN_ROUNDS;
        if (argc >= 3) {
            cfg.rounds = parse_positive_int(argv[2], DEFAULT_TRAIN_ROUNDS);
        }
        if (argc >= 4) {
            cfg.learning_rate = parse_positive_float(argv[3], DEFAULT_LEARNING_RATE);
        }
        if (argc >= 5) {
            cfg.model_path = argv[4];
        }
        return cfg;
    }
    if (strcmp(argv[1], "play") == 0) {
        cfg.mode = MODE_PLAY;
        cfg.rounds = DEFAULT_PLAY_ROUNDS;
        if (argc >= 3) {
            cfg.rounds = parse_positive_int(argv[2], DEFAULT_PLAY_ROUNDS);
        }
        if (argc >= 4) {
            cfg.model_path = argv[3];
        }
        return cfg;
    }

    cfg.mode = MODE_SIMULATE;
    cfg.rounds = parse_positive_int(argv[1], DEFAULT_SIM_ROUNDS);
    if (cfg.rounds == DEFAULT_SIM_ROUNDS && strcmp(argv[1], "5") != 0) {
        print_usage(argv[0]);
    }
    return cfg;
}

static void simulate_state_callback(const BlackjackState *state, void *user_data) {
    SimulationContext *ctx = (SimulationContext *) user_data;
    float encoded[NN_STATE_VECTOR_SIZE];
    float logits[2] = {0.0f, 0.0f};

    render_state_ascii(state);
    nn_encode_state(state, encoded, NN_STATE_VECTOR_SIZE);
    nn_forward_logits(ctx->model, encoded, logits, 2);
    printf("NN logits => hit: % .4f | stand: % .4f\n\n", logits[0], logits[1]);
}

static void train_state_callback(const BlackjackState *state, void *user_data) {
    TrainingContext *ctx = (TrainingContext *) user_data;
    if (strcmp(state->phase, "play") != 0) {
        return;
    }
    if (state->action != ACTION_HIT && state->action != ACTION_STAND) {
        return;
    }

    float input[NN_STATE_VECTOR_SIZE];
    size_t target = (state->action == ACTION_HIT) ? 0U : 1U;
    nn_encode_state(state, input, NN_STATE_VECTOR_SIZE);
    ctx->total_loss +=
        nn_train_supervised_step(ctx->model, input, NN_STATE_VECTOR_SIZE, target, ctx->learning_rate);
    ctx->samples += 1;
}

static void play_state_callback(const BlackjackState *state, void *user_data) {
    PlayContext *ctx = (PlayContext *) user_data;
    if (strcmp(state->phase, "play") == 0) {
        return;
    }

    render_state_ascii(state);
    if (strcmp(state->phase, "resolve") == 0) {
        if (state->outcome == OUTCOME_WIN) {
            ctx->wins += 1;
        } else if (state->outcome == OUTCOME_LOSE) {
            ctx->losses += 1;
        } else if (state->outcome == OUTCOME_PUSH) {
            ctx->pushes += 1;
        }
        printf(
            "Scoreboard => wins: %d | losses: %d | pushes: %d\n\n",
            ctx->wins,
            ctx->losses,
            ctx->pushes
        );
    }
}

static PlayerAction play_decision_callback(const BlackjackState *state, void *user_data) {
    PlayContext *ctx = (PlayContext *) user_data;
    float hit_prob = 0.5f;
    float stand_prob = 0.5f;
    char line[64];

    render_state_ascii(state);
    nn_state_action_probs(ctx->model, state, &hit_prob, &stand_prob);

    printf(
        "NN tip: %s (hit: %.2f%%, stand: %.2f%%)\n",
        hit_prob >= stand_prob ? "HIT" : "STAND",
        hit_prob * 100.0f,
        stand_prob * 100.0f
    );

    while (1) {
        printf("Your move [h/s]: ");
        if (fgets(line, sizeof(line), stdin) == NULL) {
            return ACTION_STAND;
        }
        if (line[0] == 'h' || line[0] == 'H') {
            return ACTION_HIT;
        }
        if (line[0] == 's' || line[0] == 'S') {
            return ACTION_STAND;
        }
        printf("Invalid input. Enter h (hit) or s (stand).\n");
    }
}

static int run_simulation_mode(int rounds) {
    Shoe shoe;
    NNModel *model = nn_model_create(NN_STATE_VECTOR_SIZE, 16, 2);
    if (model == NULL) {
        fprintf(stderr, "Failed to initialize NN model.\n");
        return 1;
    }

    shoe_init(&shoe);
    SimulationContext ctx;
    ctx.model = model;

    for (int round = 1; round <= rounds; ++round) {
        printf("=== Round %d ===\n", round);
        blackjack_play_round(&shoe, round, simulate_state_callback, &ctx);
    }

    nn_model_free(model);
    return 0;
}

static int run_training_mode(int rounds, float learning_rate, const char *model_path) {
    Shoe shoe;
    NNModel *model = nn_model_create(NN_STATE_VECTOR_SIZE, 16, 2);
    if (model == NULL) {
        fprintf(stderr, "Failed to initialize NN model.\n");
        return 1;
    }

    shoe_init(&shoe);

    TrainingContext ctx;
    ctx.model = model;
    ctx.learning_rate = learning_rate;
    ctx.samples = 0;
    ctx.total_loss = 0.0f;

    for (int round = 1; round <= rounds; ++round) {
        blackjack_play_round(&shoe, round, train_state_callback, &ctx);
        if (round % 10000 == 0) {
            float avg = (ctx.samples > 0) ? (ctx.total_loss / (float) ctx.samples) : 0.0f;
            printf("Training progress: %d rounds, avg loss %.5f\n", round, avg);
        }
    }

    if (!nn_model_save(model, model_path)) {
        fprintf(stderr, "Failed to save model to '%s'.\n", model_path);
        nn_model_free(model);
        return 1;
    }

    printf(
        "Training complete. Rounds: %d, samples: %ld, avg loss: %.5f\nModel saved to: %s\n",
        rounds,
        ctx.samples,
        (ctx.samples > 0) ? (ctx.total_loss / (float) ctx.samples) : 0.0f,
        model_path
    );

    nn_model_free(model);
    return 0;
}

static int run_play_mode(int rounds, const char *model_path) {
    Shoe shoe;
    NNModel *model = nn_model_load(model_path);
    if (model == NULL) {
        fprintf(
            stderr,
            "Could not load model from '%s'. Train first with: ./black_jack_nn train\n",
            model_path
        );
        return 1;
    }

    shoe_init(&shoe);
    PlayContext ctx;
    ctx.model = model;
    ctx.wins = 0;
    ctx.losses = 0;
    ctx.pushes = 0;

    for (int round = 1; round <= rounds; ++round) {
        printf("=== Your Round %d ===\n", round);
        blackjack_play_round_with_policy(
            &shoe,
            round,
            play_decision_callback,
            &ctx,
            play_state_callback,
            &ctx
        );
    }

    nn_model_free(model);
    return 0;
}

int main(int argc, char **argv) {
    CliConfig cfg = parse_cli(argc, argv);
    blackjack_seed((unsigned int) time(NULL));

    if (cfg.mode == MODE_SIMULATE) {
        return run_simulation_mode(cfg.rounds);
    }
    if (cfg.mode == MODE_TRAIN) {
        return run_training_mode(cfg.rounds, cfg.learning_rate, cfg.model_path);
    }
    return run_play_mode(cfg.rounds, cfg.model_path);
}
