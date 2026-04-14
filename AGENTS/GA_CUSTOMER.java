import java.io.File;
import java.io.FileNotFoundException;
import java.util.Random;

public class GA_CUSTOMER {

	// ── Testsieger GA-Parameter (jetzt dynamisch einstellbar) ────────────────
	static int POPULATION_SIZE = 70;
	static int MAX_GENERATIONS = 1000000;
	static double MUTATION_RATE = 0.60;
	static int TOURNAMENT_SIZE = 4;
	static int ELITISM_COUNT = 3;

	// ── Stagnationserkennung ──────────────────────────────────────
	static int STAGNATION_LIMIT = 1000;
	static double RESTART_RATIO = 0.40;

	static Random rng = new Random();

	public static void main(String[] args) {

		try {
			File file = new File("CI/AGENTS/daten3ACustomer_200_10.txt");
			if (!file.exists()) {
				file = new File("daten3ACustomer_200_10.txt");
			}
			CustomerAgent ag = new CustomerAgent(file);
			int n = ag.getContractSize();

			// ── Grid-Search Definitionen ──
			int[] popSizes = { 40, 100 };
			double[] mutRates = { 0.20, 0.90 };
			int[] stagLimits = { 500, 2000 };

			int runsPerConfig = 2; // Jeweils Durchschnitt aus mehreren Läufen berechnen
			MAX_GENERATIONS = 25000; // Weniger Generationen für die breite Parametersuche

			System.out.println("🚀 Starte Automatisches Hyperparameter Grid-Search");
			System.out.println("Teste verschiedene Kombinationen. Läufe pro Config: " + runsPerConfig
					+ ", MaxGen: " + MAX_GENERATIONS);
			System.out.println("──────────────────────────────────────────────────────────────────");

			int bestConfigFit = Integer.MAX_VALUE;
			String bestConfigDesc = "";
			int[] absoluteBestSequence = null;

			for (int pop : popSizes) {
				for (double mut : mutRates) {
					for (int stag : stagLimits) {
						// Parameter dynamisch anwenden
						POPULATION_SIZE = pop;
						MUTATION_RATE = mut;
						STAGNATION_LIMIT = stag;
						ELITISM_COUNT = Math.max(2, pop / 20); // Elitismus dynamisch anhand PopSize anpassen

						System.out.println(
								String.format("🔄 Teste Config: Pop=%3d | Mut=%.2f | StagLimit=%4d", pop, mut, stag));

						long startTime = System.currentTimeMillis();
						int bestFitForConfig = Integer.MAX_VALUE;

						for (int r = 1; r <= runsPerConfig; r++) {
							System.out.print("   -> Run " + r + "/" + runsPerConfig + " läuft... ");
							long runStart = System.currentTimeMillis();

							GAResult runResult = runGA(ag, n);

							long runTime = (System.currentTimeMillis() - runStart) / 1000;
							System.out.println("Ergebnis: " + runResult.fitness + " (" + runTime + "s)");

							if (runResult.fitness < bestFitForConfig) {
								bestFitForConfig = runResult.fitness;
							}

							if (runResult.fitness < bestConfigFit) {
								absoluteBestSequence = runResult.sequence.clone();
							}
						}

						System.out.println("   🌟 Bestes Ergebnis dieser Config: " + bestFitForConfig + "\n");

						if (bestFitForConfig < bestConfigFit) {
							bestConfigFit = bestFitForConfig;
							bestConfigDesc = String.format("Pop=%d, Mut=%.2f, StagLimit=%d", pop, mut, stag);
						}
					}
				}
			}

			System.out.println("\n════════════════════════════════════════════════════════════════════");
			System.out.println("🏆 BESTE KONFIGURATION GEFUNDEN:");
			System.out.println("   " + bestConfigDesc);
			System.out.println("   Optimum / Beste Fitness: " + bestConfigFit);
			System.out.println("   (Diese Werte jetzt als Standard festlegen für 100k Runs!)");
			System.out.println("════════════════════════════════════════════════════════════════════");
			if (absoluteBestSequence != null) {
				System.out.println("🌟 Bester gefundener Vertrag (Reihenfolge):");
				System.out.println(java.util.Arrays.toString(absoluteBestSequence));
			}
		} catch (FileNotFoundException e) {
			System.out.println(e.getMessage());
		}
	}

	static class GAResult {
		int fitness;
		int[] sequence;

		GAResult(int f, int[] s) {
			this.fitness = f;
			this.sequence = s;
		}
	}

