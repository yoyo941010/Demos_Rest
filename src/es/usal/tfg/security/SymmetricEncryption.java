package es.usal.tfg.security;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStore.SecretKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import es.usal.tfg.CampaignManagement;

public class SymmetricEncryption {
	
	private static final int AES_KEYLENGTH = 128;	// change this as desired for the security level you want
	private static final String KEYSTORE_PASS = "/etc/tomcat8/.DemosKey";
	private static final String KEYSTORE_FILE = CampaignManagement.WEBSERVICE_ABSOLUTE_ROUTE + "/.keystore";
	private static final File keyStoreFile = new File(KEYSTORE_FILE);
	private static final Object keyStoreLock = new Object();

	public static File getKeystorefile() {
		return keyStoreFile;
	}

	public static void configureKeyStore() throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException {

	
			
			/**
			 * Step 1. Generate an AES key using KeyGenerator Initialize the
			 * keysize to 128 bits (16 bytes)
			 * 
			 */
			KeyGenerator keyGen = KeyGenerator.getInstance("AES");
			keyGen.init(128);
			SecretKey secretKey = keyGen.generateKey();

			
			
			/**
			 * Step 2. Generate an Initialization Vector (IV) 
			 * 		a. Use SecureRandom to generate random bits
			 * 		   The size of the IV matches the blocksize of the cipher (128 bits for AES)
			 * 		b. Construct the appropriate IvParameterSpec object for the data to pass to Cipher's init() method
			 */

			
			byte[] iv = new byte[AES_KEYLENGTH / 8];	// Save the IV bytes or send it in plaintext with the encrypted data so you can decrypt the data later
			SecureRandom prng = new SecureRandom();
			prng.nextBytes(iv);
			
			
			/**
			 * Step 3. Store the master secret key used to encrypt campaign.json to a keystore
			 * 		a. Create a Keystore in JCEKS format to store SecretKeys for AES encryption
			 * 		b. Protect this Keystore with a master pass 
			 * 		
			 */
			 
			
			String keyStoreType = "JCEKS";
			//File keyFile = new File(KEYSTORE_FILE);
            KeyStore keyStore;
            FileOutputStream fos=null;
            BufferedReader br=null;
            char [] password = {'\0'};
			try {
				
				keyStore = KeyStore.getInstance(keyStoreType);
				
				br = new BufferedReader(new FileReader(new File (KEYSTORE_PASS)));
				
				password = br.readLine().toCharArray();
				
				keyStore.load(null, password);
				
				br.close();
				br = null;
				
				
				KeyStore.ProtectionParameter keyProtectionParm= new KeyStore.PasswordProtection(password);
	            SecretKeyEntry entry = new SecretKeyEntry(secretKey);
	            keyStore.setEntry("master_key", entry, keyProtectionParm);
				
				
				synchronized (keyStoreLock) {
					fos = new FileOutputStream(keyStoreFile);
					
					keyStore.store(fos, password);
					fos.close();
					fos = null;
				}
				
				
					
			} finally {
				
				try {
					if (fos != null) {
						fos.flush();
						fos.close();
						fos = null;
					}
					if (br!=null) {
						br.close();
						br = null;
					}
				} catch (IOException e) {
					
				}
				
				Arrays.fill(password, '\0');
			}
           
		
			

	}

