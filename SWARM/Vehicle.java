import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.awt.*;

import javax.swing.JLabel;
import javax.swing.JPanel;


public	class Vehicle{
	static int allId = 0;
	int id;					//Fahrzeug-ID
	double rad_sep;			//Radius f r Zusammenbleiben
	double rad_zus;			//Radius f r Separieren
	int type;				//Fahrzeug-Type (0: Verfolger; 1: Anf hrer)
	final double FZL;		//L nge
	final double FZB;		//Breite

	//Aktuelle Bewegung 	(wird in der Methode bewegen() berechnet)
	double[] pos;			//Position
	double[] vel;			//Geschwindigkeit
	double[] acc;			//Beschleunigung

	//Steuerungsparameter 	(wird in der Methode steuerparameter_festlegen() berechnet)
	double[] acc_dest;		//Zielbeschleunigung
	final double max_acc;	//Maximale Beschleunigung
	final double max_vel;	//Maximale Geschwindigkeit
	
	//Zuk nftige Bewegung 	(wird in der Methode steuern() berechnet)
	double[] pos_new;		//Neue Position
	double[] vel_new;		//Neue Geschwindigkeit
	double[] acc_new;   	//Neue Beschleunigung
	
	Vehicle(){
		allId++;
		this.id       		= allId;
		this.FZL          	= 2;
		this.FZB          	= 1;
		this.rad_sep        = 5;//50
		this.rad_zus        = 30;//25
		this.type           = 0;
		this.max_acc        = 0.05;//0.1
		this.max_vel        = 1;

		pos		 			= new double[2];
		vel    	 			= new double[2];
		acc		 			= new double[2];
		acc_dest			= new double[2];
		pos_new	 			= new double[2];
		vel_new	 			= new double[2];
		acc_new	 			= new double[2];
		
		pos[0]              = Simulation.pix*500*Math.random();
		pos[1]              = Simulation.pix*500*Math.random();
		vel[0]     			= max_vel*Math.random();
		vel[1] 	    		= max_vel*Math.random();
		acc[0]     			= max_vel*Math.random();
		acc[1] 	    		= max_vel*Math.random();
		acc_dest[0]         = 0;
		acc_dest[1]         = 0;
		vel_new[0] 			= vel[0];
		vel_new[1] 			= vel[1];
		pos_new[0]          = pos[0];
		pos_new[1]          = pos[1];
		acc_new[0]          = acc[0];
		acc_new[1]          = acc[1];
	}

	
	double[] folgen(ArrayList<Vehicle> all){
		double[] vel_dest   = new double[2];
		double[] acc_dest   = new double[2];
		acc_dest[0]         = 0;
		acc_dest[1]         = 0;
		Vehicle v = null;

		if(type == 0){
			for(int i=0;i<all.size();i++){
				Vehicle c = all.get(i);
				if(c.type == 1){ v = c; break; }
			}
			if(v == null) return acc_dest;

			double dist = Math.sqrt(Math.pow(v.pos[0]-this.pos[0],2) + Math.pow(v.pos[1]-this.pos[1],2));

			//Nur aktiv, wenn ausserhalb des roten Kreises -> zum Anfuehrer ziehen
			if(dist > rad_zus){
				vel_dest[0] = v.pos[0]-pos[0];
				vel_dest[1] = v.pos[1]-pos[1];

				vel_dest    = normalize(vel_dest);
				vel_dest[0] = vel_dest[0]*max_vel;
				vel_dest[1] = vel_dest[1]*max_vel;

				acc_dest[0] = vel_dest[0]-vel[0];
				acc_dest[1] = vel_dest[1]-vel[1];
			}
		}

		return acc_dest;
	}

