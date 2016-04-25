package es.usal.tfg.imageProcessing;

import java.io.File;

public class Firma {
	
	
	private String nombre;
	private String apellidos;
	private String numDni;
	private File dniFrontal;
	private File dniPosterior;
	
	
	public Firma(File dniFrontal, File dniPosterior, String nombre, String apellidos, String numDni) {
	
		this.dniFrontal = dniFrontal;
		this.dniPosterior = dniPosterior;
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.numDni = numDni;
	}
	
	
	
}
