import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Hochparalleler Memetischer Algorithmus mit Island Model für
 * gewichtete Flow-Shop Scheduling Optimierung.
 *
 * Architektur:
 *   - N Evolutions-Inseln (eigenständige GAs mit Migration)
 *   - M Pathfinder-Threads (Iterated Greedy + VND Local Search)
 *   - Globaler Migrations-Pool mit thread-sicherem Best-Tracking
 */
public class GA_CUSTOMER {

	// ── Algorithmus-Konstanten ────────────────────────────────────
	private static final int POPULATION_SIZE   = 80;
	private static final int MAX_GENERATIONS   = 100_000;
	private static final double MUTATION_RATE  = 0.25;
	private static final int TOURNAMENT_SIZE   = 4;
	private static final int ELITISM_COUNT     = 3;
	private static final int STAGNATION_LIMIT  = 800;
	private static final int MIGRATION_INTERVAL = 200;

	// ── Pathfinder-Konstanten ────────────────────────────────────
	private static final int    PF_MIN_DESTROY   = 4;
	private static final int    PF_MAX_DESTROY   = 12;
	private static final double PF_COOLING_RATE  = 0.9975;
	private static final int    PF_REHEAT_LIMIT  = 80;
	private static final int    PF_LOCAL_SEARCH_DEPTH = 15;

	// ── VND Neighborhoods ────────────────────────────────────────
	private static final int VND_INSERT_TRIES = 12;
	private static final int VND_OROPT_TRIES  = 8;

	// ═══════════════════════════════════════════════════════════════
	//  MAIN
	// ═══════════════════════════════════════════════════════════════
	public static void main(String[] args) {
		try {
			File file = new File("CI/AGENTS/daten3ACustomer_200_10.txt");
			if (!file.exists()) file = new File("daten3ACustomer_200_10.txt");

			CustomerAgent ag = new CustomerAgent(file);
			int n = ag.getContractSize();
			int cores = Runtime.getRuntime().availableProcessors();

			printHeader(cores);

			int globalBest = Integer.MAX_VALUE;
			int[] globalBestSeq = null;

			// Drei Strategien als Configs
			double[][] configs = {
				// { popSize, mutRate, stagLimit }
				{  60, 0.20, 600  },
				{  80, 0.30, 800  },
				{ 100, 0.15, 1000 },
			};

			for (int ci = 0; ci < configs.length; ci++) {
				int pop      = (int) configs[ci][0];
				double mut   = configs[ci][1];
				int stag     = (int) configs[ci][2];

				System.out.println("┌─────────────────────────────────────────────────────────────────");
				System.out.printf("│ 🔄 Config %d/%d: Pop=%d | Mut=%.2f | Stag=%d%n",
						ci + 1, configs.length, pop, mut, stag);
				System.out.println("└─────────────────────────────────────────────────────────────────");

				long t0 = System.currentTimeMillis();
				GAResult result = runIslandModel(ag, n, cores, pop, mut, stag, ci + 1, configs.length);
				long elapsed = (System.currentTimeMillis() - t0) / 1000;

				System.out.printf("   ✅ Fertig → Fitness: %d (%ds)%n%n", result.fitness, elapsed);

				if (result.fitness < globalBest) {
					globalBest = result.fitness;
					globalBestSeq = result.sequence.clone();
				}
			}

			printResult(globalBest, globalBestSeq);

		} catch (FileNotFoundException e) {
			System.err.println("Datenfehler: " + e.getMessage());
		}
	}

