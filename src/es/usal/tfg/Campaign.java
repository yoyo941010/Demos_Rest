/*
 * Archivo: Campaign.java 
 * Proyecto: Demos_Rest
 * 
 * Autor: Aythami Estévez Olivas
 * Email: aythae@gmail.com
 * Fecha: 04-jul-2016
 * Repositorio GitHub: https://github.com/AythaE/Demos_Rest
 */
package es.usal.tfg;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Date;

import com.google.common.primitives.Longs;

/**
 * Clase que almacena toda la información necesaria para manejar una campaña
 * desde cualquier clase del proyecto. Forma la estructura de datos de campañas
 * <p>
 * <b>Nota:</b> No contiene la contraseña ni la fecha de borrado ya que estos
 * datos se guardan en la base de datos de campañas y podría ser inseguro que 
 * estuvieran en memoria principal constantemente
 * 
 */
public class Campaign {
	
	/** The campaign name. */
	private final String campaignName;
	
	/** The data base. */
	private final File dataBase;
	
	/** The directory. */
	private final File directory;
	
	/** The lock data base. */
	public final Object lockDataBase = new Object();
	
	/**
	 * The sign ctr, fichero que almacena el número de firmas de la campaña,
	 * este se almacena para poder recuperarlo en caso de que se relance el
	 * servidor
	 */
	private final File signCtr;
	
	/** The numero firmas. */
	private long numeroFirmas;
	
	
	
	/**
	 * Instantiates a new campaign, cargando el número de firmas de disco con
	 * el método {@link Campaign#loadSignCtr()}
	 *
	 * @param campaignName the campaign name
	 * @param directory the directory
	 */
	public Campaign(String campaignName, File directory) {
		
		this.campaignName = campaignName;
		this.directory = directory;
		this.dataBase = new File(this.directory.getAbsolutePath()+ "/signatures.json");
		this.signCtr = new File(this.directory.getAbsolutePath()+ "/.numFirmas");
		try {
			this.numeroFirmas = loadSignCtr();
		} catch (Exception e) {
			e.printStackTrace();
			this.numeroFirmas = 0;
		}
	}

	/**
	 * Busca el fichero que almacena el numero de firmas, si existe y tiene 
	 * contenido lo carga, en caso contrario pone a 0 el campo 
	 * {@link Campaign#numeroFirmas}
	 *
	 * @return the long
	 * @throws Exception cualquier excepcion que se pueda producir
	 */
	private long loadSignCtr () throws Exception{
		if (signCtr.exists() && signCtr.length()>0) {
			byte [] bytes= Files.readAllBytes(signCtr.toPath());
			long numFirmas = Longs.fromByteArray(bytes);
			
			System.out.println(campaignName+ " cargando firmas, num: "+numFirmas);
			return numFirmas;
		}
		else {
			
			return 0;
		}
		
	}
	
	/**
	 * Gets the campaign name.
	 *
	 * @return the campaign name
	 */
	public String getCampaignName() {
		return campaignName;
	}

	/**
	 * Gets the data base.
	 *
	 * @return the data base
	 */
	public File getDataBase() {
		return dataBase;
	}

	/**
	 * Gets the directory.
	 *
	 * @return the directory
	 */
	public File getDirectory() {
		return directory;
	}

	/**
	 * Gets the numero firmas.
	 *
	 * @return the numero firmas
	 */
	public long getNumeroFirmas() {

		return numeroFirmas;
	}

	/**
	 * Sets the numero firmas y lo escribe en el fichero
	 *
	 * @param numeroFirmas the new numero firmas
	 */
	public void setNumeroFirmas(long numeroFirmas) {
		
		this.numeroFirmas = numeroFirmas;
		try {
			Files.write(signCtr.toPath(), Longs.toByteArray(numeroFirmas));
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("["+new Date().toString()+"] "+campaignName + "-Set: numero de firmas: "+this.numeroFirmas);
	}

	/**
	 * Devuelve la representación abstracta del fichero de numero de firmas.
	 *
	 * @return the sign ctr
	 */
	public File getSignCtr() {
		return signCtr;
	}
	
	
}
