import java.io.File;
import java.io.FileNotFoundException;
import java.util.Random;

public class GA_CUSTOMER {

	// ── Testsieger GA-Parameter (jetzt dynamisch einstellbar) ────────────────
	static int POPULATION_SIZE = 70;
	static int MAX_GENERATIONS = 80000;
	static double MUTATION_RATE = 0.60;
	static int TOURNAMENT_SIZE = 4;
	static int ELITISM_COUNT = 3;

	// ── Stagnationserkennung ──────────────────────────────────────
	static int STAGNATION_LIMIT = 1200;
	static double RESTART_RATIO = 0.40;

	static Random rng = new Random();

	public static void main(String[] args) {

		try {
			File file = new File("/Users/max/Documents/CI/AGENTS/daten3ACustomer_200_10.txt");
			if (!file.exists()) {
				file = new File("daten3ACustomer_200_10.txt");
			}
			CustomerAgent ag = new CustomerAgent(file);
			int n = ag.getContractSize();

			// ── Grid-Search Definitionen ──
			int[] popSizes = { 50, 70 };
			double[] mutRates = { 0.40, 0.60 };
			int[] stagLimits = { 500, 1200 };

			int runsPerConfig = 2; // Jeweils Durchschnitt aus mehreren Läufen berechnen
			MAX_GENERATIONS = 25000; // Weniger Generationen für die breite Parametersuche

			System.out.println("🚀 Starte Automatisches Hyperparameter Grid-Search");
			System.out.println("Teste verschiedene Kombinationen. Läufe pro Config: " + runsPerConfig
					+ ", MaxGen: " + MAX_GENERATIONS);
			System.out.println("──────────────────────────────────────────────────────────────────");

			int bestConfigFit = Integer.MAX_VALUE;
			String bestConfigDesc = "";

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

							int runResult = runGA(ag, n);

							long runTime = (System.currentTimeMillis() - runStart) / 1000;
							System.out.println("Ergebnis: " + runResult + " (" + runTime + "s)");

							if (runResult < bestFitForConfig) {
								bestFitForConfig = runResult;
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
		} catch (FileNotFoundException e) {
			System.out.println(e.getMessage());
		}
	}

	public static int runGA(CustomerAgent ag, int n) {
		// ── 1. Population initialisieren
		int[][] population = new int[POPULATION_SIZE][n];
		for (int i = 0; i < POPULATION_SIZE; i++) {
			population[i] = randomPermutation(n);
		}

		int[] bestEver = null;
		int bestEverFit = Integer.MAX_VALUE;
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

			// Global bestes auswerten
			if (fitnessValues[bestIdx] < bestEverFit) {
				bestEverFit = fitnessValues[bestIdx];
				bestEver = population[bestIdx].clone();
				gensSinceImprovement = 0;

				// ── Starke lokale Suche (Hill-Climbing) auf neuen Champion anwenden ──
				int lsResult = localSearch(bestEver, ag, bestEverFit);
				if (lsResult < bestEverFit) {
					bestEverFit = lsResult;
				}
			} else {
				gensSinceImprovement++;
			}

			// ── NEU: Periodischer Memetic Pulse (VNS Lamarckian Learning) ──
			// Wir "ziehen" den besten der aktuellen Generation alle 250 Generationen
			// ins tiefstmögliche Tal und lassen ihn das gelernte DNA-Wissen zurückgeben!
			if (gen > 0 && gen % 250 == 0) {
				int[] localChamp = population[bestIdx].clone();
				int lsResult = localSearch(localChamp, ag, fitnessValues[bestIdx]);

				if (lsResult < fitnessValues[bestIdx]) {
					// Lamarck'sche Evolution: Der Champion darf sein in der Lebenszeit
					// angeeignetes Wissen in den Genpool weitergeben!
					population[bestIdx] = localChamp;
					fitnessValues[bestIdx] = lsResult;

					// Prüfen, ob er durch das Training den Weltrekord geknackt hat
					if (lsResult < bestEverFit) {
						bestEverFit = lsResult;
						bestEver = localChamp.clone();
						gensSinceImprovement = 0;
					}
				}
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
				int[] parent1 = tournamentSelection(population, fitnessValues);
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

		return bestEverFit;
	}

	// ── Lokale Suche: VNS (Variable Neighborhood Search) mit Delta-Fitness ──
	// Swap-Delta: O(1). Insert-Delta: O(|to-from|). Kein Full-Fitness-Recompute
	// mehr.
	static int localSearch(int[] individual, CustomerAgent ag, int currentBestFit) {
		int n = individual.length;
		int[][] delay = ag.getDelayMatrix();
		int currentFit = currentBestFit;
		boolean improved = true;

		while (improved) {
			improved = false;

			// ── NEIGHBORHOOD 1: FULL INSERT (Delta-Fitness) ──
			outerInsert: for (int from = 0; from < n; from++) {
				for (int to = 0; to < n; to++) {
					if (from == to)
						continue;

					int newFit = insertFit(individual, from, to, currentFit, delay, n);

					if (newFit < currentFit) {
						applyInsert(individual, from, to);
						currentFit = newFit;
						improved = true;
						break outerInsert;
					}
				}
			}

			// ── NEIGHBORHOOD 2: FULL SWAP (Delta-Fitness) ──
			if (!improved) {
				outerSwap: for (int i = 0; i < n - 1; i++) {
					for (int j = i + 1; j < n; j++) {

						int newFit = swapFit(individual, i, j, currentFit, delay, n);

						if (newFit < currentFit) {
							int tmp = individual[i];
							individual[i] = individual[j];
							individual[j] = tmp;
							currentFit = newFit;
							improved = true;
							break outerSwap;
						}
					}
				}
			}
		}
		return currentFit;
	}

	// Delta-Fitness für Swap(i, j) mit i < j. O(1).
	static int swapFit(int[] p, int i, int j, int curFit, int[][] delay, int n) {
		int a = p[i], b = p[j];
		int oldSum = 0, newSum = 0;

		if (i > 0) {
			int prev = p[i - 1];
			oldSum += delay[prev][a] * (n - i);
			newSum += delay[prev][b] * (n - i);
		}
		if (j == i + 1) {
			oldSum += delay[a][b] * (n - j);
			newSum += delay[b][a] * (n - j);
		} else {
			int nextI = p[i + 1];
			oldSum += delay[a][nextI] * (n - i - 1);
			newSum += delay[b][nextI] * (n - i - 1);
			int prevJ = p[j - 1];
			oldSum += delay[prevJ][b] * (n - j);
			newSum += delay[prevJ][a] * (n - j);
		}
		if (j + 1 < n) {
			int nextJ = p[j + 1];
			oldSum += delay[b][nextJ] * (n - j - 1);
			newSum += delay[a][nextJ] * (n - j - 1);
		}
		return curFit - oldSum + newSum;
	}

	// Delta-Fitness für Insert(from → to). O(|to - from|).
	static int insertFit(int[] p, int from, int to, int curFit, int[][] delay, int n) {
		int oldSum = 0, newSum = 0;
		if (from < to) {
			if (from > 0) {
				oldSum += delay[p[from - 1]][p[from]] * (n - from);
				newSum += delay[p[from - 1]][p[from + 1]] * (n - from);
			}
			for (int k = from + 1; k < to; k++) {
				oldSum += delay[p[k - 1]][p[k]] * (n - k);
				newSum += delay[p[k]][p[k + 1]] * (n - k);
			}
			oldSum += delay[p[to - 1]][p[to]] * (n - to);
			newSum += delay[p[to]][p[from]] * (n - to);
			if (to + 1 < n) {
				oldSum += delay[p[to]][p[to + 1]] * (n - to - 1);
				newSum += delay[p[from]][p[to + 1]] * (n - to - 1);
			}
		} else {
			if (to > 0) {
				oldSum += delay[p[to - 1]][p[to]] * (n - to);
				newSum += delay[p[to - 1]][p[from]] * (n - to);
			}
			oldSum += delay[p[to]][p[to + 1]] * (n - to - 1);
			newSum += delay[p[from]][p[to]] * (n - to - 1);
			for (int k = to + 2; k <= from; k++) {
				oldSum += delay[p[k - 1]][p[k]] * (n - k);
				newSum += delay[p[k - 2]][p[k - 1]] * (n - k);
			}
			if (from + 1 < n) {
				oldSum += delay[p[from]][p[from + 1]] * (n - from - 1);
				newSum += delay[p[from - 1]][p[from + 1]] * (n - from - 1);
			}
		}
		return curFit - oldSum + newSum;
	}

	static void applyInsert(int[] p, int from, int to) {
		int gene = p[from];
		if (from < to) {
			for (int i = from; i < to; i++)
				p[i] = p[i + 1];
		} else {
			for (int i = from; i > to; i--)
				p[i] = p[i - 1];
		}
		p[to] = gene;
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
		// Kein clone(): orderCrossover liest die Parents nur, mutiert sie nicht.
		return population[bestIdx];
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
