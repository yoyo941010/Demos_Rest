/*
 * Archivo: MaintenanceService.java 
 * Proyecto: Demos_Rest
 * 
 * Autor: Aythami Estévez Olivas
 * Email: aythae@gmail.com
 * Fecha: 04-jul-2016
 * Repositorio GitHub: https://github.com/AythaE/Demos_Rest
 */
package es.usal.tfg;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.FutureTask;

import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;

import org.apache.commons.io.FilenameUtils;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import es.usal.tfg.security.SymmetricEncryption;

/**
 * The Class MaintenanceService que implementa las tareas de mantimiento, es
 * invocada por la clase {@link MyTaskExecutor} diariamente.
 * <p>
 * Se encarga de borrar los tokens activos, los de descarga, los PDFs generados
 * y recorrer la base de datos de campa�as comprobando sus fechas de borrado y 
 * en caso de estar en dicha fecha borra el directorio de campa�a, la campa�a
 * de la base de datos y de la estructura de campa�as. Para esta comprobación 
 * va leyendo la base de datos de campa�as y copiando las campa�as correctas a 
 * una base de datos de campa�as temporal (todo esto encriptado), al finalizar
 * sustituye la base de datos temporal por la original.
 */
public class MaintenanceService implements Runnable {

	

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		
		
		System.out.println("["+new Date().toString()+"] Mantenimiento: iniciando");
		
		
		//Borrado de la estructura de datos activeTokens
		CampaignManagement.clearActiveToken();
		
		System.out.println("["+new Date().toString()+"] Mantenimiento: tokens activos borrados");
				
		//Se obtienen todos los FutureTask encargados de generar PDFs que haya actualmente para ir
		//cancelandoloos si no han acabado y posteriormente borrar todos los pdfs así como la 
		//estructura de datos downloadTokens
		Collection<FutureTask<File>> pdfs = CampaignManagement.getAllPDFFuture();
		
		System.out.println("["+new Date().toString()+"] Mantenimiento: futuretask recuperadas");
		for (FutureTask<File> pdf : pdfs) {
			if (!pdf.isDone()) {
				
				if(pdf.cancel(true)){
					
					System.out.println("["+new Date().toString()+"] Mantenimiento: Tarea PDF parada");
					
				}
				else{
					
					System.err.println("["+new Date().toString()+"] Mantenimiento: Error parando Tarea PDF");
					
				}
			}
		}
		

		
		System.out.println("["+new Date().toString()+"] Mantenimiento: Todas las tareas PDF paradas");
		
		
		File campaingsDirectory = new File(CampaignManagement.WEBSERVICE_ABSOLUTE_ROUTE+"/campanias");
		
		try {
			borrarPDFs(campaingsDirectory);
		} catch (IOException e) {
			
			System.err.println("["+new Date().toString()+"] Mantenimiento: Error borrando PDFs");
		
			e.printStackTrace();
			System.out.println(CampaignManagement.SEPARADOR);
		}
		
		System.out.println("["+new Date().toString()+"] Mantenimiento: Todos los PDF borrados");
		
		
		CampaignManagement.clearDownloadToken();
		
		System.out.println("["+new Date().toString()+"] Mantenimiento: tokens de descarga borrados");
		

		// A continuación se lee el la base de datos de campa�as comprobando que
		// no haya pasado su fecha de borrado de ser así se borra esa campa�a de
		// la base de datos y su contenido, para ello se crea un fichero 
		// temporal en el que se van escribiendo las campa�as validas 
		// encriptadas y se omiten las que hayan pasado de su fecha. Al finalizar
		// se copia el fichero temporal sobre el de campa�as original
		
		Set<PosixFilePermission> permsRW = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
		File originalCampaignsFile = CampaignManagement.getCampaignsFile();
		File newCampaignsFile = new File(originalCampaignsFile.getParentFile(), "campaigns.tmp.json");
		CipherOutputStream cos = null;
		CipherInputStream cis = null;