	// ═══════════════════════════════════════════════════════════════
	//  ISLAND MODEL
	// ═══════════════════════════════════════════════════════════════
	static GAResult runIslandModel(CustomerAgent ag, int n, int cores,
			int popSize, double mutRate, int stagLimit, int cfgIdx, int cfgTotal) {

		// Thread-Aufteilung: halbe Kerne für Pathfinder (dort kommen die meisten Verbesserungen)
		int numPathfinders = Math.max(2, cores / 2);
		int numIslands     = Math.max(2, cores - numPathfinders);

		ExecutorService pool = Executors.newFixedThreadPool(numIslands + numPathfinders);

		// Globaler State
		int[] nehSol = ag.createNEHContract();
		AtomicReference<int[]> bestContract = new AtomicReference<>(nehSol.clone());
		AtomicInteger          bestFit      = new AtomicInteger(ag.fitness(nehSol));
		AtomicBoolean          running      = new AtomicBoolean(true);
		ConcurrentLinkedQueue<int[]> migrationPool = new ConcurrentLinkedQueue<>();
		CountDownLatch latch = new CountDownLatch(numIslands);

		// Tracking
		AtomicIntegerArray islandGen  = new AtomicIntegerArray(numIslands);
		AtomicLong pfIterCount        = new AtomicLong(0);
		AtomicInteger pfImprovements  = new AtomicInteger(0);
		AtomicInteger gaImprovements  = new AtomicInteger(0);
		long t0 = System.currentTimeMillis();

		// ── LIVE LOGGER ──
		Thread logger = createLogger(running, bestFit, islandGen,
				numIslands, pfIterCount, pfImprovements, gaImprovements,
				t0, cfgIdx, cfgTotal);
		logger.start();

		// ── PATHFINDER THREADS ──
		for (int p = 0; p < numPathfinders; p++) {
			final int pid = p;
			pool.submit(() -> runPathfinder(ag, n, pid, numPathfinders,
					bestContract, bestFit, running, pfIterCount, pfImprovements));
		}

		// ── ISLAND THREADS ──
		for (int isl = 0; isl < numIslands; isl++) {
			final int islandId = isl;
			pool.submit(() -> {
				try {
					runIsland(ag, n, islandId, popSize, mutRate, stagLimit,
							bestContract, bestFit, running, migrationPool,
							islandGen, gaImprovements);
				} finally {
					latch.countDown();
				}
			});
		}

		try { latch.await(); } catch (InterruptedException ignored) { }

		running.set(false);
		pool.shutdown();
		try { pool.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }

		return new GAResult(bestFit.get(), bestContract.get());
	}

	// ═══════════════════════════════════════════════════════════════
	//  PATHFINDER  –  Iterated Greedy + VND
	// ═══════════════════════════════════════════════════════════════
	private static void runPathfinder(CustomerAgent ag, int n, int pid, int totalPF,
			AtomicReference<int[]> bestContract, AtomicInteger bestFit,
			AtomicBoolean running, AtomicLong iterCount, AtomicInteger improvements) {

		ThreadLocalRandom rng = ThreadLocalRandom.current();
		int localFit = bestFit.get();
		int[] localBest = bestContract.get().clone();
		double tBase = computeTemperature(ag, n);
		double temp = tBase;
		int reheat = 0;

		// Vorallozierte Arbeits-Arrays → kein GC-Druck im Hot Path
		int[] workFull = new int[n];

		// Jeder Pathfinder hat eine leicht andere Zerstörungstiefe
		int baseDestroy = PF_MIN_DESTROY + (pid * (PF_MAX_DESTROY - PF_MIN_DESTROY)) / Math.max(1, totalPF - 1);

		while (running.get()) {
			// Resync mit globalem Best
			int gf = bestFit.get();
			if (gf < localFit) {
				localFit = gf;
				localBest = bestContract.get().clone();
				temp = tBase;
				reheat = 0;
			}

			// Variable Neighborhood: Zerstörungstiefe anpassen bei Stagnation
			int d = baseDestroy;
			if (reheat > 30) d = Math.min(d + 4, n / 8);

			// Iterated Greedy Search
			int newFit = iteratedGreedy(localBest, ag, localFit, temp, d, rng, workFull);

			// Nach Reconstruction: VND-Nachbesserung (Insert + Or-Opt)
			if (newFit <= localFit + 500) {
				newFit = vndLocalSearch(localBest, ag, newFit, rng);
			}

			iterCount.incrementAndGet();

			if (newFit < localFit) {
				localFit = newFit;
				reheat = 0;
				if (newFit < bestFit.get()) {
					syncGlobalBest(bestContract, bestFit, localBest, newFit);
					improvements.incrementAndGet();
				}
			} else {
				localFit = newFit;
				reheat++;
			}

			temp *= PF_COOLING_RATE;
			if (reheat > PF_REHEAT_LIMIT) {
				temp = tBase;
				reheat = 0;
			}
		}
	}

