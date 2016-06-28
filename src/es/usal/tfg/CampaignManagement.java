package es.usal.tfg;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;

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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.sun.corba.se.spi.orb.ParserDataFactory;

import es.usal.tfg.security.SymmetricEncryption;
import es.usal.tfg.security.PasswordStorage;
import es.usal.tfg.security.PasswordStorage.CannotPerformOperationException;
import es.usal.tfg.security.PasswordStorage.InvalidHashException;


@Singleton
@WebListener
@Path("/campaign")
public class CampaignManagement implements ServletContextListener {

	public static final String WEBSERVICE_ABSOLUTE_ROUTE = "/demos";
	public static final String SEPARADOR = "------------------------------------------------------------";
	static final String masterKeyAlias = "master_key";

	public static final int LOGIN_EXITO = 0;
	public static final int LOGIN_ERROR_INTERNO = 1;
	public static final int LOGIN_NO_EXISTE_CAMPAÑA = 2;
	public static final int LOGIN_INCORRECTO = 3;
	
	public static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
	
	private static HashMap<String ,Campaign> campañas = new HashMap<>();
	private static HashSet<String> activeTokens = new HashSet<>();
	private static HashMap<String, FutureTask<File>> downloadTokens = new HashMap<>();
	private static final Object lockDownload = new Object();
	private static final Object lockCampañas = new Object();
	private static final Object lockTokens = new Object();
	
	/**Archivo que actua como base de datos de las campañas*/
	private static final File campaignsFile = new File(WEBSERVICE_ABSOLUTE_ROUTE + "/campaigns.json");
	static final Object lockCampaignsFile = new Object();
	private MyTaskExecutor taskExecutor = null;
	private static CampaignManagement instance = null;
	

	
	
	static File getCampaignsFile(){
		
		return campaignsFile;
	}
	
	public static Campaign getCampaña(String campaignName) {
		
		synchronized (lockCampañas) {
			return campañas.get(campaignName);
		}	
	}
	

	static Campaign deleteCampaña(String campaignName) {
		
		
		synchronized (lockCampañas) {
			return campañas.remove(campaignName);
		}	
	}
	public static boolean existsDownloadToken(String downloadToken)
	{
		
		synchronized (lockDownload) {
			return downloadTokens.containsKey(downloadToken);
		}
	}
	public static FutureTask<File> getPDFFuture(String downloadToken)
	{
		
		synchronized (lockDownload) {
			return downloadTokens.get(downloadToken);
		}
	}
	
	public static void addDownloadToken (String downloadToken, FutureTask<File> pdfT){
		
		synchronized (lockDownload) {
			downloadTokens.put(downloadToken, pdfT);
		}
	}
	
	public static Collection<FutureTask<File>> getAllPDFFuture (){
		synchronized (lockDownload) {
			return downloadTokens.values();
		}
	}
	static void clearDownloadToken(){
		synchronized (lockDownload) {
			downloadTokens.clear();
		}
	}
	
	static void clearActiveToken(){
		synchronized (lockTokens) {
			activeTokens.clear();
		}
	}

	
	public CampaignManagement() {
		
		if (instance == null) {
			rellenarHashMap();
			MaintenanceService maintenance = new MaintenanceService();
			taskExecutor = new MyTaskExecutor(maintenance);
			taskExecutor.startScheduleExecutionAt(10, 05, 0);
			instance = this;
		}
		
	}
	
