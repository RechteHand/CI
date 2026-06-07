#include "nn.h"

#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static float random_weight(void) {
    float r = (float) rand() / (float) RAND_MAX;
    return (r - 0.5f) * 0.2f;
}

static int layer_init(NNLayer *layer, size_t in_size, size_t out_size) {
    layer->input_size = in_size;
    layer->output_size = out_size;
    layer->weights = (float *) calloc(in_size * out_size, sizeof(float));
    layer->biases = (float *) calloc(out_size, sizeof(float));
    if (layer->weights == NULL || layer->biases == NULL) {
        free(layer->weights);
        free(layer->biases);
        layer->weights = NULL;
        layer->biases = NULL;
        return 0;
    }

    for (size_t i = 0; i < in_size * out_size; ++i) {
        layer->weights[i] = random_weight();
    }
    for (size_t i = 0; i < out_size; ++i) {
        layer->biases[i] = random_weight();
    }

    return 1;
}

static void softmax(const float *logits, float *probs, size_t n) {
    float max_logit = logits[0];
    for (size_t i = 1; i < n; ++i) {
        if (logits[i] > max_logit) {
            max_logit = logits[i];
        }
    }

    float sum = 0.0f;
    for (size_t i = 0; i < n; ++i) {
        probs[i] = expf(logits[i] - max_logit);
        sum += probs[i];
    }
    if (sum <= 0.0f) {
        for (size_t i = 0; i < n; ++i) {
            probs[i] = 1.0f / (float) n;
        }
        return;
    }
    for (size_t i = 0; i < n; ++i) {
        probs[i] /= sum;
    }
}

NNModel *nn_model_create(size_t input_size, size_t hidden_size, size_t output_size) {
    NNModel *model = (NNModel *) calloc(1, sizeof(NNModel));
    if (model == NULL) {
        return NULL;
    }

    model->input_size = input_size;
    model->hidden_size = hidden_size;
    model->output_size = output_size;

    if (!layer_init(&model->hidden_layer, input_size, hidden_size)) {
        free(model);
        return NULL;
    }
    if (!layer_init(&model->output_layer, hidden_size, output_size)) {
        free(model->hidden_layer.weights);
        free(model->hidden_layer.biases);
        free(model);
        return NULL;
    }

    return model;
}

void nn_model_free(NNModel *model) {
    if (model == NULL) {
        return;
    }
    free(model->hidden_layer.weights);
    free(model->hidden_layer.biases);
    free(model->output_layer.weights);
    free(model->output_layer.biases);
    free(model);
}

void nn_encode_state(const BlackjackState *state, float *out, size_t out_len) {
    if (out_len < NN_STATE_VECTOR_SIZE) {
        return;
    }

    out[0] = (float) state->player_total / 21.0f;
    out[1] = (float) state->usable_ace;
    out[2] = (float) state->dealer_upcard / 11.0f;
    out[3] = (float) state->busted;
    out[4] = (float) state->terminal;
    out[5] = (float) state->running_count / 20.0f;
    out[6] = state->true_count / 10.0f;
}

void nn_forward_logits(
    const NNModel *model,
    const float *input,
    float *output,
    size_t out_len
) {
    if (model == NULL || out_len < model->output_size) {
        return;
    }

    float *hidden = (float *) calloc(model->hidden_size, sizeof(float));
    if (hidden == NULL) {
        return;
    }

    for (size_t o = 0; o < model->hidden_size; ++o) {
        float sum = model->hidden_layer.biases[o];
        for (size_t i = 0; i < model->input_size; ++i) {
            sum += input[i] * model->hidden_layer.weights[o * model->input_size + i];
        }
        hidden[o] = sum > 0.0f ? sum : 0.0f;
    }

    for (size_t o = 0; o < model->output_size; ++o) {
        float sum = model->output_layer.biases[o];
        for (size_t i = 0; i < model->hidden_size; ++i) {
            sum += hidden[i] * model->output_layer.weights[o * model->hidden_size + i];
        }
        output[o] = sum;
    }

    free(hidden);
}

void nn_state_action_probs(
    const NNModel *model,
    const BlackjackState *state,
    float *hit_prob,
    float *stand_prob
) {
    float encoded[NN_STATE_VECTOR_SIZE];
    float logits[2] = {0.0f, 0.0f};
    float probs[2] = {0.5f, 0.5f};

    nn_encode_state(state, encoded, NN_STATE_VECTOR_SIZE);
    nn_forward_logits(model, encoded, logits, 2);
    softmax(logits, probs, 2);

    if (hit_prob != NULL) {
        *hit_prob = probs[0];
    }
    if (stand_prob != NULL) {
        *stand_prob = probs[1];
    }
}

