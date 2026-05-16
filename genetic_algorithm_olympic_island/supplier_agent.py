import random
from pathlib import Path
from agent import Agent

class SupplierAgent(Agent):
    def __init__(self, file_path: Path | str) -> None:
        file_path = Path(file_path)
        if not file_path.exists():
            raise FileNotFoundError(f"File {file_path} not found")

        with open(file_path, 'r') as f:
            lines = f.read().split()

        dim = int(lines[0])
        idx = 1
        self.cost_matrix: list[list[int]] = []
        for _ in range(dim):
            row: list[int] = []
            for _ in range(dim):
                row.append(int(lines[idx]))
                idx += 1
            self.cost_matrix.append(row)

    def vote(self, contract: list[int], proposal: list[int]) -> bool:
        cost_contract = self._evaluate(contract)
        cost_proposal = self._evaluate(proposal)
        return cost_proposal < cost_contract

    def get_contract_size(self) -> int:
        return len(self.cost_matrix)

    def print_utility(self, contract: list[int]) -> None:
        print(self._evaluate(contract), end="")

    def _evaluate(self, contract: list[int]) -> int:
        result = 0
        for i in range(len(contract) - 1):
            zeile = contract[i]
            spalte = contract[i + 1]
            result += self.cost_matrix[zeile][spalte]
        return result
