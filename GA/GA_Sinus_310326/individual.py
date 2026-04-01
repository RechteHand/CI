from __future__ import annotations

import random
from problem import Problem

class Individual:
    def __init__(self):
        #self.problem_size = problem_size
        self.bits = [0] * Problem.bitstring_length
        self.fitness_value = 0
        self.p_mut = 1.0 / Problem.bitstring_length

    def mutation(self) -> None:
        for i in range(len(self.bits)):
            if random.random() < self.p_mut:
                self.bits[i] = 1 if self.bits[i] == 0 else 0

    @staticmethod
    def crossover(papa: "Individual", mama: "Individual", son: "Individual", daughter: "Individual") -> None:
        crosspoint = random.randrange(0, len(papa.bits))

        for i in range(crosspoint):
            son.bits[i] = papa.bits[i]
            daughter.bits[i] = mama.bits[i]

        for i in range(crosspoint, len(papa.bits)):
            son.bits[i] = mama.bits[i]
            daughter.bits[i] = papa.bits[i]

    def output(self) -> None:
        print("".join(str(bit) for bit in self.bits), self.fitness_value)

    def initialize(self) -> None:
        count = 0
        for i in range(len(self.bits)):
            self.bits[i] = 0
            if random.random() < 0.5:
                self.bits[i] = 1
                count += 1

        #if count == 0:
        #    nr = random.randrange(0, len(self.bits))
        #    self.bits[nr] = 1

    def fitness(self) -> None:
        self.fitness_value = Problem.fitness(self.bits)

    def reproduce(self, template: "Individual") -> None:
        for i in range(len(self.bits)):
            self.bits[i] = template.bits[i]
        self.fitness_value = template.fitness_value
        self.p_mut = template.p_mut
