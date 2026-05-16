#include "blackjack.h"

#include <stdlib.h>

static int hi_lo_delta(int card_value) {
    if (card_value >= 2 && card_value <= 6) {
        return 1;
    }
    if (card_value == 1 || card_value == 10) {
        return -1;
    }
    return 0;
}

static void hand_recompute(Hand *hand) {
    int total = 0;
    int ace_count = 0;

    for (int i = 0; i < hand->card_count; ++i) {
        int v = hand->cards[i];
        if (v == 1) {
            ++ace_count;
            total += 11;
        } else {
            total += v;
        }
    }

    while (total > 21 && ace_count > 0) {
        total -= 10;
        --ace_count;
    }

    hand->total = total;
    hand->usable_ace = ace_count > 0 ? 1 : 0;
    hand->busted = total > 21 ? 1 : 0;
}

static void emit_state(
    const Shoe *shoe,
    const Hand *player,
    const Hand *dealer,
    int round_number,
    const char *phase,
    PlayerAction action,
    int terminal,
    RoundOutcome outcome,
    BlackjackStateCallback callback,
    void *user_data
) {
    if (callback == NULL) {
        return;
    }

    BlackjackState state;
    state.round_number = round_number;
    state.phase = phase;
    state.action = action;
    state.player_total = player->total;
    state.usable_ace = player->usable_ace;
    state.dealer_upcard = dealer->card_count > 0 ? dealer->cards[0] : 0;
    state.terminal = terminal;
    state.busted = player->busted;
    state.running_count = shoe->running_count;
    state.true_count = shoe_true_count(shoe);
    state.dealer_total = dealer->total;
    state.dealer_busted = dealer->busted;
    state.outcome = outcome;

    callback(&state, user_data);
}

void blackjack_seed(unsigned int seed) {
    srand(seed);
}

void shoe_reshuffle(Shoe *shoe) {
    int idx = 0;

    for (int deck = 0; deck < SHOE_DECKS; ++deck) {
        for (int value = 1; value <= 9; ++value) {
            for (int count = 0; count < 4; ++count) {
                shoe->cards[idx++] = value;
            }
        }
        for (int count = 0; count < 16; ++count) {
            shoe->cards[idx++] = 10;
        }
    }

    for (int i = SHOE_TOTAL_CARDS - 1; i > 0; --i) {
        int j = rand() % (i + 1);
        int temp = shoe->cards[i];
        shoe->cards[i] = shoe->cards[j];
        shoe->cards[j] = temp;
    }

    shoe->top_index = 0;
    shoe->running_count = 0;
}

void shoe_init(Shoe *shoe) {
    shoe_reshuffle(shoe);
}

int shoe_should_reshuffle(const Shoe *shoe) {
    float penetration = (float) shoe->top_index / (float) SHOE_TOTAL_CARDS;
    return penetration >= RESHUFFLE_PENETRATION ? 1 : 0;
}

int shoe_draw_card(Shoe *shoe) {
    if (shoe->top_index >= SHOE_TOTAL_CARDS) {
        shoe_reshuffle(shoe);
    }

    int card_value = shoe->cards[shoe->top_index++];
    shoe->running_count += hi_lo_delta(card_value);
    return card_value;
}

float shoe_true_count(const Shoe *shoe) {
    int cards_left = SHOE_TOTAL_CARDS - shoe->top_index;
    float decks_left = (float) cards_left / 52.0f;
    if (decks_left < 0.25f) {
        decks_left = 0.25f;
    }
    return (float) shoe->running_count / decks_left;
}

void hand_init(Hand *hand) {
    hand->card_count = 0;
    hand->total = 0;
    hand->usable_ace = 0;
    hand->busted = 0;
}

void hand_add_card(Hand *hand, int card_value) {
    if (hand->card_count < MAX_HAND_CARDS) {
        hand->cards[hand->card_count++] = card_value;
    }
    hand_recompute(hand);
}

const char *action_to_string(PlayerAction action) {
    switch (action) {
        case ACTION_HIT:
            return "HIT";
        case ACTION_STAND:
            return "STAND";
        case ACTION_NONE:
        default:
            return "-";
    }
}

const char *outcome_to_string(RoundOutcome outcome) {
    switch (outcome) {
        case OUTCOME_WIN:
            return "WIN";
        case OUTCOME_LOSE:
            return "LOSE";
        case OUTCOME_PUSH:
            return "PUSH";
        case OUTCOME_UNRESOLVED:
        default:
            return "-";
    }
}

