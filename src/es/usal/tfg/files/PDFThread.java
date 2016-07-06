/*
 * Archivo: PDFThread.java 
 * Proyecto: Demos_Rest
 * 
 * Autor: Aythami Estévez Olivas
 * Email: aythae@gmail.com
 * Fecha: 04-jul-2016
 * Repositorio GitHub: https://github.com/AythaE/Demos_Rest
 */
package es.usal.tfg.files;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import be.quodlibet.boxable.BaseTable;
import be.quodlibet.boxable.Cell;
import be.quodlibet.boxable.HorizontalAlignment;
import be.quodlibet.boxable.Row;
import be.quodlibet.boxable.VerticalAlignment;
import es.usal.tfg.Campaign;
import es.usal.tfg.CampaignManagement;
import es.usal.tfg.imageProcessing.Firma;
import es.usal.tfg.security.SymmetricEncryption;

/**
 * Clase PDFThread que contiene el metodo necesario para montar un PDF con las
 * firmas de una determinada campaña,.
 */
public class PDFThread implements Callable<File> {

	/**
	 * NUMERO_DNI_X_HOJA que controla cuantos DNI se introduciran por cada cara
	 */
	public static final int NUMERO_DNI_X_HOJA = 5;
	
	/** La campaña de la que se recuperarán los DNI */
	private Campaign campaign; 
	
	/** El nombre de la campaña. */
	private String campaignName;
	 
	/** The pdf file. */
	private File pdfFile;
	
	/**
	 * Instantiates a new PDF thread.
	 *
	 * @param campaign the campaign
	 */
	public PDFThread(Campaign campaign) {
		this.campaign = campaign;
		this.campaignName = campaign.getCampaignName();
		this.pdfFile = new File(this.campaign.getDirectory(), "Firmas.pdf");
	}


