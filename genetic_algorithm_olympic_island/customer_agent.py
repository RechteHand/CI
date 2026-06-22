import math
import multiprocessing as mp
import random
from dataclasses import dataclass, replace
from enum import Enum
from pathlib import Path

from agent import Agent


class SelectionStrategy(Enum):
    Tournament = "Tournament"
    RankBased = "RankBased"
    Roulette = "Roulette"


class CrossoverStrategy(Enum):
    OrderCrossover = "OrderCrossover"
    PartiallyMappedCrossover = "PartiallyMappedCrossover"
    CycleCrossover = "CycleCrossover"


class MutationStrategy(Enum):
    InversionMutation = "InversionMutation"
    InsertionMutation = "InsertionMutation"
    ScrambleMutation = "ScrambleMutation"


@dataclass
class GaConfig:
    selection_strategy: SelectionStrategy
    crossover_strategy: CrossoverStrategy
    mutation_strategy: MutationStrategy
    pop_size: int = 50
    iterations: int = 100_000
    mutation_rate: float = 0.20
    local_search_rate: float = 0.05
    local_search_intensity: int = 15
    stagnation_threshold: int = 4
    diversity_floor: float = 0.30
    adaptive_mutation_boost: float = 1.50
    death_chance: float = 0.15
    parameter_evolution_interval: int = 400
    parameter_shift_rate: float = 0.10


def _clamp(value: float, lower: float, upper: float) -> float:
    return max(lower, min(upper, value))


