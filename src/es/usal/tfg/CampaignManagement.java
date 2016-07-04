/*
 * Archivo: CampaignManagement.java 
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
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.SimpleFileVisitor;
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
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.FutureTask;

import javax.crypto.BadPaddingException;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.inject.Singleton;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import es.usal.tfg.files.PDFThread;
import es.usal.tfg.security.PasswordStorage;
import es.usal.tfg.security.PasswordStorage.CannotPerformOperationException;
import es.usal.tfg.security.PasswordStorage.InvalidHashException;
import es.usal.tfg.security.SymmetricEncryption;

/**
 * Clase principal del proyecto, encargada de gestionar las campañas, además 
 * se encarga de instanciar y lanzar la tarea de {@link es.usal.tfg.MaintenanceService mantenimiento} mediante
 * la clase encargada de ejecutarla, {@link es.usal.tfg.MyTaskExecutor MyTaskExecutor} 
 * 
 * 
 * @see es.usal.tfg.Campaign Campaign 
 * @see es.usal.tfg.CampaignCredentials CampaignCredentials
 */
@Singleton
@WebListener
@Path("/campaign")
public class CampaignManagement implements ServletContextListener {

	/**
	 * The Constant WEBSERVICE_ABSOLUTE_ROUTE que inidica el directorio
	 * principal del proyecto
	 */
	public static final String WEBSERVICE_ABSOLUTE_ROUTE = "/demos";
	
	/** The Constant SEPARADOR usada para formatear salida por pantalla*/
	public static final String SEPARADOR = "------------------------------------------------------------";
	
	/** 
	 * The Constant masterKeyAlias que representa el alias de la clave 
	 * de cifrado de la base de datos de campañas, campaigns.json
	 */
	static final String masterKeyAlias = "master_key";

	/**
	 * The Constant LOGIN_EXITO para indicar exito en
	 * {@link CampaignManagement#loginInterno(String, String) loginInterno}
	 */
	public static final int LOGIN_EXITO = 0;

	/**
	 * The Constant LOGIN_ERROR_INTERNO para indicar error interno en
	 * {@link CampaignManagement#loginInterno(String, String) loginInterno}
	 */
	public static final int LOGIN_ERROR_INTERNO = 1;

	/**
	 * The Constant LOGIN_NO_EXISTE_CAMPAÑA para indicar que no existe campaña
	 * en {@link CampaignManagement#loginInterno(String, String) loginInterno}
	 */
	public static final int LOGIN_NO_EXISTE_CAMPAÑA = 2;

	/**
	 * The Constant LOGIN_INCORRECTO para indicar login incorrecto en
	 * {@link CampaignManagement#loginInterno(String, String) loginInterno}
	 */
	public static final int LOGIN_INCORRECTO = 3;

	/** The Constant dateFormat para formatear los tipo {@link Date} */
	public static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
	
	/** 
	 * Estructura de datos utilizada para asociar instancias de {@link Campaign} 
	 * con su nombre. Se utiliza desde otras clases para poder buscar y acceder
	 * a los campos de una campaña conociendo solo su nombre
	 * 
	 */
	private static HashMap<String ,Campaign> campañas = new HashMap<>();
	
	/** 
	 * Estructura de datos utilizada almacenar los tokens de sesion de campaña
	 * activos, un token está se introduce aqui al generarse en los metodos
	 * {@link CampaignManagement#register(String, String, String) registro} o
	 * {@link CampaignManagement#login(String, String) login} y también cuando
	 * se comprueba un token antiguo pero valido en 
	 * {@link CampaignManagement#authenticateToken(String, String) autenticateToken}
	 * <p>
	 * Esta estructura se usa por razones de eficiencia para no desencriptar los
	 * tokens cada vez que llegan. Se borra una vez al día.
	 * 
	 * @see es.usal.tfg.MaintenanceService MaintenanceService
	 * 
	 */
	private static HashSet<String> activeTokens = new HashSet<>();
	
	/** 
	 * Estructura de datos utilizada para asociar un token de subida con el
	 * {@link FutureTask} que ejecutara la instancia de {@link PDFThread} 
	 * encargada de generar ese PDF
	 * 
	 * @see es.usal.tfg.files.FileManagement#downloadQuery(String, String)
	 */
	private static HashMap<String, FutureTask<File>> downloadTokens = new HashMap<>();
	
	/** 
	 * The Constant lockDownload que actua como lock para los bloques syncronized
	 * en las lecturas o modificaciones de la estructura de datos 
	 * {@link CampaignManagement#downloadTokens} de forma sincronizada en
	 * los distintos hilos
	 */
	private static final Object lockDownload = new Object();
	
	/** 
	 * The Constant lockCampañas que actua como lock para los bloques syncronized
	 * en las lecturas o modificaciones de la estructura de datos 
	 * {@link CampaignManagement#campañas} de forma sincronizada en
	 * los distintos hilos
	 */
	private static final Object lockCampañas = new Object();
	
	/** The Constant lockTokens que actua como lock para los bloques syncronized
	 * en las lecturas o modificaciones de la estructura de datos 
	 * {@link CampaignManagement#activeTokens} de forma sincronizada en
	 * los distintos hilos
	 */
	private static final Object lockTokens = new Object();
	
	/** Archivo que representa a la base de datos de las campañas. */
	private static final File campaignsFile = new File(WEBSERVICE_ABSOLUTE_ROUTE + "/campaigns.json");
	
