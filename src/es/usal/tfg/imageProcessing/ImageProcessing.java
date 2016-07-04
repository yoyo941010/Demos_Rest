/*
 * Archivo: ImageProcessing.java 
 * Proyecto: Demos_Rest
 * 
 * Autor: Aythami Estévez Olivas
 * Email: aythae@gmail.com
 * Fecha: 04-jul-2016
 * Repositorio GitHub: https://github.com/AythaE/Demos_Rest
 */
package es.usal.tfg.imageProcessing;

import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.crypto.BadPaddingException;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import es.usal.tfg.Campaign;
import es.usal.tfg.CampaignManagement;
import es.usal.tfg.files.PDFThread;
import es.usal.tfg.imageProcessing.ImageProcessingThread.CaraDni;
import es.usal.tfg.security.SymmetricEncryption;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

/**
 * Clase ImageProcessing encargada de gestionar todo el procesamiento necesario
 * para detectar el DNI en una fotograía y posteriormente realizar el proceso 
 * OCR. También se encarga de guardar esos resultados en la base de datos de la
 * campaña y guardar las fotos recortadas de forma encriptadas en el directorio
 * de su campaña.
 * 
 */
public class ImageProcessing {

	/**
	 * Constante DEBUG usada a lo largo del desarrollo para mostrar por
	 * pantalla más información sobre el procesamiento y las imagenes
	 * intermedias generadas.
	 */
	public static final boolean DEBUG = false;
	
	
	/**
	 * Constante DETECTION_TIMEOUT que determina el tiempo máximo que se
	 * esperará a que las instancias de {@link ImageProcessingThread} hayan
	 * finalizado el proceso de detección y OCR, tras este tiempo se entiende
	 * que ha fallado el proceso.
	 */
	public static final int DETECTION_TIMEOUT = 30;
	
	/** 
	 * Constante RELACIONDEASPECTO que determina la proporción entre el ancho
	 * y el alto que tiene un DNI. 
	 */
	public static final double RELACIONDEASPECTO = 1.581481481;
	
	/** 
	 * Constante MARGENRATIO que indica un margen de variación de 
	 * {@link ImageProcessing#RELACIONDEASPECTO} aceptable dentro del cual
	 * el objeto detectado puede ser un DNI. 
	 */
	public static final double MARGENRATIO = 0.1;
	
	/** 
	 * Constante CORRECTO usada como valor de retorno para identificar cuando
	 * el procesamiento ha finalizado correctamente. 
	 */
	public static final int CORRECTO = 0;
	

	/** 
	 * Constante ERROR_INTERNO usada como valor de retorno para identificar 
	 * cuando el procesamiento ha finalizado debido a un error interno 
	 * (indeterminado). 
	 */
	public static final int ERROR_INTERNO = 1;
	
	/** 
	 * Constante ERROR_TIMEOUT usada como valor de retorno para identificar 
	 * cuando el procesamiento ha finalizado debido a que se ha cumpldo el 
	 * timeout sin que el procesamiento haya concluido. 
	 */
	public static final int ERROR_TIMEOUT = 2;
	
	/** 
	 * Constante ERROR_FRONTAL usada como valor de retorno para identificar 
	 * cuando el procesamiento ha finalizado debido a un error detectado el DNI
	 *  o realizando el OCR de la cara frontal del DNI
	 */
	public static final int ERROR_FRONTAL = 3;
	

	/** 
	 * Constante ERROR_POSTERIOR usada como valor de retorno para identificar 
	 * cuando el procesamiento ha finalizado debido a un error detectado el DNI
	 *  o realizando el OCR de la cara posterior del DNI
	 */
	public static final int ERROR_POSTERIOR = 4;
	
	/** 
	 * Constante ERROR_AMBOS usada como valor de retorno para identificar 
	 * cuando el procesamiento ha finalizado debido a un error detectado el DNI
	 *  o realizando el OCR de ambas caras del DNI
	 */
	public static final int ERROR_AMBOS = 5;
	

