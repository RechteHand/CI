from __future__ import annotations

from pathlib import Path
import math #neu

class Problem:
    # n = 0
    # m = 0
    # f: list[int] = []
    # t: list[list[int]] = []

    bitstring_length = 22 #neu

    # Intervall xE[-1, 2]
    x_min = -1 #neu
    x_max = 2 # neu 
    
    @classmethod
    def decode(cls, gene: list[int]) -> float: #Hilfsfunktion für Decodierung
        zahl = 0
        for bit in gene:
            zahl = zahl * 2 + bit

        x = cls.x_min + zahl / (2**cls.bitstring_length - 1) * (cls.x_max - cls.x_min)
        return x
    
    @classmethod
    def fitness(cls, gene: list[int]) -> float:
        x = cls.decode(gene)
        z = x * math.sin(10 * math.pi * x) + 1 # Zielfunktion
        return z


    """
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
    """
