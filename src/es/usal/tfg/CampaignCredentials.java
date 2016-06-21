package es.usal.tfg;

public class CampaignCredentials {

	private String campaignName;
	private String hashPass;
	
	public CampaignCredentials(String campaignName, String hashPass) {

		this.campaignName = campaignName;
		this.hashPass = hashPass;
	}

	public String getCampaignName() {
		return campaignName;
	}

	public String getHashPass() {
		return hashPass;
	}
	
	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return "Campaña: " + campaignName + "\nPass: " + hashPass;
	}
}
