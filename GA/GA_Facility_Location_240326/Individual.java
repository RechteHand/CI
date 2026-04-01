
public class Individual {
	int[] bits;    // SOLUTION (OR REPRESENTATION OF A SOLUTION)
	int   fitness; // SOLUTION-QUALITY 
	double p_MUT;  //MUTATION-PROBABILITY of EACH BIT
	int    problemsize = 40;
	
	public Individual(int problemsize){
		//bits    = new int[Daten.anzahlProjekte];
		this.problemsize = problemsize;
		bits    = new int[problemsize];
		p_MUT   = 1./bits.length;
	}

	
	public void mutation(){
		for(int i=0;i<bits.length;i++){
			double p = Math.random();//[0;1)
			if(p < p_MUT){
				if(this.bits[i] == 0)this.bits[i] = 1;
				else                 this.bits[i] = 0;
			}
		}
	}
s
	
	public static void crossover(Individual papa, Individual mama, Individual son, Individual doughter){
		int crosspoint = (int)(Math.random()*papa.bits.length);
		
		
		for(int i=0;i<crosspoint;i++){
			son.bits[i]      = papa.bits[i];
			doughter.bits[i] = mama.bits[i];
		}
		for(int i=crosspoint;i<papa.bits.length;i++){
			son.bits[i]      = mama.bits[i];
			doughter.bits[i] = papa.bits[i];
		}
	}
	
	public void output(){
		for(int i=0;i<bits.length;i++){
			System.out.print(bits[i]);
		}
		System.out.print(" " + fitness);
		System.out.println();
	}

	
	
	public void initialize(){
		int count = 0;
		for(int i=0;i<this.bits.length;i++){
			bits[i] = 0;
			if(Math.random()<0.5){
				bits[i] = 1;
				count++;
			}
		}
		if(count == 0) {
			int nr = (int)(bits.length*Math.random());
			bits[nr] = 1;
		}
	}
	
	public void fitness(){
		fitness = Problem.fitness(bits);
	}

	public void reproduce(Individual template){
		for(int i=0;i<bits.length;i++){
			this.bits[i] = template.bits[i];
		}
		this.fitness = template.fitness;
		this.p_MUT   = template.p_MUT;
	}

	
	
}