	/* (non-Javadoc)
	 * @see java.util.concurrent.Callable#call()
	 */
	@Override
	public File call() throws Exception {
		//Variables para medicion de tiempos
		long tIni =0, tfin=0;
		tIni = System.currentTimeMillis();
		
		
		System.out.println("[" + new Date().toString() + "] PDFThread "+campaignName+": Iniciado");
		
		//Inicialización del documento PDF
		PDDocument doc = new PDDocument();
		PDPageContentStream contents = null;
		
				
		// Calculo del numero de paginas que ocupara el pdf de la peticion en funcion 
		// del numero de firmas que tiene y el numero de dnis por pagina
		long numFirmas;
		synchronized (campaign) {
			numFirmas = campaign.getNumeroFirmas();
		}
		System.out.println("[" + new Date().toString() + "] PDFThread "+campaignName+": numero de firmas: "+numFirmas);
		
		//Recuperación del todas las firmas de la peticion
		ArrayList<Firma> firmas = null;
		try {
			firmas = retrieveFirmas();
		} catch (InvalidKeyException | KeyStoreException | NoSuchAlgorithmException | CertificateException
				| UnrecoverableEntryException | NoSuchPaddingException | InvalidAlgorithmParameterException
				| IOException e) {
			System.err.println("[" + new Date().toString() + "] PDFThread "+campaignName+": Error recuperando las firmas");
			e.printStackTrace();
			try {
				if (doc!=null) {

					doc.close();
				}
			} catch (IOException e1) {}
			return null;
		} 
		
		//Se ordenan por el numero de la hoja de firmas
		Collections.sort(firmas);
		
	
		
		//Entran 5 firmas por cada hoja
		long numHojasTotales = numFirmas/ NUMERO_DNI_X_HOJA;
		
		//Si el modulo es distinto de 0 significa que tenemos que sumar otra hoja
		if (numFirmas % NUMERO_DNI_X_HOJA > 0) {
			numHojasTotales++;
		}
		
		float margin = 20;
		PDFont fontPiePag = PDType1Font.HELVETICA;
		int fontSizePiePag = 10;
		int pieDePagHeight = 14;	
		
		
		/**
		 * Para incrementar velocidad con imagenes pequeñas
		 * @reference http://stackoverflow.com/questions/18522398/fastest-way-to-read-write-images-from-a-file-into-abufferedimage
		 * http://docs.oracle.com/javase/8/docs/api/javax/imageio/ImageIO.html
		 */
		
		ImageIO.setUseCache(false);
			

		PDPage page;	
		float pageWidth =0 , pageHeight = 0;
		String[] pieDePagComponentesInit = { campaign.getCampaignName(), "Hoja de firmas: ",
				Integer.toString(doc.getDocumentCatalog().getPages().getCount()) + "/" + numHojasTotales };
		String[] pieDePagComponentes = new String[pieDePagComponentesInit.length];
		
		
		CipherInputStream cisFrontal = null, cisTrasero=null;
		try {	
			for (int i = 0; i < firmas.size(); i++) {
				
				Firma f = firmas.get(i);
				
				
				//Si el modulo es 0 es necesario añadir una nueva pagina al pdf
				if (i%NUMERO_DNI_X_HOJA == 0) {
					
					if (contents!=null) {

						contents.close();
					}
					//Se crea y añade la pagina
					page = new PDPage(PDRectangle.A4);
					pageWidth = page.getMediaBox().getWidth();
					pageHeight = page.getMediaBox().getHeight();
					doc.addPage(page);
					
					System.out.println("[" + new Date().toString() + "] PDFThread "+campaignName+": página añadida");
					
					//Inicializacion del pie de pagina
					System.arraycopy(pieDePagComponentesInit, 0, pieDePagComponentes, 0, pieDePagComponentesInit.length);
					
					//Comprobación de la hoja de firmas en la que estan la firmas
					//que irán en esta pagina para escribirlo al pie de pagina
					
					String pieDePagHojaFirmas = pieDePagComponentes[1];
					long [] numHojaDeFirmas = {f.getNumHojaFirmas(),-1,-1,-1,-1};
					
					pieDePagHojaFirmas+=numHojaDeFirmas[0];
					int k=1;
					
					for (int j = i+1; j < i+NUMERO_DNI_X_HOJA; j++) {
						
						if (j< firmas.size()) {
							Firma temp = firmas.get(j);
							if (numHojaDeFirmas[k-1] != temp.getNumHojaFirmas()) {
								numHojaDeFirmas[k] = temp.getNumHojaFirmas();	
								pieDePagHojaFirmas+=", "+numHojaDeFirmas[k];
								k++;
							}
						}
					}
					pieDePagComponentes[1] = pieDePagHojaFirmas;
					pieDePagComponentes[2] = Integer.toString(doc.getDocumentCatalog().getPages().getCount()) + "/" + numHojasTotales ;
					
					//Construccion del pie de pagina y adición de espacios para cuadrarlo
					//con el ancho de la pagina y los margenes laterales
					String pieDePag = pieDePagComponentes[0] + pieDePagComponentes[1] + pieDePagComponentes[2];
				
					float pieDePagWidth = (fontPiePag.getStringWidth(pieDePag) / 1000f) * fontSizePiePag;
					
					while (pieDePagWidth < pageWidth -2*margin ) {
						for (k = 0; k < pieDePagComponentes.length-1; k++) {
							pieDePagComponentes[k] += " ";
						}
						String tempStr = pieDePagComponentes[0] + pieDePagComponentes[1] + pieDePagComponentes[2];
						
						float tempWidth  = fontPiePag.getStringWidth(tempStr) / 1000f * fontSizePiePag;
						
						if(tempWidth < pageWidth -2*margin){
							pieDePag = tempStr;
							pieDePagWidth = fontPiePag.getStringWidth(pieDePag) / 1000f * fontSizePiePag;
							
						}
						else
							break;
						
					}
					
					//Creacion del stream pdf y escritura del pie de pagina
					contents= new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true);
					contents.beginText();
					contents.setFont(fontPiePag, fontSizePiePag);
					contents.newLineAtOffset( margin , 4);
					pageHeight-=pieDePagHeight;
					contents.showText(pieDePag);
					contents.endText();
					
				}
					
				//Creacion de streams para desencriptar las 2 fotos de esta firma y lectura de las imagenes
				cisFrontal = SymmetricEncryption.decryptFileUsingKey(f.getDniFrontal(), campaignName);
				cisTrasero = SymmetricEncryption.decryptFileUsingKey(f.getDniPosterior(), campaignName);
				
				
				
				
				BufferedImage dniImgFront = ImageIO.read(cisFrontal);	
				BufferedImage dniImgBack = ImageIO.read(cisTrasero);
				 
				cisFrontal.close(); cisTrasero.close();
				cisFrontal = null; cisTrasero = null;
				
				if (dniImgFront != null) {
					PDImageXObject pdImageFront = LosslessFactory.createFromImage(doc,dniImgFront);
					
					contents.drawImage(pdImageFront, // Imagen
							2, // Coordenada X
							pageHeight + pieDePagHeight - ((i % NUMERO_DNI_X_HOJA)+1) * ((pageHeight / NUMERO_DNI_X_HOJA) - 2)
									- 2 * ((i % NUMERO_DNI_X_HOJA)+1), // Coordenada Y
							(pageWidth / 2) - 2, // Ancho
							(pageHeight / NUMERO_DNI_X_HOJA) - 2); // Alto

					
				}
				
				if (dniImgBack!=null) {
					PDImageXObject pdImageBack = LosslessFactory.createFromImage(doc,dniImgBack);
					
					contents.drawImage(pdImageBack, 
							pageWidth / 2,
							pageHeight + pieDePagHeight -((i % NUMERO_DNI_X_HOJA)+1) * ((pageHeight / NUMERO_DNI_X_HOJA) - 2)
							- 2 * ((i % NUMERO_DNI_X_HOJA)+1), 
							(pageWidth / 2) - 2,
							(pageHeight / NUMERO_DNI_X_HOJA) - 2);
				}
				
				if (f.getNumHojaDNIs() != doc.getDocumentCatalog().getPages().getCount()) {
					f.setNumHojaDNIs(doc.getDocumentCatalog().getPages().getCount());
				}	
			
			}
		} catch (IOException | InvalidKeyException | KeyStoreException | NoSuchAlgorithmException | CertificateException
				| UnrecoverableEntryException | NoSuchPaddingException | InvalidAlgorithmParameterException e) {

				
			System.err.println("[" + new Date().toString() + "] PDFThread "+campaignName+": Error añadiendo imagenes a los PDF");
			e.printStackTrace();
			
			try {
				if (doc!=null) {

					doc.close();
				}
			} catch (IOException e1) {}
			return null;
		} finally {
			try {
				if (contents != null) {
					contents.close();
					contents = null;
				}				
				if (cisFrontal != null) {
					cisFrontal.close();
				}
				if (cisTrasero != null) {
					cisTrasero.close();
				}
			} catch (IOException e1) {}
		}
	