	/** The Constant lockCampaignsFile que actua como lock para los bloques 
	 * syncronized en las lecturas o modificaciones del archivo representado por
	 * {@link CampaignManagement#campaignsFile} de forma sincronizada en
	 * los distintos hilos
	 */
	static final Object lockCampaignsFile = new Object();
	
	/** Instancia de {@link MyTaskExecutor} */
	private MyTaskExecutor taskExecutor = null;
	
	/** 
	 * Instancia de esta clase usada para controlar que no se creen multiples
	 * instancias, para más información buscar patron Singleton
	 */
	private static CampaignManagement instance = null;
	

	
	
	/**
	 * Gets the {@link CampaignManagement#campaignsFile}.
	 *
	 * @return the campaigns file
	 */
	static File getCampaignsFile(){
		
		return campaignsFile;
	}
	
	/**
	 * Comprueba si la estructura {@link CampaignManagement#campañas} está vacía.
	 *
	 * @return true, si está vacía, false, en caso contrario
	 */
	static boolean campañasIsEmpty (){
		synchronized (lockCampañas) {
			return campañas.isEmpty();
		}
	}
	
	/**
	 * Obtiene la campaña asociada al parametro en la estructura 
	 * {@link CampaignManagement#campañas}.
	 *
	 * @param campaignName nombre de la campaña que se desea recuperar
	 * @return la campaña asociada o null en caso de no existir
	 */
	public static Campaign getCampaña(String campaignName) {
		
		synchronized (lockCampañas) {
			return campañas.get(campaignName);
		}	
	}
	

	/**
	 * Borra la campaña asociada al parametro en la estructura 
	 * {@link CampaignManagement#campañas}.
	 *
	 *
	 * @param campaignName nombre de la campaña que se desea borrar
	 * @return true, en caso de exito o false en el contrario
	 */
	static Campaign deleteCampaña(String campaignName) {
		
		
		synchronized (lockCampañas) {
			return campañas.remove(campaignName);
		}	
	}
	
	/**
	 * Comprueba si existe alguna entrada en la estructura 
	 * {@link CampaignManagement#downloadTokens} con clave igual al parametro.
	 * Es decir si existe el token de descarga pasado por parametro.
	 *
	 * @param downloadToken el token que se desa comprobar
	 * @return true, si existe, o false, si no
	 */
	public static boolean existsDownloadToken(String downloadToken)
	{
		
		synchronized (lockDownload) {
			return downloadTokens.containsKey(downloadToken);
		}
	}
	
	/**
	 * Obtiene el FutureTask encargado de una creación de PDF y asociado al
	 * token pasado por parametro
	 *
	 * @param downloadToken el token de descarga del que se desea obtener su
	 * FutureTask
	 * @return the PDF future o null si no existe
	 */
	public static FutureTask<File> getPDFFuture(String downloadToken)
	{
		
		synchronized (lockDownload) {
			return downloadTokens.get(downloadToken);
		}
	}
	
	/**
	 * Añade una entrada download token, FutureTask a la estructura 
	 * {@link CampaignManagement#downloadTokens} con los parametros pasados
	 * por parametro.
	 *
	 * @param downloadToken que se desea introducir
	 * @param pdfT FutureTask que se desea introducir
	 */
	public static void addDownloadToken (String downloadToken, FutureTask<File> pdfT){
		
		synchronized (lockDownload) {
			downloadTokens.put(downloadToken, pdfT);
		}
	}
	
	/**
	 * Gets the all PDF future.
	 *
	 * @return the all PDF future
	 */
	public static Collection<FutureTask<File>> getAllPDFFuture (){
		synchronized (lockDownload) {
			return downloadTokens.values();
		}
	}
	
	/**
	 * Limpia la estructura de datos {@link CampaignManagement#downloadTokens}.
	 */
	static void clearDownloadToken(){
		synchronized (lockDownload) {
			downloadTokens.clear();
		}
	}
	
	/**
	 * Limpia la estructura de datos {@link CampaignManagement#activeTokens}.
	 */
	static void clearActiveToken(){
		synchronized (lockTokens) {
			activeTokens.clear();
		}
	}

	
	/**
	 * Constructor que crea una instancia de esta clase, para ello lee las
	 * campañas existentes en la base de datos, instancia las tareas de
	 * mantenimiento y las programa para su ejecución a las 5 de la mañana
	 * 
	 * @see MaintenanceService
	 * @see MyTaskExecutor
	 */
	public CampaignManagement() {
		
		if (instance == null) {
			inicializaCampañas();
			MaintenanceService maintenance = new MaintenanceService();
			taskExecutor = new MyTaskExecutor(maintenance);
			taskExecutor.startScheduleExecutionAt(5, 0, 0);
			instance = this;
		}
		
	}
	
