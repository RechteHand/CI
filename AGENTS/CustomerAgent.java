import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class CustomerAgent extends Agent {

	private int[][] timeMatrix;
	private int[][] delayMatrix;// wird anhand der timeMatrix berechnet
	private int totalTimeSum;   // Konstante über alle Contracts → einmal vorberechnen
	int[]   localContract;


	public CustomerAgent(File file) throws FileNotFoundException {

		Scanner scanner = new Scanner(file);
		int jobs = scanner.nextInt();
		int machines = scanner.nextInt();
		timeMatrix = new int[jobs][machines];
		for (int i = 0; i < timeMatrix.length; i++) {
			for (int j = 0; j < timeMatrix[i].length; j++) {
				int x = scanner.nextInt();
				timeMatrix[i][j] = x;
			}
		}
		calculateDelay(timeMatrix.length);

		int sum = 0;
		for (int i = 0; i < timeMatrix.length; i++) {
			for (int j = 0; j < timeMatrix[i].length; j++) {
				sum += timeMatrix[i][j];
			}
		}
		totalTimeSum = sum;

		scanner.close();

	}

	public int[][] getDelayMatrix() {
		return delayMatrix;
	}

	public boolean vote(int[] contract, int[] proposal) {
//		int timeContract = evaluate(contract);
//		int timeProposal = evaluate(proposal);
		int timeContract = fitness(contract);
		int timeProposal = fitness(proposal);
		if (timeProposal < timeContract)
			return true;
		else
			return false;
	}

	public int getContractSize() {
		return timeMatrix.length;
	}

//	public void printUtility(int[] contract) {
//		System.out.print(evaluate(contract));
//	}

	public void printUtility(int[] contract) {
		System.out.print(fitness(contract));
	}

	private void calculateDelay(int jobNr) {
		delayMatrix = new int[jobNr][jobNr];
		for (int h = 0; h < jobNr; h++) {
			for (int j = 0; j < jobNr; j++) {
				delayMatrix[h][j] = 0;
				if (h != j) {
					int maxWait = 0;
					for (int machine = 0; machine < timeMatrix[0].length; machine++) {
						int wait_h_j_machine;

						int time1 = 0;
						for (int k = 0; k <= machine; k++) {
							time1 += timeMatrix[h][k];
						}
						int time2 = 0;
						for (int k = 1; k <= machine; k++) {
							time2 += timeMatrix[j][k - 1];
						}
						wait_h_j_machine = Math.max(time1 - time2, 0);
						if (wait_h_j_machine > maxWait)
							maxWait = wait_h_j_machine;
					}
					delayMatrix[h][j] = maxWait;
				}
			}
		}
	}

	private int evaluate(int[] contract) {

		int result = 0;

		for (int i = 1; i < contract.length; i++) {// starte bei zweitem Job
													// (also Index 1)
			int jobVor = contract[i - 1];
			int job = contract[i];
			result += delayMatrix[jobVor][job];
		}

		int lastjob = contract[contract.length - 1];
		for (int machine = 0; machine < timeMatrix[0].length; machine++) {
			result += timeMatrix[lastjob][machine];
		}

		return result;
	}

	public int fitness(int[] contract) {
		//Fink — totalTimeSum ist kontractunabhängig und vorberechnet
		int weightSum = 0;
		int n = contract.length;
		for (int i = 1; i < n; i++) {
			weightSum += delayMatrix[contract[i - 1]][contract[i]] * (n - i);
		}
		return weightSum + totalTimeSum;
	}

	public void initContract(){
		int contractSize = timeMatrix.length;
		int[] contract = new int[contractSize];
		for(int i=0;i<contractSize;i++)contract[i] = i;
		
		for(int i=0;i<2000;i++) {
			int element = (int)((contract.length-1)*Math.random());
			int wert1   = contract[element];
			int wert2   = contract[element+1];
			contract[element]   = wert2;
			contract[element+1] = wert1;		
		}
		
		localContract = contract;
	}

}
