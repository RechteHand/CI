import java.io.File;
import java.io.FileNotFoundException;
import java.util.Random;

public class GA_CUSTOMER {

	// ── Testsieger GA-Parameter (Grid-Search Ergebnis: Pop=80, Mut=0.40, Stag=2000) ──
	static int POPULATION_SIZE = 70;
	static int MAX_GENERATIONS = 100000;
	static double MUTATION_RATE = 0.40;
	static int TOURNAMENT_SIZE = 2; // (Reduziert für geringeren Selektionsdruck/mehr Vielfalt)
	static int ELITISM_COUNT = 5; // dynamisch: max(2, pop/20) = 4 bei Pop=80

	// ── Stagnationserkennung ──────────────────────────────────────
	static int STAGNATION_LIMIT = 2000;
	static double RESTART_RATIO = 0.85; // (Erhöht für massiven Wipeout bei Stagnation)

	// ── Island-Model ─────────────────────────────────────────────────────────
	static final int MIGRATION_INTERVAL = 10000; // (Erhöht: Inseln bleiben sehr lange isoliert)

	// Geteilte beste Lösung zwischen allen Inseln (volatile + Lock)
	static volatile int   globalBestFit      = Integer.MAX_VALUE;
	static volatile int[] globalBestSolution = null;
	static final Object   migrationLock      = new Object();

	// Jede Insel bekommt ihren eigenen Zufallsgenerator (Thread-Safety)
	static final ThreadLocal<Random> tlRng = ThreadLocal.withInitial(Random::new);
	static Random rng() { return tlRng.get(); }