	/**
	 * Recibe peticiones de registro aceptando formularios de tipo 
	 * application/x-www-form-urlencoded.
	 * 
	 * <p>
	 * Todos sus parametros estan encodeados en Base64
	 *
	 * @param campaignName64 nombre de la campaña
	 * @param password64 contraseña de la campaña
	 * @param deleteDate64 fecha de borrado de la campaña
	 * @return response con determinado codigo y mensaje en funcion del exito
	 * o fracaso de la operación de registro
	 */
	@POST
	@Path("/register")
	@Consumes("application/x-www-form-urlencoded")	
	public Response register (@FormParam("campaign") String campaignName64, @FormParam("password") String password64, @FormParam("delete_date") String deleteDate64) {
		
		
		
		
		

		String campaignName = new String(Base64.getUrlDecoder().decode(campaignName64));
		String password = new String(Base64.getUrlDecoder().decode(password64));
		
		String deleteDateStr = new String(Base64.getUrlDecoder().decode(deleteDate64));
		
	
		System.out.println("["+new Date().toString()+"] Registro: camapaña "+campaignName+ " iniciado");
		
		Date deleteDate = null;
		try {
			deleteDate = dateFormat.parse(deleteDateStr);
		} catch (ParseException e2) {
			System.err.println("["+new Date().toString()+"] Registro: camapaña "+campaignName+ " Error parsando la fecha de borrado");
			e2.printStackTrace();
			try {
				return Response.status(500).entity(Base64.getUrlEncoder().encode("Error interno del servidor".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e1) {
				
			}
		}
		
		synchronized (lockCampañas) {
		
			if (campañas.containsKey(campaignName)) {
				try {
					System.err.println("["+new Date().toString()+"] Registro: camapaña "+campaignName+ " Error ya existe una campaña con ese nombre");
					return Response.status(400).entity(Base64.getUrlEncoder().encode("Error, ya existe una campaña con ese nombre".getBytes("UTF-8"))).build();
				} catch (UnsupportedEncodingException e) {
					
				}
			}
		
		}
		
		//Si la fecha de borrado es inferior o igual a la actual entonces rechazamos la peticion de registro
		Date fechaActual = new Date();
		if (!deleteDate.after(fechaActual)) {
			try {
				System.err.println("["+new Date().toString()+"] Registro: camapaña "+campaignName+ " Error la fecha de borrado no es posterior a la actual");
				return Response.status(400)
						.entity(Base64.getUrlEncoder().encode(new String("Error, la fecha ha de ser superior a la actual ("
								+ dateFormat.format(fechaActual) + ")").getBytes("UTF-8")))
						.build();
			} catch (UnsupportedEncodingException e) {

			}
		}
		
		String hashPass=null;
		//Creamos el hash de la contraseña de la campaña
		try {
			hashPass = PasswordStorage.createHash(password);
		} catch (CannotPerformOperationException e) {
			System.err.println("["+new Date().toString()+"] Registro: camapaña "+campaignName+ " Error creando hash de la contraseña");
			e.printStackTrace();
			try {
				
				return Response.status(500).entity(Base64.getUrlEncoder().encode("Error interno del servidor".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e1) {
				
			}
		}
		
		CampaignCredentials campaignCred = new CampaignCredentials(campaignName, hashPass, deleteDateStr);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		
		/**
		 * @reference https://docs.oracle.com/javase/8/docs/api/java/nio/file/attribute/PosixFileAttributeView.html
		 */
		Set<PosixFilePermission> permsRWX = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
		
		Set<PosixFilePermission> permsRW = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
		
		//Si no existe la base de datos campañas es que es la primera en ser registrada asi que
		//configuramos el keystore
		
		if (!campaignsFile.exists()) {
			
			try {
				
				
				Files.createDirectories(SymmetricEncryption.getKeystorefile().getParentFile().toPath(), PosixFilePermissions.asFileAttribute(permsRWX));
				
				Files.createFile(SymmetricEncryption.getKeystorefile().toPath(), PosixFilePermissions.asFileAttribute(permsRW));
				SymmetricEncryption.configureKeyStore();
				
			} catch (NoSuchAlgorithmException | KeyStoreException | CertificateException | IOException e) {
				System.err.println("["+new Date().toString()+"] Registro: camapaña "+campaignName+ " Error configurando el Keystore");
				e.printStackTrace();
				try {
					
					return Response.status(500).entity(Base64.getUrlEncoder().encode("Error interno del servidor".getBytes("UTF-8"))).build();
				} catch (UnsupportedEncodingException e1) {
					
				}
				
			}
		}
	
		
		
		
		Campaign campaign = new Campaign(campaignName, new File(WEBSERVICE_ABSOLUTE_ROUTE+ "/campanias/" + campaignName));
		
		
		byte[] token=null;
		try {
			
			Files.createDirectories(campaign.getDirectory().toPath(), PosixFilePermissions.asFileAttribute(permsRWX));
			
			
			
			Files.createFile(campaign.getDataBase().toPath(), PosixFilePermissions.asFileAttribute(permsRW));
			
			Files.createFile(campaign.getSignCtr().toPath(), PosixFilePermissions.asFileAttribute(permsRW));
			
			SymmetricEncryption.encryptFileSavingKey(campaign.getDataBase(), campaignName);
			token = SymmetricEncryption.encryptUsingKey(campaignName.getBytes("UTF-8"),campaignName );

		} catch (InvalidKeyException | NoSuchAlgorithmException | KeyStoreException | CertificateException
				| NoSuchPaddingException | InvalidAlgorithmParameterException | IOException
				| UnrecoverableEntryException | IllegalBlockSizeException | BadPaddingException e) {

			e.printStackTrace();
			try {
				borrarArchivosCampaña(campaign);
			} catch (IOException e1) {
				System.err.println("[" + new Date().toString() + "] Registro: camapaña " + campaignName
						+ " Error creando los archivos de la campaña o el token");
				e1.printStackTrace();
			}
			try {
				return Response.status(500)
						.entity(Base64.getUrlEncoder().encode("No se ha podido guardar la campaña".getBytes("UTF-8")))
						.build();
			} catch (UnsupportedEncodingException e1) {

			}
		}
		
		// Si no existe significa que es la primera campaña por lo que habra que
		// crearlo configurando
		// un keystore donde se almacenaran las claves de encriptacion de este
		// fichero y los siguientes
		// Posteriormente se crea el fichero en sí usando la clave configurada
		// en el keyStore
		CipherOutputStream cos = null;
		synchronized (lockCampaignsFile) {

			if (!campaignsFile.exists()) {
				try {

					Files.createFile(campaignsFile.toPath(), PosixFilePermissions.asFileAttribute(permsRW));
					cos = SymmetricEncryption.encryptFileUsingKey(campaignsFile, masterKeyAlias);

				} catch (NoSuchAlgorithmException | KeyStoreException | CertificateException | IOException
						| InvalidKeyException | NoSuchPaddingException | InvalidAlgorithmParameterException
						| UnrecoverableEntryException e) {
					System.err.println("[" + new Date().toString() + "] Registro: camapaña " + campaignName
							+ " Error creando la base datos de campañas");
					e.printStackTrace();
					try {
						return Response.status(500)
								.entity(Base64.getUrlEncoder().encode("Error interno del servidor".getBytes("UTF-8")))
								.build();
					} catch (UnsupportedEncodingException e1) {

					}

				}
			}
			// Si el fichero ya existe lo abrimos para añadir esta campaña
			else {
				try {
					cos = SymmetricEncryption.appendAES(campaignsFile, masterKeyAlias);
				} catch (InvalidKeyException | IllegalArgumentException | KeyStoreException | NoSuchAlgorithmException
						| CertificateException | UnrecoverableEntryException | InvalidAlgorithmParameterException
						| NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException | IOException e) {
					System.err.println("[" + new Date().toString() + "] Registro: camapaña " + campaignName
							+ " Error abriendo para escribir la base de datos campañas");
					e.printStackTrace();
					try {
						return Response.status(500)
								.entity(Base64.getUrlEncoder().encode("Error interno del servidor".getBytes("UTF-8")))
								.build();
					} catch (UnsupportedEncodingException e1) {

					}
				}
			}
			
			
			
			
			
			
			
			Writer wr = null;
			try {
				wr = new OutputStreamWriter(cos);
				gson.toJson(campaignCred, wr);
				
			
			} finally {
				try{
					if(wr != null) {
						wr.flush();
						wr.close();
					}
					if (cos!=null) {
						cos.close();
						cos =null;
					}
				} catch (IOException e){
					try {
						borrarArchivosCampaña(campaign);
					} catch (IOException e1) {
						
						e1.printStackTrace();
					}
					try {
						return Response.status(500).entity(Base64.getUrlEncoder().encode("No se ha podido guardar la campaña".getBytes("UTF-8"))).build();
					} catch (UnsupportedEncodingException e1) {
						
					}
				}
			}
		}
		synchronized (lockCampañas) {
			campañas.put(campaignName, campaign);
		}
		synchronized (lockTokens) {
			activeTokens.add(new String(Base64.getUrlEncoder().encode(token)));
		}
		
		System.out.println("["+new Date().toString()+"] Registro: camapaña "+campaignName+" registrada correctamente");
		
		
		return Response.status(200).entity(Base64.getUrlEncoder().encode(token)).build();
		
		
	}
	
	
	/**
	 * Borrar archivos de una campaña pasada por parámetro.
	 *
	 * @param c Campaña de la que se desea borrar los archivos
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @see <a href="http://stackoverflow.com/a/8685959/6441806">Referencia</a>
	 */
	static void borrarArchivosCampaña (Campaign c) throws IOException{
		
		 Files.walkFileTree(c.getDirectory().toPath(), new SimpleFileVisitor<java.nio.file.Path>()
		    {
		        @Override
		        public FileVisitResult visitFile(java.nio.file.Path file, BasicFileAttributes attrs)
		                throws IOException
		        {
		            Files.delete(file);
		            return FileVisitResult.CONTINUE;
		        }

		        @Override
		        public FileVisitResult visitFileFailed(java.nio.file.Path  file, IOException exc) throws IOException
		        {
		            // try to delete the file anyway, even if its attributes
		            // could not be read, since delete-only access is
		            // theoretically possible
		            Files.delete(file);
		            return FileVisitResult.CONTINUE;
		        }

		        @Override
		        public FileVisitResult postVisitDirectory(java.nio.file.Path  dir, IOException exc) throws IOException
		        {
		            if (exc == null)
		            {
		                Files.delete(dir);
		                return FileVisitResult.CONTINUE;
		            }
		            else
		            {
		                // directory iteration failed; propagate exception
		                throw exc;
		            }
		        }
		    });
	}
	
	/**
	 * Recorre la base de datos de campañas 
	 * {@link CampaignManagement#campaignsFile} rellenando la estructura
	 * {@link CampaignManagement#campañas} con su contenido
	 */
	private static void inicializaCampañas (){
		
		
		CipherInputStream cis = null;
		synchronized (lockCampaignsFile) {
			if (!campaignsFile.exists()) {
					return;
				
			}

			else {
				
				System.out.println("["+new Date().toString()+"] inicializaCampañas: inicializando");
				
				
				try {
					cis = SymmetricEncryption.decryptFileUsingKey(campaignsFile, masterKeyAlias);
				} catch (InvalidKeyException | IllegalArgumentException | KeyStoreException | NoSuchAlgorithmException
						| CertificateException | UnrecoverableEntryException | InvalidAlgorithmParameterException
						| NoSuchPaddingException | IOException e) {
					System.err.println("["+new Date().toString()+"] inicializaCampañas: error abriendo el fichero o este está vacío");
					e.printStackTrace();
					
					return;
					
				}
			}
			
	
			/**
			 * @see https://sites.google.com/site/gson/streaming
			 */
			Gson gson = new Gson();
			
			CampaignCredentials c;
			
			JsonReader reader = null;
			
			
			try {
				reader= new JsonReader(new InputStreamReader(cis, "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				System.err.println("["+new Date().toString()+"] inicializaCampañas: error abriendo el fichero o este está vacío");
				e.printStackTrace();
			
				return;
			
			}
	
			reader.setLenient(true);
			try {
				System.out.println("["+new Date().toString()+"] inicializaCampañas: Empezando a recorrer la base de datos");
				Set<PosixFilePermission> permsRWX = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
				Set<PosixFilePermission> permsRW = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
				
				while(reader.hasNext()){
					JsonToken tokenJson =  reader.peek();
					if (!tokenJson.equals(JsonToken.BEGIN_OBJECT)) {
						break;
					}
					c = gson.fromJson(reader, CampaignCredentials.class);
					
					
					
					Campaign campaign = new Campaign(c.getCampaignName(), new File(WEBSERVICE_ABSOLUTE_ROUTE+ "/campanias/" + c.getCampaignName()));
					
					
					
					
					if (!campaign.getDirectory().exists()) {
						System.out.println("["+new Date().toString()+"] inicializaCampañas: Creando directorio: "+campaign.getDirectory().getAbsolutePath());
						Files.createDirectories(campaign.getDirectory().toPath(), PosixFilePermissions.asFileAttribute(permsRWX));
					}
					
					if (!campaign.getDataBase().exists()) {
						
						
						Files.createFile(campaign.getDataBase().toPath(), PosixFilePermissions.asFileAttribute(permsRW));
						System.out.println("["+new Date().toString()+"] inicializaCampañas: Creando fichero: "+campaign.getDataBase().getAbsolutePath());
						try {
							SymmetricEncryption.encryptFileUsingKey(campaign.getDataBase(), campaign.getCampaignName());
						} catch (InvalidKeyException | NoSuchAlgorithmException | KeyStoreException
								| CertificateException | NoSuchPaddingException | InvalidAlgorithmParameterException
								| UnrecoverableEntryException e) {
							System.out.println("["+new Date().toString()+"] inicializaCampañas: Error creando fichero: "+campaign.getDataBase().getAbsolutePath());
							e.printStackTrace();
						}
						
					}
					
					
					
					if (!campaign.getSignCtr().exists()) {

						Files.createFile(campaign.getSignCtr().toPath(), PosixFilePermissions.asFileAttribute(permsRW));
						System.out.println("["+new Date().toString()+"] inicializaCampañas: Creando fichero: "+campaign.getSignCtr().getAbsolutePath());
					}
					
					synchronized (lockCampañas) {
						campañas.put(campaign.getCampaignName(), campaign);
					}
				}
			} catch (JsonIOException | JsonSyntaxException | IOException  e) {
				System.err.println("["+new Date().toString()+"] inicializaCampañas: Error leyendo base de datos de campañas");
				
				e.printStackTrace();
			
				return ;
				
			} finally {
				try {
					if (reader!=null) {
						reader.close();
					}	
				} catch (IOException e) {
				}
			}
		}
		
		System.out.println("["+new Date().toString()+"] inicializaCampañas: finalizado");
		
		
		return;
			
	}
	
	
	/**
	 * Recibe peticiones de login aceptando formularios de tipo 
	 * application/x-www-form-urlencoded.
	 * 
	 * <p>
	 * Todos sus parametros estan encodeados en Base64
	 *
	 * @param campaignName64 nombre de la campaña
	 * @param password64 contraseña de la campaña
	 * @return response con determinado codigo y mensaje en funcion del exito
	 * o fracaso de la operación de login
	 */
	@POST
	@Path("/login")
	@Consumes("application/x-www-form-urlencoded")
	public Response login (@FormParam("campaign") String campaignName64, @FormParam("password") String password64) {

		
		String campaignName = new String(Base64.getUrlDecoder().decode(campaignName64));
		String password = new String(Base64.getUrlDecoder().decode(password64));
		
		System.out.println("["+new Date().toString()+"] login campaña "+campaignName+": inicializando");
		
		
		
		
		synchronized (lockCampañas) {
			if (!campañas.containsKey(campaignName)) {
				System.err.println("["+new Date().toString()+"] login campaña "+campaignName+": Error no existe campaña con ese nombre");
				try {
					
					return Response.status(400).entity(Base64.getUrlEncoder()
							.encode("Error, no existe una cuenta con ese nombre".getBytes("UTF-8"))).build();
				} catch (UnsupportedEncodingException e) {
				}
			}
		}
		
		CipherInputStream cis = null;
		synchronized (lockCampaignsFile) {
			if (!campaignsFile.exists()) {
				System.err.println("["+new Date().toString()+"] login campaña "+campaignName+": Error no existen campañas creadas");
				try {
					return Response.status(400).entity(Base64.getUrlEncoder().encode("Error, no existen campañas creadas".getBytes("UTF-8"))).build();
				} catch (UnsupportedEncodingException e) {}
				
				
			}

			else {
				try {
					cis = SymmetricEncryption.decryptFileUsingKey(campaignsFile, masterKeyAlias);
				} catch (InvalidKeyException | IllegalArgumentException | KeyStoreException | NoSuchAlgorithmException
						| CertificateException | UnrecoverableEntryException | InvalidAlgorithmParameterException
						| NoSuchPaddingException | IOException e) {
					System.err.println("["+new Date().toString()+"] login campaña "+campaignName+": Error abriendo base de datos de campañas");
					e.printStackTrace();
					try {
						return Response.status(500).entity(Base64.getUrlEncoder().encode("Error interno del servidor".getBytes("UTF-8"))).build();
					} catch (UnsupportedEncodingException e1) {}
				}
			}
			
	
			/**
			 * @see https://sites.google.com/site/gson/streaming
			 */
			Gson gson = new Gson();
			
			CampaignCredentials c;
			
			JsonReader reader = null;
			
			
			try {
				reader= new JsonReader(new InputStreamReader(cis, "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				System.err.println("["+new Date().toString()+"] login campaña "+campaignName+": Error abriendo base de datos de campañas");
				e.printStackTrace();
				try {
					return Response.status(500).entity(Base64.getUrlEncoder().encode("Error interno del servidor".getBytes("UTF-8"))).build();
				} catch (UnsupportedEncodingException e2) {}
			}
	
			reader.setLenient(true);
			try {
				System.out.println("["+new Date().toString()+"] login campaña "+campaignName+": Empezando a recorrer campaigns.json");
				while(reader.hasNext()){
					JsonToken tokenJson =  reader.peek();
					if (!tokenJson.equals(JsonToken.BEGIN_OBJECT)) {
						break;
					}
					c = gson.fromJson(reader, CampaignCredentials.class);
					
					
					
					if (c.getCampaignName().equals(campaignName)) {
						if(PasswordStorage.verifyPassword(password, c.getHashPass())){
							byte[] token=null;
							token = SymmetricEncryption.encryptUsingKey(campaignName.getBytes("UTF-8"),campaignName );
							
							synchronized (lockTokens) {
								activeTokens.add(new String(Base64.getUrlEncoder().encode(token)));
							}
							System.out.println("["+new Date().toString()+"] login campaña "+campaignName+": Login correcto");
							
							return Response.status(200).entity(Base64.getUrlEncoder().encode(token)).build();
						}
						else{
							System.out.println("["+new Date().toString()+"] login campaña "+campaignName+": Login incorrecto");
							try {
								
								return Response.status(400).entity(Base64.getUrlEncoder().encode("Error, contraseña incorrecta".getBytes("UTF-8"))).build();
							} catch (UnsupportedEncodingException e) {}
							
						}
						
					}
				}
			} catch (JsonIOException | JsonSyntaxException | InvalidKeyException | NoSuchAlgorithmException
					| KeyStoreException | CertificateException | NoSuchPaddingException | InvalidAlgorithmParameterException
					| UnrecoverableEntryException | IllegalBlockSizeException | BadPaddingException | IOException
					| CannotPerformOperationException | InvalidHashException e) {
				System.err.println("["+new Date().toString()+"] login campaña "+campaignName+": Error leyendo base de datos de campañas");
				e.printStackTrace();
				try {
					return Response.status(500).entity(Base64.getUrlEncoder().encode("Error interno del servidor".getBytes("UTF-8"))).build();
				} catch (UnsupportedEncodingException e2) {}
			} finally {
				try {
					if (reader!=null) {
						reader.close();
					}	
				} catch (IOException e) {
				}
			}
		}
		
		System.out.println("["+new Date().toString()+"] login campaña "+campaignName+": se ha recorrido toda la base de datos sin encontrar la campaña buscada");
		try {
			return Response.status(400).entity(Base64.getUrlEncoder().encode("Error, no existe una campaña con ese nombre".getBytes("UTF-8"))).build();
		} catch (UnsupportedEncodingException e) {return Response.status(500).build();}
			
	}
	
	
	/**
	 * Realiza el mismo proceso que 
	 * {@link CampaignManagement#login(String, String)} pero es usado por 
	 * algunos métodos internos, no es invocado con peticiones HTTP.
	 *
	 * @param campaignName nombre de la campaña
	 * @param password contraseña de la campaña
	 * @return response un entero usado como código y que coincide con las 
	 * constantes tipo LOGIN_X definidas en esta clase
	 */
	public static int loginInterno (String campaignName,String password) {

		
		synchronized (lockCampañas) {
			if (!campañas.containsKey(campaignName)) {
				System.out.println("["+new Date().toString()+"] login interno campaña "+campaignName+": no existe campaña con ese nombre");
			
				return LOGIN_NO_EXISTE_CAMPAÑA;
				
			}
		}
		
		CipherInputStream cis = null;
		synchronized (lockCampaignsFile) {
			if (!campaignsFile.exists()) {
				System.out.println("["+new Date().toString()+"] login interno campaña "+campaignName+": no existen campañas");
				return LOGIN_NO_EXISTE_CAMPAÑA;
		
			}

			else {
				try {
					cis = SymmetricEncryption.decryptFileUsingKey(campaignsFile, masterKeyAlias);
				} catch (InvalidKeyException | IllegalArgumentException | KeyStoreException | NoSuchAlgorithmException
						| CertificateException | UnrecoverableEntryException | InvalidAlgorithmParameterException
						| NoSuchPaddingException | IOException e) {
					System.err.println("["+new Date().toString()+"] login interno campaña "+campaignName+": Error abriendo la base de datos de campañas");
					e.printStackTrace();
					return LOGIN_ERROR_INTERNO;
				}
			}
			
	
			/**
			 * @see https://sites.google.com/site/gson/streaming
			 */
			Gson gson = new Gson();
			
			CampaignCredentials c;
			
			JsonReader reader = null;
			
			
			try {
				reader= new JsonReader(new InputStreamReader(cis, "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				System.err.println("["+new Date().toString()+"] login interno campaña "+campaignName+": Error abriendo la base de datos de campañas");
				e.printStackTrace();
				return LOGIN_ERROR_INTERNO;
			}
	
			reader.setLenient(true);
			try {
				System.out.println("["+new Date().toString()+"] login interno campaña "+campaignName+": Empezando a recorrer campaigns.json");
				while(reader.hasNext()){
					JsonToken tokenJson =  reader.peek();
					if (!tokenJson.equals(JsonToken.BEGIN_OBJECT)) {
						break;
					}
					c = gson.fromJson(reader, CampaignCredentials.class);
					
					
					
					if (c.getCampaignName().equals(campaignName)) {
						if(PasswordStorage.verifyPassword(password, c.getHashPass())){
							
							System.out.println("["+new Date().toString()+"] login interno campaña "+campaignName+": Login correcto");
							
							return LOGIN_EXITO;
						}
						else{
							
							System.out.println("["+new Date().toString()+"] login interno campaña "+campaignName+": Login incorrecto");
							
							return LOGIN_INCORRECTO;
							
							
						}
						
					}
				}
			} catch (JsonIOException | JsonSyntaxException | IOException
					| CannotPerformOperationException | InvalidHashException e) {
			
				System.err.println("["+new Date().toString()+"] login interno campaña "+campaignName+": Error leyendo base de datos de campañas");
				return LOGIN_ERROR_INTERNO;
			} finally {
				try {
					if (reader!=null) {
						reader.close();
					}	
				} catch (IOException e) {
				}
			}
		}
		
		System.out.println("["+new Date().toString()+"] login interno campaña "+campaignName+": se ha recorrido toda la base de datos sin encontrar la campaña buscada");
		
		return LOGIN_NO_EXISTE_CAMPAÑA;
			
	}
	
	/**
	 * Recibe peticiones de autenticación de token aceptando formularios de tipo 
	 * application/x-www-form-urlencoded.
	 * 
	 * @param token token que se desea autentica
	 * @param campaignName64 nombre de la campaña en base 64
	 * @return response con determinado codigo y mensaje en funcion del exito
	 * o fracaso de la operación de autenticación
	 */
	@POST
	@Path("/authenticate_token")
	@Consumes("application/x-www-form-urlencoded")
	public Response authenticateToken (@FormParam("token") String token, @FormParam("campaign") String campaignName64) {
		
		System.out.println("["+new Date().toString()+"] authenticateToken token: "+token);
		
		boolean contains = false;
		synchronized (lockTokens) {
			contains = activeTokens.contains(token);
		}	
		
		//Si el token esta en el set de tokens activos contestamos afirmativamente
		if (contains) {
			System.out.println("["+new Date().toString()+"] authenticateToken: Token en activeTokens");
			try {
				return Response.status(200).entity(Base64.getUrlEncoder().encode("Token valido".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e) {}
		}
		//Si no pasamos a desencriptar el token
		else{
			System.out.println("["+new Date().toString()+"] authenticateToken: Token NO en activeTokens");
			String campaignName = new String(Base64.getUrlDecoder().decode(campaignName64));
			byte [] tokenDecrypted = null;
			try {
				tokenDecrypted = SymmetricEncryption.decryptUsingKey(Base64.getUrlDecoder().decode(token), campaignName);
			} catch (InvalidKeyException | KeyStoreException | NoSuchAlgorithmException | CertificateException
					| UnrecoverableEntryException | NoSuchPaddingException | InvalidAlgorithmParameterException
					| IllegalBlockSizeException | BadPaddingException | IOException e) {
				System.err.println("["+new Date().toString()+"] authenticateToken: Error desencriptando token");
				e.printStackTrace();
				try {
					return Response.status(500).entity(Base64.getUrlEncoder().encode("Error interno del servidor".getBytes("UTF-8"))).build();
				} catch (UnsupportedEncodingException e1) {}
				
			}
			
			String campaignToken = null;
			try {
				campaignToken = new String(tokenDecrypted, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				System.err.println("["+new Date().toString()+"] authenticateToken: Error creando string token desencriptado");
				e.printStackTrace();
				try {
					return Response.status(500).entity(Base64.getUrlEncoder().encode("Error interno del servidor".getBytes("UTF-8"))).build();
				} catch (UnsupportedEncodingException e1) {}
			}
			
			System.out.println("["+new Date().toString()+"] authenticateToken: Campaña: "+campaignName+ " token desencriptado: "+campaignToken);
			if (campaignToken.equals(campaignName)) {
				
				contains = false;
				synchronized (lockCampañas) {
					contains = campañas.containsKey(campaignToken);
				}
				
				if(contains){
				
					synchronized (lockTokens) {
						activeTokens.add(token);
					}
					
					System.out.println("["+new Date().toString()+"] authenticateToken: token añadido a activos");
					try {
						return Response.status(200).entity(Base64.getUrlEncoder().encode("Token valido".getBytes("UTF-8"))).build();
					} catch (UnsupportedEncodingException e1) {}
				}
				else
				{
					System.out.println("["+new Date().toString()+"] authenticateToken: No existe campaña con ese token");
					try{
						return Response.status(404).entity(Base64.getUrlEncoder().encode("Token invalido, no existe una campaña con dicho token".getBytes("UTF-8"))).build();
					} catch (UnsupportedEncodingException e1) {}
				}
				
			}
			else {
				
				System.out.println("["+new Date().toString()+"] authenticateToken: No coinciden el token desencriptado y la campaña");
				try {
					return Response.status(404).entity(Base64.getUrlEncoder().encode("Token invalido, no coincide con la campaña enviada".getBytes("UTF-8"))).build();
				} catch (UnsupportedEncodingException e1) {}
			}
			
		}
		return Response.status(500).build();
		
	}


	/**
	 * Realiza el mismo proceso que 
	 * {@link CampaignManagement#authenticateToken(String, String)} pero es 
	 * usado por algunos métodos internos, no es invocado con peticiones HTTP.
	 *
	 * @param token token a comprobar
	 * @param campaignName nombre de la campaña
	 * @return true, si el token es autenticado, o false, en caso contrario
	 */
	
	
	public static boolean compruebaTokenInterno (String token, String campaignName) {
		
		boolean contains = false;
		System.out.println("["+new Date().toString()+"] authenticateToken interno token: "+token);
		synchronized (lockTokens) {
			contains = activeTokens.contains(token);
		}	
		
		//Si el token esta en el set de tokens activos devolvemos true
		if (contains) {
			System.out.println("["+new Date().toString()+"] authenticateToken interno: Token en activeTokens");
			return true;
		}
		//Si no pasamos a desencriptar el token
		else{
			System.out.println("["+new Date().toString()+"] authenticateToken interno: Token NO en activeTokens");
			byte [] tokenDecrypted = null;
			try {
				tokenDecrypted = SymmetricEncryption.decryptUsingKey(Base64.getUrlDecoder().decode(token), campaignName);
			} catch (InvalidKeyException | KeyStoreException | NoSuchAlgorithmException | CertificateException
					| UnrecoverableEntryException | NoSuchPaddingException | InvalidAlgorithmParameterException
					| IllegalBlockSizeException | BadPaddingException | IOException e) {
				System.err.println("["+new Date().toString()+"] authenticateToken interno: Error desencriptando token");
				e.printStackTrace();
				return false;
				
				
			}
			
			String campaignToken = null;
			try {
				campaignToken = new String(tokenDecrypted, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				System.err.println("["+new Date().toString()+"] authenticateToken interno: Error creando string token desencriptado");
				e.printStackTrace();
				
				return false;
			}
			
			System.out.println("["+new Date().toString()+"] authenticateToken interno: Campaña: "+campaignName+ " token desencriptado: "+campaignToken);
			if (campaignToken.equals(campaignName)) {
				
				contains = false;
				synchronized (lockCampañas) {
					contains = campañas.containsKey(campaignToken);
				}
				
				if(contains){
				
					synchronized (lockTokens) {
						activeTokens.add(token);
					}
					
					System.out.println("["+new Date().toString()+"] authenticateToken interno: token añadido a activos");
					return true;
				}
				else
				{
					System.out.println("["+new Date().toString()+"] authenticateToken interno: No existe campaña con ese token");
					return false;
				}
				
			}
			else {
				
				System.out.println("["+new Date().toString()+"] authenticateToken interno: No coinciden el token desencriptado y la campaña");
				return false;
			}
			
		}
	}

	/**
	 * Método invocado al parar el servidor Tomcat, se encarga de parar todos
	 * los hilos creados, los encargados de generar PDFs y el de mantenimiento
	 * @see javax.servlet.ServletContextListener#contextDestroyed(javax.servlet.ServletContextEvent)
	 */
	@Override
	public void contextDestroyed(ServletContextEvent arg0) {
		
		taskExecutor.stop();
		System.out.println(SEPARADOR);
		System.out.println("["+new Date().toString()+"] Contexto destruido: hilos parados");
		System.out.println(SEPARADOR);
	}

	/**
	 * Método invocado al arrancar el servidor Tomcat, se implementa debido a 
	 * que es necesario el método 
	 * {@link CampaignManagement#contextDestroyed(ServletContextEvent)} y ademas
	 * hace que esta clase se instancie e inicialice sus estructuras de datos
	 * de modo automático sin tener que recibir petición alguna para ello
	 * 
	 * @see javax.servlet.ServletContextListener#contextDestroyed(javax.servlet.ServletContextEvent)
	 */
	@Override
	public void contextInitialized(ServletContextEvent arg0) {
		System.out.println(SEPARADOR);
		System.out.println("["+new Date().toString()+"] Contexto creado");
		System.out.println(SEPARADOR);
	}
	
}