float nn_train_supervised_step(
    NNModel *model,
    const float *input,
    size_t input_len,
    size_t target_index,
    float learning_rate
) {
    if (model == NULL || input == NULL) {
        return 0.0f;
    }
    if (input_len < model->input_size || target_index >= model->output_size) {
        return 0.0f;
    }

    float *hidden_preact = (float *) calloc(model->hidden_size, sizeof(float));
    float *hidden = (float *) calloc(model->hidden_size, sizeof(float));
    float *logits = (float *) calloc(model->output_size, sizeof(float));
    float *probs = (float *) calloc(model->output_size, sizeof(float));
    float *grad_logits = (float *) calloc(model->output_size, sizeof(float));
    float *grad_hidden = (float *) calloc(model->hidden_size, sizeof(float));
    if (hidden_preact == NULL || hidden == NULL || logits == NULL || probs == NULL ||
        grad_logits == NULL || grad_hidden == NULL) {
        free(hidden_preact);
        free(hidden);
        free(logits);
        free(probs);
        free(grad_logits);
        free(grad_hidden);
        return 0.0f;
    }

    for (size_t o = 0; o < model->hidden_size; ++o) {
        float sum = model->hidden_layer.biases[o];
        for (size_t i = 0; i < model->input_size; ++i) {
            sum += input[i] * model->hidden_layer.weights[o * model->input_size + i];
        }
        hidden_preact[o] = sum;
        hidden[o] = sum > 0.0f ? sum : 0.0f;
    }

    for (size_t o = 0; o < model->output_size; ++o) {
        float sum = model->output_layer.biases[o];
        for (size_t i = 0; i < model->hidden_size; ++i) {
            sum += hidden[i] * model->output_layer.weights[o * model->hidden_size + i];
        }
        logits[o] = sum;
    }
    softmax(logits, probs, model->output_size);

    for (size_t o = 0; o < model->output_size; ++o) {
        grad_logits[o] = probs[o];
    }
    grad_logits[target_index] -= 1.0f;

    for (size_t i = 0; i < model->hidden_size; ++i) {
        float grad = 0.0f;
        for (size_t o = 0; o < model->output_size; ++o) {
            grad += grad_logits[o] * model->output_layer.weights[o * model->hidden_size + i];
        }
        if (hidden_preact[i] <= 0.0f) {
            grad = 0.0f;
        }
        grad_hidden[i] = grad;
    }

    for (size_t o = 0; o < model->output_size; ++o) {
        for (size_t i = 0; i < model->hidden_size; ++i) {
            float grad_w = grad_logits[o] * hidden[i];
            model->output_layer.weights[o * model->hidden_size + i] -= learning_rate * grad_w;
        }
        model->output_layer.biases[o] -= learning_rate * grad_logits[o];
    }

    for (size_t o = 0; o < model->hidden_size; ++o) {
        for (size_t i = 0; i < model->input_size; ++i) {
            float grad_w = grad_hidden[o] * input[i];
            model->hidden_layer.weights[o * model->input_size + i] -= learning_rate * grad_w;
        }
        model->hidden_layer.biases[o] -= learning_rate * grad_hidden[o];
    }

    float safe_prob = probs[target_index];
    if (safe_prob < 1e-6f) {
        safe_prob = 1e-6f;
    }
    float loss = -logf(safe_prob);

    free(hidden_preact);
    free(hidden);
    free(logits);
    free(probs);
    free(grad_logits);
    free(grad_hidden);

    return loss;
}

int nn_model_save(const NNModel *model, const char *path) {
    if (model == NULL || path == NULL) {
        return 0;
    }

    FILE *fp = fopen(path, "wb");
    if (fp == NULL) {
        return 0;
    }

    const char magic[8] = {'B', 'J', 'N', 'N', '1', '\0', '\0', '\0'};
    uint32_t dims[3];
    dims[0] = (uint32_t) model->input_size;
    dims[1] = (uint32_t) model->hidden_size;
    dims[2] = (uint32_t) model->output_size;

    int ok = 1;
    ok &= fwrite(magic, sizeof(magic), 1, fp) == 1;
    ok &= fwrite(dims, sizeof(dims), 1, fp) == 1;
    ok &= fwrite(
              model->hidden_layer.weights,
              sizeof(float),
              model->input_size * model->hidden_size,
              fp
          ) == model->input_size * model->hidden_size;
    ok &= fwrite(model->hidden_layer.biases, sizeof(float), model->hidden_size, fp) ==
          model->hidden_size;
    ok &= fwrite(
              model->output_layer.weights,
              sizeof(float),
              model->hidden_size * model->output_size,
              fp
          ) == model->hidden_size * model->output_size;
    ok &= fwrite(model->output_layer.biases, sizeof(float), model->output_size, fp) ==
          model->output_size;

    fclose(fp);
    return ok ? 1 : 0;
}

NNModel *nn_model_load(const char *path) {
    if (path == NULL) {
        return NULL;
    }

    FILE *fp = fopen(path, "rb");
    if (fp == NULL) {
        return NULL;
    }

    char magic[8];
    uint32_t dims[3];
    if (fread(magic, sizeof(magic), 1, fp) != 1 || fread(dims, sizeof(dims), 1, fp) != 1) {
        fclose(fp);
        return NULL;
    }
    if (memcmp(magic, "BJNN1\0\0\0", 8) != 0) {
        fclose(fp);
        return NULL;
    }

    NNModel *model = nn_model_create(dims[0], dims[1], dims[2]);
    if (model == NULL) {
        fclose(fp);
        return NULL;
    }

    int ok = 1;
    ok &= fread(
              model->hidden_layer.weights,
              sizeof(float),
              model->input_size * model->hidden_size,
              fp
          ) == model->input_size * model->hidden_size;
    ok &= fread(model->hidden_layer.biases, sizeof(float), model->hidden_size, fp) ==
          model->hidden_size;
    ok &= fread(
              model->output_layer.weights,
              sizeof(float),
              model->hidden_size * model->output_size,
              fp
          ) == model->hidden_size * model->output_size;
    ok &= fread(model->output_layer.biases, sizeof(float), model->output_size, fp) ==
          model->output_size;

    fclose(fp);

    if (!ok) {
        nn_model_free(model);
        return NULL;
    }
    return model;
}