	/** 
	 * Constante THRESHOLD_OCR que marca el valor inicial de threshold para
	 * {@link ImageProcessing#imageProcessingPreOCR(Mat, double)}. 
	 */
	public static final int THRESHOLD_OCR = 90;
	
	/** 
	 * Constante THRESHOLD_THRESH que marca el valor inicial de threshold para
	 * {@link ImageProcessing#detectaDni(Mat, Size, double)}. 
	 */
	public static final int THRESHOLD_THRESH = 142;
	

	/** {@link RotatedRect} que representa el area detectad como DNI. */
	private RotatedRect rectangulo;
	
	/** 
	 * Ángulo de {@link ImageProcessing#rectangulo} sobre el que se aplica
	 * la corrección y se emplea para girar la imagen del DNI. 
	 */
	private double correctedAngle;
	
	/** La campaña a la que está asociado este DNI. */
	private Campaign campaign;
	
	/** Número de la hoja de firmas que tiene este DNI. */
	private long numSignPaper;
	
	/**
	 * Bloque estatico (que se ejecuta una única vez independientemente del 
	 * número de instancias) empleado para cargar la libería nativa 
	 * de OpenCV: libopencv_java310.so, una vez cargada se pueden usar todas sus
	 * funcionalidades.
	 */
	static{
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
	}
	
	/**
	 * Crea una nueva instancia de {@link ImageProcessing}.
	 *
	 * @param c la cammpaña de los DNI que se pretende detectar
	 * @param numSignPaper el numero de la hoja de firmas de dichos DNI
	 */
	public ImageProcessing (Campaign c, long numSignPaper)
	{
		this.campaign = c;
		this.rectangulo = new RotatedRect();
		this.correctedAngle = 0;
		this.numSignPaper = numSignPaper;
		
		
	}
	
