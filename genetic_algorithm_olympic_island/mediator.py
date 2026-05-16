import random

class Mediator:
    def __init__(self, contract_size_a: int, contract_size_b: int) -> None:
        if contract_size_a != contract_size_b:
            raise ValueError(
                "Verhandlung kann nicht durchgefuehrt werden, "
                "da Problemdaten nicht kompatibel"
            )
        self.contract_size = contract_size_a

    def init_contract(self) -> list[int]:
        contract = list(range(self.contract_size))
        
        for _ in range(2000):
            elem = random.randint(0, len(contract) - 2)
            contract[elem], contract[elem + 1] = contract[elem + 1], contract[elem]
            
        return contract

    def construct_proposal(self, contract: list[int]) -> list[int]:
        proposal = contract.copy()

        elem1 = random.randint(0, len(proposal) - 1)
        elem2 = random.randint(0, len(proposal) - 1)

        val1 = proposal[elem1]
        val2 = proposal[elem2]

        proposal[elem1] = val2
        proposal[elem2] = val1

        return proposal
