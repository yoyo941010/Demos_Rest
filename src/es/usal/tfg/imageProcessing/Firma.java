/*
 * Archivo: Firma.java 
 * Proyecto: Demos_Rest
 * 
 * Autor: Aythami Estévez Olivas
 * Email: aythae@gmail.com
 * Fecha: 04-jul-2016
 * Repositorio GitHub: https://github.com/AythaE/Demos_Rest
 */
package es.usal.tfg.imageProcessing;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Clase Firma que contiene toda la información disponible de una subida, las 
 * fotografías de los dni, el resultado del OCR, el numero de la hoja de firmas,
 * la fecha y el número estimado en la hoja de DNIs.
 * <p>
 * La base de datos de firmas de una campaña concreta está formada por 
 * instancias de esta clase
 */
public class Firma implements Comparable<Firma>{

	/** The nombre. */
	private String nombre;
	
	/** The apellidos. */
	private String apellidos;
	
	/** The num dni. */
	private String numDni;
	
	/**
	 * Fecha de la firma generada automaticamente tomando la hora del servidor
	 * cuando crea la instancia de firma.
	 */
	private String fecha;
	
	/** The numHojaFirmas. */
	private long numHojaFirmas;
	
	/**
	 * Número estimado de hoja que ocuparan las fotografias de esta firma en la
	 * hoja de DNIs.
	 */
	private long numHojaDNIs;
	
	/** The dni frontal. */
	private File dniFrontal;
	
	/** The dni posterior. */
	private File dniPosterior;

	/**
	 * Crea una nueva instancia de firma
	 *
	 * @param dniFrontal the dni frontal
	 * @param dniPosterior the dni posterior
	 * @param nombre the nombre
	 * @param apellidos the apellidos
	 * @param numDni the num dni
	 * @param numHojaFirmas the num hoja firmas
	 * @param numHojaDNIs the num hoja DN is
	 */
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

	/**
	 * Gets the nombre.
	 *
	 * @return the nombre
	 */
	public String getNombre() {
		return nombre;
	}

	/**
	 * Gets the apellidos.
	 *
	 * @return the apellidos
	 */
	public String getApellidos() {
		return apellidos;
	}

	/**
	 * Gets the num dni.
	 *
	 * @return the num dni
	 */
	public String getNumDni() {
		return numDni;
	}

	/**
	 * Gets the fecha.
	 *
	 * @return the fecha
	 */
	public String getFecha() {
		return fecha;
	}

	/**
	 * Gets the num hoja firmas.
	 *
	 * @return the num hoja firmas
	 */
	public long getNumHojaFirmas() {
		return numHojaFirmas;
	}

	/**
	 * Gets the num hoja DN is.
	 *
	 * @return the num hoja DN is
	 */
	public long getNumHojaDNIs() {
		return numHojaDNIs;
	}

	/**
	 * Sets the num hoja DN is.
	 *
	 * @param numHojaDNIs the new num hoja DN is
	 */
	public void setNumHojaDNIs(long numHojaDNIs) {
		this.numHojaDNIs = numHojaDNIs;
	}

	/**
	 * Gets the dni frontal.
	 *
	 * @return the dni frontal
	 */
	public File getDniFrontal() {
		return dniFrontal;
	}

	/**
	 * Gets the dni posterior.
	 *
	 * @return the dni posterior
	 */
	public File getDniPosterior() {
		return dniPosterior;
	}

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(Firma o) {
		return Long.compare(this.numHojaFirmas, o.numHojaFirmas);
	}

}
