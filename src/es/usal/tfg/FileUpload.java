package es.usal.tfg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FilenameUtils;
import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.opencv.core.Core;

import com.recognition.software.jdeskew.ImageDeskew;

import es.usal.tfg.imageProcessing.ImageProcessing;
import es.usal.tfg.security.SymmetricEncryption;

@Path("/files")
public class FileUpload {

	/**Update the following constant to the desired location for the uploads*/
	//public static final String SERVER_UPLOAD_LOCATION_FOLDER = CampaignManagement.WEBSERVICE_ABSOLUTE_ROUTE +"/files/";

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
		String frontField = "front", backField = "back", tokenField = "token", campaignField = "campaign";

		System.out.println(CampaignManagement.SEPARADOR);
		System.out.println("Extrayendo token");
		//Extraemos el token del FormDataMultiPart para verificarlo
		FormDataBodyPart filePart = form.getField(tokenField);

		//ContentDisposition headerOfFilePart = filePart.getContentDisposition();

		String token = filePart.getValueAs(String.class);
		
		
		filePart = form.getField(campaignField);

		//headerOfFilePart = filePart.getContentDisposition();

		String campaign64 = filePart.getValueAs(String.class);
		
		String campaign = null;
		try {
			campaign = new String(Base64.getUrlDecoder().decode(campaign64.getBytes("UTF-8")));
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			try {
				return Response.status(500).entity(Base64.getUrlEncoder().encodeToString("Error interno del servidor".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e) {}
		}
		
		

		
		
		//El siguiente metodo comprueba si el token esta en el conjunto de activos,
		//en caso negativo intenta desencriptarlo y comprobar que es correcto
		if (CampaignManagement.compruebaTokenInterno(token, campaign) == false) {
			System.out.println("Error con su sesion, inicie sesion otra vez por favor.");
			try {
				return Response.status(404).entity(Base64.getUrlEncoder().encodeToString("Error con su sesion, inicie sesion otra vez por favor.".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e) {}
		}
		
		Campaign c = CampaignManagement.getCampaña(campaign);
		
		UploadThread front = new UploadThread(frontField, form, this, c);
		UploadThread back = new UploadThread(backField, form, this, c);
		
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
			
			
			try {
				return Response.status(500).entity(Base64.getUrlEncoder().encodeToString("Error interno del servidor".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e1) {}
		
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

			try {
				return Response.status(500).entity(Base64.getUrlEncoder().encodeToString("Error interno del servidor".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e1) {}
			
		}

		
		if (back.isExito() ==false || front.isExito()==false) {
			System.out.println("Alguno de los hilos ha fallado");
			
			removeUploadedFiles(back.getFile(), front.getFile());
			
			tfin = System.currentTimeMillis();	
			System.out.println("Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");

			try {
				return Response.status(500).entity(Base64.getUrlEncoder().encodeToString("Error interno del servidor".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e1) {}
		}
		
		File dniFrontal = null, dniPosterior = null;
		
		if (front.getFile() !=null && back.getFile() !=null) {
			dniFrontal = front.getFile();
			dniPosterior = back.getFile();
		}
		else {
			System.out.println("Alguno de los hilos ha fallado");
			removeUploadedFiles(back.getFile(), front.getFile());
			tfin = System.currentTimeMillis();	
			System.out.println("Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");

			try {
				return Response.status(500).entity(Base64.getUrlEncoder().encodeToString("Error interno del servidor".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e1) {}
		}
		
		
		
		ImageProcessing imgP = new ImageProcessing(c);	
		int result = imgP.imageProcessingAndOCR(dniFrontal, dniPosterior);
		
		if (result == ImageProcessing.ERROR_INTERNO) {
			System.out.println("El proceso de reconocimiento ha fallado");

			removeUploadedFiles(back.getFile(), front.getFile());
			tfin = System.currentTimeMillis();	
			System.out.println("Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");

			try {
				return Response.status(500).entity(Base64.getUrlEncoder().encodeToString("Error interno del servidor".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e1) {}
			
		}
		else if (result == ImageProcessing.ERROR_TIMEOUT || result == ImageProcessing.ERROR_AMBOS) {
			System.out.println("El proceso de reconocimiento ha superado el timeout "+ImageProcessing.DETECTION_TIMEOUT);

			removeUploadedFiles(back.getFile(), front.getFile());
			tfin = System.currentTimeMillis();	
			System.out.println("Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
			try {
				return Response.status(510)
						.entity(Base64.getUrlEncoder().encode(
						"Error reconociendo el DNI, intente hacer unas fotos mas claras".getBytes("UTF-8")))
						.build();
			} catch (UnsupportedEncodingException e) {
			}
		}
		else if (result == ImageProcessing.ERROR_FRONTAL) {
			System.out.println("El proceso de reconocimiento frontal ha fallado");

			removeUploadedFiles(back.getFile(), front.getFile());
			tfin = System.currentTimeMillis();	
			System.out.println("Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
			try {
				return Response.status(511).entity(Base64.getUrlEncoder().encode(
						"Error reconociendo la parte frontal del DNI, intente mejorar esa foto".getBytes("UTF-8")))
						.build();
			} catch (UnsupportedEncodingException e) {
			}
		}
		else if (result == ImageProcessing.ERROR_POSTERIOR) {
			System.out.println("El proceso de reconocimiento posterior ha fallado");

			removeUploadedFiles(back.getFile(), front.getFile());
			tfin = System.currentTimeMillis();	
			System.out.println("Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
			try {
				return Response.status(512).entity(Base64.getUrlEncoder().encode(
						"Error reconociendo la parte frontal del DNI, intente mejorar esa foto".getBytes("UTF-8")))
						.build();
			} catch (UnsupportedEncodingException e) {
			}
		}
		//removeUploadedFiles(back.getFile(), front.getFile());
		tfin = System.currentTimeMillis();	
		System.out.println("Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
		
		try {
			return Response.status(200).entity(Base64.getUrlEncoder().encode(
					"DNI guardado en el servidor".getBytes("UTF-8")))
					.build();
		} catch (UnsupportedEncodingException e) {
		}
		return Response.status(500).build();

	}

	

	// save uploaded file to a defined location on the server
	File saveFile(InputStream uploadedInputStream, String serverLocation) {

		OutputStream outpuStream =null;
		try {
			
			int read = 0;
			byte[] bytes = new byte[1024];
			Set<PosixFilePermission> perms = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
			File file = new File(serverLocation);
			String extension =FilenameUtils.getExtension(serverLocation);
			int i=1;
			
			while (file.exists()) {
				file = new File(serverLocation.replace("."+extension, "("+ i +")."+extension));
				i++;
			}
			Files.createFile(file.toPath(), PosixFilePermissions.asFileAttribute(perms));
			
			outpuStream = new FileOutputStream(file);
			if (uploadedInputStream == null) {
				return null;
			}
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
			} catch (IOException e) {}
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

	@GET
	@Path("/download")
	@Produces
	public Response downloadFiles(@QueryParam("campania") String Campania){
		return null;
		
	}

}

