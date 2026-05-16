from abc import ABC, abstractmethod


class Agent(ABC):
    @abstractmethod
    def vote(self, contract: list[int], proposal: list[int]) -> bool:
        pass

    @abstractmethod
    def print_utility(self, contract: list[int]) -> None:
        pass

    @abstractmethod
    def get_contract_size(self) -> int:
        pass