	double[] zusammenbleiben(ArrayList<Vehicle> all){
		ArrayList<Vehicle>  neighbours = new ArrayList<Vehicle>();
		double[] pos_dest   = new double[2];
		double[] vel_dest   = new double[2];
		double[] acc_dest   = new double[2];
		acc_dest[0]         = 0;
		acc_dest[1]         = 0;
		
		for(int i=0;i<all.size();i++){
			Vehicle v = all.get(i);
			if(v.id != this.id){
				double dist = Math.sqrt(Math.pow(v.pos[0]-this.pos[0],2) + Math.pow(v.pos[1]-this.pos[1],2));
				if(dist < rad_zus){
					neighbours.add(v);
				}
			}
		}
		
		if(neighbours.size() > 0){
			//1. Zielposition
			pos_dest[0]     = 0;
			pos_dest[1]     = 0;
			for(int i=0;i<neighbours.size();i++){
				Vehicle v   = neighbours.get(i);
				pos_dest[0] = pos_dest[0] + v.pos[0];
				pos_dest[1] = pos_dest[1] + v.pos[1];
			}			
			pos_dest[0] = pos_dest[0] / neighbours.size();
			pos_dest[1] = pos_dest[1] / neighbours.size();

			//2. Zielgeschwindigkeit
			vel_dest[0]  = pos_dest[0]-pos[0];
			vel_dest[1]  = pos_dest[1]-pos[1];
			
			//3. Zielgeschwindigkeit maximieren (aber  ber das Ziel hinausbewegen)
			vel_dest     = normalize(vel_dest);//
			vel_dest[0]  = vel_dest[0]*max_vel;//
			vel_dest[1]  = vel_dest[1]*max_vel;//
			
			//4. Zielbeschleunigung
			acc_dest[0]  = vel_dest[0]-vel[0];
			acc_dest[1]  = vel_dest[1]-vel[1];
			
		}
		return acc_dest;
	}

	double[] separieren(ArrayList<Vehicle> all){
		ArrayList<Vehicle> myneighbours = new ArrayList<Vehicle>();
		double[] vel_dest = new double[2];
		double[] acc_dest = new double[2];
		acc_dest[0]       = 0;
		acc_dest[1]       = 0;
		
		for(int i=0;i<all.size();i++){
			Vehicle v = all.get(i);
			if(v.id != this.id){
				double dist = Math.sqrt(Math.pow(v.pos[0]-this.pos[0],2) + Math.pow(v.pos[1]-this.pos[1],2));
				if(dist < rad_sep){
					myneighbours.add(v);
				}
			}
		}
		
		if(myneighbours.size() > 0){
			//1. Zielgeschwindigkeit
			vel_dest[0] = 0;
			vel_dest[1] = 0;
			for(int i=0;i<myneighbours.size();i++){
				Vehicle v    = myneighbours.get(i);
				double[] tmp = new double[2];
				double dist;
				tmp[0]  = v.pos[0] - pos[0];
				tmp[1]  = v.pos[1] - pos[1];
				dist    = rad_sep-length(tmp);
		//dist = length(tmp);
				if(dist < 0) System.out.println("fehler in rad");
				tmp          = normalize(tmp);
				tmp[0]       = -tmp[0] * dist;
				tmp[1]       = -tmp[1] * dist;
				vel_dest[0]  = vel_dest[0] + tmp[0];
				vel_dest[1]  = vel_dest[1] + tmp[1];
			}
			vel_dest[0] = vel_dest[0] / myneighbours.size();
			vel_dest[1] = vel_dest[1] / myneighbours.size();
			
			//2. Zielgeschwindigkeit maximieren (aber  ber das Ziel hinausbewegen)
			vel_dest     = normalize(vel_dest);
			vel_dest[0]  = vel_dest[0]*max_vel;
			vel_dest[1]  = vel_dest[1]*max_vel;
			
			//3. Zielbeschleunigung
			acc_dest[0]  = vel_dest[0]-vel[0];
			acc_dest[1]  = vel_dest[1]-vel[1];
		}

		return acc_dest;
	}

