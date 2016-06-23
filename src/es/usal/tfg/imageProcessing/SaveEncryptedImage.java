package es.usal.tfg.imageProcessing;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;

import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.imageio.ImageIO;

import org.opencv.core.Mat;

import es.usal.tfg.Campaign;
import es.usal.tfg.security.SymmetricEncryption;

public class SaveEncryptedImage implements Runnable {

	Mat image;
	File destination;
	Campaign campaign; 
	public SaveEncryptedImage(Mat image, File destination, Campaign campaign){
		this.image = image;
		this.destination = destination;
		this.campaign = campaign;
	}
	@Override
	public void run() {
		
		BufferedImage dniImage = ImageProcessing.Mat2BufferedImage(image);
		
		
		CipherOutputStream cos = null;
		try {
			cos = SymmetricEncryption.encryptFileUsingKey(destination, campaign.getCampaignName());
			/**
			 * Para incrementar velocidad con imagenes pequeñas
			 * @reference http://stackoverflow.com/questions/18522398/fastest-way-to-read-write-images-from-a-file-into-abufferedimage
			 * http://docs.oracle.com/javase/8/docs/api/javax/imageio/ImageIO.html
			 */
			
			ImageIO.setUseCache(false);
			ImageIO.write(dniImage, "jpg", cos);
			
		} catch (InvalidKeyException | NoSuchAlgorithmException | KeyStoreException | CertificateException
				| NoSuchPaddingException | InvalidAlgorithmParameterException | UnrecoverableEntryException
				| IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} finally {
			try {
				if (cos != null) {
					cos.flush();
					cos.close();
				}
			} catch (IOException e){}
		}
		
		return;
	}
}
