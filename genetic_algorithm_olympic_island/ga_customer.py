import datetime
import json
from pathlib import Path

from customer_agent import (
    IslandGroup,
    GaConfig,
    SelectionStrategy, CrossoverStrategy, MutationStrategy
)

def save_results(fitness: int, contract: list[int], configs: list[GaConfig], island_names: list[str]):
    timestamp = datetime.datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    filename = f"result_{fitness}_{timestamp}.txt"
    
    output = [
        "="*50,
        "GENETIC ALGORITHM OPTIMIZATION REPORT",
        f"Timestamp: {timestamp}",
        "="*50,
        f"\nFINAL BEST FITNESS: {fitness}",
        f"\nOPTIMAL CONTRACT (JOB SEQUENCE):",
        str(contract),
        "\n" + "="*50,
        "ISLAND CONFIGURATIONS USED:"
    ]
    
    for island_idx, cfg in enumerate(configs):
        island_label = island_names[island_idx] if island_idx < len(island_names) else f"Island {island_idx}"
        output.append(
            f"{island_label}: {cfg.selection_strategy.value} | "
            f"{cfg.crossover_strategy.value} | {cfg.mutation_strategy.value} "
            f"(Pop: {cfg.pop_size}, Mutation: {cfg.mutation_rate:.3f}, "
            f"LocalSearch: {cfg.local_search_rate:.3f}/{cfg.local_search_intensity})"
        )
    
    output.append("="*50)
    
    with open(filename, "w") as f:
        f.write("\n".join(output))
    print(f"\nResults saved to: {filename}")


def _load_island_config(raw_cfg: dict) -> GaConfig:
    return GaConfig(
        selection_strategy=SelectionStrategy(raw_cfg["selection"]),
        crossover_strategy=CrossoverStrategy(raw_cfg["crossover"]),
        mutation_strategy=MutationStrategy(raw_cfg["mutation"]),
        pop_size=int(raw_cfg.get("pop_size", 50)),
        mutation_rate=float(raw_cfg.get("mutation_rate", 0.20)),
        local_search_rate=float(raw_cfg.get("local_search_rate", 0.05)),
        local_search_intensity=int(raw_cfg.get("local_search_intensity", 15)),
        stagnation_threshold=int(raw_cfg.get("stagnation_threshold", 4)),
        diversity_floor=float(raw_cfg.get("diversity_floor", 0.30)),
        adaptive_mutation_boost=float(raw_cfg.get("adaptive_mutation_boost", 1.50)),
        death_chance=float(raw_cfg.get("death_chance", 0.15)),
        parameter_evolution_interval=int(raw_cfg.get("parameter_evolution_interval", 400)),
        parameter_shift_rate=float(raw_cfg.get("parameter_shift_rate", 0.10)),
    )


def load_run_config(config_path: Path) -> tuple[Path, int, int, int, list[GaConfig]]:
    with open(config_path, "r") as f:
        run_cfg = json.load(f)

    data_file = Path(run_cfg.get("data_file", Path(__file__).parent.parent / "java" / "daten3ACustomer_200_10.txt"))
    if not data_file.is_absolute():
        data_file = (config_path.parent / data_file).resolve()

    island_raw_cfgs = run_cfg.get("islands", [])
    if not island_raw_cfgs:
        raise ValueError("Config must contain at least one island.")

    island_configs = [_load_island_config(raw_cfg) for raw_cfg in island_raw_cfgs]
    total_iterations = int(run_cfg.get("total_iterations", 100_000))
    migration_interval = int(run_cfg.get("migration_interval", 500))
    olympic_interval = int(run_cfg.get("olympic_interval", 2_000))

    return data_file, total_iterations, migration_interval, olympic_interval, island_configs

def main():
    try:
        config_file = Path(__file__).with_name("config.json")
        data_file, total_iterations, migration_interval, olympic_interval, island_configs = load_run_config(config_file)

        print(f"Initializing IslandGroup with {len(island_configs)} islands...")
        group = IslandGroup(data_file, island_configs)

        print(
            f"Starting evolution for {total_iterations} iterations "
            f"(migration every {migration_interval}, olympics every {olympic_interval})..."
        )
        group.run(
            total_iterations=total_iterations,
            migration_interval=migration_interval,
            olympic_interval=olympic_interval
        )

        all_finals = []
        for pop in group.populations:
            all_finals.extend(pop)
        
        best_fitness, best_contract = min(all_finals, key=lambda x: x[0])

        print(f"\nFinal Global Solution: {best_fitness}")
        save_results(best_fitness, best_contract, group.configs, group.island_names)
        
    except FileNotFoundError as e:
        print(f"Datei nicht gefunden: {e}")
    except Exception as e:
        print(f"Ein unerwarteter Fehler ist aufgetreten: {e}")
        import traceback
        traceback.print_exc()

if __name__ == '__main__':
    main()
