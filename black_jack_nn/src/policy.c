#include "blackjack.h"

PlayerAction basic_strategy_action(const Hand *player, int dealer_upcard) {
    int up = dealer_upcard == 1 ? 11 : dealer_upcard;
    int total = player->total;

    if (player->usable_ace) {
        if (total <= 17) {
            return ACTION_HIT;
        }
        if (total == 18) {
            if (up == 2 || up == 7 || up == 8) {
                return ACTION_STAND;
            }
            return ACTION_HIT;
        }
        return ACTION_STAND;
    }

    if (total <= 11) {
        return ACTION_HIT;
    }
    if (total == 12) {
        if (up >= 4 && up <= 6) {
            return ACTION_STAND;
        }
        return ACTION_HIT;
    }
    if (total >= 13 && total <= 16) {
        if (up >= 2 && up <= 6) {
            return ACTION_STAND;
        }
        return ACTION_HIT;
    }

    return ACTION_STAND;
}