	// ═══════════════════════════════════════════════════════════════
	//  ISLAND  –  Genetischer Algorithmus
	// ═══════════════════════════════════════════════════════════════
	private static void runIsland(CustomerAgent ag, int n, int islandId,
			int popSize, double mutRate, int stagLimit,
			AtomicReference<int[]> bestContract, AtomicInteger bestFit,
			AtomicBoolean running, ConcurrentLinkedQueue<int[]> migrationPool,
			AtomicIntegerArray genTracker, AtomicInteger improvements) {

		ThreadLocalRandom rng = ThreadLocalRandom.current();
		int elitism = Math.max(2, popSize / 20);

		// ── Diversifizierte Initialisierung ──
		int[][] pop = new int[popSize][n];
		int[] seed = bestContract.get().clone();

		switch (islandId % 3) {
			case 0: // NEH-Seed + leichte Mutation
				pop[0] = seed.clone();
				for (int i = 1; i < popSize; i++) {
					pop[i] = seed.clone();
					applyMutations(pop[i], 2 + rng.nextInt(6), rng);
				}
				break;
			case 1: // 50/50 NEH + Random
				pop[0] = seed.clone();
				for (int i = 1; i < popSize / 2; i++) {
					pop[i] = seed.clone();
					applyMutations(pop[i], 3 + rng.nextInt(10), rng);
				}
				for (int i = popSize / 2; i < popSize; i++) {
					pop[i] = randomPermutation(n, rng);
				}
				break;
			case 2: // Komplett zufällig (maximale Diversität)
				pop[0] = seed.clone();
				for (int i = 1; i < popSize; i++) {
					pop[i] = randomPermutation(n, rng);
				}
				break;
		}

		int localBestFit = ag.fitness(pop[0]);
		int[] localBest = pop[0].clone();
		int stagnation = 0;

		for (int gen = 0; gen < MAX_GENERATIONS; gen++) {
			genTracker.set(islandId, gen);

			// ── Fitness evaluieren ──
			int[] fitVals = new int[popSize];
			for (int i = 0; i < popSize; i++) {
				fitVals[i] = ag.fitness(pop[i]);
			}

			int bestIdx = 0, worstIdx = 0;
			for (int i = 1; i < popSize; i++) {
				if (fitVals[i] < fitVals[bestIdx])   bestIdx = i;
				if (fitVals[i] > fitVals[worstIdx]) worstIdx = i;
			}

			// ── Lokales Best aktualisieren ──
			if (fitVals[bestIdx] < localBestFit) {
				localBestFit = fitVals[bestIdx];
				localBest = pop[bestIdx].clone();
				stagnation = 0;

				if (localBestFit < bestFit.get()) {
					syncGlobalBest(bestContract, bestFit, localBest, localBestFit);
					improvements.incrementAndGet();
				}
			} else {
				stagnation++;
			}

			// ── Global-Import alle 50 Generationen ──
			if (gen % 50 == 0) {
				int gf = bestFit.get();
				if (gf < localBestFit) {
					localBestFit = gf;
					localBest = bestContract.get().clone();
					pop[worstIdx] = localBest.clone();
					fitVals[worstIdx] = localBestFit;
					stagnation = 0;
				}
			}

			// ── Migration ──
			if (gen % MIGRATION_INTERVAL == 0 && gen > 0) {
				migrationPool.add(localBest.clone());
				int[] immigrant = migrationPool.poll();
				if (immigrant != null) {
					int immFit = ag.fitness(immigrant);
					if (immFit < fitVals[worstIdx]) {
						pop[worstIdx] = immigrant;
						fitVals[worstIdx] = immFit;
					}
				}
			}

			// ── Stagnation → Dreistufiger Restart ──
			if (stagnation > stagLimit) {
				restartPopulation(pop, localBest, popSize, n, rng);
				stagnation = 0;
				continue;
			}

			// ── Neue Generation ──
			int[][] newPop = new int[popSize][n];
			int[][] sorted = sortByFitness(pop, fitVals);
			for (int i = 0; i < elitism; i++) {
				newPop[i] = sorted[i].clone();
			}

			double progress = (double) gen / MAX_GENERATIONS;
			double adaptMut = mutRate + (0.40 * progress);

			for (int i = elitism; i < popSize; i++) {
				int[] p1 = (rng.nextDouble() < 0.08)
						? localBest.clone()
						: tournamentSelect(pop, fitVals, popSize, rng);
				int[] p2 = tournamentSelect(pop, fitVals, popSize, rng);

				int[] child = orderCrossover(p1, p2, rng);

				// Memetik: 15% Insert-LS, 10% Or-Opt, Rest Mutation
				double r = rng.nextDouble();
				if (r < 0.15) {
					insertLocalSearch(child, ag, rng, VND_INSERT_TRIES);
				} else if (r < 0.25) {
					orOptLocalSearch(child, ag, rng, VND_OROPT_TRIES);
				} else if (rng.nextDouble() < adaptMut) {
					applyMutations(child, 1 + rng.nextInt(3), rng);
				}

				newPop[i] = child;
			}
			pop = newPop;
		}
	}