	double[] ausrichten(ArrayList<Vehicle> all){
		ArrayList<Vehicle>  neighbours = new ArrayList<Vehicle>();
		double[] vel_dest   = new double[2];
		double[] acc_dest   = new double[2];
		acc_dest[0]         = 0;
		acc_dest[1]         = 0;

		for(int i=0;i<all.size();i++){
			Vehicle v = all.get(i);
			if(v.id != this.id){
				double dist = Math.sqrt(Math.pow(v.pos[0]-this.pos[0],2) + Math.pow(v.pos[1]-this.pos[1],2));
				if(dist < rad_zus){
					neighbours.add(v);
				}
			}
		}

		if(neighbours.size() > 0){
			//1. Zielgeschwindigkeit = Mittelwert der Nachbargeschwindigkeiten
			vel_dest[0] = 0;
			vel_dest[1] = 0;
			for(int i=0;i<neighbours.size();i++){
				Vehicle v   = neighbours.get(i);
				vel_dest[0] = vel_dest[0] + v.vel[0];
				vel_dest[1] = vel_dest[1] + v.vel[1];
			}
			vel_dest[0] = vel_dest[0] / neighbours.size();
			vel_dest[1] = vel_dest[1] / neighbours.size();

			//2. Zielgeschwindigkeit maximieren
			vel_dest    = normalize(vel_dest);
			vel_dest[0] = vel_dest[0]*max_vel;
			vel_dest[1] = vel_dest[1]*max_vel;

			//3. Zielbeschleunigung
			acc_dest[0] = vel_dest[0]-vel[0];
			acc_dest[1] = vel_dest[1]-vel[1];
		}

		return acc_dest;
	}
	
	double[] zufall(){
		double[] acc_dest = new double[2];
		acc_dest[0] = 0;
		acc_dest[1] = 0;
		
		if(Math.random()<0.01){
			acc_dest[0]  = max_acc*Math.random();
			acc_dest[1]  = max_acc*Math.random();
		}
		
		return acc_dest;
	}

	
	public void steuerparameter_festlegen(ArrayList<Vehicle> allVehicles){
		
		double[] acc_dest1 = new double[2];
		double[] acc_dest2 = new double[2];
		double[] acc_dest3 = new double[2];
		double[] acc_dest4 = new double[2];
		double f1  = 0.2;//0.1
		double f2  = 0.4;//0.6
		double f3  = 0.4;
		double f4  = 0.8; //Gewicht fuer Verfolgen des Anfuehrers

		if(type == 1){
			this.acc_dest = zufall();
		}
		else{
			acc_dest1   = zusammenbleiben(allVehicles);
			acc_dest2   = separieren(allVehicles);
			acc_dest3   = ausrichten(allVehicles);
			acc_dest4   = folgen(allVehicles);

			this.acc_dest[0] = (f1 * acc_dest1[0]) + (f2 * acc_dest2[0]) + (f3 * acc_dest3[0]) + (f4 * acc_dest4[0]);
			this.acc_dest[1] = (f1 * acc_dest1[1]) + (f2 * acc_dest2[1]) + (f3 * acc_dest3[1]) + (f4 * acc_dest4[1]);
		}
		
//		steuern(acc_dest);
	}

	

	void steuern(){
		
		//1. Beschleunigung berechnen
		acc_new = truncate(acc_dest, max_acc);
		
		//2. Neue Geschwindigkeit berechnen
		vel_new[0] = vel[0]+acc_new[0];
		vel_new[1] = vel[1]+acc_new[1];
		vel_new  = truncate(vel_new, max_vel);
				
		//3. Neue Position berechnen
		pos_new[0] = pos[0] + vel_new[0];
		pos_new[1] = pos[1] + vel_new[1];
		
		//4. Position ggf. korrigieren, falls Rand der Bewegungsfl che erreicht
		if(pos_new[0] < 10){
			vel_new[0] = Math.abs(vel_new[0]);
			pos_new[0] = pos[0] + vel_new[0];
		}
		if(pos_new[0] > 1600*Simulation.pix){
			vel_new[0] = -Math.abs(vel_new[0]);
			pos_new[0] = pos[0] + vel_new[0];
		}
		if(pos_new[1] < 10){
			vel_new[1] = Math.abs(vel_new[1]);
			pos_new[1] = pos[1] + vel_new[1];
		}
		if(pos_new[1] > 800*Simulation.pix){
			vel_new[1] = -Math.abs(vel_new[1]);
			pos_new[1] = pos[1] + vel_new[1];
		}
	}

	
	

	
	


