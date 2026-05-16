#ifndef NN_H
#define NN_H

#include <stddef.h>

#include "blackjack.h"

#define NN_STATE_VECTOR_SIZE 7

typedef struct {
    size_t input_size;
    size_t output_size;
    float *weights;
    float *biases;
} NNLayer;

typedef struct {
    size_t input_size;
    size_t hidden_size;
    size_t output_size;
    NNLayer hidden_layer;
    NNLayer output_layer;
} NNModel;

NNModel *nn_model_create(size_t input_size, size_t hidden_size, size_t output_size);
void nn_model_free(NNModel *model);

void nn_encode_state(const BlackjackState *state, float *out, size_t out_len);
void nn_forward_logits(
    const NNModel *model,
    const float *input,
    float *output,
    size_t out_len
);
void nn_state_action_probs(
    const NNModel *model,
    const BlackjackState *state,
    float *hit_prob,
    float *stand_prob
);
float nn_train_supervised_step(
    NNModel *model,
    const float *input,
    size_t input_len,
    size_t target_index,
    float learning_rate
);
int nn_model_save(const NNModel *model, const char *path);
NNModel *nn_model_load(const char *path);

#endif
