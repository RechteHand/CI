public class GA {

	static int popSize             = 100; 			//Based on the problem size
	static int numberOfIterations  = 10000;  		//Termination Criterion
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

		//READ PROBLEM DATA  PROBLEM Instance
		Problem.readInstance("ga750a-1.txt");// 763528
		//Problem.printCost();

		Individual best         = new Individual(Problem.n);
		best.initialize();
		best.fitness();
		System.out.println("best solution: " + best.fitness);
		Individual[] pop        = new Individual[popSize];
		Individual[] children   = new Individual[popSize];
		
		//1. Generate START-POPULATION
		for(int i=0;i<pop.length;i++){
			pop[i] = new Individual(Problem.n);
			pop[i].initialize();
			pop[i].fitness();
		}
		bestIndividual(pop, best);

		for(int iter=1; iter<=numberOfIterations; iter++){
				
			for(int i=0;i<children.length;i=i+2){
				int parentIndex1 = selection(pop);
				int parentIndex2 = selection(pop);
				
				children[i]      = new Individual(Problem.n);
				children[i+1]    = new Individual(Problem.n);
		
				Individual.crossover(pop[parentIndex1], pop[parentIndex2], children[i], children[i+1]);

				children[i].mutation();
				children[i+1].mutation();
				
				children[i].fitness();
				children[i+1].fitness();
			}	

			//Replacement
			pop        = children;
			children   = new Individual[popSize];

			bestIndividual(pop, best);
			System.out.println(iter + " " + best.fitness);
		}
		
		System.out.println();
		best.output();
		
		
	}

	
	public static int selection(Individual[] list){
		int index = 0;
		
		//Tournement Selection for maximization!
		
		int index1 = (int)(Math.random()*list.length);
		int index2 = (int)(Math.random()*list.length);
		
		
		if(list[index1].fitness < list[index2].fitness){
			index = index1;
		}
		else{
			index = index2;
		}
		return index;
	}

	
	
	
	
	public static void bestIndividual(Individual[] liste, Individual best){
		for(int i=0;i<liste.length;i++){
			if(liste[i].fitness < best.fitness){
				best.reproduce(liste[i]);
			}
		}
	}

}
