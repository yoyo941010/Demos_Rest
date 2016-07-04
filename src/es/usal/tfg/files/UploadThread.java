/*
 * Archivo: UploadThread.java 
 * Proyecto: Demos_Rest
 * 
 * Autor: Aythami Estévez Olivas
 * Email: aythae@gmail.com
 * Fecha: 04-jul-2016
 * Repositorio GitHub: https://github.com/AythaE/Demos_Rest
 */
package es.usal.tfg.files;

import java.io.File;
import java.io.InputStream;

import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import es.usal.tfg.Campaign;

/**
 * Clase UploadThread empleada para guardar las fotografías mandadas el servidor,
 * A partir de formulario que se le envía a 
 * {@link FileManagement#upload(FormDataMultiPart)} extrae la foto concreta para
 * la que haya sido instanciado y la guarda en disco usando el método 
 * {@link FileManagement#saveFile(InputStream, String)}
 */
public class UploadThread implements Runnable {

	/** Flag que permite determinar si la subida se ha relizado correctamente. */
	private boolean exito = false;
	
	/** Campo del formulario que tiene que subir. */
	private String field;
	
	/** The form. */
	private FormDataMultiPart form;

	/**
	 * Instancia de {@link FileManagement} que ha creado esta instancia y a la
	 * que se referencia para poder guardar la fotografía.
	 */
	private FileManagement fileManagement;
	
	/** The file. */
	private File file;
	
	/** The campaign. */
	private Campaign campaign;
	
	/**
	 * Instantiates a new upload thread.
	 *
	 * @param field Campo del formulario que es necesario extraer
	 * @param form the form
	 * @param fileUpload the file upload
	 * @param campaign the campaign
	 */
	public UploadThread(String field, FormDataMultiPart form, FileManagement fileUpload, Campaign campaign) {
		this.field = field;
		this.form = form;
		this.fileManagement = fileUpload;
		this.file=null;
		this.campaign = campaign;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {

		FormDataBodyPart filePart = form.getField(field);

		ContentDisposition headerOfFilePart = filePart.getContentDisposition();

		InputStream fileInputStream = filePart.getValueAs(InputStream.class);

		String filePath = campaign.getDirectory().getAbsolutePath() + "/"
				+ headerOfFilePart.getFileName();

		// save the file to the server
		file = fileManagement.saveFile(fileInputStream, filePath);

		if (file != null) {
			exito = true;
		}
	}

	/**
	 * Checks if is exito.
	 *
	 * @return true, if is exito
	 */
	public boolean isExito() {
		return exito;
	}

	/**
	 * Gets the file.
	 *
	 * @return the file
	 */
	public File getFile() {
		return file;
	}
}
