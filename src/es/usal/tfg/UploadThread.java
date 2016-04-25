package es.usal.tfg;

import java.io.File;
import java.io.InputStream;

import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;

public class UploadThread implements Runnable {

	private boolean exito = false;
	private String field;
	private FormDataMultiPart form;
	private FileUpload fileUpload;
	private File file;
	
	public UploadThread(String field, FormDataMultiPart form, FileUpload fileUpload) {
		this.field = field;
		this.form = form;
		this.fileUpload = fileUpload;
		this.file=null;
	}
	

	@Override
	public void run() {
		
		 FormDataBodyPart filePart = form.getField(field);

		 ContentDisposition headerOfFilePart =  filePart.getContentDisposition();

		 InputStream fileInputStream = filePart.getValueAs(InputStream.class);

		 String filePath = FileUpload.SERVER_UPLOAD_LOCATION_FOLDER + headerOfFilePart.getFileName();

		// save the file to the server
		file =fileUpload.saveFile(fileInputStream, filePath);
		exito = true;
	}
	public boolean isExito() {
		return exito;
	}


	public File getFile() {
		return file;
	}
}