	public static GAResult runGA(CustomerAgent ag, int n) {
		// ── 1. Population initialisieren mit NEH Heuristik
		int[][] population = new int[POPULATION_SIZE][n];
		int[] pureNEH = ag.createNEHContract();
		population[0] = pureNEH.clone();

		int seedCount = (int) (POPULATION_SIZE * 0.10); // 10% seeded
		for (int i = 1; i < seedCount; i++) {
			int[] mutatedNEH = pureNEH.clone();
			int numMutations = 1 + rng.nextInt(3);
			for (int m = 0; m < numMutations; m++)
				mutate(mutatedNEH);
			population[i] = mutatedNEH;
		}
		for (int i = seedCount; i < POPULATION_SIZE; i++) {
			population[i] = randomPermutation(n);
		}

		// ── ASYNCHRONE LOKALE SUCHE (Pathfinder Thread) ──
		java.util.concurrent.atomic.AtomicReference<int[]> globalBestContract = new java.util.concurrent.atomic.AtomicReference<>(
				pureNEH.clone());
		java.util.concurrent.atomic.AtomicInteger globalBestFit = new java.util.concurrent.atomic.AtomicInteger(
				ag.fitness(pureNEH));
		java.util.concurrent.atomic.AtomicBoolean gaRunning = new java.util.concurrent.atomic.AtomicBoolean(true);

		Thread pathfinder = new Thread(() -> {
			int threadLocalBestFit = globalBestFit.get();
			int[] currentBest = globalBestContract.get().clone();
			double T_base = (double) ag.getBaseProcessingTimeSum() / (10.0 * n * ag.getNumMachines()) * 0.4;
			double currentTemp = T_base;
			int reheatCounter = 0;

			while (gaRunning.get()) {
				// Resync mit GA, falls die Evolution ein noch besseres Peak gefunden hat
				int currentGlobalFit = globalBestFit.get();
				if (currentGlobalFit < threadLocalBestFit) {
					threadLocalBestFit = currentGlobalFit;
					currentBest = globalBestContract.get().clone();
					currentTemp = T_base;
					reheatCounter = 0;
				}

				// Dynamischer Destruction Faktor (Variable Neighborhood Destruction)
				int d = 3; 
				if (reheatCounter > 20) {
					d = 6; // Big Ruin (Ausbruchs-Versuch)
				}

				int newFit = iteratedGreedySearch(currentBest, ag, threadLocalBestFit, currentTemp, d);

				if (newFit < threadLocalBestFit) {
					threadLocalBestFit = newFit;
					reheatCounter = 0;
					if (newFit < globalBestFit.get()) {
						globalBestFit.set(newFit);
						globalBestContract.set(currentBest.clone());
					}
				} else {
					// SA Akzeptanz. Eine temporäre Verschlechterung wurde beibehalten
					threadLocalBestFit = newFit;
					reheatCounter++;
				}

				// Adaptive Cooling Schedule & Re-Heating
				currentTemp *= 0.995;
				if (reheatCounter > 50) {
					currentTemp = T_base; // Reheat!
					reheatCounter = 0;
				}
			}
		});
		pathfinder.setDaemon(true);
		pathfinder.start();

		int[] bestEver = pureNEH.clone();
		int bestEverFit = globalBestFit.get();
		int gensSinceImprovement = 0;
		int restartCount = 0;

		// ── 2. Evolutionsschleife
		for (int gen = 0; gen < MAX_GENERATIONS; gen++) {

			int[] fitnessValues = new int[POPULATION_SIZE];
			for (int i = 0; i < POPULATION_SIZE; i++) {
				fitnessValues[i] = ag.fitness(population[i]);
			}

			int bestIdx = 0;
			for (int i = 1; i < POPULATION_SIZE; i++) {
				if (fitnessValues[i] < fitnessValues[bestIdx]) {
					bestIdx = i;
				}
			}

			// Global bestes / Pathfinder auswerten
			int currentPathfinderFit = globalBestFit.get();
			if (currentPathfinderFit < bestEverFit) {
				bestEverFit = currentPathfinderFit;
				bestEver = globalBestContract.get().clone();
				gensSinceImprovement = 0;
			}

			if (fitnessValues[bestIdx] < bestEverFit) {
				bestEverFit = fitnessValues[bestIdx];
				bestEver = population[bestIdx].clone();

				// Push neuen Rekord an den Pathfinder-Thread
				globalBestFit.set(bestEverFit);
				globalBestContract.set(bestEver.clone());
				gensSinceImprovement = 0;
			} else {
				gensSinceImprovement++;
			}

			// ── Stagnationserkennung + Restarts ──
			if (gensSinceImprovement >= STAGNATION_LIMIT) {
				int replaceCount = (int) (POPULATION_SIZE * RESTART_RATIO);
				int[][] sorted = sortByFitness(population, fitnessValues);

				for (int i = 0; i < POPULATION_SIZE - replaceCount; i++) {
					population[i] = sorted[i];
				}

				int topK = Math.min(10, POPULATION_SIZE);
				for (int i = POPULATION_SIZE - replaceCount; i < POPULATION_SIZE; i++) {
					int[] mutant = sorted[rng.nextInt(topK)].clone();
					int numMutations = 5 + rng.nextInt(15);
					for (int m = 0; m < numMutations; m++) {
						mutate(mutant);
					}
					population[i] = mutant;
				}
				if (bestEver != null) {
					population[0] = bestEver.clone();
				}

				gensSinceImprovement = 0;
				restartCount++;
				continue;
			}

			// ── 3. Neue Population erzeugen ──
			int[][] newPopulation = new int[POPULATION_SIZE][n];
			int[][] sorted = sortByFitness(population, fitnessValues);

			newPopulation[0] = bestEver.clone();
			for (int i = 1; i < ELITISM_COUNT; i++) {
				newPopulation[i] = sorted[i].clone();
			}

			double currentProgress = (double) gen / MAX_GENERATIONS;
			double adaptiveMutationRate = MUTATION_RATE + (0.35 * currentProgress);
			int mutationPower = 1 + (int) (3.0 * currentProgress);

			for (int i = ELITISM_COUNT; i < POPULATION_SIZE; i++) {
				int[] parent1;
				// ── 10% Alpha-Wolf Mating (Hybride Kreuzung) ──
				if (rng.nextDouble() < 0.10) {
					parent1 = globalBestContract.get().clone();
				} else {
					parent1 = tournamentSelection(population, fitnessValues);
				}
				
				int[] parent2 = tournamentSelection(population, fitnessValues);

				int[] child = orderCrossover(parent1, parent2);

				if (rng.nextDouble() < adaptiveMutationRate) {
					for (int m = 0; m < mutationPower; m++) {
						mutate(child);
					}
				}

				newPopulation[i] = child;
			}

			population = newPopulation;
		}

		gaRunning.set(false);
		try {
			pathfinder.join(100);
		} catch (InterruptedException e) {
		}

		return new GAResult(bestEverFit, bestEver);
	}

