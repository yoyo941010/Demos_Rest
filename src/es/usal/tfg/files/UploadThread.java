package es.usal.tfg.files;

import java.io.File;
import java.io.InputStream;

import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import es.usal.tfg.Campaign;

public class UploadThread implements Runnable {

	private boolean exito = false;
	private String field;
	private FormDataMultiPart form;
	private FileManagement fileUpload;
	private File file;
	private Campaign campaign;
	
	public UploadThread(String field, FormDataMultiPart form, FileManagement fileUpload, Campaign campaign) {
		this.field = field;
		this.form = form;
		this.fileUpload = fileUpload;
		this.file=null;
		this.campaign = campaign;
	}
	
	@Override
	public void run() {

		FormDataBodyPart filePart = form.getField(field);

		ContentDisposition headerOfFilePart = filePart.getContentDisposition();

		InputStream fileInputStream = filePart.getValueAs(InputStream.class);

		String filePath = campaign.getDirectory().getAbsolutePath() + "/"
				+ headerOfFilePart.getFileName();

		// save the file to the server
		file = fileUpload.saveFile(fileInputStream, filePath);

		if (file != null) {
			exito = true;
		}
	}

	public boolean isExito() {
		return exito;
	}

	public File getFile() {
		return file;
	}
}
