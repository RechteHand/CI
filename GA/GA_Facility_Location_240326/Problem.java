import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;
//Optimal Value for 123UnifS.txt: 71342



public class Problem{
	
	// problembezogene Daten
	static int n;							//Number of Facilities
	static int m;							//Number of Customers
	static int[]   f;   					//Fix-Cost; cost of Facilities
	static int[][] t;						//Transportcost

	// verfahrensbezogene Daten
	//static double pm; 						//Mutationswahrscheinlichkeit

	
	public static void readInstance(String dateiName){
		// von Florian Haefner

		// Aufbau der Datei:
		// Dateiname
		// Zeilen   --- Spalten --- 0 (= Es gibt keine Kapazität)
		// Standort --- Fixkosten --- TransportkostenZuKunde1 --- ...
		// Standort --- ...
		// ...

		// Die Eingabedatei muss im Projektordner liegen!
		//File inputfile = new File("123UnifS.txt");
		File inputfile = new File(dateiName);
		
			
		FileReader filereader = null;
		BufferedReader reader = null;
		Scanner lineScanner   = null;

		try
		{
			filereader = new FileReader(inputfile);
			reader     = new BufferedReader(filereader);

			// Erste Zeile wird gelesen und direkt verworfen, da dort nur der Dateiname
			// steht.
			reader.readLine();

			String secondline = reader.readLine();
			lineScanner = new Scanner(secondline);
			lineScanner.useDelimiter(" ");
			int zeilen  = lineScanner.nextInt();//Anzahl Standorte
			int spalten = lineScanner.nextInt();//Anzahl Kunden
			n    = zeilen;
			m    = spalten;
			f    = new int[zeilen];
			t    = new int[zeilen][spalten];
			
			//System.out.println(zeilen + " " + spalten);
			
			for (int y = 0; y < zeilen; y++){
				lineScanner = new Scanner(reader.readLine());
				lineScanner.useDelimiter(" ");
				lineScanner.nextInt(); // Standortnummer
				f[y] = lineScanner.nextInt(); // Fixkosten
				
				for (int x = 0; x < spalten; x++)
				{
					t[y][x] = lineScanner.nextInt();
				}
			}
		}

		catch (IOException e)
		{
			e.printStackTrace();
		}

		finally
		{
			try
			{
				filereader.close();
				reader.close();
				lineScanner.close();
			}

			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	
	
	
	public static void printCost() {
		System.out.println("Transportkosten:");		
		for(int i=0;i<t.length;i++) {
			for(int j=0;j<t[i].length;j++) {
				System.out.print(t[i][j] + " "); 
			}
			System.out.println();
		}
		
		System.out.println("Fixkosten:");
		for(int i=0;i<t.length;i++) {
			System.out.println(f[i]); 
		}
		System.out.println("-------------------------------");
		
	}		
	
	
	public static int fitness(int[] gene) {
		// gene: 110101
		
		int fit = 0;

		for (int s = 0; s < gene.length; s++) {
			if (gene[s] == 1) {
				fit += f[s];
			}
		}

		for (int k = 0; k < m; k++) {
			int min = Integer.MAX_VALUE;
			for (int s = 0; s < gene.length; s++) {
				if (gene[s] == 1) {
					if (t[s][k] < min) {
						min = t[s][k];
					}
				}
			}
			fit += min;
		}

		return fit;
	}
}