	public static void main(String[] args) {

		try {
			File file = new File("CI/AGENTS/daten3ACustomer_200_10.txt");
			if (!file.exists()) {
				file = new File("daten3ACustomer_200_10.txt");
			}
			CustomerAgent ag = new CustomerAgent(file);
			int n = ag.getContractSize();

			// ── 10 Inseln mit vollständig zufälligen Kombinationen aus den Pools ──
			int[]    poolPopSizes   = { 20,  30,  40, 50,  60,  70, 80,  80};
			double[] poolMutRates   = {0.90,0.80, 0.70,0.60, 0.50,0.40,0.30,0.20};
			int[]    poolStagLimits = {2000,50, 1500,2500, 1000,200,70, 500 };
			int[]    poolEliteCnts  = { 2,   4,    4,   5,    2,   3,   5,   1  };

			int islands = 10;
			int[]    popSizes   = new int[islands];
			double[] mutRates   = new double[islands];
			int[]    stagLimits = new int[islands];
			int[]    eliteCnts  = new int[islands];

			Random initRnd = new Random();
			for (int i = 0; i < islands; i++) {
				popSizes[i]   = poolPopSizes[initRnd.nextInt(poolPopSizes.length)];
				mutRates[i]   = poolMutRates[initRnd.nextInt(poolMutRates.length)];
				stagLimits[i] = poolStagLimits[initRnd.nextInt(poolStagLimits.length)];
				eliteCnts[i]  = poolEliteCnts[initRnd.nextInt(poolEliteCnts.length)];
			}

			System.out.println("Island-Model: " + islands + " parallele Inseln (Zufällig kombiniert)");
			System.out.println("MaxGen=" + MAX_GENERATIONS + " | Migration alle " + MIGRATION_INTERVAL + " Gen");
			System.out.printf("%-8s %-6s %-6s %-10s %-6s%n","Insel","Pop","Mut","StagLimit","Elite");
			for (int t = 0; t < islands; t++)
				System.out.printf("%-8d %-6d %-6.2f %-10d %-6d%n", t, popSizes[t], mutRates[t], stagLimits[t], eliteCnts[t]);
			System.out.println("──────────────────────────────────────────────────────────────────");

			// Globalen State zurücksetzen
			globalBestFit      = Integer.MAX_VALUE;
			globalBestSolution = null;

			long startMs  = System.currentTimeMillis();
			Thread[] threads = new Thread[islands];
			int[]    results = new int[islands];

			for (int t = 0; t < islands; t++) {
				final int tid = t;
				final int    pop   = popSizes[tid];
				final double mut   = mutRates[tid];
				final int    stag  = stagLimits[tid];
				final int    elite = eliteCnts[tid];
				threads[t] = new Thread(() -> {
					results[tid] = runGA(ag, n, pop, mut, stag, elite);
					System.out.printf("   Insel %d fertig: %d%n", tid, results[tid]);
				});
				threads[t].setName("Insel-" + t);
				threads[t].start();
			}

			for (Thread th : threads) {
				try { th.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
			}

			int bestOverall  = Integer.MAX_VALUE;
			int worstOverall = Integer.MIN_VALUE;
			for (int r : results) {
				if (r < bestOverall)  bestOverall  = r;
				if (r > worstOverall) worstOverall = r;
			}
			long elapsed = (System.currentTimeMillis() - startMs) / 1000;

			System.out.println("\n════════════════════════════════════════════════════════════════════");
			System.out.println("ERGEBNIS (" + islands + " Inseln parallel):");
			System.out.println("   Bestes Ergebnis : " + bestOverall);
			System.out.println("   Schlechtestes   : " + worstOverall);
			System.out.println("   Spanne          : " + (worstOverall - bestOverall));
			System.out.println("   Laufzeit        : " + elapsed + "s  (sequenziell wäre ~"
					+ (elapsed * islands) + "s)");
			System.out.println("════════════════════════════════════════════════════════════════════");
		} catch (FileNotFoundException e) {
			System.out.println(e.getMessage());
		}
	}

	public static int runGA(CustomerAgent ag, int n,
			int popSize, double mutRate, int stagLimit, int elitismCount) {
		// ── 1. Population initialisieren (1 NEH-Seed + Rest zufällig)
		int[][] population = new int[popSize][n];
		population[0] = nehSeed(ag, n);
		for (int i = 1; i < popSize; i++) {
			population[i] = randomPermutation(n);
		}

		int[] bestEver = null;
		int bestEverFit = Integer.MAX_VALUE;
		int gensSinceImprovement = 0;
		int restartCount = 0;

		// ── 2. Evolutionsschleife
		for (int gen = 0; gen < MAX_GENERATIONS; gen++) {

			int[] fitnessValues = new int[popSize];
			for (int i = 0; i < popSize; i++) {
				fitnessValues[i] = ag.fitness(population[i]);
			}

			int bestIdx = 0;
			for (int i = 1; i < popSize; i++) {
				if (fitnessValues[i] < fitnessValues[bestIdx]) {
					bestIdx = i;
				}
			}

			// Global bestes auswerten
			if (fitnessValues[bestIdx] < bestEverFit) {
				bestEverFit = fitnessValues[bestIdx];
				bestEver = population[bestIdx].clone();
				gensSinceImprovement = 0;

				// ── Lokale Suche auf neuen Champion ──
				int lsResult = localSearch(bestEver, ag, bestEverFit);
				if (lsResult < bestEverFit) {
					bestEverFit = lsResult;
				}
				// LS-verbessertes Individuum zurück in die Population injizieren:
				// Sonst bleibt population[bestIdx] die alte schlechtere Version und
				// das LS-Ergebnis pflanzt sich nie via Crossover fort.
				population[bestIdx]    = bestEver.clone();
				fitnessValues[bestIdx] = bestEverFit;
			} else {
				gensSinceImprovement++;
			}

			// ── Periodischer Memetic Pulse (VNS Lamarckian Learning) ──
			// Interval 1000 statt 250: spart 75% der Pulse-VNS-Kosten.
			// In der Spätphase lief die VNS 400× nur um "kein Fortschritt" zu bestätigen.
			if (gen > 0 && gen % 1000 == 0) {
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

			// ── Migration: beste Lösung mit anderen Inseln teilen ──
			if (gen > 0 && gen % MIGRATION_INTERVAL == 0) {
				synchronized (migrationLock) {
					if (bestEver != null && bestEverFit < globalBestFit) {
						// Diese Insel hat die globale Bestlösung — veröffentlichen
						globalBestFit      = bestEverFit;
						globalBestSolution = bestEver.clone();
					} else if (globalBestSolution != null && globalBestFit < bestEverFit) {
						// Immigranten nur als normales Populationsmitglied injizieren.
						// KEIN bestEver-Update: die Insel bewertet ihn im nächsten
						// Generationsschritt selbst und entscheidet, ob er Champion wird.
						// So bleibt jede Insel auf ihrer eigenen Evolutionsbahn.
						population[popSize - 1] = globalBestSolution.clone();
					}
				}
			}

			// ── Stagnationserkennung + Restarts ──
			if (gensSinceImprovement >= stagLimit) {

				// ILS-Schritt: erst Double-Bridge + VNS versuchen bevor wir die Population
				// aufwühlen. Landet man in einem neuen Basin → kein voller Restart nötig.
				if (bestEver != null) {
					int[] bridged = doubleBridge(bestEver);
					int bridgedFit = localSearch(bridged, ag, ag.fitness(bridged));
					if (bridgedFit < bestEverFit) {
						bestEverFit = bridgedFit;
						bestEver    = bridged;
						population[0] = bestEver.clone();
						gensSinceImprovement = 0;
						continue; // kein Restart — neues Basin gefunden!
					}
				}

				int replaceCount = (int) (popSize * RESTART_RATIO);
				int[][] sorted = sortByFitness(population, fitnessValues);

				for (int i = 0; i < popSize - replaceCount; i++) {
					population[i] = sorted[i];
				}

				int topK = Math.min(10, popSize);
				for (int i = popSize - replaceCount; i < popSize; i++) {
					double zone = rng().nextDouble();
					if (zone < 0.4) {
						// 40%: leichte Mutation eines Top-Kandidaten (feine Exploration)
						int[] mutant = sorted[rng().nextInt(topK)].clone();
						int numMutations = 5 + rng().nextInt(16);
						for (int m = 0; m < numMutations; m++) mutate(mutant);
						population[i] = mutant;
					} else if (zone < 0.7) {
						// 30%: starke Perturbation (aggressiv) → erzwingt Ausbruch aus tiefem Lokaloptimum
						int[] mutant = sorted[rng().nextInt(topK)].clone();
						int numMutations = n / 3 + rng().nextInt(n / 3); // (massiv erhöht, vorher n/5)
						for (int m = 0; m < numMutations; m++) {
							if (rng().nextDouble() < 0.2) inversionMutation(mutant); // Toxisch aber gut zum Ausbrechen
							else mutate(mutant);
						}
						population[i] = mutant;
					} else {
						// 30%: Double-Bridge von bestEver → garantiert anderes Basin,
						// aber noch mit strukturellem Wissen aus der Bestlösung
						population[i] = doubleBridge(bestEver != null ? bestEver : sorted[0]);
					}
				}
				if (bestEver != null) {
					population[0] = bestEver.clone();
					// Stark perturbierte Kopie von bestEver als zweites Individuum:
					// zwingt die Suche aus dem Basin von bestEver heraus
					int[] perturbedBest = bestEver.clone();
					int perturbStr = n / 8 + rng().nextInt(n / 8);
					for (int m = 0; m < perturbStr; m++) mutate(perturbedBest);
					population[1] = perturbedBest;
				}

				gensSinceImprovement = 0;
				restartCount++;
				continue;
			}

			// ── 3. Neue Population erzeugen ──
			int[][] newPopulation = new int[popSize][n];
			int[][] sorted = sortByFitness(population, fitnessValues);

			newPopulation[0] = bestEver.clone();
			for (int i = 1; i < elitismCount; i++) {
				newPopulation[i] = sorted[i].clone();
			}

			double currentProgress = (double) gen / MAX_GENERATIONS;
			double adaptiveMutationRate = mutRate + (0.35 * currentProgress); // Steigt deutlich stärker an
			int mutationPower = 1 + (int) (3.0 * currentProgress); // 1 → 4 Mutationen im Lategame

			for (int i = elitismCount; i < popSize; i++) {
				int[] parent1 = tournamentSelection(population, fitnessValues);
				int[] parent2 = tournamentSelection(population, fitnessValues);

				int[] child = orderCrossover(parent1, parent2);

				if (rng().nextDouble() < adaptiveMutationRate) {
					for (int m = 0; m < mutationPower; m++) {
						mutate(child);
					}
				}

				newPopulation[i] = child;
			}

			population = newPopulation;
		}

		System.out.printf("   [%s] MutRate=%.2f | Restarts: %d%n",
				Thread.currentThread().getName(), mutRate, restartCount);
		return bestEverFit;
	}

	// ── Lokale Suche: VNS (Variable Neighborhood Search) ──
	static int localSearch(int[] individual, CustomerAgent ag, int currentBestFit) {
		int n = individual.length;
		int currentFit = currentBestFit;
		boolean improved = true;

		// Wiederhole den Scan, solange noch Verbesserungen gefunden werden
		while (improved) {
			improved = false;

			// ── NEIGHBORHOOD 1: FULL INSERT (Maximale Zerstörungskraft) ──
			for (int from = 0; from < n; from++) {
				for (int to = 0; to < n; to++) {
					if (from == to)
						continue;

					int gene = individual[from];
					if (from < to) {
						for (int i = from; i < to; i++)
							individual[i] = individual[i + 1];
					} else {
						for (int i = from; i > to; i--)
							individual[i] = individual[i - 1];
					}
					individual[to] = gene;

					int newFit = ag.fitness(individual);

					if (newFit < currentFit) {
						currentFit = newFit;
						improved = true;
						break;
					} else {
						// Undo
						int geneToRestore = individual[to];
						if (from < to) {
							for (int i = to; i > from; i--)
								individual[i] = individual[i - 1];
						} else {
							for (int i = to; i < from; i++)
								individual[i] = individual[i + 1];
						}
						individual[from] = geneToRestore;
					}
				}
				if (improved)
					break;
			}

			// ── NEIGHBORHOOD 2: FULL SWAP (Ausbruchs-Nachbarschaft) ──
			// Feuert nur, wenn Insert völlig in einer Sackgasse steckt (improved == false)
			if (!improved) {
				for (int i = 0; i < n - 1 && !improved; i++) {
					for (int j = i + 1; j < n; j++) {

						int tmp = individual[i];
						individual[i] = individual[j];
						individual[j] = tmp;

						int newFit = ag.fitness(individual);

						if (newFit < currentFit) {
							currentFit = newFit;
							improved = true;
							break;
						} else {
							// Rücktausch (Undo)
							tmp = individual[i];
							individual[i] = individual[j];
							individual[j] = tmp;
						}
					}
				}
			}
			// ── NEIGHBORHOOD 3: OR-OPT-2 (Paare verschieben) ──────────────────
			// Feuert nur wenn Insert UND Swap keine Verbesserung mehr finden.
			// Bewegt 2 aufeinanderfolgende Jobs als Block an jede andere Position —
			// macht Züge die weder Insert noch Swap alleine erreichen können.
			if (!improved) {
				int[] tmp   = new int[n - 2];
				int[] trial = new int[n];
				outer2:
				for (int from = 0; from < n - 1; from++) {
					int g1 = individual[from];
					int g2 = individual[from + 1];

					// Paar aus Sequenz entfernen → tmp
					int idx = 0;
					for (int k = 0; k < n; k++)
						if (k != from && k != from + 1) tmp[idx++] = individual[k];

					// Paar an jeder Position in tmp einfügen
					for (int to = 0; to <= n - 2; to++) {
						if (to == from) continue; // gleiche Position → keine Änderung

						System.arraycopy(tmp, 0, trial, 0, to);
						trial[to]     = g1;
						trial[to + 1] = g2;
						System.arraycopy(tmp, to, trial, to + 2, n - 2 - to);

						int newFit = ag.fitness(trial);
						if (newFit < currentFit) {
							System.arraycopy(trial, 0, individual, 0, n);
							currentFit = newFit;
							improved   = true;
							break outer2;
						}
					}
				}
			}
			// Nach jedem erfolgreichen Zug (Insert, Swap oder Or-opt-2) springt
			// while() wieder zu NEIGHBORHOOD 1 — echtes VNS-Design.
		}
		return currentFit;
	}

	// ── Gewichtete Mutation: Insert ist King bei asymmetrischen Problemen!
	// ────────
	static void mutate(int[] individual) {
		double r = rng().nextDouble();
		if (r < 0.30) { // 30% Swap
			swapMutation(individual);
		} else { // 70% Insert (Inversion wurde gelöscht, da toxisch für dieses Problem)
			insertMutation(individual);
		}
	}

	static void swapMutation(int[] individual) {
		int n = individual.length;
		int i = rng().nextInt(n);
		int j = rng().nextInt(n);
		int tmp = individual[i];
		individual[i] = individual[j];
		individual[j] = tmp;
	}

	static void insertMutation(int[] individual) {
		int n = individual.length;
		int from = rng().nextInt(n);
		int to = rng().nextInt(n);
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
		int start = rng().nextInt(n);
		int end = rng().nextInt(n);
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

	// ── NEH-Initialisierung (Nawaz-Enscore-Ham) ──────────────────────────────
	// Baut eine gute Startlösung konstruktiv auf:
	// Jobs werden absteigend nach Gesamtbearbeitungszeit sortiert, dann wird
	// jeder Job an der Stelle eingefügt, die die Fitness minimal hält.
	static int[] nehSeed(CustomerAgent ag, int n) {
		int[] sortedJobs = ag.getJobsSortedByTotalTime();

		int[] seq = new int[]{ sortedJobs[0] };

		for (int k = 1; k < n; k++) {
			int job     = sortedJobs[k];
			int seqLen  = seq.length;
			int bestFit = Integer.MAX_VALUE;
			int bestPos = 0;

			int[] trial = new int[seqLen + 1];
			for (int pos = 0; pos <= seqLen; pos++) {
				System.arraycopy(seq, 0, trial, 0, pos);
				trial[pos] = job;
				System.arraycopy(seq, pos, trial, pos + 1, seqLen - pos);

				int f = ag.fitness(trial);
				if (f < bestFit) {
					bestFit = f;
					bestPos = pos;
				}
			}

			int[] newSeq = new int[seqLen + 1];
			System.arraycopy(seq, 0, newSeq, 0, bestPos);
			newSeq[bestPos] = job;
			System.arraycopy(seq, bestPos, newSeq, bestPos + 1, seqLen - bestPos);
			seq = newSeq;
		}
		return seq;
	}

	// ── Double-Bridge Perturbation (4-opt) ──────────────────────────────────
	// Schneidet die Sequenz an 4 zufälligen Punkten und setzt sie anders zusammen:
	//   Original:  A | B | C | D
	//   Ergebnis:  A | C | B | D
	// Erzeugt eine Lösung die durch keine Folge von Insert- oder Swap-Moves
	// erreichbar ist → echter Ausbruch aus dem aktuellen Basin.
	static int[] doubleBridge(int[] individual) {
		int n = individual.length;
		int p1 = 1 + rng().nextInt(n / 4);
		int p2 = p1 + 1 + rng().nextInt(n / 4);
		int p3 = p2 + 1 + rng().nextInt(n / 4);

		int[] result = new int[n];
		int idx = 0;
		for (int i = 0;  i < p1; i++) result[idx++] = individual[i]; // A
		for (int i = p2; i < p3; i++) result[idx++] = individual[i]; // C
		for (int i = p1; i < p2; i++) result[idx++] = individual[i]; // B
		for (int i = p3; i < n;  i++) result[idx++] = individual[i]; // D
		return result;
	}

	// Kopiert den NEH-Seed und wendet `strength` Insert-Mutationen an
	static int[] perturbNeh(int[] base, int strength) {
		int[] copy = base.clone();
		for (int i = 0; i < strength; i++) insertMutation(copy);
		return copy;
	}

	static int[] randomPermutation(int n) {
		int[] perm = new int[n];
		for (int i = 0; i < n; i++)
			perm[i] = i;
		for (int i = n - 1; i > 0; i--) {
			int j = rng().nextInt(i + 1);
			int tmp = perm[i];
			perm[i] = perm[j];
			perm[j] = tmp;
		}
		return perm;
	}

	static int[] tournamentSelection(int[][] population, int[] fitnessValues) {
		int size = population.length;
		int bestIdx = rng().nextInt(size);
		for (int i = 1; i < TOURNAMENT_SIZE; i++) {
			int candidate = rng().nextInt(size);
			if (fitnessValues[candidate] < fitnessValues[bestIdx])
				bestIdx = candidate;
		}
		return population[bestIdx].clone();
	}

	static int[] orderCrossover(int[] parent1, int[] parent2) {
		int n = parent1.length;
		int[] child = new int[n];
		boolean[] used = new boolean[n];

		int start = rng().nextInt(n);
		int end = rng().nextInt(n);
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