		System.out.println("[" + new Date().toString() + "] PDFThread "+campaignName+": Imagenes añadidas correctamente");
		
		page = new PDPage(PDRectangle.A4);
		doc.addPage(page);
		
	
		
		pageHeight = page.getMediaBox().getHeight();
		
		
		/**
		 * Para la creacion de la tabla se ha empleado la librería boxable-1.4
		 * @see https://github.com/dhorions/boxable
		 */
		
		//Initializate table variables
		float yStartNewPage = pageHeight - margin;	
		float tableWidth = pageWidth - 2*margin;
		boolean drawContent = true;
		boolean drawLines = true;
		float yStart = yStartNewPage;
		float bottomMargin = margin;
		
		//Creacion de la tabla
		BaseTable table=null;
		try {
			table = new BaseTable(yStart, yStartNewPage, bottomMargin, tableWidth, margin, doc, page, drawLines,
					drawContent);
		} catch (IOException e) {

			System.err.println(CampaignManagement.SEPARADOR);
			System.err.println("PDF Error creando la tabla");
			e.printStackTrace();

			try {
				if (doc!=null) {

					doc.close();
				}
			} catch (IOException e1) {}
			return null;
		}
		System.out.println("[" + new Date().toString() + "] PDFThread "+campaignName+": Tabla creada, pasando a rellenarla");
		
