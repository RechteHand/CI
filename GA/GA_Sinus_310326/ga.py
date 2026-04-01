from __future__ import annotations

import random

from individual import Individual
from problem import Problem


class GA:
    pop_size = 100
    number_of_iterations = 10000

    @staticmethod
    def selection(individuals: list[Individual]) -> int:
        index1 = random.randrange(0, len(individuals))
        index2 = random.randrange(0, len(individuals))

        if individuals[index1].fitness_value > individuals[index2].fitness_value:
            return index1
        return index2

    @staticmethod
    def best_individual(individuals: list[Individual], best: Individual) -> None:
        for individual in individuals:
            if individual.fitness_value > best.fitness_value:
                best.reproduce(individual)


def main() -> None:
    #Problem.read_instance("ga750a-1.txt")

    best = Individual()
    best.initialize()
    best.fitness()
    print(f"best solution: {best.fitness_value}")

    population = []
    children = []

    for _ in range(GA.pop_size):
        individual = Individual()
        individual.initialize()
        individual.fitness()
        population.append(individual)

    GA.best_individual(population, best)

    for iteration in range(1, GA.number_of_iterations + 1):
        children = []

        for _ in range(0, GA.pop_size, 2):
            parent_index1 = GA.selection(population)
            parent_index2 = GA.selection(population)

            son = Individual()
            daughter = Individual()

            Individual.crossover(population[parent_index1], population[parent_index2], son, daughter)

            son.mutation()
            daughter.mutation()

            son.fitness()
            daughter.fitness()

            children.append(son)
            children.append(daughter)

        population = children
        GA.best_individual(population, best)
        print(iteration, best.fitness_value)

    print()
    best.output()


if __name__ == "__main__":
    main()
