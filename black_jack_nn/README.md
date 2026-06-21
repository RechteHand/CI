# Black Jack (Neuronales Netz / RL)

A Blackjack simulator and training environment written in C that uses a feed-forward neural network to learn Blackjack decisions from gameplay data.

## Overview
This project combines a Blackjack game engine with a neural network capable of learning when to Hit or Stand in a game of Blackjack.

There are three supported operating modes:
- Simulation Mode - visualizes Blackjack rounds and allows you to inspect neural network outputs.
- Training Mode - generate training samples from simulated games and train the network
- Play Mode - allows to play blackjack while receiving recommendations from the model (percentage to stand or hit).

## Neural Network
The network architecture consists of:

Input Layer -> Hidden layer (16 neurons) -> output layer (2 neurons)


## Building

The project uses a standard CMake build process.

```bash
mkdir build
cd build
cmake ..
cmake --build .
```

This will generate the executable:
```bash
black_jack_nn
```

## Usage
### Simulation Mode

Run Blackjack rounds and inspect the neural network's evaluation of each game state.
```bash
./black_jack_nn simulate 5
```

### Training Mode

Train a new neural network using simulated Blackjack rounds.

```bash
./black_jack_nn train
```

Custom training:

```bash
./black_jack_nn train 100000 0.01 model.nn
```
Parameter	Description
rounds	Number of training rounds
learning_rate	Learning rate used during training
model_path	Path where the trained model will be saved

Example output:

| Parameter     | Description                                |
| ------------- | ------------------------------------------ |
| rounds        | Number of training rounds                  |
| learning_rate | Learning rate used during training         |
| model_path    | Path where the trained model will be saved |


Load a trained model and play Blackjack interactively.

```bash
./black_jack_nn play
```

Or load a custom model:

```bash
./black_jack_nn play 10 model.nn
```

The neural network provides action recommendations before each move:

NN tip: HIT (hit: 73.4%, stand: 26.6%)

Available commands:

h = Hit
s = Stand
Project Structure
```bash
.
├── CMakeLists.txt
├── include/
│   ├── blackjack.h
│   └── nn.h
├── src/
│   ├── main.c
│   ├── blackjack.c
│   └── nn.c
├── build/
└── model.nn
```