	/**
	 * Metodo para recibir peticiones de registro
	 * @param campaignName
	 * @param password
	 * @return
	 */
	@POST
	@Path("/register")
	@Consumes("application/x-www-form-urlencoded")	
	public Response register (@FormParam("campaign") String campaignName64, @FormParam("password") String password64, @FormParam("delete_date") String deleteDate64) {
		
		
		
		
		

		String campaignName = new String(Base64.getUrlDecoder().decode(campaignName64));
		String password = new String(Base64.getUrlDecoder().decode(password64));
		
		String deleteDateStr = new String(Base64.getUrlDecoder().decode(deleteDate64));
		
		System.out.println(SEPARADOR);
		System.out.println("["+new Date().toString()+"] Registro: camapaña "+campaignName);
		
		Date deleteDate = null;
		try {
			deleteDate = dateFormat.parse(deleteDateStr);
		} catch (ParseException e2) {
			e2.printStackTrace();
			try {
				return Response.status(500).entity(Base64.getUrlEncoder().encode("Error interno del servidor".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e1) {
				
			}
		}
		
		synchronized (lockCampañas) {
		
			if (campañas.containsKey(campaignName)) {
				try {
					return Response.status(400).entity(Base64.getUrlEncoder().encode("Error, ya existe una campaña con ese nombre".getBytes("UTF-8"))).build();
				} catch (UnsupportedEncodingException e) {
					
				}
			}
		
		}
		
		//Si la fecha de borrado es inferior o igual a la actual entonces rechazamos la peticion de registro
		Date fechaActual = new Date();
		if (!deleteDate.after(fechaActual)) {
			try {
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
				
				e1.printStackTrace();
			}
			try {
				return Response.status(500).entity(Base64.getUrlEncoder().encode("No se ha podido guardar la campaña".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e1) {
				
			}
		}
		
		System.out.println(gson.toJson(campaignCred));
		
		//Si no existe significa que es la primera campaña por lo que habra que crearlo configurando 
		//un keystore donde se almacenaran las claves de encriptacion de este fichero y los siguientes
		//Posteriormente se crea el fichero en sí usando la clave configurada en el keyStore
		CipherOutputStream cos = null;
		synchronized (lockCampaignsFile) {
		
			if (!campaignsFile.exists()) {
				try {
					
					Files.createFile(campaignsFile.toPath(), PosixFilePermissions.asFileAttribute(permsRW));
					cos = SymmetricEncryption.encryptFileUsingKey(campaignsFile, masterKeyAlias);
					
				} catch (NoSuchAlgorithmException | KeyStoreException | CertificateException | IOException
						| InvalidKeyException | NoSuchPaddingException | InvalidAlgorithmParameterException
						| UnrecoverableEntryException e) {
					
					e.printStackTrace();
					try {
						return Response.status(500).entity(Base64.getUrlEncoder().encode("Error interno del servidor".getBytes("UTF-8"))).build();
					} catch (UnsupportedEncodingException e1) {
						
					}
					
				}
			}
			//Si el fichero ya existe lo abrimos para añadir esta campaña
			else {
				try {
					cos = SymmetricEncryption.appendAES(campaignsFile, masterKeyAlias);
				} catch (InvalidKeyException | IllegalArgumentException | KeyStoreException | NoSuchAlgorithmException
						| CertificateException | UnrecoverableEntryException | InvalidAlgorithmParameterException
						| NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException | IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					try {
						return Response.status(500).entity(Base64.getUrlEncoder().encode("Error interno del servidor".getBytes("UTF-8"))).build();
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
		
		System.out.println(new String(Base64.getUrlEncoder().encode(token)));
		return Response.status(200).entity(Base64.getUrlEncoder().encode(token)).build();
		
		
	}
	
	
	/**
	 * 
	 * @param c
	 * @throws IOException
	 * @reference http://stackoverflow.com/a/8685959/6441806
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
	
	private static void rellenarHashMap (){
		
		
		CipherInputStream cis = null;
		synchronized (lockCampaignsFile) {
			if (!campaignsFile.exists()) {
					return;
				
			}

			else {
				
				System.out.println(SEPARADOR);
				try {
					cis = SymmetricEncryption.decryptFileUsingKey(campaignsFile, masterKeyAlias);
				} catch (InvalidKeyException | IllegalArgumentException | KeyStoreException | NoSuchAlgorithmException
						| CertificateException | UnrecoverableEntryException | InvalidAlgorithmParameterException
						| NoSuchPaddingException | IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					
					System.err.println("Error abriendo fichero campaigns para desencripcion");
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			
				System.err.println("Error abriendo fichero campaigns para desencripcion");
				return;
			
			}
	
			reader.setLenient(true);
			try {
				System.out.println("Empezando a recorrer campaigns.json");
				Set<PosixFilePermission> permsRWX = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
				Set<PosixFilePermission> permsRW = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
				
				while(reader.hasNext()){
					JsonToken tokenJson =  reader.peek();
					if (!tokenJson.equals(JsonToken.BEGIN_OBJECT)) {
						break;
					}
					c = gson.fromJson(reader, CampaignCredentials.class);
					
					System.out.println(c.toString());
					
					Campaign campaign = new Campaign(c.getCampaignName(), new File(WEBSERVICE_ABSOLUTE_ROUTE+ "/campanias/" + c.getCampaignName()));
					
					
					
					//TODO System.out.println(campaign.getDataBase().getParentFile().getAbsolutePath());
					if (!campaign.getDirectory().exists()) {
						System.out.println("Creando directorio: "+campaign.getDirectory().getAbsolutePath());
						Files.createDirectories(campaign.getDirectory().toPath(), PosixFilePermissions.asFileAttribute(permsRWX));
					}
					
					if (!campaign.getDataBase().exists()) {
						//TODO System.out.println(campaign.getDataBase().getAbsolutePath());
						
						System.out.println("Creando fichero: "+campaign.getDataBase().getAbsolutePath());
						Files.createFile(campaign.getDataBase().toPath(), PosixFilePermissions.asFileAttribute(permsRW));
						
						try {
							SymmetricEncryption.encryptFileUsingKey(campaign.getDataBase(), campaign.getCampaignName());
						} catch (InvalidKeyException | NoSuchAlgorithmException | KeyStoreException
								| CertificateException | NoSuchPaddingException | InvalidAlgorithmParameterException
								| UnrecoverableEntryException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
					}
					
					
					
					if (!campaign.getSignCtr().exists()) {

						Files.createFile(campaign.getSignCtr().toPath(), PosixFilePermissions.asFileAttribute(permsRW));
						System.out.println("Creando fichero: "+campaign.getSignCtr().getAbsolutePath());
					}
					
					synchronized (lockCampañas) {
						campañas.put(campaign.getCampaignName(), campaign);
					}
				}
			} catch (JsonIOException | JsonSyntaxException | IOException  e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			
				System.err.println("Error leyendo fichero campaigns para desencripcion");
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
		
		System.out.println("Se ha recorrido todo");
		return;
			
	}
	
	
	@POST
	@Path("/login")
	@Consumes("application/x-www-form-urlencoded")
	public Response login (@FormParam("campaign") String campaignName64, @FormParam("password") String password64) {

		
		String campaignName = new String(Base64.getUrlDecoder().decode(campaignName64));
		String password = new String(Base64.getUrlDecoder().decode(password64));
		
		System.out.println(SEPARADOR);
		System.out.println("Encoded: "+campaignName64 +  ":" + password64);
		System.out.println("Decoded: "+campaignName + ":" + password);
		
		
		synchronized (lockCampañas) {
			if (!campañas.containsKey(campaignName)) {
				try {
					System.out.println("no existe campaña con ese nombre");
					return Response.status(400).entity(Base64.getUrlEncoder()
							.encode("Error, no existe una cuenta con ese nombre".getBytes("UTF-8"))).build();
				} catch (UnsupportedEncodingException e) {
				}
			}
		}
		
		CipherInputStream cis = null;
		synchronized (lockCampaignsFile) {
			if (!campaignsFile.exists()) {
				
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
					// TODO Auto-generated catch block
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
				// TODO Auto-generated catch block
				e.printStackTrace();
				try {
					return Response.status(500).entity(Base64.getUrlEncoder().encode("Error interno del servidor".getBytes("UTF-8"))).build();
				} catch (UnsupportedEncodingException e2) {}
			}
	
			reader.setLenient(true);
			try {
				System.out.println("Empezando a recorrer campaigns.json");
				while(reader.hasNext()){
					JsonToken tokenJson =  reader.peek();
					if (!tokenJson.equals(JsonToken.BEGIN_OBJECT)) {
						break;
					}
					c = gson.fromJson(reader, CampaignCredentials.class);
					
					System.out.println(c.toString());
					
					if (c.getCampaignName().equals(campaignName)) {
						if(PasswordStorage.verifyPassword(password, c.getHashPass())){
							byte[] token=null;
							token = SymmetricEncryption.encryptUsingKey(campaignName.getBytes("UTF-8"),campaignName );
							
							synchronized (lockTokens) {
								activeTokens.add(new String(Base64.getUrlEncoder().encode(token)));
							}
							System.out.println("Login correcto en "+campaignName);
							System.out.println(new String(Base64.getUrlEncoder().encode(token)));
							return Response.status(200).entity(Base64.getUrlEncoder().encode(token)).build();
						}
						else{
							try {
								System.out.println("Login incorrecto en "+campaignName);
								return Response.status(400).entity(Base64.getUrlEncoder().encode("Error, contraseña incorrecta".getBytes("UTF-8"))).build();
							} catch (UnsupportedEncodingException e) {}
							
						}
						
					}
				}
			} catch (JsonIOException | JsonSyntaxException | InvalidKeyException | NoSuchAlgorithmException
					| KeyStoreException | CertificateException | NoSuchPaddingException | InvalidAlgorithmParameterException
					| UnrecoverableEntryException | IllegalBlockSizeException | BadPaddingException | IOException
					| CannotPerformOperationException | InvalidHashException e) {
				// TODO Auto-generated catch block
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
		
		System.out.println("Se ha recorrido todo sin exito");
		try {
			return Response.status(400).entity(Base64.getUrlEncoder().encode("Error, no existe una campaña con ese nombre".getBytes("UTF-8"))).build();
		} catch (UnsupportedEncodingException e) {return Response.status(500).build();}
			
	}
	
	
	public static int loginInterno (String campaignName,String password) {

		
		synchronized (lockCampañas) {
			if (!campañas.containsKey(campaignName)) {
				
				System.out.println("no existe campaña con ese nombre");
				return LOGIN_NO_EXISTE_CAMPAÑA;
				
			}
		}
		
		CipherInputStream cis = null;
		synchronized (lockCampaignsFile) {
			if (!campaignsFile.exists()) {
				return LOGIN_NO_EXISTE_CAMPAÑA;
		
			}

			else {
				try {
					cis = SymmetricEncryption.decryptFileUsingKey(campaignsFile, masterKeyAlias);
				} catch (InvalidKeyException | IllegalArgumentException | KeyStoreException | NoSuchAlgorithmException
						| CertificateException | UnrecoverableEntryException | InvalidAlgorithmParameterException
						| NoSuchPaddingException | IOException e) {
					// TODO Auto-generated catch block
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
				// TODO Auto-generated catch block
				e.printStackTrace();
				return LOGIN_ERROR_INTERNO;
			}
	
			reader.setLenient(true);
			try {
				System.out.println("Empezando a recorrer campaigns.json");
				while(reader.hasNext()){
					JsonToken tokenJson =  reader.peek();
					if (!tokenJson.equals(JsonToken.BEGIN_OBJECT)) {
						break;
					}
					c = gson.fromJson(reader, CampaignCredentials.class);
					
					
					
					if (c.getCampaignName().equals(campaignName)) {
						if(PasswordStorage.verifyPassword(password, c.getHashPass())){
							
							System.out.println("Login correcto en "+campaignName);
							
							return LOGIN_EXITO;
						}
						else{
							
							System.out.println("Login incorrecto en "+campaignName);
							return LOGIN_INCORRECTO;
							
							
						}
						
					}
				}
			} catch (JsonIOException | JsonSyntaxException | IOException
					| CannotPerformOperationException | InvalidHashException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
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
		
		System.out.println("Se ha recorrido todo sin exito");
		
		return LOGIN_NO_EXISTE_CAMPAÑA;
			
	}
	
	@POST
	@Path("/authenticate_token")
	@Consumes("application/x-www-form-urlencoded")
	public Response authenticateToken (@FormParam("token") String token, @FormParam("campaign") String campaignName64) {
		
		System.out.println(SEPARADOR);
		System.out.println("Token es: "+token);
		boolean contains = false;
		synchronized (lockTokens) {
			contains = activeTokens.contains(token);
		}	
		
		//Si el token esta en el set de tokens activos contestamos afirmativamente
		if (contains) {
			System.out.println("token en activetokens");
			try {
				return Response.status(200).entity(Base64.getUrlEncoder().encode("Token valido".getBytes("UTF-8"))).build();
			} catch (UnsupportedEncodingException e) {}
		}
		//Si no pasamos a desencriptar el token
		else{
			System.out.println("token NO en activetokens");
			String campaignName = new String(Base64.getUrlDecoder().decode(campaignName64));
			byte [] tokenDecrypted = null;
			try {
				tokenDecrypted = SymmetricEncryption.decryptUsingKey(Base64.getUrlDecoder().decode(token), campaignName);
			} catch (InvalidKeyException | KeyStoreException | NoSuchAlgorithmException | CertificateException
					| UnrecoverableEntryException | NoSuchPaddingException | InvalidAlgorithmParameterException
					| IllegalBlockSizeException | BadPaddingException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				try {
					return Response.status(500).entity(Base64.getUrlEncoder().encode("Error interno del servidor".getBytes("UTF-8"))).build();
				} catch (UnsupportedEncodingException e1) {}
				
			}
			
			String campaignToken = null;
			try {
				campaignToken = new String(tokenDecrypted, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				try {
					return Response.status(500).entity(Base64.getUrlEncoder().encode("Error interno del servidor".getBytes("UTF-8"))).build();
				} catch (UnsupportedEncodingException e1) {}
			}
			
			System.out.println("Campaña: "+campaignName+ " token desencriptado: "+campaignToken);
			if (campaignToken.equals(campaignName)) {
				
				contains = false;
				synchronized (lockCampañas) {
					contains = campañas.containsKey(campaignToken);
				}
				
				if(contains){
				
					synchronized (lockTokens) {
						activeTokens.add(token);
					}
					
					System.out.println("token añadido a activos");
					try {
						return Response.status(200).entity(Base64.getUrlEncoder().encode("Token valido".getBytes("UTF-8"))).build();
					} catch (UnsupportedEncodingException e1) {}
				}
				else
				{
					System.out.println("No existe campaña con ese token");
					try{
						return Response.status(404).entity(Base64.getUrlEncoder().encode("Token invalido, no existe una campaña con dicho token".getBytes("UTF-8"))).build();
					} catch (UnsupportedEncodingException e1) {}
				}
				
			}
			else {
				
				System.out.println("No coinciden el token desencriptado y la campaña");
				try {
					return Response.status(404).entity(Base64.getUrlEncoder().encode("Token invalido, no coincide con la campaña enviada".getBytes("UTF-8"))).build();
				} catch (UnsupportedEncodingException e1) {}
			}
			
		}
		return Response.status(500).build();
		
	}

	public static boolean compruebaTokenInterno (String token, String campaignName) {
		
		//Para asegurarse de que la clase está instanciada antes de continuar

		
		boolean contains = false;
		System.out.println("Campaign es: " + campaignName+ " Token es: "+token);
		synchronized (lockTokens) {
			contains = activeTokens.contains(token);
		}	
		
		//Si el token esta en el set de tokens activos devolvemos true
		if (contains) {
			System.out.println("token en activetokens");
			return true;
		}
		//Si no pasamos a desencriptar el token
		else{
			System.out.println("token NO en activetokens");
			//String campaignName = new String(Base64.getUrlDecoder().decode(campaignName64));
			byte [] tokenDecrypted = null;
			try {
				tokenDecrypted = SymmetricEncryption.decryptUsingKey(Base64.getUrlDecoder().decode(token), campaignName);
			} catch (InvalidKeyException | KeyStoreException | NoSuchAlgorithmException | CertificateException
					| UnrecoverableEntryException | NoSuchPaddingException | InvalidAlgorithmParameterException
					| IllegalBlockSizeException | BadPaddingException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
				
				
			}
			
			String campaignToken = null;
			try {
				campaignToken = new String(tokenDecrypted, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				
				return false;
			}
			
			System.out.println("Campaña: "+campaignName+ " token desencriptado: "+campaignToken);
			if (campaignToken.equals(campaignName)) {
				
				contains = false;
				synchronized (lockCampañas) {
					contains = campañas.containsKey(campaignToken);
				}
				
				if(contains){
				
					synchronized (lockTokens) {
						activeTokens.add(token);
					}
					
					System.out.println("token añadido a activos");
					return true;
				}
				else
				{
					System.out.println("No existe campaña con ese token");
					return false;
				}
				
			}
			else {
				
				System.out.println("No coinciden el token desencriptado y la campaña");
				return false;
			}
			
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent arg0) {
		// TODO Auto-generated method stub
		taskExecutor.stop();
		System.out.println(SEPARADOR);
		System.out.println("Context Destroyed");
	}

	@Override
	public void contextInitialized(ServletContextEvent arg0) {
		// TODO Auto-generated method stub
		System.out.println(SEPARADOR);
		System.out.println("Context created");
	}
	
}