		//Creacion de la cabecera de la tabla
		Row<PDPage> header = table.createRow(15f);
		Cell<PDPage> cell = header.createCell((100 / 7f)/2f, "Nº", HorizontalAlignment.CENTER, VerticalAlignment.MIDDLE);
		cell.setFont(PDType1Font.HELVETICA_BOLD);
		cell.setFontSize(8);
		cell = header.createCell((100 / 7f), "NÚMERO DNI", HorizontalAlignment.CENTER, VerticalAlignment.MIDDLE);
		cell.setFont(PDType1Font.HELVETICA_BOLD);
		cell.setFontSize(8);
		cell = header.createCell((100 / 7f) * (3f/2f), "NOMBRE", HorizontalAlignment.CENTER, VerticalAlignment.MIDDLE);
		cell.setFont(PDType1Font.HELVETICA_BOLD);
		cell.setFontSize(8);
		cell = header.createCell((100 / 7f) * 2f, "APELLIDOS", HorizontalAlignment.CENTER, VerticalAlignment.MIDDLE);
		cell.setFont(PDType1Font.HELVETICA_BOLD);
		cell.setFontSize(8);
		cell = header.createCell((100 / 7f), "FECHA", HorizontalAlignment.CENTER, VerticalAlignment.MIDDLE);
		cell.setFont(PDType1Font.HELVETICA_BOLD);
		cell.setFontSize(8);
		cell = header.createCell((100 / 7f) /2f, "Nº HOJA DE FIRMAS", HorizontalAlignment.CENTER, VerticalAlignment.MIDDLE);
		cell.setFont(PDType1Font.HELVETICA_BOLD);
		cell.setFontSize(8);
		cell = header.createCell((100 / 7f) /2f, "Nº HOJA DE DNI", HorizontalAlignment.CENTER, VerticalAlignment.MIDDLE);
		cell.setFont(PDType1Font.HELVETICA_BOLD);
		cell.setFontSize(8);
		
		
		table.addHeaderRow(header);
		

			
		
		//Rellenado de la tabla, cuando se haya llenado una pagina la librería se encarga de añadir una nueva
		//e incluir la cabecera de la tabla en cada página
		
