package es.usal.tfg;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FilenameUtils;
import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.opencv.core.Core;

import com.recognition.software.jdeskew.ImageDeskew;

import es.usal.tfg.imageProcessing.ImageProcessing;

@Path("/files")
public class FileUpload {

	/**Update the following constant to the desired location for the uploads*/
	public static final String SERVER_UPLOAD_LOCATION_FOLDER = CampaignManagement.WEBSERVICE_ABSOLUTE_ROUTE +"/files/";

	/**
	 * Upload a File
	 * Reference : https://examples.javacodegeeks.com/enterprise-java/rest/jersey/jersey-file-upload-example/
	 */
	
	@POST
	@Path("/upload")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response uploadFile(FormDataMultiPart form) {

		long tIni =0, tfin=0;
		tIni = System.currentTimeMillis();
		String frontField = "front", backField = "back";
		
		UploadThread front = new UploadThread(frontField, form, this);
		UploadThread back = new UploadThread(backField, form, this);
		
		Thread hFront = new Thread(front);
		Thread hBack = new Thread(back);
		
		System.out.println("Arrancando los hilos");
		
		hFront.start();
		hBack.start();
		
		try {
			hFront.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			System.out.println("El hilo front ha fallado");
			
			e.printStackTrace();
			removeUploadedFiles(back.getFile(), front.getFile());
			tfin = System.currentTimeMillis();	
			System.out.println("Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
			
			return Response.status(500).entity("Error interno del servidor").build();
		}
		
		try {
			hBack.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			System.out.println("El hilo back ha fallado");
			e.printStackTrace();
			removeUploadedFiles(back.getFile(), front.getFile());
			tfin = System.currentTimeMillis();	
			System.out.println("Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
			return Response.status(500).entity("Error interno del servidor").build();
			
		}

		
		if (back.isExito() ==false || front.isExito()==false) {
			System.out.println("Alguno de los hilos ha fallado");
			
			removeUploadedFiles(back.getFile(), front.getFile());
			
			tfin = System.currentTimeMillis();	
			System.out.println("Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
			return Response.status(500).entity("Error interno del servidor").build();
		}
		
		File dniFrontal, dniPosterior;
		
		if (front.getFile() !=null && back.getFile() !=null) {
			dniFrontal = front.getFile();
			dniPosterior = back.getFile();
		}
		else {
			System.out.println("Alguno de los hilos ha fallado");
			removeUploadedFiles(back.getFile(), front.getFile());
			tfin = System.currentTimeMillis();	
			System.out.println("Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
			return Response.status(500).entity("Error interno del servidor").build();
		}
		
		
		ImageProcessing imgP = new ImageProcessing();	
		boolean result = imgP.imageProcessingAndOCR(dniFrontal, dniPosterior);
		
		if (result == false) {
			System.out.println("El proceso de reconocimiento ha fallado");

			removeUploadedFiles(back.getFile(), front.getFile());
			tfin = System.currentTimeMillis();	
			System.out.println("Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
			return Response.status(500).entity("Error interno del servidor").build();
			
		}
		removeUploadedFiles(back.getFile(), front.getFile());
		tfin = System.currentTimeMillis();	
		System.out.println("Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
		
		String output = "File saved to server";
		return Response.status(200).entity(output).build();

	}

	

	// save uploaded file to a defined location on the server
	File saveFile(InputStream uploadedInputStream, String serverLocation) {

		OutputStream outpuStream =null;
		try {
			
			int read = 0;
			byte[] bytes = new byte[1024];

			File file = new File(serverLocation);
			String extension =FilenameUtils.getExtension(serverLocation);
			int i=1;
			
			while (file.exists()) {
				file = new File(serverLocation.replace("."+extension, "("+ i +")."+extension));
				i++;
			}
			outpuStream = new FileOutputStream(file);
			while ((read = uploadedInputStream.read(bytes)) != -1) {
				outpuStream.write(bytes, 0, read);
			}

			outpuStream.flush();
			
			
			return file;
		} catch (IOException e) {

			e.printStackTrace();
		}
		finally {

			try {
				if (outpuStream !=null) {
					outpuStream.close();
				}
				uploadedInputStream.close();
			} catch (IOException e) {
				
				e.printStackTrace();
			}
		}

		
		return null;
	}

	private void removeUploadedFiles(File f, File f2) {
		
		if (f != null && f.exists()) {
			f.delete();
		}
		if (f2!=null && f2.exists()) {
			f2.delete();
		}
	}
}