class CustomerAgent(Agent):
    def __init__(self, file_path: Path | str) -> None:
        file_path = Path(file_path)
        if not file_path.exists():
            raise FileNotFoundError(f"File {file_path} not found")

        with open(file_path, "r") as f:
            lines = f.read().split()

        if not lines:
            raise ValueError("Empty file")

        jobs = int(lines[0])
        machines = int(lines[1])
        matrix_idx = 2

        self.time_matrix: list[list[int]] = []
        for _ in range(jobs):
            row: list[int] = []
            for _ in range(machines):
                row.append(int(lines[matrix_idx]))
                matrix_idx += 1
            self.time_matrix.append(row)

        self.delay_matrix = self._calculate_delay(jobs)

    def get_contract_size(self) -> int:
        return len(self.time_matrix)

    def print_utility(self, contract: list[int]) -> None:
        print(self.fitness(contract), end="")

    def vote(self, contract: list[int], proposal: list[int]) -> bool:
        return self.fitness(proposal) < self.fitness(contract)

    def _calculate_delay(self, job_nr: int) -> list[list[int]]:
        delay_matrix = [[0] * job_nr for _ in range(job_nr)]
        machines = len(self.time_matrix[0])

        for h in range(job_nr):
            for j in range(job_nr):
                if h != j:
                    max_wait = 0
                    for machine in range(machines):
                        time1 = sum(self.time_matrix[h][k] for k in range(machine + 1))
                        time2 = sum(
                            self.time_matrix[j][k - 1] for k in range(1, machine + 1)
                        )
                        wait_h_j_machine = max(time1 - time2, 0)
                        if wait_h_j_machine > max_wait:
                            max_wait = wait_h_j_machine
                    delay_matrix[h][j] = max_wait
        return delay_matrix

    def evolve_population(
        self, ga_config: GaConfig, population: list[tuple[int, list[int]]], steps: int
    ) -> list[tuple[int, list[int]]]:
        """Evolves a population over a number of generations"""
        current_pop = population
        for _ in range(steps):
            current_pop = self._next_generation(
                ga_config, current_pop, ga_config.pop_size
            )
        return current_pop

    def _next_generation(
        self,
        ga_config: GaConfig,
        population: list[tuple[int, list[int]]],
        pop_size: int,
    ) -> list[tuple[int, list[int]]]:
        """Creates the next population generation: selection, crossover, mutation, and local search"""
        best_individual = min(population, key=lambda item: item[0])
        children: list[tuple[int, list[int]]] = [best_individual]

        while len(children) < pop_size:
            parent_a = population[
                self._run_selection(ga_config.selection_strategy, population)
            ][1]
            parent_b = population[
                self._run_selection(ga_config.selection_strategy, population)
            ][1]

            for child in self._crossover(
                ga_config.crossover_strategy, parent_a, parent_b
            ):
                if random.random() < ga_config.mutation_rate:
                    child = self._mutate(ga_config.mutation_strategy, child)

                if random.random() < ga_config.local_search_rate:
                    child = self._local_search(
                        child, intensity=ga_config.local_search_intensity
                    )

                children.append((self.fitness(child), child))
                if len(children) == pop_size:
                    break
        return children

    def _local_search(self, individual: list[int], intensity: int) -> list[int]:
        """Improves an individual using neighborhood search with simulated annealing elements."""
        current_individual = individual.copy()
        current_fitness = self.fitness(current_individual)
        best_individual = current_individual.copy()
        best_fitness = current_fitness
        no_gain_steps = 0
        temperature = max(1.0, len(individual) / 6.0)

        for _ in range(max(3, intensity)):
            candidate = current_individual.copy()
            if random.random() < 0.7:
                swap_a, swap_b = random.sample(range(len(candidate)), 2)
                candidate[swap_a], candidate[swap_b] = (
                    candidate[swap_b],
                    candidate[swap_a],
                )
            else:
                seg_start, seg_end = sorted(random.sample(range(len(candidate)), 2))
                segment = candidate[seg_start : seg_end + 1]
                random.shuffle(segment)
                candidate[seg_start : seg_end + 1] = segment

            candidate_fitness = self.fitness(candidate)
            delta = candidate_fitness - current_fitness
            accept_worse = False
            if delta > 0:
                accept_worse = random.random() < math.exp(
                    -delta / max(temperature, 1e-9)
                )

            if delta < 0 or accept_worse:
                current_individual = candidate
                current_fitness = candidate_fitness

                if candidate_fitness < best_fitness:
                    best_individual = candidate.copy()
                    best_fitness = candidate_fitness
                    no_gain_steps = 0
                else:
                    no_gain_steps += 1
            else:
                no_gain_steps += 1

            if no_gain_steps >= max(3, intensity // 3):
                candidate_restart = self._mutate_scramble(current_individual.copy())
                restart_fitness = self.fitness(candidate_restart)
                if restart_fitness <= current_fitness:
                    current_individual = candidate_restart
                    current_fitness = restart_fitness
                no_gain_steps = 0

            temperature *= 0.92

        return best_individual

    def _run_selection(
        self, strategy: SelectionStrategy, population: list[tuple[int, list[int]]]
    ) -> int:
        match strategy:
            case SelectionStrategy.Tournament:
                return self._tournament_selection(population)
            case SelectionStrategy.RankBased:
                return self._rank_selection(population)
            case SelectionStrategy.Roulette:
                return self._roulette_selection(population)

    def _tournament_selection(
        self, population: list[tuple[int, list[int]]], k: int = 5
    ) -> int:
        """Selects the best individual from a random subset"""
        candidates = [random.randrange(len(population)) for _ in range(k)]
        return min(candidates, key=lambda i: population[i][0])

    def _rank_selection(self, population: list[tuple[int, list[int]]]) -> int:
        """Selects an individual based on rank-weighted probabilities"""
        sorted_indices = sorted(range(len(population)), key=lambda i: population[i][0])
        n = len(population)
        weights = [n - i for i in range(n)]
        return random.choices(sorted_indices, weights=weights, k=1)[0]

    def _roulette_selection(self, population: list[tuple[int, list[int]]]) -> int:
        """Selects an individual proportionally to fitness (inverse)"""
        fitness_values = [ind[0] for ind in population]
        max_fit = max(fitness_values)
        min_fit = min(fitness_values)
        probs = [(max_fit - f) + (max_fit - min_fit) * 0.1 for f in fitness_values]
        if sum(probs) == 0:
            return random.randrange(len(population))
        return random.choices(range(len(population)), weights=probs, k=1)[0]

    def _crossover(
        self, strategy: CrossoverStrategy, p_a: list[int], p_b: list[int]
    ) -> tuple[list[int], list[int]]:
        match strategy:
            case CrossoverStrategy.OrderCrossover:
                return self._order_crossover(p_a, p_b)
            case CrossoverStrategy.PartiallyMappedCrossover:
                return self._pmx_crossover(p_a, p_b)
            case CrossoverStrategy.CycleCrossover:
                return self._cycle_crossover(p_a, p_b)

    def _order_crossover(
        self, p_a: list[int], p_b: list[int]
    ) -> tuple[list[int], list[int]]:
        size = len(p_a)

        def gen(p1, p2):
            start, end = sorted(random.sample(range(size), 2))
            child = [None] * size
            child[start:end] = p1[start:end]
            p2_idx = 0
            for i in range(size):
                fill_idx = (end + i) % size
                if child[fill_idx] is None:
                    while p2[p2_idx] in child:
                        p2_idx += 1
                    child[fill_idx] = p2[p2_idx]
            return child

        return gen(p_a, p_b), gen(p_b, p_a)

    def _pmx_crossover(
        self, p_a: list[int], p_b: list[int]
    ) -> tuple[list[int], list[int]]:
        """partially mapped crossover"""
        size = len(p_a)

        def gen(p1, p2):
            start, end = sorted(random.sample(range(size), 2))
            child = [None] * size
            child[start:end] = p1[start:end]
            for i in range(start, end):
                if p2[i] not in child:
                    mapped_value = p2[i]
                    current_idx = i
                    while start <= current_idx < end:
                        current_idx = p2.index(p1[current_idx])
                    child[current_idx] = mapped_value
            for i in range(size):
                if child[i] is None:
                    child[i] = p2[i]
            return child

        return gen(p_a, p_b), gen(p_b, p_a)

    def _cycle_crossover(
        self, p_a: list[int], p_b: list[int]
    ) -> tuple[list[int], list[int]]:
        size = len(p_a)

        def gen(p1, p2):
            child = [None] * size
            while None in child:
                start = next(i for i, v in enumerate(child) if v is None)
                current_idx = start
                cycle = []
                while current_idx not in cycle:
                    cycle.append(current_idx)
                    current_idx = p1.index(p2[current_idx])
                for cycle_idx in cycle:
                    child[cycle_idx] = p1[cycle_idx]
                for i in range(size):
                    if child[i] is None:
                        child[i] = p2[i]
                break
            return child

        return gen(p_a, p_b), gen(p_b, p_a)

    def _mutate(self, strategy: MutationStrategy, individual: list[int]) -> list[int]:
        match strategy:
            case MutationStrategy.InversionMutation:
                return self._mutate_inversion(individual)
            case MutationStrategy.InsertionMutation:
                return self._mutate_insertion(individual)
            case MutationStrategy.ScrambleMutation:
                return self._mutate_scramble(individual)
        return individual

    def _mutate_inversion(self, ind: list[int]) -> list[int]:
        """Randomly shuffles a selected subsequence."""
        start, end = sorted(random.sample(range(len(ind)), 2))
        ind[start : end + 1] = ind[start : end + 1][::-1]
        return ind

    def _mutate_insertion(self, ind: list[int]) -> list[int]:
        val = ind.pop(random.randrange(len(ind)))
        ind.insert(random.randrange(len(ind)), val)
        return ind

    def _mutate_scramble(self, ind: list[int]) -> list[int]:
        start, end = sorted(random.sample(range(len(ind)), 2))
        sub = ind[start : end + 1]
        random.shuffle(sub)
        ind[start : end + 1] = sub
        return ind

    def fitness(self, contract: list[int]) -> int:
        weight_sum = 0
        for i in range(1, len(contract)):
            weight_sum += self.delay_matrix[contract[i - 1]][contract[i]] * (
                len(contract) - i
            )
        return weight_sum + sum(sum(row) for row in self.time_matrix)

    def init_contract(self) -> list[int]:
        contract = list(range(len(self.time_matrix)))
        random.shuffle(contract)
        return contract


def evolve_island_task(agent, config, population, steps):
    return agent.evolve_population(config, population, steps)


class IslandGroup:
    def __init__(self, file_path: Path | str, configs: list[GaConfig]):
        self.agent = CustomerAgent(file_path)
        self.configs = configs
        self.populations = [
            [
                (self.agent.fitness(ind), ind)
                for ind in (self.agent.init_contract() for _ in range(cfg.pop_size))
            ]
            for cfg in configs
        ]
        self.island_generation = [1 for _ in configs]
        self.island_names = [
            self._compose_island_name(island_idx, 1)
            for island_idx in range(len(configs))
        ]
        self.best_scores = [
            min(population, key=lambda item: item[0])[0]
            for population in self.populations
        ]
        self.stagnant_cycles = [0 for _ in configs]
        self.diversity_scores = [
            self._population_diversity(population) for population in self.populations
        ]
        self.current_mutation_rates = [cfg.mutation_rate for cfg in configs]
        self.olympic_config = GaConfig(
            SelectionStrategy.Tournament,
            CrossoverStrategy.OrderCrossover,
            MutationStrategy.InversionMutation,
            pop_size=30,
        )
        self.olympic_population = []

    def run(
        self,
        total_iterations: int = 100_000,
        migration_interval: int = 500,
        olympic_interval: int = 2000,
    ):
        num_cores = min(len(self.configs), mp.cpu_count())

        with mp.Pool(processes=num_cores) as pool:
            for iteration in range(0, total_iterations, migration_interval):
                self._evolve_island_parameters(iteration)
                runtime_configs = self._build_runtime_configs()
                tasks = [
                    (
                        self.agent,
                        runtime_configs[island_idx],
                        self.populations[island_idx],
                        migration_interval,
                    )
                    for island_idx in range(len(self.configs))
                ]
                self.populations = pool.starmap(evolve_island_task, tasks)
                self._refresh_island_metrics()
                self._retire_stagnant_islands()

                current_best = min(self.best_scores)
                print(f"Iter {iteration} | Global Best: {current_best}")
                self._print_island_state()

                self._migrate_ring()

                if iteration % olympic_interval == 0 and iteration > 0:
                    self._run_olympics()

    def _migrate_ring(self):
        for island_idx in range(len(self.populations)):
            next_island_idx = (island_idx + 1) % len(self.populations)
            migrants = [
                (fitness, contract.copy())
                for fitness, contract in sorted(
                    self.populations[island_idx], key=lambda item: item[0]
                )[:2]
            ]
            self.populations[next_island_idx].sort(key=lambda item: item[0])
            self.populations[next_island_idx][-2:] = migrants

    def _run_olympics(self):
        print("--- Archipelago Olympic Games Started ---")
        champions = [
            (fitness, contract.copy())
            for fitness, contract in [
                min(population, key=lambda item: item[0])
                for population in self.populations
            ]
        ]
        self.olympic_population.extend(champions)

        self.olympic_population.sort(key=lambda item: item[0])
        self.olympic_population = self.olympic_population[
            : self.olympic_config.pop_size
        ]

        self.olympic_population = self.agent.evolve_population(
            self.olympic_config, self.olympic_population, 200
        )

        gold_medalist = self.olympic_population[0]
        print(f"Olympic Gold Medalist Fitness: {gold_medalist[0]}")

        for population in self.populations:
            population.sort(key=lambda item: item[0])
            population[-1] = (gold_medalist[0], gold_medalist[1].copy())

    def _print_island_state(self):
        state_parts = []
        for island_idx, island_name in enumerate(self.island_names):
            state_parts.append(
                f"{island_name}: mu={self.current_mutation_rates[island_idx]:.3f}, "
                f"div={self.diversity_scores[island_idx]:.3f}, stg={self.stagnant_cycles[island_idx]}"
            )
        print(" | ".join(state_parts))

    def _refresh_island_metrics(self):
        for island_idx, population in enumerate(self.populations):
            best_fitness = min(population, key=lambda item: item[0])[0]
            if best_fitness < self.best_scores[island_idx]:
                self.best_scores[island_idx] = best_fitness
                self.stagnant_cycles[island_idx] = 0
            else:
                self.stagnant_cycles[island_idx] += 1
            self.diversity_scores[island_idx] = self._population_diversity(population)

    def _population_diversity(
        self, population: list[tuple[int, list[int]]], sample_size: int = 12
    ) -> float:
        if len(population) < 2:
            return 1.0

        sampled_population = random.sample(
            population, min(sample_size, len(population))
        )
        sampled_contracts = [contract for _, contract in sampled_population]
        job_count = len(sampled_contracts[0])

        if job_count == 0:
            return 0.0

        pair_count = 0
        diff_sum = 0.0
        for first_idx in range(len(sampled_contracts)):
            for second_idx in range(first_idx + 1, len(sampled_contracts)):
                diff_positions = sum(
                    1
                    for pos_idx in range(job_count)
                    if sampled_contracts[first_idx][pos_idx]
                    != sampled_contracts[second_idx][pos_idx]
                )
                diff_sum += diff_positions / job_count
                pair_count += 1

        if pair_count == 0:
            return 0.0
        return diff_sum / pair_count

    def _build_runtime_configs(self) -> list[GaConfig]:
        runtime_configs: list[GaConfig] = []
        for island_idx, config in enumerate(self.configs):
            stagnation_steps = self.stagnant_cycles[island_idx]
            diversity = self.diversity_scores[island_idx]

            mutation_scale = (
                1.0 + min(stagnation_steps, config.stagnation_threshold * 2) * 0.05
            )
            if stagnation_steps >= config.stagnation_threshold:
                mutation_scale *= config.adaptive_mutation_boost
            if diversity < config.diversity_floor:
                diversity_gap = config.diversity_floor - diversity
                mutation_scale *= 1.0 + diversity_gap * 2.5

            runtime_mutation_rate = _clamp(
                config.mutation_rate * mutation_scale, 0.02, 0.95
            )
            self.current_mutation_rates[island_idx] = runtime_mutation_rate
            runtime_configs.append(replace(config, mutation_rate=runtime_mutation_rate))

        return runtime_configs

    def _evolve_island_parameters(self, iteration: int):
        for island_idx, config in enumerate(self.configs):
            if iteration <= 0:
                continue
            if iteration % config.parameter_evolution_interval != 0:
                continue

            jitter = config.parameter_shift_rate
            config.mutation_rate = _clamp(
                config.mutation_rate * (1 + random.uniform(-jitter, jitter)), 0.03, 0.80
            )
            config.local_search_rate = _clamp(
                config.local_search_rate * (1 + random.uniform(-jitter, jitter)),
                0.01,
                0.40,
            )
            config.diversity_floor = _clamp(
                config.diversity_floor * (1 + random.uniform(-jitter, jitter)),
                0.08,
                0.85,
            )
            config.adaptive_mutation_boost = _clamp(
                config.adaptive_mutation_boost * (1 + random.uniform(-jitter, jitter)),
                1.05,
                3.00,
            )

            evolved_intensity = int(
                round(
                    config.local_search_intensity
                    * (1 + random.uniform(-jitter, jitter))
                )
            )
            config.local_search_intensity = max(5, min(80, evolved_intensity))

            print(
                f"[Param Evolution] {self.island_names[island_idx]} -> "
                f"base_mu={config.mutation_rate:.3f}, ls_rate={config.local_search_rate:.3f}, "
                f"ls_int={config.local_search_intensity}"
            )

    def _retire_stagnant_islands(self):
        for island_idx, config in enumerate(self.configs):
            if self.stagnant_cycles[island_idx] < config.stagnation_threshold:
                continue
            if random.random() >= config.death_chance:
                continue
            self._retire_and_respawn_island(island_idx)

    def _retire_and_respawn_island(self, island_idx: int):
        retired_name = self.island_names[island_idx]
        retirement_lines = [
            "missed the podium one season too many",
            "failed the anti-doping test for too much local optimum",
        ]
        reborn_titles = [
            "Torch Relay Cay",
            "New Podium Key",
        ]

        self.island_generation[island_idx] += 1
        reborn_name = (
            f"{random.choice(reborn_titles)} {self.island_generation[island_idx]}"
        )
        self.island_names[island_idx] = reborn_name

        config = self.configs[island_idx]
        self._randomize_island_config(config)
        self.populations[island_idx] = [
            (self.agent.fitness(individual), individual)
            for individual in (
                self.agent.init_contract() for _ in range(config.pop_size)
            )
        ]
        self.best_scores[island_idx] = min(
            self.populations[island_idx], key=lambda item: item[0]
        )[0]
        self.stagnant_cycles[island_idx] = 0
        self.diversity_scores[island_idx] = self._population_diversity(
            self.populations[island_idx]
        )

        print(
            f"[Island Retired] {retired_name} {random.choice(retirement_lines)}. "
            f"{reborn_name} enters the Olympic archipelago."
        )

    def _randomize_island_config(self, config: GaConfig):
        config.mutation_rate = _clamp(
            config.mutation_rate * random.uniform(0.80, 1.25), 0.05, 0.85
        )
        config.local_search_rate = _clamp(
            config.local_search_rate * random.uniform(0.80, 1.25), 0.01, 0.45
        )
        config.local_search_intensity = max(
            5,
            min(
                90,
                int(round(config.local_search_intensity * random.uniform(0.80, 1.25))),
            ),
        )
        config.diversity_floor = _clamp(
            config.diversity_floor * random.uniform(0.90, 1.15), 0.08, 0.85
        )
        config.adaptive_mutation_boost = _clamp(
            config.adaptive_mutation_boost * random.uniform(0.90, 1.20), 1.05, 3.20
        )

    def _compose_island_name(self, island_idx: int, generation: int) -> str:
        island_prefixes = [
            "Greek",
            "Spartan",
            "Athenian",
            "Roman",
            "Macedonian",
            "Trojan",
            "Ionian",
            "Dorian",
        ]
        island_crews = [
            "Ringers",
            "Boxers",
            "Rowers",
            "Wrestlers",
            "Torchbearers",
            "Medalists",
            "Triathletes",
            "Soldiers",
        ]
        prefix = island_prefixes[island_idx % len(island_prefixes)]
        crew = island_crews[island_idx % len(island_crews)]
        return f"{prefix} {crew} (gen {generation})"
