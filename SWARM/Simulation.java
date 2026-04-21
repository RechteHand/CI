import java.math.*;
import java.util.ArrayList;

import javax.swing.*;

import java.awt.*;
//4
	public class Simulation extends JFrame implements Runnable{
		static int    sleep            = 2;
		static double pix              = 0.2;
		int anzFz = 100;//120
		ArrayList<Vehicle> allVehicles = new ArrayList<Vehicle>();
		JPanel                  canvas = new Canvas(allVehicles, pix);
		Thread t1;
		
		Simulation(){
			setTitle("Swarm");
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			setLayout(null);
			
			
			for(int k=0;k<anzFz;k++){
				Vehicle car  = new Vehicle();
				if(k==0)car.type=1;
				allVehicles.add(car);
			}
			
			
			
			
			Vehicle v = allVehicles.get(0);
			v.pos[0]  = 10;
			v.pos[1]  = 100;
			v.vel[0]  = v.max_vel;
			v.vel[1]  = 0;
			v = allVehicles.get(1);
			v.pos[0]  = 90;
			v.pos[1]  = 90;
			v.vel[0]  = -v.max_vel;
			v.vel[1]  = 0;
			
			
			
			
			add(canvas);
			setSize(1000,800);
			setVisible(true);
			
		}
		
		public static void main(String args[]){
			Simulation xx = new Simulation();
			Thread      t = new Thread(xx);
			t.start();
		}

		public void run() {
			t1 = new diagonal();
			t1.start();	
		}
	
		class diagonal extends Thread{
			public void run(){
				int z    	  = 0;
				boolean flag  = true;
				Vehicle v;
				
				//flag = false;
				
				while(flag){
					z++;
					if(z==169900)flag=false;
					
					for(int i=0;i<allVehicles.size();i++){
						v = allVehicles.get(i);
						v.steuerparameter_festlegen(allVehicles);
						v.steuern();
					}					
					for(int i=0;i<allVehicles.size();i++){
						v = allVehicles.get(i);
						v.bewegen();
					}
					
					try {
						Thread.sleep(sleep);
					} catch (InterruptedException e) {	}
				
					repaint();
				}
			}
		}
}