	// ═══════════════════════════════════════════════════════════════
	//  ITERATED GREEDY SEARCH  (optimiert, vorallozierte Arrays)
	// ═══════════════════════════════════════════════════════════════
	private static int iteratedGreedy(int[] current, CustomerAgent ag, int currentFit,
			double temp, int d, ThreadLocalRandom rng, int[] workFull) {
		int n = current.length;
		if (n <= d) return currentFit;

		// 1. Destruction – d zufällige Jobs entfernen
		int[] removed = new int[d];
		boolean[] isRemoved = new boolean[n];
		int cnt = 0;
		while (cnt < d) {
			int r = rng.nextInt(n);
			if (!isRemoved[r]) {
				isRemoved[r] = true;
				removed[cnt++] = current[r];
			}
		}

		int partialLen = n - d;
		int[] partial = new int[partialLen];
		int pi = 0;
		for (int i = 0; i < n; i++) {
			if (!isRemoved[i]) partial[pi++] = current[i];
		}

		// 2. Construction – NEH-Style Reinsertion
		int[] seq = partial;
		for (int i = 0; i < d; i++) {
			int job = removed[i];
			int bestPos = 0;
			int bestFitVal = Integer.MAX_VALUE;
			int len = seq.length;
			int newLen = len + 1;

			// Hintere verbleibende Jobs in workFull vorbelegen
			int tail = newLen;
			for (int k = i + 1; k < d; k++) workFull[tail++] = removed[k];

			for (int pos = 0; pos <= len; pos++) {
				System.arraycopy(seq, 0, workFull, 0, pos);
				workFull[pos] = job;
				System.arraycopy(seq, pos, workFull, pos + 1, len - pos);

				int f = ag.fitness(workFull);
				if (f < bestFitVal) {
					bestFitVal = f;
					bestPos = pos;
				}
			}

			int[] next = new int[newLen];
			System.arraycopy(seq, 0, next, 0, bestPos);
			next[bestPos] = job;
			System.arraycopy(seq, bestPos, next, bestPos + 1, len - bestPos);
			seq = next;
		}

		int newFit = ag.fitness(seq);

		// 3. Acceptance – Simulated Annealing
		if (newFit < currentFit) {
			System.arraycopy(seq, 0, current, 0, n);
			return newFit;
		} else if (temp > 0.01) {
			double p = Math.exp(-(newFit - currentFit) / temp);
			if (rng.nextDouble() < p) {
				System.arraycopy(seq, 0, current, 0, n);
				return newFit;
			}
		}
		return currentFit;
	}

