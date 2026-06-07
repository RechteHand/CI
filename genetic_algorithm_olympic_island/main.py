from pathlib import Path
import os
from agent import Agent
from supplier_agent import SupplierAgent
from customer_agent import CustomerAgent
from mediator import Mediator

def print_utilities(a1: Agent, a2: Agent, round_num: int, contract: list[int]) -> None:
    print(f"{round_num} -> ", end="")
    a1.print_utility(contract)
    print("  ", end="")
    a2.print_utility(contract)
    print()

def main():
    try:
        data_dir = Path(__file__).parent.parent / 'java'

        ag_a = SupplierAgent(data_dir / 'daten3ASupplier_200.txt')
        ag_b = CustomerAgent(data_dir / 'daten3ACustomer_200_10.txt')

        med = Mediator(ag_a.get_contract_size(), ag_b.get_contract_size())

        contract = med.init_contract()
        for i in contract:
            print(f"{i}-", end="")
        print()

        max_rounds = 0

        print_utilities(ag_a, ag_b, 0, contract)

        for round_num in range(1, max_rounds):
            proposal = med.construct_proposal(contract)
            vote_a = ag_a.vote(contract, proposal)
            vote_b = ag_b.vote(contract, proposal)

            vote_a = True
            if vote_a and vote_b:
                contract = proposal
                print_utilities(ag_a, ag_b, round_num, contract)

    except FileNotFoundError as e:
        print(e)
    except Exception as e:
        print(f"An unexpected error occurred: {e}")

if __name__ == '__main__':
    main()