static PlayerAction basic_decision_from_state(const BlackjackState *state, void *user_data) {
    (void) user_data;

    Hand hand;
    hand_init(&hand);
    hand.total = state->player_total;
    hand.usable_ace = state->usable_ace;
    hand.busted = state->busted;

    return basic_strategy_action(&hand, state->dealer_upcard);
}

void blackjack_play_round(
    Shoe *shoe,
    int round_number,
    BlackjackStateCallback callback,
    void *user_data
) {
    blackjack_play_round_with_policy(
        shoe,
        round_number,
        basic_decision_from_state,
        NULL,
        callback,
        user_data
    );
}

void blackjack_play_round_with_policy(
    Shoe *shoe,
    int round_number,
    BlackjackDecisionCallback decision_callback,
    void *decision_user_data,
    BlackjackStateCallback state_callback,
    void *state_user_data
) {
    if (shoe_should_reshuffle(shoe)) {
        shoe_reshuffle(shoe);
    }

    Hand player;
    Hand dealer;
    hand_init(&player);
    hand_init(&dealer);

    hand_add_card(&player, shoe_draw_card(shoe));
    emit_state(
        shoe,
        &player,
        &dealer,
        round_number,
        "deal",
        ACTION_NONE,
        0,
        OUTCOME_UNRESOLVED,
        state_callback,
        state_user_data
    );

    hand_add_card(&dealer, shoe_draw_card(shoe));
    emit_state(
        shoe,
        &player,
        &dealer,
        round_number,
        "deal",
        ACTION_NONE,
        0,
        OUTCOME_UNRESOLVED,
        state_callback,
        state_user_data
    );

    hand_add_card(&player, shoe_draw_card(shoe));
    emit_state(
        shoe,
        &player,
        &dealer,
        round_number,
        "deal",
        ACTION_NONE,
        0,
        OUTCOME_UNRESOLVED,
        state_callback,
        state_user_data
    );

    hand_add_card(&dealer, shoe_draw_card(shoe));
    emit_state(
        shoe,
        &player,
        &dealer,
        round_number,
        "deal",
        ACTION_NONE,
        0,
        OUTCOME_UNRESOLVED,
        state_callback,
        state_user_data
    );

    while (!player.busted) {
        BlackjackState decision_state;
        decision_state.round_number = round_number;
        decision_state.phase = "play";
        decision_state.action = ACTION_NONE;
        decision_state.player_total = player.total;
        decision_state.usable_ace = player.usable_ace;
        decision_state.dealer_upcard = dealer.cards[0];
        decision_state.terminal = 0;
        decision_state.busted = player.busted;
        decision_state.running_count = shoe->running_count;
        decision_state.true_count = shoe_true_count(shoe);
        decision_state.dealer_total = dealer.total;
        decision_state.dealer_busted = dealer.busted;
        decision_state.outcome = OUTCOME_UNRESOLVED;

        PlayerAction action = ACTION_STAND;
        if (decision_callback != NULL) {
            action = decision_callback(&decision_state, decision_user_data);
        } else {
            action = basic_strategy_action(&player, dealer.cards[0]);
        }
        if (action != ACTION_HIT && action != ACTION_STAND) {
            action = ACTION_STAND;
        }

        emit_state(
            shoe,
            &player,
            &dealer,
            round_number,
            "play",
            action,
            0,
            OUTCOME_UNRESOLVED,
            state_callback,
            state_user_data
        );

        if (action == ACTION_STAND) {
            break;
        }

        hand_add_card(&player, shoe_draw_card(shoe));
        emit_state(
            shoe,
            &player,
            &dealer,
            round_number,
            "play",
            ACTION_HIT,
            0,
            OUTCOME_UNRESOLVED,
            state_callback,
            state_user_data
        );
    }

    if (!player.busted) {
        while (dealer.total < 17) {
            hand_add_card(&dealer, shoe_draw_card(shoe));
            emit_state(
                shoe,
                &player,
                &dealer,
                round_number,
                "dealer",
                ACTION_NONE,
                0,
                OUTCOME_UNRESOLVED,
                state_callback,
                state_user_data
            );
        }
    }

    RoundOutcome outcome = OUTCOME_PUSH;
    if (player.busted) {
        outcome = OUTCOME_LOSE;
    } else if (dealer.busted) {
        outcome = OUTCOME_WIN;
    } else if (player.total > dealer.total) {
        outcome = OUTCOME_WIN;
    } else if (player.total < dealer.total) {
        outcome = OUTCOME_LOSE;
    }

    emit_state(
        shoe,
        &player,
        &dealer,
        round_number,
        "resolve",
        ACTION_NONE,
        1,
        outcome,
        state_callback,
        state_user_data
    );
}