	/**
	 * Método principal de la clase, recibe dos {@link File} que representan 
	 * las dos fotografías (ambas caras del DNI) sobre las que se trabajara.
	 * <p>
	 * Se encarga de crear los {@link ImageProcessingThread} para detectar cara, 
	 * recoger sus resultados, crear una instancia de {@link Firma} con ellos, 
	 * guardar en la base de datos de la campaña dicha firma así como las 
	 * imagenes de los DNI cortados.
	 *
	 * @param dniFrontal
	 *            the dni frontal
	 * @param dniPosterior
	 *            the dni posterior
	 *            
	 * @return {@link ImageProcessing#CORRECTO}, 
	 * {@link ImageProcessing#ERROR_INTERNO}, 
	 * {@link ImageProcessing#ERROR_AMBOS},  
	 * {@link ImageProcessing#ERROR_FRONTAL}, 
	 * {@link ImageProcessing#ERROR_POSTERIOR} o
	 * {@link ImageProcessing#ERROR_TIMEOUT}  
	 * 
	 * @see <a href=
	 *      "http://opencvexamples.blogspot.com/2013/09/find-contour.html">
	 *      Finding contours</a>
	 * @see <a href=
	 *      "http://docs.opencv.org/2.4/doc/tutorials/imgproc/shapedescriptors/bounding_rotated_ellipses/bounding_rotated_ellipses.html">
	 *      Bounding RotatedRect</a>
	 * 
	 * @see <a href=
	 *      "http://felix.abecassis.me/2011/10/opencv-bounding-box-skew-angle/">
	 *      Detect Skew angle</a>
	 * 
	 * @see <a href=
	 *      "http://felix.abecassis.me/2011/10/opencv-rotation-deskewing/">
	 *      Correct Skew Angle</a>
	 * 
	 */
	
	
	public int imageProcessingAndOCR(File dniFrontal, File dniPosterior) {

		
	
		Mat  dniCortadoFrontal = new Mat(), dniCortadoPosterior = new Mat();

		System.out.println("["+new Date().toString()+"] imageProcessingAndOCR "+campaign.getCampaignName()+": Iniciado");
		
		// Loading the Image
		Mat dni = Imgcodecs.imread(dniFrontal.getAbsolutePath(), Imgcodecs.CV_LOAD_IMAGE_UNCHANGED);
		Mat dniDetras = Imgcodecs.imread(dniPosterior.getAbsolutePath(), Imgcodecs.CV_LOAD_IMAGE_UNCHANGED);
		if (dni.empty() == true || dniDetras.empty() == true) {
			System.err.println("["+new Date().toString()+"] imageProcessingAndOCR "+campaign.getCampaignName()+": Error abriendo las imagenes");
			return ERROR_INTERNO;
		}
		
		ImageProcessingThread hFrontal = new ImageProcessingThread(dni, this, CaraDni.FRONTAL);
		ImageProcessingThread hPosterior = new ImageProcessingThread(dniDetras, new ImageProcessing(campaign, numSignPaper), CaraDni.POSTERIOR);
	
		new Thread(hFrontal).start();
		new Thread(hPosterior).start();
		
		try {
			if (ImageProcessingThread.getSemaforo().tryAcquire(2, DETECTION_TIMEOUT, TimeUnit.SECONDS)==false) {
				System.err.println("["+new Date().toString()+"] imageProcessingAndOCR "+campaign.getCampaignName()+": Ha pasado el timeout de "+DETECTION_TIMEOUT+
						" segundos");
				
				return ERROR_TIMEOUT;
			}
		} catch (InterruptedException e1) {
			System.err.println("["+new Date().toString()+"] imageProcessingAndOCR "+campaign.getCampaignName()+": Error esperando el procesamiento");
			e1.printStackTrace();
			
			return ERROR_INTERNO;
		}
		if (!hFrontal.isExito() || !hPosterior.isExito()) {
			//Alguno de los hilos no ha logrado detectar texto
			
			System.err.println("["+new Date().toString()+"] imageProcessingAndOCR "+campaign.getCampaignName()+": Alguno de los hilos ha fallado en su tarea");
			System.err.println("["+new Date().toString()+"] imageProcessingAndOCR "+campaign.getCampaignName()+": Exito hilo frontal: "+hFrontal.isExito());
			System.err.println("["+new Date().toString()+"] imageProcessingAndOCR "+campaign.getCampaignName()+": Exito hilo posterior: "+hPosterior.isExito());
			
			if (!hFrontal.isExito() && !hPosterior.isExito())
			{
				return ERROR_AMBOS;
			}
			else if (!hFrontal.isExito()) {
				return ERROR_FRONTAL;
			}
			else if (!hPosterior.isExito()) {
				return ERROR_POSTERIOR;
			}
		}
		
		String numDni = hFrontal.getNumDni();
		String nombre = hPosterior.getNombre();
		String apellidos = hPosterior.getApellidos();
		
		dniCortadoFrontal = hFrontal.getDniCortado();
		dniCortadoPosterior = hPosterior.getDniCortado();
		
		
		System.out.println("["+new Date().toString()+"] imageProcessingAndOCR "+campaign.getCampaignName()+": Detección y OCR correctos, "
				+ "falta escribir los resultados en disco");
		long tIni =0, tfin=0;
		tIni = System.currentTimeMillis();
		SaveEncryptedImage sFrontal = new SaveEncryptedImage(dniCortadoFrontal, dniFrontal, campaign), 
				sPosterior = new SaveEncryptedImage(dniCortadoPosterior, dniPosterior, campaign);
		
		Thread tF = new Thread(sFrontal), tP = new Thread(sPosterior);
		tF.start(); tP.start();
		
		try {
			tF.join(0);
			tP.join(0);
		} catch (InterruptedException e1) {
			System.err.println("["+new Date().toString()+"] imageProcessingAndOCR "+campaign.getCampaignName()+": Error guardando las imagenes encriptadas");
			
			e1.printStackTrace();
		}
		
		if (!sFrontal.isExito() || !sPosterior.isExito()) {
			//Alguno de los hilos no ha logrado detectar texto
			
			System.err.println("["+new Date().toString()+"] imageProcessingAndOCR "+campaign.getCampaignName()+": "
					+ "Alguno de los hilos que guardan las imagenes ha fallado en su tarea");
			System.err.println("["+new Date().toString()+"] imageProcessingAndOCR "+campaign.getCampaignName()+": "
					+ "Exito hilo frontal: "+sFrontal.isExito());
			System.err.println("["+new Date().toString()+"] imageProcessingAndOCR "+campaign.getCampaignName()+": "
					+ "Exito hilo posterior: "+sPosterior.isExito());
			
			if (!sFrontal.isExito() && !sPosterior.isExito())
			{
				return ERROR_AMBOS;
			}
			else if (!sFrontal.isExito()) {
				return ERROR_FRONTAL;
			}
			else if (!sPosterior.isExito()) {
				return ERROR_POSTERIOR;
			}
		}
		tfin = System.currentTimeMillis();
		System.out.println("["+new Date().toString()+"] imageProcessingAndOCR "+campaign.getCampaignName()+": "
				+ "Tiempo total guardando encriptado: " + ((double)(tfin-tIni) / 1000)+ " segundos");
		
		long numFirmas;
		synchronized (campaign) {
			numFirmas = campaign.getNumeroFirmas() + 1;
			campaign.setNumeroFirmas(numFirmas);
		}
		
		//Entran 5 firmas por cada hoja
		long numHojaDNI = numFirmas/ PDFThread.NUMERO_DNI_X_HOJA;
		
		//Si el modulo es distinto de 0 significa que tenemos que sumar otra hoja
		if (numFirmas % PDFThread.NUMERO_DNI_X_HOJA > 0) {
			numHojaDNI++;
		}
		
		Firma firma = new Firma(dniFrontal.getAbsoluteFile(), dniPosterior.getAbsoluteFile(), nombre, apellidos, numDni,
				numSignPaper, numHojaDNI);

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
	
		
		File signaturesDB = campaign.getDataBase();
		BufferedWriter wr=null;
		CipherOutputStream cos = null;
		try {
			
			synchronized (campaign.lockDataBase) {
				cos = SymmetricEncryption.appendAES(signaturesDB, campaign.getCampaignName());
				wr = new BufferedWriter(new OutputStreamWriter(cos));
				gson.toJson(firma, wr);
				
				wr.close();
				wr = null;
			}
			
			
			
		} catch (IOException | InvalidKeyException | IllegalArgumentException | KeyStoreException
				| NoSuchAlgorithmException | CertificateException | UnrecoverableEntryException
				| InvalidAlgorithmParameterException | NoSuchPaddingException | IllegalBlockSizeException
				| BadPaddingException e) {
			System.err.println("["+new Date().toString()+"] imageProcessingAndOCR "+campaign.getCampaignName()+": "
					+ "Error escribiendo la firma en la base de datos de la campaña");
			e.printStackTrace();
			return ERROR_INTERNO;
		}
		finally {
			try {
				if (wr != null)
				{
					wr.flush();
					wr.close();
				}
			} catch (IOException e) {}
		}
		
		System.out.println("["+new Date().toString()+"] imageProcessingAndOCR "+campaign.getCampaignName()+": "
				+ "Exito escribiendo los resultados en disco");
		return CORRECTO;
		
	}

	
	/**
	 * Intenta detectar el DNI usando el parametro thresh para 
	 * {@link Imgproc#threshold(Mat, Mat, double, double, int)}.
	 *
	 * @param dni El dni sobre el que trabajar
	 * @param s el tamaño usado para la debugeación solo
	 * @param thresh el valor thresh
	 * @return la relacion de aspecto del objeto encontrado o -1 si no se ha 
	 * encontrado nada 
	 */
	double detectaDni(Mat dni, Size s, double thresh) {
		Mat dniGauss = new Mat(), dniThresh = new Mat(), dniResize = new Mat(), 
				dniGray = new Mat(), dniContours = new Mat();
		double largestArea = 0;
		int largestAreaIndex = -1;
		

		// Convert to gray scale image
		Imgproc.cvtColor(dni, dniGray, Imgproc.COLOR_BGR2GRAY);

		// filter the image to eliminate noise
		Imgproc.GaussianBlur(dniGray, dniGauss, new Size(3, 3), 0.5, 1.5);
		
		Imgproc.threshold(dniGauss, dniThresh, thresh, 255, Imgproc.THRESH_BINARY_INV);

		if (DEBUG) {
			Imgproc.resize(dniThresh, dniResize, s);
			displayImage(Mat2BufferedImage(dniResize), "Imagen threshold y filtrada");
		}

		
		// Find contours
		// http://opencvexamples.blogspot.com/2013/09/find-contour.html
		List<MatOfPoint> contours = new ArrayList<>();
		Mat hierarchy = new Mat();
		Imgproc.findContours(dniThresh, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE,
				new Point(0, 0));

		dni.copyTo(dniContours);

		// if contours exists
		if (contours.size() > 0) {

			// Detection of the larger contour, that should be the DNI
			for (int i = 0; i < contours.size(); i++) {
				double a = Imgproc.contourArea(contours.get(i), false);

				if (a > largestArea) {
					largestArea = a;
					largestAreaIndex = i;

				}

			}
		} else {
			System.err.println("contornos no encontrados");
			return -1;
		}

		MatOfPoint2f correctArea = new MatOfPoint2f(contours.get(largestAreaIndex).toArray());

		rectangulo = Imgproc.minAreaRect(correctArea);


		// Angle correction in case than the angle was calculated using a
		// "vertical" rectangle
		// due to the lack of reference rectangle
		correctedAngle = rectangulo.angle;
		if (correctedAngle < -45) {
			correctedAngle += 90;

			// Swap width and height
			double temp = rectangulo.size.height;
			rectangulo.size.height = rectangulo.size.width;
			rectangulo.size.width = temp;

		}

		// The DNI is rotated 90º
		if (rectangulo.size.width < rectangulo.size.height) {

			//Don't know if is rotated +90 or -90 so turn to the left 90º
			correctedAngle += 90;

			// Swap width and height
			double temp = rectangulo.size.height;
			rectangulo.size.height = rectangulo.size.width;
			rectangulo.size.width = temp;
		}
		
		double aspectRatio = rectangulo.size.width / rectangulo.size.height;
		
		if (DEBUG) {
			System.out.println("\n\nAngulo: " + correctedAngle + "\tAngulo sin corregir: " + rectangulo.angle);
			System.out.println("Ancho: " + rectangulo.size.width + "\tAlto: " + rectangulo.size.height);
			System.out.println("Relacion de aspecto: " + aspectRatio);
		}
		
		
		return aspectRatio;
	}

