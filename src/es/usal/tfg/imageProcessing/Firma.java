package es.usal.tfg.imageProcessing;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Firma implements Comparable<Firma>{

	private String nombre;
	private String apellidos;
	private String numDni;
	private String fecha;
	private long numHojaFirmas;
	private long numHojaDNIs;
	private File dniFrontal;
	private File dniPosterior;

	public Firma(File dniFrontal, File dniPosterior, String nombre, String apellidos, String numDni, long numHojaFirmas,
			long numHojaDNIs) {

		this.dniFrontal = dniFrontal;
		this.dniPosterior = dniPosterior;
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.numDni = numDni;
		LocalDateTime currentTime = LocalDateTime.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy kk:mm");
		this.fecha = currentTime.format(formatter);
		this.numHojaFirmas = numHojaFirmas;
		this.numHojaDNIs = numHojaDNIs;

	}

	public String getNombre() {
		return nombre;
	}

	public String getApellidos() {
		return apellidos;
	}

	public String getNumDni() {
		return numDni;
	}

	public String getFecha() {
		return fecha;
	}

	public long getNumHojaFirmas() {
		return numHojaFirmas;
	}

	public long getNumHojaDNIs() {
		return numHojaDNIs;
	}

	public void setNumHojaDNIs(long numHojaDNIs) {
		this.numHojaDNIs = numHojaDNIs;
	}

	public File getDniFrontal() {
		return dniFrontal;
	}

	public File getDniPosterior() {
		return dniPosterior;
	}

	@Override
	public int compareTo(Firma o) {
		// TODO Auto-generated method stub
		return Long.compare(this.numHojaFirmas, o.numHojaFirmas);
	}

}
