# Genetic Algorithm: Island Model

This project implements a Genetic Algorithm based on the **Island Model** in Python.
We adjusted the version to somewhat refelct the olympic games, the best indiviuals of each island are send to compete in the olympics.

## Basic Idea

Instead of a single large population, multiple independent populations ("islands") are simulated. Each island has its own culture (its own configuration for selection, crossover, and mutation) and evolves independently.

At fixed intervals, **migration** (exchange of individuals between islands) and **Olympic Games** take place, where the champions of all islands compete against each other. In addition, self-adaptation mechanisms such as island extinction and rebirth in cases of stagnation are implemented.

## Running the Project

1. Make sure Python is installed.
2. Run the main script:

   ```bash
   python main.py
   ```


## How It Works

### 1. Island Initialization

Instead of evolving a single population, the algorithm creates multiple independent **islands**. Each island maintains:

* Its own population
* A selection strategy
* A crossover strategy
* A mutation strategy
* Adaptive search parameters

This creates several parallel search processes with different evolutionary behaviors, increasing diversity and exploration of the search space.


### 2. Parallel Evolution

All islands evolve simultaneously using multiprocessing, allowing the utilization of multiple CPU cores.

For each generation, every island performs:

1. Selection
2. Crossover
3. Mutation
4. Local Search
5. Elitism
6. Fitness Evaluation

The best individual of each generation is preserved through elitism, ensuring that high-quality solutions are never lost.

---

### 3. Adaptive Search

Each island continuously monitors:

* Fitness improvement
* Population diversity
* Number of stagnant generations

When progress slows or diversity decreases, mutation rates are automatically increased to encourage exploration.

This dynamic balance between **exploration** and **exploitation** helps prevent premature convergence.

---

### 4. Memetic Local Search

In addition to classical genetic operators, individuals can be refined through local optimization techniques:

* Swap mutations
* Segment shuffling
* Simulated Annealing–style acceptance of worse solutions
* Restarts during prolonged stagnation

This hybrid approach combines the global search capabilities of Genetic Algorithms with the fine-tuning power of local optimization, forming a **Memetic Algorithm**.

---

### 5. Migration

At fixed intervals, islands exchange their best individuals using a **ring topology**:

```
Island A → Island B → Island C → ... → Island A
```

Migration spreads successful genetic material across the archipelago while preserving sufficient diversity between islands.

---

### 6. Olympic Competition

Periodically, the best individual from each island participates in a global competition:

1. Each island sends its champion.
2. Champions form a temporary Olympic population.
3. This population evolves independently.
4. The winning solution is copied back to all islands.

This mechanism combines the strongest solutions discovered across the entire system without sacrificing island independence.

---

### 7. Island Extinction and Rebirth

If an island stagnates for too long:

* Its population is discarded
* New individuals are generated
* Parameters are randomized
* Evolution restarts

This prevents long-term entrapment in local optima and introduces new search directions.
