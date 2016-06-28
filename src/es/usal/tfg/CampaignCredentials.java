package es.usal.tfg;

public class CampaignCredentials {

	private String campaignName;
	private String hashPass;
	private String deleteDate;
	
	public CampaignCredentials(String campaignName, String hashPass, String deleteDate) {

		this.campaignName = campaignName;
		this.hashPass = hashPass;
		this.deleteDate = deleteDate;
	}

	public String getCampaignName() {
		return campaignName;
	}

	public String getHashPass() {
		return hashPass;
	}
	public String getDeleteDate() {
		return deleteDate;
	}
	
	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return "Campaña: " + campaignName + "\nPass: " + hashPass+"\nDelete date: "+deleteDate;
	}
}