	// ── Iterated Greedy Local Search (Ruin & Recreate) mit Simulated Annealing ──
	static int iteratedGreedySearch(int[] currentBest, CustomerAgent ag, int currentFit, double temperature, int d) {
		int n = currentBest.length;
		if (n <= d)
			return currentFit;

		// 1. Destruction (Ruin)
		int[] partial = new int[n - d];
		int[] removed = new int[d];
		boolean[] toRemove = new boolean[n];

		int count = 0;
		while (count < d) {
			int r = rng.nextInt(n);
			if (!toRemove[r]) {
				toRemove[r] = true;
				removed[count++] = currentBest[r];
			}
		}

		int pIdx = 0;
		for (int i = 0; i < n; i++) {
			if (!toRemove[i])
				partial[pIdx++] = currentBest[i];
		}

		// 2. Construction (Recreate via NEH-Prinzip)
		int[] currentContract = partial;

		for (int i = 0; i < d; i++) {
			int jobToInsert = removed[i];
			int bestPos = -1;
			int bestFit = Integer.MAX_VALUE;

			int currentLength = currentContract.length;
			int[] bestContract = new int[currentLength + 1];

			for (int pos = 0; pos <= currentLength; pos++) {
				int[] testContract = new int[currentLength + 1];
				for (int j = 0; j < pos; j++)
					testContract[j] = currentContract[j];
				testContract[pos] = jobToInsert;
				for (int j = pos; j < currentLength; j++)
					testContract[j + 1] = currentContract[j];

				// Array-Padding zum Simulieren des End-Vertrags (damit ag.fitness funktioniert)
				int[] fullContract = new int[n];
				for (int j = 0; j < testContract.length; j++)
					fullContract[j] = testContract[j];
				int remIdx = testContract.length;
				for (int k = i + 1; k < d; k++) {
					fullContract[remIdx++] = removed[k];
				}

				int fit = ag.fitness(fullContract);
				if (fit < bestFit) {
					bestFit = fit;
					bestPos = pos;
					for (int k = 0; k < testContract.length; k++)
						bestContract[k] = testContract[k];
				}
			}
			currentContract = bestContract;
		}

		int newFit = ag.fitness(currentContract);

		// 3. Acceptance Criterion (Simulated Annealing)
		if (newFit < currentFit) {
			// Besser -> direkt annehmen
			for (int i = 0; i < n; i++)
				currentBest[i] = currentContract[i];
			return newFit;
		} else {
			// Schlechter -> Mit SA-Wahrscheinlichkeit annehmen
			double prob = Math.exp(-(newFit - currentFit) / temperature);
			if (rng.nextDouble() < prob) {
				// Akzeptiert Verschlechterung für Ausbruch aus lokalem Optimum!
				for (int i = 0; i < n; i++)
					currentBest[i] = currentContract[i];
				return newFit;
			}
		}

		return currentFit; // Abgelehnt
	}

