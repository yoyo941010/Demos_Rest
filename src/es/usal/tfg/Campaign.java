package es.usal.tfg;

import java.io.File;

public class Campaign {
	
	private final String campaignName;
	private final File dataBase;
	private final File directory;
	
	public Campaign(String campaignName, File directory) {
		
		this.campaignName = campaignName;
		this.directory = directory;
		this.dataBase = new File(this.directory.getAbsolutePath()+ "/signatures.json");
	}

	public File getDirectory() {
		return directory;
	}

	public String getCampaignName() {
		return campaignName;
	}

	public File getDataBase() {
		return dataBase;
	}
	
	
}
