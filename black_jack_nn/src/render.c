#include "blackjack.h"

#include <stdio.h>

void render_state_ascii(const BlackjackState *state) {
    printf("+------------------------------------------------+\n");
    printf("| Round %-3d | Phase %-7s | Action %-5s |\n",
           state->round_number,
           state->phase,
           action_to_string(state->action));
    printf("+------------------------------------------------+\n");
    printf("| player_total: %-3d usable_ace: %-1d busted: %-1d     |\n",
           state->player_total,
           state->usable_ace,
           state->busted);
    printf("| dealer_upcard: %-2d dealer_total: %-3d dealer_bust: %-1d |\n",
           state->dealer_upcard,
           state->dealer_total,
           state->dealer_busted);
    printf("| running_count: %-4d true_count: %7.3f           |\n",
           state->running_count,
           state->true_count);
    printf("| terminal: %-1d outcome: %-4s                        |\n",
           state->terminal,
           outcome_to_string(state->outcome));
    printf("+------------------------------------------------+\n");
}
