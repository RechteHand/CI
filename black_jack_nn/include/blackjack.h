#ifndef BLACKJACK_H
#define BLACKJACK_H

#include <stddef.h>

#define SHOE_DECKS 6
#define SHOE_TOTAL_CARDS (SHOE_DECKS * 52)
#define RESHUFFLE_PENETRATION 0.75f
#define MAX_HAND_CARDS 12

typedef enum {
    ACTION_NONE = 0,
    ACTION_HIT = 1,
    ACTION_STAND = 2
} PlayerAction;

typedef enum {
    OUTCOME_UNRESOLVED = 2,
    OUTCOME_LOSE = -1,
    OUTCOME_PUSH = 0,
    OUTCOME_WIN = 1
} RoundOutcome;

typedef struct {
    int cards[MAX_HAND_CARDS];
    int card_count;
    int total;
    int usable_ace;
    int busted;
} Hand;

typedef struct {
    int cards[SHOE_TOTAL_CARDS];
    int top_index;
    int running_count;
} Shoe;

typedef struct {
    int round_number;
    const char *phase;
    PlayerAction action;
    int player_total;
    int usable_ace;
    int dealer_upcard;
    int terminal;
    int busted;
    int running_count;
    float true_count;
    int dealer_total;
    int dealer_busted;
    RoundOutcome outcome;
} BlackjackState;

typedef void (*BlackjackStateCallback)(const BlackjackState *state, void *user_data);
typedef PlayerAction (*BlackjackDecisionCallback)(const BlackjackState *state, void *user_data);

void blackjack_seed(unsigned int seed);
void shoe_init(Shoe *shoe);
void shoe_reshuffle(Shoe *shoe);
int shoe_should_reshuffle(const Shoe *shoe);
int shoe_draw_card(Shoe *shoe);
float shoe_true_count(const Shoe *shoe);

void hand_init(Hand *hand);
void hand_add_card(Hand *hand, int card_value);

PlayerAction basic_strategy_action(const Hand *player, int dealer_upcard);
void blackjack_play_round(
    Shoe *shoe,
    int round_number,
    BlackjackStateCallback callback,
    void *user_data
);
void blackjack_play_round_with_policy(
    Shoe *shoe,
    int round_number,
    BlackjackDecisionCallback decision_callback,
    void *decision_user_data,
    BlackjackStateCallback state_callback,
    void *state_user_data
);

const char *action_to_string(PlayerAction action);
const char *outcome_to_string(RoundOutcome outcome);
void render_state_ascii(const BlackjackState *state);

#endif
