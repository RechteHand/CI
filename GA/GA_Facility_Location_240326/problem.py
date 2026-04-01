from __future__ import annotations

from pathlib import Path


class Problem:
    n = 0
    m = 0
    f: list[int] = []
    t: list[list[int]] = []

    @classmethod
    def read_instance(cls, file_name: str) -> None:
        input_file = Path(file_name)
        with input_file.open("r", encoding="utf-8") as reader:
            reader.readline()
            second_line = reader.readline().strip().split()
            rows = int(second_line[0])
            cols = int(second_line[1])

            cls.n = rows
            cls.m = cols
            cls.f = [0] * rows
            cls.t = [[0] * cols for _ in range(rows)]

            for y in range(rows):
                parts = reader.readline().strip().split()
                cls.f[y] = int(parts[1])
                for x in range(cols):
                    cls.t[y][x] = int(parts[2 + x])

    @classmethod
    def print_cost(cls) -> None:
        print("Transportkosten:")
        for row in cls.t:
            print(" ".join(str(value) for value in row))

        print("Fixkosten:")
        for value in cls.f:
            print(value)
        print("-------------------------------")

    @classmethod
    def fitness(cls, gene: list[int]) -> int:
        fit = 0

        for s, bit in enumerate(gene):
            if bit == 1:
                fit += cls.f[s]

        for customer in range(cls.m):
            min_cost = 2**31 - 1
            for facility, bit in enumerate(gene):
                if bit == 1 and cls.t[facility][customer] < min_cost:
                    min_cost = cls.t[facility][customer]
            fit += min_cost

        return fit