		for (int i = 0; i < firmas.size(); i++) {
			
			Firma firma = firmas.get(i);
			
			Row<PDPage> row = table.createRow(8f);
			cell = row.createCell((100 / 7f)/2f, Integer.toString(i+1));
			cell.setFont(PDType1Font.HELVETICA);
			cell.setFontSize(8);
			cell = row.createCell((100 / 7f), firma.getNumDni());
			cell.setFont(PDType1Font.HELVETICA);
			cell.setFontSize(8);
			cell = row.createCell((100 / 7f) * (3f/2f), firma.getNombre());
			cell.setFont(PDType1Font.HELVETICA);
			cell.setFontSize(8);
			cell = row.createCell((100 / 7f) * 2f, firma.getApellidos());
			cell.setFont(PDType1Font.HELVETICA);
			cell.setFontSize(8);
			cell = row.createCell((100 / 7f), firma.getFecha());
			cell.setFont(PDType1Font.HELVETICA);
			cell.setFontSize(8);
			cell = row.createCell((100 / 7f) /2f, Long.toString(firma.getNumHojaFirmas()));
			cell.setFont(PDType1Font.HELVETICA);
			cell.setFontSize(8);
			cell = row.createCell((100 / 7f) /2f, Long.toString(firma.getNumHojaDNIs()));
			cell.setFont(PDType1Font.HELVETICA);
			cell.setFontSize(8);
			
		}
	
		
		try {
			table.draw();
		} catch (IOException e) {
			System.err.println("[" + new Date().toString() + "] PDFThread "+campaignName+": Error escribiendo la tabla");
			e.printStackTrace();

			try {
				if (doc!=null) {

					doc.close();
				}
			} catch (IOException e1) {}
			return null;
		}
		
		
		Set<PosixFilePermission> permsRW = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
		try {
			
			if(!pdfFile.exists()){
				Files.createFile(pdfFile.toPath(), PosixFilePermissions.asFileAttribute(permsRW));
			}

			doc.save(pdfFile);
			
			
		} catch (IOException e) {
			System.out.println("[" + new Date().toString() + "] PDFThread "+campaignName+": Error guardando el pdf");
			e.printStackTrace();

			
		} finally {
			try {
				if (doc!=null) {

					doc.close();
				}
			} catch (IOException e1) {}
		}
		
		
		
		
		tfin = System.currentTimeMillis();
		System.out.println("[" + new Date().toString() + "] PDFThread "+campaignName+": Montado de PDF correcto");
		System.out.println("[" + new Date().toString() + "] PDFThread "+campaignName+": Tiempo para montar pdf "+campaignName+": "+(tfin - tIni)/1000.0 + " segundos");
		
		
		
		return pdfFile;
	}
	
	/**
	 * Método que recupera todas la firmas de una campaña accediendo para ello
	 * a la base de datos de firmas de esta, desencriptando elemento a elemento
	 * y añadiendolo a un {@link ArrayList}.
	 *
	 * @return Arraylist de firmas construido
	 * 
	 * @throws InvalidKeyException the invalid key exception
	 * @throws KeyStoreException the key store exception
	 * @throws NoSuchAlgorithmException the no such algorithm exception
	 * @throws CertificateException the certificate exception
	 * @throws UnrecoverableEntryException the unrecoverable entry exception
	 * @throws NoSuchPaddingException the no such padding exception
	 * @throws InvalidAlgorithmParameterException the invalid algorithm parameter exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * 
	 * @see <a href="https://sites.google.com/site/gson/streaming">Referencia</a>
	 */
	public ArrayList<Firma> retrieveFirmas()
			throws InvalidKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException,
			UnrecoverableEntryException, NoSuchPaddingException, InvalidAlgorithmParameterException, IOException {
		
		Gson gson = new Gson();

		ArrayList<Firma> firmas = new ArrayList<>();

		Firma f;

		JsonReader reader = null;
		CipherInputStream cis = null;
		synchronized (campaign.lockDataBase) {
			try {
				cis = SymmetricEncryption.decryptFileUsingKey(campaign.getDataBase(), campaign.getCampaignName());

				reader = new JsonReader(new InputStreamReader(cis, "UTF-8"));

				reader.setLenient(true);
				while (reader.hasNext()) {
					JsonToken token = reader.peek();
					if (!token.equals(JsonToken.BEGIN_OBJECT)) {
						break;
					}
					f = gson.fromJson(reader, Firma.class);
					firmas.add(f);
				}

			} finally {
				if (reader != null) {
					reader.close();
				}
				if (cis != null) {
					cis.close();
				}
			}

		}
		return firmas;

	}

	/**
	 * Gets the pdf file.
	 *
	 * @return the pdf file
	 */
	public File getPdfFile() {
		return pdfFile;
	}

	/**
	 * Gets the campaign name.
	 *
	 * @return the campaign name
	 */
	public String getCampaignName() {
		return campaignName;
	}

	


}
