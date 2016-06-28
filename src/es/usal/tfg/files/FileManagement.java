package es.usal.tfg.files;

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
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.FilenameUtils;
import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.opencv.core.Core;

import com.recognition.software.jdeskew.ImageDeskew;

import es.usal.tfg.Campaign;
import es.usal.tfg.CampaignManagement;
import es.usal.tfg.MyTaskExecutor;
import es.usal.tfg.imageProcessing.ImageProcessing;
import es.usal.tfg.security.SymmetricEncryption;

@Path("/files")
public class FileManagement {

	/**Update the following constant to the desired location for the uploads*/
	//public static final String SERVER_UPLOAD_LOCATION_FOLDER = CampaignManagement.WEBSERVICE_ABSOLUTE_ROUTE +"/files/";

	
	
	public static final String DOWNLOAD_URL = 
			"https://prodiasv08.fis.usal.es/Demos_Rest/rest/files/download?campaign=%s&download_token=%s";
	
	public static String webPageMessage = "<!DOCTYPE html><html><head><title>%s</title>"
			+ "<style type=\"text/css\">"
			+ "H1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;} "
			+ "H2 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:16px;} "
			+ "H3 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:14px;} "
			+ "BODY {font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;} "
			+ "B {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;} "
			+ "P {font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;}"
			+ ".line {height: 1px; background-color: #525D76; border: none;}</style> "
			+ "</head><body>"
			+ "<h1> %s</h1><div class=\"line\"></div>"
			+ "<p><b>mensaje</b> %s</p><hr class=\"line\"><h3>Apache Tomcat/8.0.14 (Debian)</h3></body></html>";

	/**
	 * Upload a File
	 * 
	 * @see <a href=
	 *      "https://examples.javacodegeeks.com/enterprise-java/rest/jersey/jersey-file-upload-example/">
	 *      Jersey File Upload Example</a>
	 */
	