	void bewegen(){
		pos[0] = pos_new[0];
		pos[1] = pos_new[1];
		vel[0] = vel_new[0];
		vel[1] = vel_new[1];
		acc[0] = acc_new[0];
		acc[1] = acc_new[1];
	}

	
	
	
	
	
	
	
	
	
	
	
	static double truncate(double x, double y){
		if(y < 0)System.out.println("Fehler truncate");
		if(x > 0)return Math.min(x,  y);
		else     return Math.max(x, -y);
	}
		
	static double[] normalize(double[] x){
		double[] res = new double[2];
		double  norm = Math.sqrt(Math.pow(x[0], 2)+Math.pow(x[1], 2));
		res[0]       = x[0];
		res[1]       = x[1];
		if(norm!=0){
			res[0] = x[0]/norm;
			res[1] = x[1]/norm;
		}
		
		return res;
	}
		
	static double[] truncate(double[] x, double y){
		if(y < 0)System.out.println("Fehler truncate");
		double[] res  = normalize(x);
		res[0]        = res[0]*truncate(length(x), y);
		res[1]        = res[1]*truncate(length(x), y);
		return res;
	}
	
	static double length (double[] x){
		double res = Math.sqrt(Math.pow(x[0], 2)+Math.pow(x[1], 2));
		return res;
	}
	
	static double winkel(double[] v1){
		//Winkel von v1 gegen ber Koordinaten-X-Achse [0, 360[ gegen den Uhrzeigersinn
		
		double[] k = new double[2];
		double w;
		
		k[0] = 1;
		k[1] = 0;
		w    = winkel(k, v1);
		if(v1[1] < 0)w = 2*Math.PI-w;
		return w;
	}
	
	static double winkel(double[] v1, double[] v2){
		//Berechnet den Winkel zwischen zwei Vektoren in winkelRad aus [0,180]
		
		double betrag_v1   = Math.sqrt(Math.pow(v1[0], 2)+Math.pow(v1[1], 2));
		double betrag_v2   = Math.sqrt(Math.pow(v2[0], 2)+Math.pow(v2[1], 2));
		double winkelGrad;
		double winkelRad;
		double skalPro;
		
		if(betrag_v1==0 || betrag_v2==0){
			winkelGrad = 0;
			winkelRad  = 0;
//			System.out.println("Betrag = 0");
		}
		else{
			skalPro    = (v1[0]*v2[0])+(v1[1]*v2[1]);
			winkelRad  = skalPro/(betrag_v1*betrag_v2);
			if(winkelRad> 1)winkelRad= 1;
			if(winkelRad<-1)winkelRad=-1;
			winkelRad  = Math.acos(winkelRad);
			winkelGrad = winkelRad*180/Math.PI;
		}
		
		//System.out.println("Winkel " + winkelRad + " " + winkelGrad + " " + betrag_v1 + " " + betrag_v2);

		return winkelRad;
	}

/*	
	double[] folgen2(ArrayList<Vehicle> all){
		double[] pos_dest   = new double[2];
		double[] vel_dest   = new double[2];
		double[] acc_dest   = new double[2];
		acc_dest[0]         = 0;
		acc_dest[1]         = 0;
		boolean anf hrer    = true;
		Vehicle v = null;
		
		if(type == 0){
			for(int i=0;i<all.size();i++){
				v = all.get(i);
				if(v.type == 1)break;
			}
			double dist = Math.sqrt(Math.pow(v.pos[0]-this.pos[0],2) + Math.pow(v.pos[1]-this.pos[1],2));
			if(dist < rad_zus){
				if(!kollision(v)){
					pos_dest[0] = v.pos[0];
					pos_dest[1] = v.pos[1];
					vel_dest[0] = pos_dest[0]-pos[0];
					vel_dest[1] = pos_dest[1]-pos[1];
					acc_dest[0] = vel_dest[0]-vel[0];
					acc_dest[1] = vel_dest[1]-vel[1];
				}
			}
			else{
				zusammenbleiben(all);
			}
		}

		return acc_dest;
	}
	*/
/*
	boolean kollision(Vehicle v){
		boolean erg = false;
		
		if(v!=null){
			double[] abstandsPkt = punktVektorMINAbstand_punkt(pkt, ort1, ort2);
			
			
		}
		else{
			erg = false;
		}
		
		return false;
	}
*/	
	
