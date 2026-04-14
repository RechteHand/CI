import java.io.File;
import java.io.FileNotFoundException;


//SIMULATION!

/*
 * Was ist das "Problem" der nachfolgenden Verhandlung?
 * - Frühe Stagnation, da die Agenten frühzeitig neue Contracte ablehnen
 * - Verhandlung ist nur für wenige Agenten geeignet, da mit Anzahl Agenten auch die Stagnationsgefahr wächst
 * 
 * Aufgabe: Entwicklung und Anaylse einer effizienteren Verhandlung. Eine Verhandlung ist effizienter, wenn
 * eine frühe Stagnation vermieden wird und eine sozial-effiziente Gesamtlösung gefunden werden kann.
 * 
 * Ideen:
 * - Agenten müssen auch Verschlechterungen akzeptieren bzw. zustimmen, die einzuhaltende Mindestakzeptanzrate wird vom Mediator vorgegeben
 * - Agenten schlagen alternative Kontrakte vor
 * - Agenten konstruieren gemeinsam einen Kontrakt
 * - In jeder Runde werden mehrere alternative Kontrakte vorgeschlagen
 * - Alternative Konstruktionsmechanismen für Kontrakte
 * - Ausgleichszahlungen der Agenten (nur möglich, wenn beide Agenten eine monetaere Zielfunktion haben
 * 
 */


public class Verhandlung {	

		public static void main(String[] args) {
			int[] contract, proposal;
			Agent agA, agB, agC, agD, agE;
			Mediator med;
			int maxRounds, round;
			boolean voteA, voteB, voteC, voteD, voteE;
			
			
			try{

				
				agA = new SupplierAgent(new File("data/daten3ASupplier_200.txt"));
				agB = new CustomerAgent(new File("data/daten3ACustomer_200_10.txt"));
//				agC = new SupplierAgent(new File("data/daten5ASupplier_200.txt"));
//				agD = new CustomerAgent(new File("data/daten3BCustomer_200_20.txt"));
//				agE = new CustomerAgent(new File("data/daten4BCustomer_200_5.txt"));
//				
				med = new Mediator(agA.getContractSize(), agB.getContractSize());

				
				
				//Verhandlung initialisieren
				contract  = med.initContract();							//Vertrag=Lösung=Jobliste				
				for(int i=0;i<contract.length;i++) {
					System.out.print(contract[i]+"-");
				}
				
				maxRounds = 0;										//Verhandlungsrunden
				//ausgabe(agA, agB, 0, contract);
				
				//Verhandlung starten	
				/*
				 * 
				 */
				ausgabe(agA, agB, 0, contract);

			
				for(round=1;round<maxRounds;round++) {					//Mediator				
					proposal = med.constructProposal(contract);			//zweck: Win-win
					voteA    = agA.vote(contract, proposal);            //Autonomie + Private Infos
					voteB    = agB.vote(contract, proposal);
//					voteC    = agC.vote(contract, proposal);            //Autonomie + Private Infos
//					voteD    = agD.vote(contract, proposal);
//					voteE    = agE.vote(contract, proposal);            //Autonomie + Private Infos
//					
//					if(voteA && voteB && voteC && voteD && voteE) {
					
			voteA = true;
					if(voteA && voteB) {
						contract = proposal;
						ausgabe(agA, agB, round, contract);
					}
				
				}			

				
				
				
				
			}
			catch(FileNotFoundException e){
				System.out.println(e.getMessage());
			}
		}
		
		public static void ausgabe(Agent a1, Agent a2, int i, int[] contract){
			System.out.print(i + " -> " );
			a1.printUtility(contract);
			System.out.print("  ");
			a2.printUtility(contract);
			System.out.println();
		}
		
		public static void ausgabe(Agent a1, Agent a2, Agent a3, Agent a4, Agent a5, int i, int[] contract){
			System.out.print(i + " -> " );
			a1.printUtility(contract);
			System.out.print("  ");
			a2.printUtility(contract);
			System.out.print("  ");
			a3.printUtility(contract);
			System.out.print("  ");
			a4.printUtility(contract);
			System.out.print("  ");
			a5.printUtility(contract);
			System.out.println();
		}

}