	@POST
	@Path("/upload")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response uploadFile(FormDataMultiPart form) {

		long tIni =0, tfin=0;
		tIni = System.currentTimeMillis();
		String frontField = "front", backField = "back", tokenField = "token", campaignField = "campaign",
				numSignPaperField = "num_sign_paper";

		System.out.println(CampaignManagement.SEPARADOR);
		System.out.println("Extrayendo token");
		//Extraemos el token del FormDataMultiPart para verificarlo
		FormDataBodyPart filePart = form.getField(tokenField);

		//ContentDisposition headerOfFilePart = filePart.getContentDisposition();

		String token = filePart.getValueAs(String.class);
		
		
		filePart = form.getField(campaignField);

		//headerOfFilePart = filePart.getContentDisposition();

		String campaign64 = filePart.getValueAs(String.class);
		

		filePart = form.getField(numSignPaperField);

		//headerOfFilePart = filePart.getContentDisposition();

		String numSignPaper64 = filePart.getValueAs(String.class);
		
		String campaign = null;
		String numSignPaper= null;
		try {
			campaign = new String(Base64.getUrlDecoder().decode(campaign64.getBytes("UTF-8")));
			numSignPaper = new String(Base64.getUrlDecoder().decode(numSignPaper64.getBytes("UTF-8")));
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
				return Response.status(404).entity(Base64.getUrlEncoder().encodeToString("Error con su sesión, inicie sesión otra vez por favor.".getBytes("UTF-8"))).build();
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
		
		
		
		ImageProcessing imgP = new ImageProcessing(c, Long.parseLong(numSignPaper));	
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
						"Error reconociendo el DNI, intente hacer unas fotos más claras".getBytes("UTF-8")))
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
						"Error reconociendo la parte trasera del DNI, intente mejorar esa foto".getBytes("UTF-8")))
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


	@POST
	@Path("/download_query")
	@Consumes("application/x-www-form-urlencoded")
	public Response downloadQuery(@FormParam("campaign") String campaignName64, @FormParam("password") String password64){
		
		String campaignName = new String(Base64.getUrlDecoder().decode(campaignName64));
		String password = new String(Base64.getUrlDecoder().decode(password64));
		
		System.out.println(CampaignManagement.SEPARADOR);
		System.out.println("["+new Date()+"] Download query "+campaignName);
		
		int loginResult = CampaignManagement.loginInterno(campaignName, password);

		/**
		 * TODO custom error pages: http://stackoverflow.com/questions/13914575/how-to-build-server-level-custom-error-page-in-tomcat
		 * 
		 */
		
		String title = "Demos";
		String h1 =null;
		String mensaje = null;
		String response = null;
		switch (loginResult) {
		
		case CampaignManagement.LOGIN_EXITO:
			break;
			
		case CampaignManagement.LOGIN_INCORRECTO:
			
				
			h1 = "Petici&oacute;n de descarga - Error";
			mensaje = "Contrase&ntilde;a incorrecta";
			response = String.format(webPageMessage, title, h1, mensaje);
			return Response.status(400).entity(response).build();
			
		

		case CampaignManagement.LOGIN_NO_EXISTE_CAMPAÑA:
			
			
			h1 = "Petici&oacute;n de descarga - Error";
			mensaje = "No existe una cuenta con ese nombre";
			response = String.format(webPageMessage, title, h1, mensaje);
			return Response.status(400).entity(response)
					.build();
		

		

		case CampaignManagement.LOGIN_ERROR_INTERNO:
			

			h1 = "Petici&oacute;n de descarga - Error";
			mensaje = "Error interno del servidor";
			response = String.format(webPageMessage, title, h1, mensaje);
			return Response.status(500).entity(response)
					.build();
		
		default:
			return Response.status(500).build();

		}
		
		
		
		String downloadToken = null;
		
		try {
			downloadToken= generateDownloadToken(campaignName);
		} catch (InvalidKeyException | NoSuchAlgorithmException | KeyStoreException | CertificateException
				| NoSuchPaddingException | InvalidAlgorithmParameterException | UnrecoverableEntryException
				| IllegalBlockSizeException | BadPaddingException | IOException e) {
			
			e.printStackTrace();
			h1 = "Petici&oacute;n de descarga - Error";
			mensaje = "Error interno del servidor";
			response = String.format(webPageMessage, title, h1, mensaje);
			return Response.status(500).entity(response)
					.build();
		
		}
		
		String downloadURL = String.format(DOWNLOAD_URL, campaignName64, downloadToken);
		
		Campaign camp = CampaignManagement.getCampaña(campaignName);
		
		long numFirmas;
		synchronized (camp) {
			numFirmas = camp.getNumeroFirmas();
		}
		if (numFirmas < 1) {
			h1 = "Petici&oacute;n de descarga - Error";
			mensaje = "Esta campa&ntilde;a no contiene ninguna firma";
			response = String.format(webPageMessage, title, h1, mensaje);
			return Response.status(500).entity(response)
					.build();
		}
		
		PDFThread pdfT = new PDFThread(camp);
		FutureTask<File> pdfFuture = new FutureTask<File>(pdfT);
		boolean executionResult = MyTaskExecutor.startExecution(pdfFuture);
		
		if (executionResult == false) {
			h1 = "Petici&oacute;n de descarga - Error";
			mensaje = "El servidor est&aacute; sobrecargado, intente repetir su petici&oacute;n en unos minutos por favor";
			response = String.format(webPageMessage, title, h1, mensaje);
			return Response.status(500).entity(response)
					.build();
		}
		CampaignManagement.addDownloadToken(downloadToken, pdfFuture);
		
		
		
		long numHojasDNI = numFirmas / PDFThread.NUMERO_DNI_X_HOJA;
		

		//Si el modulo es distinto de 0 significa que tenemos que sumar otra hoja
		if (numFirmas % PDFThread.NUMERO_DNI_X_HOJA > 0) {
			numHojasDNI++;
		}
		
		//Para contar la tabla
		numHojasDNI++;
		
		//TODO medir tiempos en servidor y sustituir el 5
		int tiempoEstimado = (int) ((numHojasDNI * 5)/60);
		if ((numHojasDNI * 5)%60 > 0) {
			tiempoEstimado++;
		}
		
		long bytesEstimados = numHojasDNI*22754703;
		
		
		String tamañoEstimado = humanReadableByteCount(bytesEstimados, false);
		
		title = "Demos";
		h1 = "Petici&oacute;n de descarga";
		mensaje = "Su documento estar&aacute; preparado en "+tiempoEstimado+" minutos y "
				+ "pesar&aacute; "+tamañoEstimado+" aproximadamente en la siguiente  <a href=\""+downloadURL+"\">URL</a> ";
		response = String.format(webPageMessage, title, h1, mensaje);
		return Response.status(200).entity(response).build();
	}
	
	@GET
	@Path("/download")
	//@Produces("application/pdf")
	public Response download(@QueryParam("campaign") String campaignName64, @QueryParam("download_token") String downloadToken){
		

		String campaignName = new String(Base64.getUrlDecoder().decode(campaignName64));
		String title = "Demos";
		String h1 = null, mensaje=null, response=null;
		if (!CampaignManagement.existsDownloadToken(downloadToken)) {
			h1 = "Descarga de documento - Error";
			mensaje = "No existe ninguna descarga pendiente en esta direcci&oacute;n";
			response = String.format(webPageMessage, title, h1, mensaje);
			return Response.status(404).entity(response)
					.build();
		
		}
		FutureTask<File> pdfFuture = CampaignManagement.getPDFFuture(downloadToken);
		

		File pdfFile= null;
		if (!pdfFuture.isDone()) {
			
			h1 = "Descarga de documento";
			mensaje = "La descarga a&uacute;n no esta preparada";
			response = String.format(webPageMessage, title, h1, mensaje);
			return Response.status(420).entity(response)
					.build();
		}
		else{
			try {
				pdfFile = pdfFuture.get();
			} catch (Exception e) {
				e.printStackTrace();
				pdfFile = null;
			}
			if (pdfFile == null) {
				h1 = "Descarga de documento - Error";
				mensaje = "Ha habido problemas creando el PDF";
				response = String.format(webPageMessage, title, h1, mensaje);
				return Response.status(500).entity(response)
						.build();
			}
		}
		
		final File pdfFileFinal = pdfFile;
		
		
		/**
		 * @reference http://howtodoinjava.com/jersey/jax-rs-jersey-2-file-download-example-using-streamingoutput/
		 */
		StreamingOutput fileStream =  new StreamingOutput() 
        {
            @Override
            public void write(java.io.OutputStream output) throws IOException, WebApplicationException 
            {
                try
                {
                    
                    byte[] data = Files.readAllBytes(pdfFileFinal.toPath());
                    output.write(data);
                    output.flush();
                } 
                catch (IOException e) 
                {
                    throw new WebApplicationException("Error enviando PDF");
                } finally {
					output.close();
				}
            }
        };
        String nombreArchivo = "Firmas_"+campaignName+".pdf";
        
        return Response
                .ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
                .header("content-disposition","attachment; filename = "+nombreArchivo)
                .build();
	}
	
	/**
	 * 
	 * @param campaign
	 * @return
	 * @throws IOException 
	 * @throws UnsupportedEncodingException 
	 * @throws BadPaddingException 
	 * @throws IllegalBlockSizeException 
	 * @throws UnrecoverableEntryException 
	 * @throws InvalidAlgorithmParameterException 
	 * @throws NoSuchPaddingException 
	 * @throws CertificateException 
	 * @throws KeyStoreException 
	 * @throws NoSuchAlgorithmException 
	 * @throws InvalidKeyException 
	 * 
	 * @see https://docs.oracle.com/javase/8/docs/api/java/time/LocalTime.html
	 */
	private String generateDownloadToken (String campaign) throws InvalidKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, NoSuchPaddingException, InvalidAlgorithmParameterException, UnrecoverableEntryException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException, IOException{
		
		byte[] downloadToken=null;
		downloadToken = SymmetricEncryption.encryptUsingKey(campaign.getBytes("UTF-8"), campaign );

		String strDownloadToken =  new String (Base64.getUrlEncoder().encode(downloadToken), "UTF-8");
		
		return  strDownloadToken;
		
		
	}
	
	
	/**
	 * 
	 * @param bytes
	 * @param si
	 * @return
	 * @see http://stackoverflow.com/a/3758880/6441806
	 */
	public static String humanReadableByteCount(long bytes, boolean si) {
	    int unit = si ? 1000 : 1024;
	    if (bytes < unit) return bytes + " B";
	    int exp = (int) (Math.log(bytes) / Math.log(unit));
	    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
	    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}
}