	// ═══════════════════════════════════════════════════════════════
	//  VND  –  Variable Neighborhood Descent
	// ═══════════════════════════════════════════════════════════════
	private static int vndLocalSearch(int[] seq, CustomerAgent ag, int currentFit, ThreadLocalRandom rng) {
		// Phase 1: Insert-Moves
		currentFit = insertLocalSearch(seq, ag, rng, PF_LOCAL_SEARCH_DEPTH);
		// Phase 2: Or-Opt (Segment-Moves)
		currentFit = orOptLocalSearch(seq, ag, rng, PF_LOCAL_SEARCH_DEPTH);
		return currentFit;
	}

	/** Insert-Neighbourhood: Job von Position 'from' nach Position 'to' verschieben. */
	private static int insertLocalSearch(int[] seq, CustomerAgent ag, ThreadLocalRandom rng, int tries) {
		int n = seq.length;
		int fit = ag.fitness(seq);
		for (int t = 0; t < tries; t++) {
			int from = rng.nextInt(n);
			int to = rng.nextInt(n);
			if (from == to) continue;

			int gene = seq[from];
			if (from < to) {
				System.arraycopy(seq, from + 1, seq, from, to - from);
			} else {
				System.arraycopy(seq, to, seq, to + 1, from - to);
			}
			seq[to] = gene;

			int nf = ag.fitness(seq);
			if (nf < fit) {
				fit = nf;
			} else {
				// Revert
				int gene2 = seq[to];
				if (to < from) {
					System.arraycopy(seq, to + 1, seq, to, from - to);
				} else {
					System.arraycopy(seq, from, seq, from + 1, to - from);
				}
				seq[from] = gene2;
			}
		}
		return fit;
	}

	/** Or-Opt Neighbourhood: Segment von 2-3 aufeinanderfolgenden Jobs verschieben. */
	private static int orOptLocalSearch(int[] seq, CustomerAgent ag, ThreadLocalRandom rng, int tries) {
		int n = seq.length;
		int fit = ag.fitness(seq);
		for (int t = 0; t < tries; t++) {
			int segLen = 2 + rng.nextInt(2); // 2 oder 3
			int from = rng.nextInt(n - segLen);
			int to = rng.nextInt(n - segLen);
			if (Math.abs(from - to) <= segLen) continue;

			// Segment extrahieren
			int[] segment = new int[segLen];
			System.arraycopy(seq, from, segment, 0, segLen);

			// Segment entfernen → temp array
			int[] temp = new int[n - segLen];
			System.arraycopy(seq, 0, temp, 0, from);
			System.arraycopy(seq, from + segLen, temp, from, n - from - segLen);

			// Insertions-Position in temp anpassen
			int insertAt = (to > from) ? to - segLen : to;
			if (insertAt < 0) insertAt = 0;
			if (insertAt > temp.length) insertAt = temp.length;

			// Segment einfügen
			int[] result = new int[n];
			System.arraycopy(temp, 0, result, 0, insertAt);
			System.arraycopy(segment, 0, result, insertAt, segLen);
			System.arraycopy(temp, insertAt, result, insertAt + segLen, temp.length - insertAt);

			int nf = ag.fitness(result);
			if (nf < fit) {
				System.arraycopy(result, 0, seq, 0, n);
				fit = nf;
			}
			// Kein Revert nötig: seq wurde nur bei Verbesserung geändert
		}
		return fit;
	}

