CC ?= cc
CFLAGS ?= -std=c17 -O2 -Wall -Wextra -Werror -pedantic -D_POSIX_C_SOURCE=200809L
INCLUDES := -Iinclude

BIN_DIR := bin
COMMON_SRC := src/tetris.c src/rl_env.c
PLAY_BIN := $(BIN_DIR)/play
TRAIN_BIN := $(BIN_DIR)/train

.PHONY: all play train clean run-play run-train

all: play train

play: $(PLAY_BIN)

train: $(TRAIN_BIN)

$(BIN_DIR):
	mkdir -p $(BIN_DIR)

$(PLAY_BIN): src/play.c $(COMMON_SRC) | $(BIN_DIR)
	$(CC) $(CFLAGS) $(INCLUDES) -o $@ $^

$(TRAIN_BIN): src/train.c $(COMMON_SRC) | $(BIN_DIR)
	$(CC) $(CFLAGS) $(INCLUDES) -o $@ $^

run-play: play
	./$(PLAY_BIN)

run-train: train
	./$(TRAIN_BIN) --episodes 2000 --save qtable.bin

clean:
	rm -rf $(BIN_DIR)
