CC := cc
CFLAGS := -std=c11 -Wall -Wextra -Wpedantic -O2
CPPFLAGS := -Iinclude
LDLIBS := -lm

TARGET := black_jack_nn
SRCS := main.c src/blackjack.c src/policy.c src/render.c src/nn.c
OBJS := $(SRCS:.c=.o)

.PHONY: all run test clean

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) $(CPPFLAGS) -o $@ $(OBJS) $(LDLIBS)

%.o: %.c
	$(CC) $(CFLAGS) $(CPPFLAGS) -c $< -o $@

run: $(TARGET)
	./$(TARGET) $(ARGS)

test: $(TARGET)
	@echo "Running runtime demo test (2 rounds)..."
	./$(TARGET) 2

clean:
	rm -f $(OBJS) $(TARGET)