	/**
	 * Corrige el ángulo del DNI y lo recorta de la imagen
	 *
	 * @param dni el DNI
	 * @return el Dni recortado y girado
	 */
	Mat rotateAndCropDni (Mat dni)
	{
		Mat dniResize = new Mat(), rotationMat = new Mat(), dniRotated = new Mat(), dniCortado = new Mat();
		
		//Now copy the original image to a temp Mat which has the double size and add dni.size/2 padding
		//to rotate the image without loss of data
		Size rotatedSize = new Size(dni.cols()*2, dni.rows()*2);
		
		Mat temp = new Mat(rotatedSize, CvType.CV_8SC3);
		
		Core.copyMakeBorder(dni, temp, dni.rows()/2, dni.rows()/2, dni.cols()/2, dni.cols()/2, Core.BORDER_CONSTANT, new Scalar(0, 0, 0));
		
		if (DEBUG) {
			Imgproc.resize(temp, dniResize, new Size(rotatedSize.width / 8 , rotatedSize.height / 8));
			displayImage(Mat2BufferedImage(dniResize), "pre-girado");
		}
		
		
		// Obtain the rotation matrix from the RotatedRect, its necessary to correct the bounding box center
		//due to the padding added in the previous step
		rotationMat = Imgproc.getRotationMatrix2D(new Point(rectangulo.center.x + dni.cols()/2, rectangulo.center.y + dni.rows()/2), correctedAngle, 1);
				
		// Rotate the image
		Imgproc.warpAffine(temp, dniRotated, rotationMat, rotatedSize, Imgproc.INTER_CUBIC);

		if (DEBUG) {
			Imgproc.resize(dniRotated, dniResize, new Size(dniRotated.size().width / 8 , dniRotated.size().height / 8));
			displayImage(Mat2BufferedImage(dniResize), "Girado");
		}

		// Extended size to prevent that the RotatedRect cut the DNI
		Size extendedSize = new Size(rectangulo.size.width + (dni.size().width / 80) * 2,
				rectangulo.size.height + (dni.size().height / 80) * 2);	//Antes size /100
		


		// Crop the DNI from the rotated image
		Imgproc.getRectSubPix(dniRotated, extendedSize, new Point(rectangulo.center.x + dni.cols()/2, rectangulo.center.y + dni.rows()/2), dniCortado);
		
		
		if (DEBUG) {
			Imgproc.resize(dniCortado, dniResize, new Size(dniCortado.size().width / 2, dniCortado.size().height / 2));
			displayImage(Mat2BufferedImage(dniResize), "Cortado");
		}
		return dniCortado;
	}
	