	// ── Gewichtete Mutation: Insert ist King bei asymmetrischen Problemen!
	// ────────
	static void mutate(int[] individual) {
		double r = rng.nextDouble();
		if (r < 0.30) { // 30% Swap
			swapMutation(individual);
		} else { // 70% Insert (Inversion wurde gelöscht, da toxisch für dieses Problem)
			insertMutation(individual);
		}
	}

	static void swapMutation(int[] individual) {
		int n = individual.length;
		int i = rng.nextInt(n);
		int j = rng.nextInt(n);
		int tmp = individual[i];
		individual[i] = individual[j];
		individual[j] = tmp;
	}

	static void insertMutation(int[] individual) {
		int n = individual.length;
		int from = rng.nextInt(n);
		int to = rng.nextInt(n);
		if (from == to)
			return;

		int gene = individual[from];
		if (from < to) {
			for (int i = from; i < to; i++)
				individual[i] = individual[i + 1];
		} else {
			for (int i = from; i > to; i--)
				individual[i] = individual[i - 1];
		}
		individual[to] = gene;
	}

	static void inversionMutation(int[] individual) {
		int n = individual.length;
		int start = rng.nextInt(n);
		int end = rng.nextInt(n);
		if (start > end) {
			int tmp = start;
			start = end;
			end = tmp;
		}
		while (start < end) {
			int tmp = individual[start];
			individual[start] = individual[end];
			individual[end] = tmp;
			start++;
			end--;
		}
	}

	static int[] randomPermutation(int n) {
		int[] perm = new int[n];
		for (int i = 0; i < n; i++)
			perm[i] = i;
		for (int i = n - 1; i > 0; i--) {
			int j = rng.nextInt(i + 1);
			int tmp = perm[i];
			perm[i] = perm[j];
			perm[j] = tmp;
		}
		return perm;
	}

	static int[] tournamentSelection(int[][] population, int[] fitnessValues) {
		int bestIdx = rng.nextInt(POPULATION_SIZE);
		for (int i = 1; i < TOURNAMENT_SIZE; i++) {
			int candidate = rng.nextInt(POPULATION_SIZE);
			if (fitnessValues[candidate] < fitnessValues[bestIdx])
				bestIdx = candidate;
		}
		return population[bestIdx].clone();
	}

	static int[] orderCrossover(int[] parent1, int[] parent2) {
		int n = parent1.length;
		int[] child = new int[n];
		boolean[] used = new boolean[n];

		int start = rng.nextInt(n);
		int end = rng.nextInt(n);
		if (start > end) {
			int tmp = start;
			start = end;
			end = tmp;
		}

		for (int i = start; i <= end; i++) {
			child[i] = parent1[i];
			used[parent1[i]] = true;
		}

		int insertPos = (end + 1) % n;
		for (int i = 0; i < n; i++) {
			int idx = (end + 1 + i) % n;
			int gene = parent2[idx];
			if (!used[gene]) {
				child[insertPos] = gene;
				used[gene] = true;
				insertPos = (insertPos + 1) % n;
			}
		}

		return child;
	}

	static int[][] sortByFitness(int[][] population, int[] fitnessValues) {
		int n = population.length;
		Integer[] indices = new Integer[n];
		for (int i = 0; i < n; i++)
			indices[i] = i;

		java.util.Arrays.sort(indices, (a, b) -> fitnessValues[a] - fitnessValues[b]);

		int[][] sorted = new int[n][];
		for (int i = 0; i < n; i++) {
			sorted[i] = population[indices[i]];
		}
		return sorted;
	}
}