	// ═══════════════════════════════════════════════════════════════
	//  GA-Operatoren
	// ═══════════════════════════════════════════════════════════════
	private static void mutate(int[] ind, ThreadLocalRandom rng) {
		if (rng.nextDouble() < 0.30) {
			swapMutation(ind, rng);
		} else {
			insertMutation(ind, rng);
		}
	}

	private static void applyMutations(int[] ind, int count, ThreadLocalRandom rng) {
		for (int i = 0; i < count; i++) mutate(ind, rng);
	}

	private static void swapMutation(int[] ind, ThreadLocalRandom rng) {
		int n = ind.length;
		int i = rng.nextInt(n), j = rng.nextInt(n);
		int tmp = ind[i]; ind[i] = ind[j]; ind[j] = tmp;
	}

	private static void insertMutation(int[] ind, ThreadLocalRandom rng) {
		int n = ind.length;
		int from = rng.nextInt(n), to = rng.nextInt(n);
		if (from == to) return;
		int gene = ind[from];
		if (from < to) System.arraycopy(ind, from + 1, ind, from, to - from);
		else System.arraycopy(ind, to, ind, to + 1, from - to);
		ind[to] = gene;
	}

	private static int[] randomPermutation(int n, ThreadLocalRandom rng) {
		int[] p = new int[n];
		for (int i = 0; i < n; i++) p[i] = i;
		for (int i = n - 1; i > 0; i--) {
			int j = rng.nextInt(i + 1);
			int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
		}
		return p;
	}

	private static int[] tournamentSelect(int[][] pop, int[] fitVals, int popSize, ThreadLocalRandom rng) {
		int best = rng.nextInt(popSize);
		for (int i = 1; i < TOURNAMENT_SIZE; i++) {
			int c = rng.nextInt(popSize);
			if (fitVals[c] < fitVals[best]) best = c;
		}
		return pop[best].clone();
	}

	private static int[] orderCrossover(int[] p1, int[] p2, ThreadLocalRandom rng) {
		int n = p1.length;
		int[] child = new int[n];
		boolean[] used = new boolean[n];

		int start = rng.nextInt(n), end = rng.nextInt(n);
		if (start > end) { int t = start; start = end; end = t; }

		for (int i = start; i <= end; i++) {
			child[i] = p1[i];
			used[p1[i]] = true;
		}

		int ins = (end + 1) % n;
		for (int i = 0; i < n; i++) {
			int idx = (end + 1 + i) % n;
			int gene = p2[idx];
			if (!used[gene]) {
				child[ins] = gene;
				used[gene] = true;
				ins = (ins + 1) % n;
			}
		}
		return child;
	}

	// ═══════════════════════════════════════════════════════════════
	//  Hilfsmethoden
	// ═══════════════════════════════════════════════════════════════
	private static void restartPopulation(int[][] pop, int[] elite, int popSize, int n, ThreadLocalRandom rng) {
		// Drittel 1: Elite behalten
		int third = popSize / 3;
		pop[0] = elite.clone();
		for (int i = 1; i < third; i++) {
			pop[i] = elite.clone();
			applyMutations(pop[i], 3 + rng.nextInt(5), rng);
		}
		// Drittel 2: Starke Mutation des Besten
		for (int i = third; i < third * 2; i++) {
			pop[i] = elite.clone();
			applyMutations(pop[i], 10 + rng.nextInt(20), rng);
		}
		// Drittel 3: Komplett frisches Blut
		for (int i = third * 2; i < popSize; i++) {
			pop[i] = randomPermutation(n, rng);
		}
	}

	private static synchronized void syncGlobalBest(AtomicReference<int[]> ref, AtomicInteger fit,
			int[] seq, int val) {
		if (val < fit.get()) {
			fit.set(val);
			ref.set(seq.clone());
		}
	}