	/**
	 * Corta la parte necesaria para realizar el OCR en la cara frontal del DNI
	 *
	 * @param dni el dni frontal
	 * @return la parte que contiene el número
	 */
	Mat cropPreOCRFront(Mat dni)
	{
		Mat dniCortado = new Mat(), dniResize = new Mat();

		Size newSize = new Size(dni.cols(), dni.rows()/3);

		// Crop the DNI from the rotated image
		Imgproc.getRectSubPix(dni, newSize, new Point((double)dni.cols()/2, 5*((double)dni.rows())/6), dniCortado);
		if (DEBUG) {
			Imgproc.resize(dniCortado, dniResize, new Size(dniCortado.size().width / 2, dniCortado.size().height / 2));
			displayImage(Mat2BufferedImage(dniResize), "CortadoPreOCR");
		}
		return dniCortado;
	}
	
	/**
	 * Corta la parte necesaria para realizar el OCR en la cara posterior del DNI
	 *
	 * @param dni el dni posterior
	 * @return la parte que contiene el nombre y apellidos
	 */
	Mat cropPreOCRBack(Mat dni)
	{
		Mat dniCortado = new Mat(), dniResize = new Mat();

		Size newSize = new Size(dni.cols(), dni.rows()/3);

		// Crop the DNI from the rotated image
		Imgproc.getRectSubPix(dni, newSize, new Point((double)dni.cols()/2, 3*((double)dni.rows())/4), dniCortado);
		if (DEBUG) {
			Imgproc.resize(dniCortado, dniResize, new Size(dniCortado.size().width / 2, dniCortado.size().height / 2));
			displayImage(Mat2BufferedImage(dniResize), "CortadoPreOCR");
		}
		return dniCortado;
	}
	
