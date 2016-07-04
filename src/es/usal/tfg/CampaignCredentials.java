/*
 * Archivo: CampaignCredentials.java 
 * Proyecto: Demos_Rest
 * 
 * Autor: Aythami Estévez Olivas
 * Email: aythae@gmail.com
 * Fecha: 04-jul-2016
 * Repositorio GitHub: https://github.com/AythaE/Demos_Rest
 */
package es.usal.tfg;


/**
 * The Class CampaignCredentials.
 */
public class CampaignCredentials {

	/** The campaign name. */
	private String campaignName;
	
	/** The hash pass. */
	private String hashPass;
	
	/** The delete date. */
	private String deleteDate;
	
	/**
	 * Instantiates a new campaign credentials.
	 *
	 * @param campaignName the campaign name
	 * @param hashPass the hash pass
	 * @param deleteDate the delete date
	 */
	public CampaignCredentials(String campaignName, String hashPass, String deleteDate) {

		this.campaignName = campaignName;
		this.hashPass = hashPass;
		this.deleteDate = deleteDate;
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
	 * Gets the hash pass.
	 *
	 * @return the hash pass
	 */
	public String getHashPass() {
		return hashPass;
	}
	
	/**
	 * Gets the delete date.
	 *
	 * @return the delete date
	 */
	public String getDeleteDate() {
		return deleteDate;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return "Campaña: " + campaignName + "\nPass: " + hashPass+"\nDelete date: "+deleteDate;
	}
}
