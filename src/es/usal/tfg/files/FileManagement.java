/*
 * Archivo: FileManagement.java 
 * Proyecto: Demos_Rest
 * 
 * Autor: Aythami EstÃ©vez Olivas
 * Email: aythae@gmail.com
 * Fecha: 04-jul-2016
 * Repositorio GitHub: https://github.com/AythaE/Demos_Rest
 */
package es.usal.tfg.files;

import java.io.File;
import java.io.FileOutputStream;
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
import java.util.Base64;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.FutureTask;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.FilenameUtils;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import es.usal.tfg.Campaign;
import es.usal.tfg.CampaignManagement;
import es.usal.tfg.MyTaskExecutor;
import es.usal.tfg.imageProcessing.ImageProcessing;
import es.usal.tfg.security.SymmetricEncryption;

/**
 * The Class FileManagement, se encarga de recibir las peticiones
 * relacionadas con los ficheros, en concreto la subida de fotografias,
 * la peticiÃ³n de descarga y la descarga en si.
 */
@Path("/files")
public class FileManagement {

	
	/**
	 * The Constant DOWNLOAD_URL a la que basta con añadirle al final el token
	 * de descarga generado para poder devolverselo al usuario
	 */
	public static final String DOWNLOAD_URL = "https://prodiasv08.fis.usal.es/Demos_Rest/rest/files/download?campaign=%s&download_token=%s";

	/**
	 * The web page message que se puede personalizar mediante el mÃ©todo
	 * {@link String#format(String, Object...)} añadiendole un tÃ­tulo, 
	 * un header y un mensaje a mostrar al usuario. EstÃ¡ basado en los mensajes
	 * que muestra <a href="http://tomcat.apache.org/">Apache Tomcat</a>
	 */
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
	 * Subida de fotografÃ­as de DNI a partir de un multipart/form-data con los
	 * siguientes campos. 
	 *  <ul>
	 *  	<li>front: un {@link File} que representa la foto frontal del DNI</li>
	 *  	<li>back: un {@link File} que representa la foto posterior del DNI</li>
	 *  	<li>token: un {@link String} que representa el token de sesiÃ³n de 
	 *  		campaña</li>
	 *  	<li>campaign: un {@link String} que representa el nombre de la 
	 * 			campaña</li>
	 * 		<li>num_sign_paper: un {@link String} que representa el nÃºmero de 
	 * 			la hoja de firmas asociada a este DNI</li>
	 *  </ul> 
	 *  <p>
	 *  Todos los campos textuales se encuentran codificados en Base64.
	 *
	 * @param form the form
	 * @return response con determinado codigo y mensaje en funcion del exito
	 * o fracaso de la operaciÃ³n de subida
	 * @see <a href=
	 *      "https://examples.javacodegeeks.com/enterprise-java/rest/jersey/jersey-file-upload-example/">
	 *      Jersey File Upload Example</a>
	 */
	