	/**
	 * Procesado de imagen Pre OCR con el objetivo de producir una imagen 
	 * binaria (en blanco y negro) para favorecer el proceso OCR posterior
	 *
	 * @param dniCortado el dni cortado
	 * @param valorThresh valor del thresh de 
	 * {@link Imgproc#threshold(Mat, Mat, double, double, int)}
	 * @return la imagen binarizada
	 */
	Mat imageProcessingPreOCR (Mat dniCortado, double valorThresh)
	{
		
		Mat dniGray = new Mat(), dniGauss= new Mat(), dniThresh = new Mat(), dniResize = new Mat();
		// Convert to grayscale image
		Imgproc.cvtColor(dniCortado, dniGray, Imgproc.COLOR_BGR2GRAY);

		// Filter to eliminate noise
		Imgproc.GaussianBlur(dniGray, dniGauss, new Size(3, 3), 0.5, 1.5);

		Imgproc.threshold(dniGauss, dniThresh, valorThresh, 255, Imgproc.THRESH_BINARY);
		
		if(DEBUG){
			Imgproc.resize(dniThresh, dniResize, new Size(dniCortado.size().width / 2, dniCortado.size().height / 2));
			displayImage(Mat2BufferedImage(dniResize), "Pre canny");
		}
		return dniThresh;
	}
	