	public static CipherOutputStream encryptFileSavingKey(File file, String keyAlias)
			throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException,
			NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {

		/**
		 * Step 1. Generate an AES key using KeyGenerator Initialize the keysize
		 * to 128 bits (16 bytes)
		 * 
		 */
		KeyGenerator keyGen;

		keyGen = KeyGenerator.getInstance("AES");
		keyGen.init(128);
		SecretKey secretKey = keyGen.generateKey();

		/**
		 * Step 2. Generate an Initialization Vector (IV) 
		 * 		a. Use SecureRandom to generate random bits The size of the IV 
		 * 			matches the blocksize of the cipher (128 bits for AES) 
		 * 		b. Construct the appropriate IvParameterSpec object for the 
		 * 			data to pass to Cipher's init() method
		 */

		byte[] iv = new byte[AES_KEYLENGTH / 8];
		SecureRandom prng = new SecureRandom();
		prng.nextBytes(iv);

		/**
		 * Step 3. Store the secret key to a keystore
		 * 
		 */

		//File keyFile = new File(KEYSTORE_FILE);
		KeyStore keyStore = null;

		FileInputStream fis = null;
		FileOutputStream fos = null;
		BufferedReader br = null;
		char[] password = { '\0' };
		try {
			keyStore = KeyStore.getInstance("JCEKS");
			

			br = new BufferedReader(new FileReader(new File(KEYSTORE_PASS)));

			password = br.readLine().toCharArray();

			br.close();
			br = null;
			
			KeyStore.ProtectionParameter keyProtectionParm = new KeyStore.PasswordProtection(password);
			SecretKeyEntry entry = new SecretKeyEntry(secretKey);
			
			synchronized (keyStoreLock) {
				fis = new FileInputStream(keyStoreFile);
				keyStore.load(fis, password);
				
				keyStore.setEntry(keyAlias, entry, keyProtectionParm);
				fos = new FileOutputStream(keyStoreFile);
				keyStore.store(fos, password);
				
				fis.close();
				fis = null;
				
				fos.close();
				fos = null;
			}

		} finally {

			try {
				if (fis != null) {
					fis.close();
					fis = null;
				}
				if (fos != null) {
					fos.flush();
					fos.close();
					fos = null;
				}
				if (br != null) {
					br.close();
					br = null;
				}
			} catch (IOException e) {

			}

			Arrays.fill(password, '\0');
		}

		/**
		 * Step 4. Create a Cipher by specifying the following parameters 
		 * 		a. Algorithm name - here it is AES
		 * 		b. Mode - here it is CBC mode 
		 * 		c. Padding - e.g. PKCS7 or PKCS5
		 */

		Cipher aesCipherForEncryption = Cipher.getInstance("AES/CBC/PKCS5PADDING");

		/**
		 * Step 5. Initialize the Cipher for Encryption
		 */

		aesCipherForEncryption.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));

		/**
		 * Step 6. Create a CipherOutputStream with the file given by attribute
		 * and the cipher initialized above. Before returning, write the IV at
		 * the beginning of the file.
		 */

		fos = new FileOutputStream(file);

		CipherOutputStream cos = new CipherOutputStream(fos, aesCipherForEncryption);

		fos.write(iv);

		return cos;

	}
	
	public static CipherOutputStream encryptFileUsingKey(File file, String keyAlias)
			throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException,
			NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, UnrecoverableEntryException {

		
		/**
		 * Step 1. Generate an Initialization Vector (IV) 
		 * 		a. Use SecureRandom to generate random bits The size of the IV 
		 * 			matches the blocksize of the cipher (128 bits for AES) 
		 * 		b. Construct the appropriate IvParameterSpec object for the 
		 * 			data to pass to Cipher's init() method
		 */

		byte[] iv = new byte[AES_KEYLENGTH / 8];
		SecureRandom prng = new SecureRandom();
		prng.nextBytes(iv);

		/**
		 * Step 2. Retrieve the secret key from the keystore
		 * 
		 */

		//File keyFile = new File(KEYSTORE_FILE);
		KeyStore keyStore = null;

		FileInputStream fis = null;
		BufferedReader br = null;
		SecretKey secretKey;
		char[] password = { '\0' };
		try {
			keyStore = KeyStore.getInstance("JCEKS");
			

			br = new BufferedReader(new FileReader(new File(KEYSTORE_PASS)));

			password = br.readLine().toCharArray();

			br.close();
			br = null;
			
			KeyStore.ProtectionParameter keyProtectionParm = new KeyStore.PasswordProtection(password);
			
			SecretKeyEntry skEntry;
			
			synchronized (keyStoreLock) {
				fis = new FileInputStream(keyStoreFile);
				keyStore.load(fis, password);
				skEntry = (SecretKeyEntry) keyStore.getEntry(keyAlias, keyProtectionParm);
				fis.close();
				fis = null;
			}
			
			secretKey = skEntry.getSecretKey();

		} finally {

			try {
				if (fis != null) {
					fis.close();
					fis = null;
				}
				if (br != null) {
					br.close();
					br = null;
				}
			} catch (IOException e) {

			}

			Arrays.fill(password, '\0');
		}

		/**
		 * Step 3. Create a Cipher by specifying the following parameters 
		 * 		a. Algorithm name - here it is AES
		 * 		b. Mode - here it is CBC mode 
		 * 		c. Padding - e.g. PKCS7 or PKCS5
		 */

		Cipher aesCipherForEncryption = Cipher.getInstance("AES/CBC/PKCS5PADDING");

		/**
		 * Step 4. Initialize the Cipher for Encryption
		 */

		aesCipherForEncryption.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));

		/**
		 * Step 5. Create a CipherOutputStream with the file given by attribute
		 * and the cipher initialized above. Before returning, write the IV at
		 * the beginning of the file.
		 */

		FileOutputStream fos = new FileOutputStream(file);

		CipherOutputStream cos = new CipherOutputStream(fos, aesCipherForEncryption);

		fos.write(iv);

		return cos;

	}
	
	public static byte[] encryptUsingKey(byte[] input, String keyAlias)
			throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException,
			NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException,
			UnrecoverableEntryException, IllegalBlockSizeException, BadPaddingException {

		/**
		 * Step 1. Generate an Initialization Vector (IV) 
		 * 		a. Use SecureRandom to generate random bits The size of the IV 
		 * 			matches the blocksize of the cipher (128 bits for AES) 
		 * 		b. Construct the appropriate IvParameterSpec object for the 
		 * 			data to pass to Cipher's init() method
		 */

		byte[] iv = new byte[AES_KEYLENGTH / 8];
		SecureRandom prng = new SecureRandom();
		prng.nextBytes(iv);

		/**
		 * Step 2. Retrieve the secret key from the keystore
		 * 
		 */

		//File keyFile = new File(KEYSTORE_FILE);
		KeyStore keyStore = null;

		FileInputStream fis = null;
		BufferedReader br = null;
		SecretKey secretKey;
		char[] password = { '\0' };
		try {
			keyStore = KeyStore.getInstance("JCEKS");
			

			br = new BufferedReader(new FileReader(new File(KEYSTORE_PASS)));

			password = br.readLine().toCharArray();

			br.close();
			br = null;
			
			KeyStore.ProtectionParameter keyProtectionParm = new KeyStore.PasswordProtection(password);
			
			SecretKeyEntry skEntry;
			synchronized (keyStoreLock) {
				fis = new FileInputStream(keyStoreFile);
				keyStore.load(fis, password);
				skEntry = (SecretKeyEntry) keyStore.getEntry(keyAlias, keyProtectionParm);
				fis.close();
				fis = null;
			}
			secretKey = skEntry.getSecretKey();

		} finally {

			try {
				if (fis != null) {
					fis.close();
					fis = null;
				}
				if (br != null) {
					br.close();
					br = null;
				}
			} catch (IOException e) {

			}

			Arrays.fill(password, '\0');
		}

		/**
		 * Step 3. Create a Cipher by specifying the following parameters 
		 * 		a. Algorithm name - here it is AES
		 * 		b. Mode - here it is CBC mode 
		 * 		c. Padding - e.g. PKCS7 or PKCS5
		 */

		Cipher aesCipherForEncryption = Cipher.getInstance("AES/CBC/PKCS5PADDING");

		/**
		 * Step 5. Initialize the Cipher for Encryption
		 */

		aesCipherForEncryption.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));



		/**
		 * Step 6. Encrypt the Data 
		 * 		a. Encrypt the bytes using doFinal method 
		 * 		b. Concatenate the IV and the encrypted data to return it
		 */
		
		
		 byte[] byteDataToEncrypt = input; 
		 byte[] byteCipherText = aesCipherForEncryption
				 .doFinal(byteDataToEncrypt);
		  
		 byte [] result = new byte[iv.length + byteCipherText.length];
		 System.arraycopy(iv, 0, result, 0, iv.length);
		 System.arraycopy(byteCipherText, 0, result, iv.length, byteCipherText.length);
		
		 
		return result;

	}
	
	
	
	public static CipherInputStream decryptFileUsingKey(File file, String keyAlias) throws KeyStoreException,
			IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableEntryException,
			NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
		

		/**
		 * Step 1. Retrieve the secret key from the keystore
		 * 
		 */

		//File keyFile = new File(KEYSTORE_FILE);
		KeyStore keyStore = null;

		FileInputStream fis = null;
		BufferedReader br = null;
		SecretKey secretKey;
		char[] password = { '\0' };
		try {
			keyStore = KeyStore.getInstance("JCEKS");
			

			br = new BufferedReader(new FileReader(new File(KEYSTORE_PASS)));

			password = br.readLine().toCharArray();

			br.close();
			br = null;
			KeyStore.ProtectionParameter keyProtectionParm = new KeyStore.PasswordProtection(password);

			
			SecretKeyEntry skEntry;
			synchronized (keyStoreLock) {
				fis = new FileInputStream(keyStoreFile);
				keyStore.load(fis, password);
				skEntry = (SecretKeyEntry) keyStore.getEntry(keyAlias, keyProtectionParm);
				fis.close();
				fis = null;
			}
			
			secretKey = skEntry.getSecretKey();

		} finally {

			try {
				if (fis != null) {
					fis.close();
					fis = null;
				}
				if (br != null) {
					br.close();
					br = null;
				}
			} catch (IOException e) {

			}

			Arrays.fill(password, '\0');
		}

		/**
		 * Step 2. Create a Cipher by specifying the following parameters 
		 * 		a. Algorithm name - here it is AES
		 * 		b. Mode - here it is CBC mode 
		 * 		c. Padding - e.g. PKCS7 or PKCS5
		 */

		Cipher aesCipherForDecryption = Cipher.getInstance("AES/CBC/PKCS5PADDING");
		
		/**
		 * Step 3. Read the IV from the file
		 */
		byte [] iv = new byte [AES_KEYLENGTH/8];
		try {
			fis = new FileInputStream(file);
			fis.read(iv);

			fis.close();
		} finally {
			if (fis!=null) {
				fis.close();
				fis=null;
			}
		}
		
		
		/**
		 * Step 4. Initialize the Cipher for Decryption
		 */

		aesCipherForDecryption.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));

		/**
		 * Step 5. Create a CipherInputStream with the file given by attribute
		 * and the cipher initialized above. 
		 */

		/**
		 * Reading all bytes to a byte array using a CipherInputStream
		 * doesn't work the finals blocks are lost. The solution is copying
		 * the bytes to a ByteArrayOutputStream and then cast this stream to
		 * a byte array
		 * 
		 * @reference http://stackoverflow.com/a/34030041/6441806
		 */
		//TODO ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		//byte[] b = new byte[1024];
		CipherInputStream cis = null;
		
		fis = new FileInputStream(file);
		// Necessary to avoid reading the IV which is on the beggining
		// of the file
		fis.read(iv);
		
		cis = new CipherInputStream(fis, aesCipherForDecryption);
		/*
		int readedB = 0;
		while ((readedB = cis.read(b)) >= 0) {
			buffer.write(b, 0, readedB);
		}
		

			*/
		

		return cis;

	}
	
	public static byte[] decryptUsingKey(byte[] input, String keyAlias) throws KeyStoreException, IOException,
			NoSuchAlgorithmException, CertificateException, UnrecoverableEntryException, NoSuchPaddingException,
			InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
	
		/**
		 * Step 1. Retrieve the secret key from the keystore
		 * 
		 */

		//File keyFile = new File(KEYSTORE_FILE);
		KeyStore keyStore = null;

		FileInputStream fis = null;
		BufferedReader br = null;
		SecretKey secretKey;
		char[] password = { '\0' };
		try {
			keyStore = KeyStore.getInstance("JCEKS");
			

			br = new BufferedReader(new FileReader(new File(KEYSTORE_PASS)));

			password = br.readLine().toCharArray();

			br.close();
			br = null;
			
			KeyStore.ProtectionParameter keyProtectionParm = new KeyStore.PasswordProtection(password);
			
			SecretKeyEntry skEntry;
			synchronized (keyStoreLock) {
				fis = new FileInputStream(keyStoreFile);
				keyStore.load(fis, password);
				skEntry = (SecretKeyEntry) keyStore.getEntry(keyAlias, keyProtectionParm);
				fis.close();
				fis = null;
			}
			secretKey = skEntry.getSecretKey();

		} finally {

			try {
				if (fis != null) {
					fis.close();
					fis = null;
				}
				if (br != null) {
					br.close();
					br = null;
				}
			} catch (IOException e) {

			}

			Arrays.fill(password, '\0');
		}
		

		/**
		 * Step 2. Create a Cipher by specifying the following parameters 
		 * 		a. Algorithm name - here it is AES
		 * 		b. Mode - here it is CBC mode 
		 * 		c. Padding - e.g. PKCS7 or PKCS5
		 */

		Cipher aesCipherForDecryption = Cipher.getInstance("AES/CBC/PKCS5PADDING");
		
		/**
		 * Step 3. Read the IV from the byteArray
		 */
		byte [] iv = new byte [AES_KEYLENGTH/8];
		ByteArrayInputStream bis = null;
		try {
			bis = new ByteArrayInputStream(input);
			bis.read(iv);

			bis.close();
		} finally {
			if (bis!=null) {
				bis.close();
				bis=null;
			}
		}
		
		
		/**
		 * Step 4. Initialize the Cipher for Decryption
		 */

		aesCipherForDecryption.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));


		/**
		 * Step 5. Decrypt the input bytes without the IV
		 */

		byte[] output = aesCipherForDecryption.doFinal(Arrays.copyOfRange(input, iv.length, input.length)); 
		
		return output;
	}

	/**
	 * 
	 * @param file
	 * @param keyAlias
	 * @return
	 * @throws KeyStoreException 
	 * @throws IOException 
	 * @throws CertificateException 
	 * @throws NoSuchAlgorithmException 
	 * @throws UnrecoverableEntryException 
	 * @throws InvalidAlgorithmParameterException 
	 * @throws InvalidKeyException 
	 * @throws NoSuchPaddingException 
	 * @throws BadPaddingException 
	 * @throws IllegalBlockSizeException 
	 * 
	 * @reference 	http://stackoverflow.com/a/4877403/6441806
	 * 				http://stackoverflow.com/a/10291282/6441806
	 */
	public static CipherOutputStream appendAES(File file, String keyAlias)
			throws IllegalArgumentException, KeyStoreException, IOException, NoSuchAlgorithmException,
			CertificateException, UnrecoverableEntryException, InvalidKeyException, InvalidAlgorithmParameterException,
			NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {

		//Recuperar la clave privada designada con keyAlias del KeyStore
		//File keyFile = new File(KEYSTORE_FILE);
		KeyStore keyStore=null;
		SecretKey secretKey = null;
		FileInputStream fis=null;
		BufferedReader br = null;
		char [] password = {'\0'};
		try {
			keyStore = KeyStore.getInstance("JCEKS");
		
			
			br = new BufferedReader(new FileReader(new File (KEYSTORE_PASS)));
			
			password = br.readLine().toCharArray();
			
			
			br.close();
			br = null;
			
			KeyStore.ProtectionParameter keyProtectionParm= new KeyStore.PasswordProtection(password);
			
			
			SecretKeyEntry skEntry;
			synchronized (keyStoreLock) {
				fis = new FileInputStream(keyStoreFile);
				keyStore.load(fis, password);
				skEntry = (SecretKeyEntry) keyStore.getEntry(keyAlias, keyProtectionParm);
				fis.close();
				fis=null;
			}
			
			secretKey = skEntry.getSecretKey();
			
		} finally {
			
			try {
				if (fis!= null) {
					fis.close();
					fis=null;
				}
				if (br!=null) {
					br.close();
					br = null;
				}
			} catch (IOException e) {
				
			}
			
			Arrays.fill(password, '\0');
		}
		
		//Bloque de inicializacion para esta concatenacion (distinto del IV del fichero entero)
		byte [] iv = new byte[AES_KEYLENGTH/8];
		
		//Desencriptar ultimo bloque
		byte [] lastBlock = null, lastBlockEncrypt = new byte [AES_KEYLENGTH/8];
		RandomAccessFile rfile;
		
		//Declaracion de los cifradores para encriptar y desencriptar
		Cipher aesCipherForEncryption = Cipher.getInstance("AES/CBC/PKCS5PADDING"); // Must specify the mode explicitly as most JCE providers default to ECB mode!!
		Cipher aesCipherForDecryption = Cipher.getInstance("AES/CBC/PKCS5PADDING"); // Must specify the mode explicitly as most JCE providers default to ECB mode!!

		//Apertura del fichero en modo lectura+escritura
		rfile = new RandomAccessFile(file, "rw");

		//Si el tamaño del fichero no es multiplo del tamaño de bloque del cifrador
		if (rfile.length() % (AES_KEYLENGTH/8) != 0L) {
			rfile.close();
			throw new IllegalArgumentException("Invalid file length (not a multiple of block size)");
		}
		//Si el tamaño del fichero es exactamente 1 bloque significa que solo tiene el IV inicial
		else if (rfile.length() == AES_KEYLENGTH / 8) {
			
			rfile.read(iv);
			
			aesCipherForEncryption.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));
			
			CipherOutputStream cos = new CipherOutputStream(new FileOutputStream(rfile.getFD()),
					aesCipherForEncryption);

			return cos;
			
		} 
		//El tamaño del fichero es de almenos 2 bloques por lo que ya contiene informacion encriptada
		//así pues se recupera y desencripta el penultimo bloque y se usa como IV para cifrar 
		//el ultimo bloque cifrado a partir del cual se construira el CipherOutputStream 
		else {

			rfile.seek(rfile.length() - 2 * (AES_KEYLENGTH / 8));
			rfile.read(iv);

			rfile.read(lastBlockEncrypt);

			aesCipherForDecryption.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
			lastBlock = aesCipherForDecryption.doFinal(lastBlockEncrypt);
			rfile.seek(rfile.length() - AES_KEYLENGTH / 8);

			aesCipherForEncryption.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));
			byte[] out = null;
			if (lastBlock != null) {
				out = aesCipherForEncryption.update(lastBlock);

				if (out != null) {
					rfile.write(out);
				}
			}

			CipherOutputStream cos = new CipherOutputStream(new FileOutputStream(rfile.getFD()),
					aesCipherForEncryption);

			return cos;
		}

		 
		
		
	}
	
	
	
}