	private static double computeTemperature(CustomerAgent ag, int n) {
		return (double) ag.getBaseProcessingTimeSum() / (10.0 * n * ag.getNumMachines()) * 0.4;
	}

	private static int[][] sortByFitness(int[][] pop, int[] fitVals) {
		int n = pop.length;
		Integer[] idx = new Integer[n];
		for (int i = 0; i < n; i++) idx[i] = i;
		java.util.Arrays.sort(idx, (a, b) -> fitVals[a] - fitVals[b]);
		int[][] sorted = new int[n][];
		for (int i = 0; i < n; i++) sorted[i] = pop[idx[i]];
		return sorted;
	}

	// ═══════════════════════════════════════════════════════════════
	//  LOGGING
	// ═══════════════════════════════════════════════════════════════
	private static Thread createLogger(AtomicBoolean running, AtomicInteger bestFit,
			AtomicIntegerArray islandGen, int numIslands,
			AtomicLong pfIters, AtomicInteger pfImpr, AtomicInteger gaImpr,
			long t0, int cfgIdx, int cfgTotal) {
		Thread t = new Thread(() -> {
			int lastBest = bestFit.get();
			try {
				while (running.get()) {
					Thread.sleep(3000);
					if (!running.get()) break;
					long elapsed = (System.currentTimeMillis() - t0) / 1000;
					int current = bestFit.get();
					int minG = Integer.MAX_VALUE, maxG = 0;
					for (int i = 0; i < numIslands; i++) {
						int g = islandGen.get(i);
						if (g < minG) minG = g;
						if (g > maxG) maxG = g;
					}
					String marker = (current < lastBest) ? " ⚡NEU" : "";
					lastBest = current;
					System.out.printf("   [%3ds] Cfg %d/%d | Best: %d%s | Gen: %d-%d/%d | PF: %dk iter, %d↑ | GA: %d↑%n",
							elapsed, cfgIdx, cfgTotal, current, marker,
							minG, maxG, MAX_GENERATIONS,
							pfIters.get() / 1000, pfImpr.get(), gaImpr.get());
				}
			} catch (InterruptedException ignored) { }
		});
		t.setDaemon(true);
		return t;
	}

	private static void printHeader(int cores) {
		System.out.println("╔══════════════════════════════════════════════════════════════════╗");
		System.out.println("║  🚀  ISLAND MODEL + VND  –  Flow-Shop Optimizer                ║");
		System.out.println("╠══════════════════════════════════════════════════════════════════╣");
		System.out.printf( "║  CPU-Kerne: %-3d | Inseln: %-2d | Pathfinder: %-2d                  ║%n",
				cores, Math.max(2, cores - Math.max(2, cores/2)), Math.max(2, cores/2));
		System.out.printf( "║  Generationen/Insel: %,d                                   ║%n", MAX_GENERATIONS);
		System.out.println("╚══════════════════════════════════════════════════════════════════╝");
		System.out.println();
	}

	private static void printResult(int bestFit, int[] bestSeq) {
		System.out.println();
		System.out.println("╔══════════════════════════════════════════════════════════════════╗");
		System.out.println("║  🏆  ENDERGEBNIS                                               ║");
		System.out.println("╠══════════════════════════════════════════════════════════════════╣");
		System.out.printf( "║  Beste Fitness: %-48d║%n", bestFit);
		System.out.println("╚══════════════════════════════════════════════════════════════════╝");
		if (bestSeq != null) {
			System.out.println();
			System.out.println("📋 Optimales Scheduling (Vertragsreihenfolge):");
			System.out.println(java.util.Arrays.toString(bestSeq));
		}
	}

	// ═══════════════════════════════════════════════════════════════
	//  DTOs
	// ═══════════════════════════════════════════════════════════════
	static class GAResult {
		final int fitness;
		final int[] sequence;
		GAResult(int f, int[] s) { this.fitness = f; this.sequence = s; }
	}
}
