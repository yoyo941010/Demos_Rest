/*
 * Archivo: SaveEncryptedImage.java 
 * Proyecto: Demos_Rest
 * 
 * Autor: Aythami Estévez Olivas
 * Email: aythae@gmail.com
 * Fecha: 04-jul-2016
 * Repositorio GitHub: https://github.com/AythaE/Demos_Rest
 */
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


/**
 * Clase SaveEncryptedImage usada para poder guardar en paralelo las imagenes 
 * de los DNI recortados encriptados.
 * <p>
 * Simplemente abre el fichero de destino para desencripcion y escribe en él
 * la imagen.
 */
public class SaveEncryptedImage implements Runnable {

	/** La imagen del DNI a guardad. */
	private Mat image;
	
	/** El destino donde se debe guardar. */
	private File destination;
	
	/** La campaña a la que pertenece esa foto. */
	private Campaign campaign;
	
	/** Flag para controlar si ha tenido exito la subida. */
	private boolean exito = false;
	
	/**
	 * Instantiates a new save encrypted image.
	 *
	 * @param image the image
	 * @param destination the destination
	 * @param campaign the campaign
	 */
	public SaveEncryptedImage(Mat image, File destination, Campaign campaign){
		this.image = image;
		this.destination = destination;
		this.campaign = campaign;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
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
			
			exito=true;
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
	
	/**
	 * Checks if is exito.
	 *
	 * @return true, if is exito
	 */
	public boolean isExito() {
		return exito;
	}
}