		JsonReader reader = null;
		Writer wr = null;
		
		
		synchronized (CampaignManagement.lockCampaignsFile) {
			
			
			if (!CampaignManagement.campa�asIsEmpty()) {
				
				try {
					Files.createFile(newCampaignsFile.toPath(), PosixFilePermissions.asFileAttribute(permsRW));
					cos = SymmetricEncryption.encryptFileUsingKey(newCampaignsFile, CampaignManagement.masterKeyAlias);
				
					cis = SymmetricEncryption.decryptFileUsingKey(originalCampaignsFile, CampaignManagement.masterKeyAlias);
					
	
				
					/**
					 * @see https://sites.google.com/site/gson/streaming
					 */
					Gson gson = new Gson();
					
					CampaignCredentials c;
					
					
					reader= new JsonReader(new InputStreamReader(cis, "UTF-8"));
					reader.setLenient(true);
					
					wr = new OutputStreamWriter(cos);
					
		
					
					while(reader.hasNext()){
						JsonToken tokenJson =  reader.peek();
						if (!tokenJson.equals(JsonToken.BEGIN_OBJECT)) {
							break;
						}
						c = gson.fromJson(reader, CampaignCredentials.class);
	
						String deleteDateStr = c.getDeleteDate();
						
						Date deleteDate = CampaignManagement.dateFormat.parse(deleteDateStr);
						
						Date fechaActual = new Date();
						
						// Si la fecha de borrado no es posterior a la actual
						// entonces se borra la campa�a, en caso contrario se 
						// escribe al fichero temporal
						
						if (!deleteDate.after(fechaActual)) {
							CampaignManagement.borrarArchivosCampa�a(CampaignManagement.getCampa�a(c.getCampaignName()));
	
							CampaignManagement.deleteCampa�a(c.getCampaignName());
							
							System.out.println("["+new Date().toString()+"] Mantenimiento: borrada campa�a "+c.getCampaignName());
	
						
						}
						else {
							gson.toJson(c, wr);
	
							System.out.println("["+new Date().toString()+"] Mantenimiento: guardada campa�a "+c.getCampaignName());
	
							
						}
					}
					
					
				
				} catch (IOException | InvalidKeyException | NoSuchAlgorithmException | KeyStoreException
						| CertificateException | NoSuchPaddingException | InvalidAlgorithmParameterException
						| UnrecoverableEntryException | ParseException e) {
					
					
					System.err.println("["+new Date().toString()+"] Mantenimiento: Error comprobando fechas de campa�as");
					e.printStackTrace();
					System.out.println(CampaignManagement.SEPARADOR);
				} finally {
					try {
						if(reader!= null){
							reader.close();
						}
						if (cis!=null) {
							cis.close();
						}
						
						if (wr!=null) {
							wr.flush();
							wr.close();
						}
						
						if (cos!=null) {
							cos.flush();
							cos.close();
						}
					} catch (IOException e){}
				}
				
				
				try {
					/**
					 * @see https://docs.oracle.com/javase/tutorial/essential/io/move.html
					 */
					Files.move(newCampaignsFile.toPath(), originalCampaignsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				
					System.out.println("["+new Date().toString()+"] Mantenimiento: Base de datos de campa�as actualizada");
					
				} catch (IOException e) {
					
					System.out.println("["+new Date().toString()+"] Mantenimiento: Error sobreescribiendo base de datos de campa�as");
					e.printStackTrace();
					System.out.println(CampaignManagement.SEPARADOR);
					
				}
					
			} else {
				System.out.println("["+new Date().toString()+"] Mantenimiento: no existe ninguna campa�a");
			}
		}
	}
	
	/**
	 * Borrar PDFS de un directorio concreto, es invocado sobre el directorio
	 * general de campa�as por lo que borra todos los PDF existentes
	 *
	 * @param campaignsDirectory
	 *            the campaigns directory
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 * @see <a href="http://stackoverflow.com/a/8685959/6441806">Referencia</a>
	 */
	private static void borrarPDFs (File campaignsDirectory) throws IOException{
		if (campaignsDirectory.isDirectory()) {
			Files.walkFileTree(campaignsDirectory.toPath(), new SimpleFileVisitor<java.nio.file.Path>()
		    {
		        @Override
		        public FileVisitResult visitFile(java.nio.file.Path file, BasicFileAttributes attrs)
		                throws IOException
		        {
		        	
		        	String extension =FilenameUtils.getExtension(file.toString());
		            
		        	if (extension.equalsIgnoreCase("pdf")) {
		        		
		        		try {
							Files.delete(file);
							System.out.println("["+new Date().toString()+"] Mantenimiento: borrado "+file.toAbsolutePath().toString());
						} catch (Exception e) {
							System.err.println("["+new Date().toString()+"] Mantenimiento: Error borrando "+file.toAbsolutePath().toString());
							e.printStackTrace();
						}
					}
		        	
		            return FileVisitResult.CONTINUE;
		        }

		        @Override
		        public FileVisitResult visitFileFailed(java.nio.file.Path  file, IOException exc) throws IOException
		        {
		            // try to delete the file anyway, even if its attributes
		            // could not be read, since delete-only access is
		            // theoretically possible
		        	String extension =FilenameUtils.getExtension(file.toString());
		            
		        	if (extension.equalsIgnoreCase("pdf")) {
		        		try {
							Files.delete(file);
							System.out.println("["+new Date().toString()+"] Mantenimiento: borrado "+file.toAbsolutePath().toString());
						} catch (Exception e) {
							System.err.println("["+new Date().toString()+"] Mantenimiento: Error borrando "+file.toAbsolutePath().toString());
							e.printStackTrace();
						}
					}
		            return FileVisitResult.CONTINUE;
		        }
		        @Override
		        public FileVisitResult postVisitDirectory(java.nio.file.Path  dir, IOException exc) throws IOException
		        {
		            
		                return FileVisitResult.CONTINUE;
		            
		          
		        }
		        
		    });
		}
		 
	
	}

}