	/**
	 * Realiza el OCR de la imagen m con la configuración config llamando para 
	 * ello a {@link ITesseract#doOCR(BufferedImage)}
	 *
	 * @param m la imagen
	 * @param config la configuracion
	 * @return el Array de {@link String} detectados en la imagen donde cada 
	 * elemento es una linea diferente
	 */
	String[] OCRMat (Mat m, List<String> config)
	{
		
		ITesseract instance = new Tesseract(); // JNA Interface Mapping
		instance.setDatapath(CampaignManagement.WEBSERVICE_ABSOLUTE_ROUTE);

		if (config != null) {
			instance.setConfigs(config);
		}
	
		instance.setLanguage("spa");
		try {
			String result = instance.doOCR(Mat2BufferedImage(m));
			String[] resultados = result.split("\n");
			return resultados;
		} catch (TesseractException e) {
			System.err.println("["+new Date().toString()+"] OCRMat "+campaign.getCampaignName()+": "
					+ "Error en el proceso OCR");
			e.printStackTrace();
			return null;
		}
	}
 	
	/**
	 * Método auxiliar para convertir un objeto {@link Mat} a 
	 * {@link BufferedImage}
	 *
	 * @param m el objeto {@link Mat} 
	 * @return la conversión a {@link BufferedImage}
	 */
	public static BufferedImage Mat2BufferedImage(Mat m) {
		// source:
		// http://answers.opencv.org/question/10344/opencv-java-load-image-to-gui/
		// Fastest code
		// The output can be assigned either to a BufferedImage or to an Image

		int type = BufferedImage.TYPE_BYTE_GRAY;
		if (m.channels() > 1) {
			type = BufferedImage.TYPE_3BYTE_BGR;
		}
		int bufferSize = m.channels() * m.cols() * m.rows();
		byte[] b = new byte[bufferSize];
		m.get(0, 0, b); // get all the pixels
		BufferedImage image = new BufferedImage(m.cols(), m.rows(), type);
		final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
		System.arraycopy(b, 0, targetPixels, 0, b.length);
		return image;

	}

	/**
	 * Método auxiliar usado para mostrar una imagen por pantalla en el modo 
	 * DEBUG.
	 *
	 * @param img2 la imagen
	 * @param title el título de la ventana
	 */
	public static void displayImage(Image img2, String title) {

		ImageIcon icon = new ImageIcon(img2);
		JFrame frame = new JFrame();
		frame.setLayout(new FlowLayout());
		frame.setSize(img2.getWidth(null) + 50, img2.getHeight(null) + 50);
		frame.setTitle(title);
		JLabel lbl = new JLabel();
		lbl.setIcon(icon);
		frame.add(lbl);
		frame.setVisible(true);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

	}
	
}
 

