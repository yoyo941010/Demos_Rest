package es.usal.tfg;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

import com.google.common.primitives.Longs;

public class Campaign {
	
	private final String campaignName;
	private final File dataBase;
	private final File directory;
	public final Object lockDataBase = new Object();
	private final File signCtr;
	private long numeroFirmas;
	
	
	
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
	public String getCampaignName() {
		return campaignName;
	}

	public File getDataBase() {
		return dataBase;
	}

	public File getDirectory() {
		return directory;
	}

	public long getNumeroFirmas() {

		System.out.println(campaignName + "-Get: numero de firmas: "+this.numeroFirmas);
		return numeroFirmas;
		
	}

	public void setNumeroFirmas(long numeroFirmas) {
		
		this.numeroFirmas = numeroFirmas;
		try {
			Files.write(signCtr.toPath(), Longs.toByteArray(numeroFirmas));
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println(campaignName + "-Set: numero de firmas: "+this.numeroFirmas);
	}

	public File getSignCtr() {
		return signCtr;
	}
	
	
}