	@POST
	@Path("/upload")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response upload(FormDataMultiPart form) {

		long tIni =0, tfin=0;
		tIni = System.currentTimeMillis();
		String frontField = "front", backField = "back", tokenField = "token", campaignField = "campaign",
				numSignPaperField = "num_sign_paper";

		System.out.println("["+new Date().toString()+"] Upload:  iniciado");
		
		//Extraemos el token del FormDataMultiPart para verificarlo
		FormDataBodyPart filePart = form.getField(tokenField);

		String token = filePart.getValueAs(String.class);
		
		
		filePart = form.getField(campaignField);

		String campaign64 = filePart.getValueAs(String.class);
		

		filePart = form.getField(numSignPaperField);

		String numSignPaper64 = filePart.getValueAs(String.class);
		
		String campaign = null;
		String numSignPaper= null;
		try {
			campaign = new String(Base64.getUrlDecoder().decode(campaign64.getBytes("UTF-8")));
			numSignPaper = new String(Base64.getUrlDecoder().decode(numSignPaper64.getBytes("UTF-8")));
		} catch (UnsupportedEncodingException e1) {
			System.err.println("["+new Date().toString()+"] Upload: error decodificando campos en Base64");
			e1.printStackTrace();
			try {
				return Response.status(500).entity(Base64.getUrlEncoder().encodeToString("Error interno del servidor".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e) {}
		}
		
		

		
		
		//El siguiente metodo comprueba si el token esta en el conjunto de activos,
		//en caso negativo intenta desencriptarlo y comprobar que es correcto
		if (CampaignManagement.compruebaTokenInterno(token, campaign) == false) {
			System.err.println("["+new Date().toString()+"] Upload "+campaign+": Error con la sesion, token invalido.");
			try {
				return Response.status(404).entity(Base64.getUrlEncoder().encodeToString("Error con su sesiÃ³n, inicie sesiÃ³n otra vez por favor.".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e) {}
		}
		
		Campaign c = CampaignManagement.getCampaña(campaign);
		
		UploadThread front = new UploadThread(frontField, form, this, c);
		UploadThread back = new UploadThread(backField, form, this, c);
		
		Thread hFront = new Thread(front);
		Thread hBack = new Thread(back);
		
		
		System.out.println("["+new Date().toString()+"] Upload "+campaign+": Arrancando los hilos de subida");
		
		hFront.start();
		hBack.start();
		
		try {
			hFront.join();
		} catch (InterruptedException e) {
			System.err.println("["+new Date().toString()+"] Upload "+campaign+": Error, el hilo de subida front ha fallado");
			
			e.printStackTrace();
			removeUploadedFiles(back.getFile(), front.getFile());
			tfin = System.currentTimeMillis();	
			System.out.println("["+new Date().toString()+"] Upload "+campaign+":Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
			
			
			try {
				return Response.status(500).entity(Base64.getUrlEncoder().encodeToString("Error interno del servidor".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e1) {}
		
		}
		
		try {
			hBack.join();
		} catch (InterruptedException e) {
			System.err.println("["+new Date().toString()+"] Upload "+campaign+": Error, el hilo de subida back ha fallado");
			
			e.printStackTrace();
			removeUploadedFiles(back.getFile(), front.getFile());
			tfin = System.currentTimeMillis();	
			System.out.println("["+new Date().toString()+"] Upload "+campaign+":Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
			
			
			try {
				return Response.status(500).entity(Base64.getUrlEncoder().encodeToString("Error interno del servidor".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e1) {}
			
		}

		
		if (back.isExito() ==false || front.isExito()==false) {
			System.err.println("["+new Date().toString()+"] Upload "+campaign+": Alguno de los hilos ha fallado");
			
			removeUploadedFiles(back.getFile(), front.getFile());
			
			tfin = System.currentTimeMillis();	
			System.out.println("["+new Date().toString()+"] Upload "+campaign+":Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
			
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
			System.err.println("["+new Date().toString()+"] Upload "+campaign+": Alguno de los hilos ha fallado");
			removeUploadedFiles(back.getFile(), front.getFile());
			tfin = System.currentTimeMillis();	
			System.out.println("["+new Date().toString()+"] Upload "+campaign+":Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
			
			try {
				return Response.status(500).entity(Base64.getUrlEncoder().encodeToString("Error interno del servidor".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e1) {}
		}
		
		
		
		ImageProcessing imgP = new ImageProcessing(c, Long.parseLong(numSignPaper));	
		int result = imgP.imageProcessingAndOCR(dniFrontal, dniPosterior);
		
		if (result == ImageProcessing.ERROR_INTERNO) {
			System.err.println("["+new Date().toString()+"] Upload "+campaign+": El proceso de reconocimiento ha fallado");

			removeUploadedFiles(back.getFile(), front.getFile());
			tfin = System.currentTimeMillis();	
			System.out.println("["+new Date().toString()+"] Upload "+campaign+":Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
			
			try {
				return Response.status(500).entity(Base64.getUrlEncoder().encodeToString("Error interno del servidor".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e1) {}
			
		}
		else if (result == ImageProcessing.ERROR_TIMEOUT || result == ImageProcessing.ERROR_AMBOS) {
			System.err.println("["+new Date().toString()+"] Upload "+campaign+":El proceso de reconocimiento ha superado el timeout o ambos hilos han fallado");

			removeUploadedFiles(back.getFile(), front.getFile());
			tfin = System.currentTimeMillis();	
			System.out.println("["+new Date().toString()+"] Upload "+campaign+":Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
			try {
				return Response.status(510)
						.entity(Base64.getUrlEncoder().encode(
						"Error reconociendo el DNI, intente hacer unas fotos mÃ¡s claras".getBytes("UTF-8")))
						.build();
			} catch (UnsupportedEncodingException e) {
			}
		}
		else if (result == ImageProcessing.ERROR_FRONTAL) {
			System.err.println("["+new Date().toString()+"] Upload "+campaign+": El proceso de reconocimiento frontal ha fallado");

			removeUploadedFiles(back.getFile(), front.getFile());
			tfin = System.currentTimeMillis();	
			System.out.println("["+new Date().toString()+"] Upload "+campaign+":Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
			try {
				return Response.status(511).entity(Base64.getUrlEncoder().encode(
						"Error reconociendo la parte frontal del DNI, intente mejorar esa foto".getBytes("UTF-8")))
						.build();
			} catch (UnsupportedEncodingException e) {
			}
		}
		else if (result == ImageProcessing.ERROR_POSTERIOR) {
			System.err.println("["+new Date().toString()+"] Upload "+campaign+": El proceso de reconocimiento posterior ha fallado");

			removeUploadedFiles(back.getFile(), front.getFile());
			tfin = System.currentTimeMillis();	
			System.out.println("["+new Date().toString()+"] Upload "+campaign+":Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
			try {
				return Response.status(512).entity(Base64.getUrlEncoder().encode(
						"Error reconociendo la parte trasera del DNI, intente mejorar esa foto".getBytes("UTF-8")))
						.build();
			} catch (UnsupportedEncodingException e) {
			}
		}
		
		
		
		System.out.println("["+new Date().toString()+"] Upload"+campaign+":  finalizado");
		
		tfin = System.currentTimeMillis();	
		System.out.println("["+new Date().toString()+"] Upload "+campaign+":Tiempo total: " + ((double)(tfin-tIni) / 1000)+ " segundos");
		
		try {
			return Response.status(200).entity(Base64.getUrlEncoder().encode(
					"DNI guardado en el servidor".getBytes("UTF-8")))
					.build();
		} catch (UnsupportedEncodingException e) {
		}
		return Response.status(500).build();

	}

	

	/**
	 * Guarda un fichero procedente de un {@link InputStream} en una 
	 * localizaciÃ³n pasada por parametro. Para ello crea el fichero con 
	 * permisos de lectura y escritura para el dueño Ãºnicamente, controlando
	 * que no exista ya un fichero con ese nombre (en cuyo caso le añade un 
	 * nÃºmero al final) y va copiando el InputStream a un OutputStream KBi a 
	 * KBi.
	 *
	 * @param uploadedInputStream the uploaded input stream
	 * @param serverLocation the server location
	 * @return the file guardado
	 */
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

	/**
	 * Borra los ficheros subidos en caso de error. Estos han de ser pasados 
	 * como sus parametros.
	 *
	 * @param f fichero 1
	 * @param f2 fichero 2
	 */
	private void removeUploadedFiles(File f, File f2) {

		if (f != null && f.exists()) {
			if (f.delete()) {

				System.out.println("[" + new Date().toString() + "] RemoveUploadedFiles " + f.getAbsolutePath()
						+ ":  borrado correcto");

			} else {
				System.err.println("[" + new Date().toString() + "] RemoveUploadedFiles " + f.getAbsolutePath()
						+ ":  borrado incorrecto");
			}
		}
		if (f2 != null && f2.exists()) {
			if (f2.delete()) {

				System.out.println("[" + new Date().toString() + "] RemoveUploadedFiles " + f2.getAbsolutePath()
						+ ":  borrado correcto");

			} else {
				System.err.println("[" + new Date().toString() + "] RemoveUploadedFiles " + f2.getAbsolutePath()
						+ ":  borrado incorrecto");
			}
		}
	}

	/**
	 * Recibe peticiones de descarga aceptando formularios de tipo 
	 * application/x-www-form-urlencoded. Comprueba los credenciales del 
	 * usuario, si estos son correctos arranca proceso asÃ­ncrono de 
	 * construcciÃ³n del PDF y le muestra un mensaje HTML al usuario indicandole
	 * como descargarse el fichero, su tamaño y el tiempo estimado de espera.
	 * 
	 * <p>
	 * Todos sus parametros estan encodeados en Base64
	 *
	 * @param campaignName64 nombre de la campaña
	 * @param password64 contraseña de la campaña
	 * @return response con determinado codigo y mensaje en funcion del exito
	 * o fracaso de la operaciÃ³n de preparado de la descarga
	 */
	@POST
	@Path("/download_query")
	@Consumes("application/x-www-form-urlencoded")
	public Response downloadQuery(@FormParam("campaign") String campaignName64, @FormParam("password") String password64){
		
		String campaignName = new String(Base64.getUrlDecoder().decode(campaignName64));
		String password = new String(Base64.getUrlDecoder().decode(password64));
		
	
		System.out.println("[" + new Date().toString() + "] Download query "+campaignName+": iniciado");
		
		int loginResult = CampaignManagement.loginInterno(campaignName, password);

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
			System.err.println("[" + new Date().toString() + "] Download query "+campaignName+": Login incorrecto");
			
			return Response.status(400).entity(response).build();
			
		

		case CampaignManagement.LOGIN_NO_EXISTE_CAMPAÑA:
			
			
			h1 = "Petici&oacute;n de descarga - Error";
			mensaje = "No existe una cuenta con ese nombre.";
			response = String.format(webPageMessage, title, h1, mensaje);
			System.err.println("[" + new Date().toString() + "] Download query "+campaignName+": No existe cuenta");
			return Response.status(400).entity(response)
					.build();
		

		

		case CampaignManagement.LOGIN_ERROR_INTERNO:
			

			h1 = "Petici&oacute;n de descarga - Error";
			mensaje = "Error interno del servidor.";
			response = String.format(webPageMessage, title, h1, mensaje);
			System.err.println("[" + new Date().toString() + "] Download query "+campaignName+": Error interno");
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
			System.err.println("[" + new Date().toString() + "] Download query "+campaignName+": Error generando token de descarga");
			e.printStackTrace();
			h1 = "Petici&oacute;n de descarga - Error";
			mensaje = "Error interno del servidor.";
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
			mensaje = "Esta campa&ntilde;a no contiene ninguna firma.";
			response = String.format(webPageMessage, title, h1, mensaje);
			System.err.println("[" + new Date().toString() + "] Download query "+campaignName+": La campaña no contiene firmas.");
			return Response.status(500).entity(response)
					.build();
		}
		
		
		PDFThread pdfT = new PDFThread(camp);
		FutureTask<File> pdfFuture = new FutureTask<File>(pdfT);
		boolean executionResult = MyTaskExecutor.startExecution(pdfFuture);
		
		if (executionResult == false) {
			h1 = "Petici&oacute;n de descarga - Error";
			mensaje = "El servidor est&aacute; sobrecargado, intente repetir su petici&oacute;n en unos minutos por favor.";
			response = String.format(webPageMessage, title, h1, mensaje);
			System.err.println("[" + new Date().toString() + "] Download query "+campaignName+": Todos los hilos de PDF ocupados");
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
		

		int tiempoEstimado = (int) ((numHojasDNI * 5)/60);
		if ((numHojasDNI * 5)%60 > 0) {
			tiempoEstimado++;
		}
		
		long bytesEstimados = numHojasDNI*22754703;
		
		
		String tamañoEstimado = humanReadableByteCount(bytesEstimados, false);
		
		title = "Demos";
		h1 = "Petici&oacute;n de descarga";
		mensaje = "Su documento estar&aacute; preparado en "+tiempoEstimado+" minutos y "
				+ "pesar&aacute; "+tamañoEstimado+" aproximadamente en la siguiente  <a href=\""+downloadURL+"\">URL</a>.";
		response = String.format(webPageMessage, title, h1, mensaje);
		System.out.println("[" + new Date().toString() + "] Download query "+campaignName+": finalizando correctamente");
		return Response.status(200).entity(response).build();
	}
	
	/**
	 * Recibe peticiones de descarga para un determinado token y campaña, 
	 * comprueba si el PDF asociado a estos parametros estÃ¡ construido 
	 * correctamente y en caso afirmativo responde devolviendo el stream de ese
	 * fichero.
	 *
	 * @param campaignName64 the campaign name 64
	 * @param downloadToken the download token
	 * @return response con el stream del fichero, si todo ha ido bien, o con
	 * determinado codigo y mensaje de errorn en caso de fracaso
	 */
	@GET
	@Path("/download")
	public Response download(@QueryParam("campaign") String campaignName64, @QueryParam("download_token") String downloadToken){
		

		String campaignName = new String(Base64.getUrlDecoder().decode(campaignName64));
		String title = "Demos";
		String h1 = null, mensaje=null, response=null;
		System.out.println("[" + new Date().toString() + "] Download "+campaignName+": Iniciado");
		if (!CampaignManagement.existsDownloadToken(downloadToken)) {
			h1 = "Descarga de documento - Error";
			mensaje = "No existe ninguna descarga pendiente en esta direcci&oacute;n.";
			response = String.format(webPageMessage, title, h1, mensaje);
			
			System.err.println("[" + new Date().toString() + "] Download "+campaignName+": No existen descargas pendientes en ese token");
			return Response.status(404).entity(response)
					.build();
		
		}
		FutureTask<File> pdfFuture = CampaignManagement.getPDFFuture(downloadToken);
		

		File pdfFile= null;
		if (!pdfFuture.isDone()) {
			
			h1 = "Descarga de documento";
			mensaje = "La descarga a&uacute;n no esta preparada.";
			response = String.format(webPageMessage, title, h1, mensaje);
			System.out.println("[" + new Date().toString() + "] Download "+campaignName+": descarga aÃºn no preparada");
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
				mensaje = "Ha habido problemas creando el PDF, vuelva a intentar solicitar una descarga yendo a la siguiente "
						+ "<a href=\"https://prodiasv08.fis.usal.es/Demos_Rest/formDownloadQuery.html//\">URL</a>. ";
				response = String.format(webPageMessage, title, h1, mensaje);
				System.err.println("[" + new Date().toString() + "] Download "+campaignName+": Error preparando el PDF");
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
                	System.err.println("[" + new Date().toString() + "] Download "+campaignName+": Error enviando el PDF");
                    throw new WebApplicationException("Error enviando PDF");
                } finally {
					output.close();
				}
            }
        };
        String nombreArchivo = "Firmas_"+campaignName+".pdf";
        
        System.out.println("[" + new Date().toString() + "] Download "+campaignName+": finalizado correctamente");
        return Response
                .ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
                .header("content-disposition","attachment; filename = "+nombreArchivo)
                .build();
	}
	
	/**
	 * Genera token de descarga encriptando el nombre de la campaña que se le 
	 * pasa por parametro.
	 *
	 * @param campaign nombre de la campaña a encriptar
	 * 
	 * @return token generado
	 * 
	 * @throws InvalidKeyException the invalid key exception
	 * @throws NoSuchAlgorithmException the no such algorithm exception
	 * @throws KeyStoreException the key store exception
	 * @throws CertificateException the certificate exception
	 * @throws NoSuchPaddingException the no such padding exception
	 * @throws InvalidAlgorithmParameterException the invalid algorithm parameter exception
	 * @throws UnrecoverableEntryException the unrecoverable entry exception
	 * @throws IllegalBlockSizeException the illegal block size exception
	 * @throws BadPaddingException the bad padding exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * 
	 */
	private String generateDownloadToken (String campaign) throws InvalidKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, NoSuchPaddingException, InvalidAlgorithmParameterException, UnrecoverableEntryException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException, IOException{
		
		byte[] downloadToken=null;
		downloadToken = SymmetricEncryption.encryptUsingKey(campaign.getBytes("UTF-8"), campaign );

		String strDownloadToken =  new String (Base64.getUrlEncoder().encode(downloadToken), "UTF-8");
		
		return  strDownloadToken;
		
		
	}
	
	
	/**
	 * Convierte un tamaño en bytes representado por su parametro bytes en
	 * una cadena de caracteres que el usuario puede interpretar mÃ¡s facilmente
	 *
	 * @param bytes los bytes a convertir
	 * @param si controla si el resultado ha de ser expresado en el sistema 
	 * internacional (aumentando de 10^3 en 10^3 las unidades) o por el 
	 * contrario en binario (aumentando de 2^10 en 2^10)
	 * @return string que contiene el tamaño ya convertido
	 * @see <a href="http://stackoverflow.com/a/3758880/6441806">Referencia</a>
	 */
	public static String humanReadableByteCount(long bytes, boolean si) {
	    int unit = si ? 1000 : 1024;
	    if (bytes < unit) return bytes + " B";
	    int exp = (int) (Math.log(bytes) / Math.log(unit));
	    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
	    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}
}