	double punktVektorMINAbstand_betrag(double[] pkt, double[] ort1, double[] ort2){
		//1) ermittelt zun chst den Punkt (abstandsPkt) auf dem Vektor ort1->ort2, 
		//   der von dem geg. Punkt (pkt) den geringsten Abstand hat (entweder Lot auf dem Vektor oder ort1 oder ort2) 
		//2) anschlie end wird der Vektor  abstandsVek zwischen den beiden Punkten pkt und abstandspkt gebildet
		//3) die L nge von abstandsVek wird berechnet
		
		double abstand; 
		double[] abstandsPkt = punktVektorMINAbstand_punkt(pkt, ort1, ort2);
		double[] abstandsVek = new double[2];
		abstandsVek[0]       = abstandsPkt[0]-pkt[0];
		abstandsVek[1]       = abstandsPkt[1]-pkt[1];
		abstand              = length(abstandsVek);
		return abstand;
	}

	
	double[] punktVektorMINAbstand_punkt(double[] pkt, double[] ort1, double[] ort2){
		//berechnet denjenigen Punkt abstandsPkt auf einer Geraden [ort1, ort2], mit k rzester Entfernung zum geg. Punkt pkt
		double[] abstandsPkt = new double[2];
		abstandsPkt[0]       = 0;
		abstandsPkt[1]       = 0;
		
		double dist;
		double winkel1;
		double winkel2;
		
		double[] richtung1 = new double[2];
		double[] richtung2 = new double[2];
		
		richtung1[0] = ort2[0]-ort1[0];
		richtung1[1] = ort2[1]-ort1[1];
		richtung2[0] = pkt[0] -ort1[0];
		richtung2[1] = pkt[1] -ort1[1];
		winkel1      = winkel(richtung1, richtung2);
		richtung1[0] = ort1[0]-ort2[0];
		richtung1[1] = ort1[1]-ort2[1];
		richtung2[0] = pkt[0] -ort2[0];
		richtung2[1] = pkt[1] -ort2[1];
		winkel2      = winkel(richtung1, richtung2);
		
		if(winkel1>=Math.PI/2){
			abstandsPkt[0] = ort1[0];
			abstandsPkt[1] = ort1[1];
		}
		else if(winkel2>=Math.PI/2){
			abstandsPkt[0] = ort2[0];
			abstandsPkt[1] = ort2[1];
		}
		else{
			richtung1[0] = ort2[0]-ort1[0];
			richtung1[1] = ort2[1]-ort1[1];
			richtung2[0] = pkt[0] -ort1[0];
			richtung2[1] = pkt[1] -ort1[1];
			winkel1      = winkel(richtung1, richtung2);
			dist         = length(richtung2);
			double lot   = dist  * Math.cos(winkel1);
			double[] lotPkt = normalize(richtung1);
			abstandsPkt[0]  = ort1[0] + lot*lotPkt[0];
			abstandsPkt[1]  = ort1[1] + lot*lotPkt[1];
		}
		
		return abstandsPkt;
	}


	

	
}


