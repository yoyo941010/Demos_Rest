package es.usal.tfg.imageProcessing;

import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

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

import es.usal.tfg.imageProcessing.Hilo.CaraDni;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
public class ImageProcessing {

	public static final boolean DEBUG = false;
	private static final boolean TOTAL_DEBUG = false;
	
	private static final String PROJECT_ABSOLUTE_ROUTE = "/home/aythae/Escritorio/TFG/Demos_Rest";
	
	
	public static final double RELACIONDEASPECTO = 1.581481481;
	public static final double MARGENRATIO = 0.1;
	public static final int IMAGE_PROCESSING_TIMEOUT=150;
	// CAMARA 13MPX
	private static final String DNIE1NORMAL = "img/IMG_20160412_14484728.jpg";
	private static final String DNIE1VERTICAL = "img/IMG_20160412_144855203.jpg";
	private static final String DNIE1MAS90 = "img/IMG_20160412_144847286.jpg";

	// CAMARA 8MPX
	private static final String DNIE1MENOS45 = "img/IMG_20160416_140524689.jpg";
	private static final String DNIE1NORMAL2 = "img/IMG_20160416_143152822.jpg";
	private static final String DNIE1ABRASADO = "img/IMG_20160416_143142945.jpg";
	private static final String DNIE1DETRASMAS5 = "img/porDetrasP5.jpg";

	// CAMARA LAURA
	private static final String DNIE1LAU = "img/IMG_20160417_133817.jpg";
	private static final String DNIE1LAUDETRAS = "img/IMG_20160417_133839.jpg";

	//CAMARA HUAWEI Y300
	private static final String DNIE1MUYCERCA = "img/IMG_20160418_185527.jpg";
	private static final String DNIE1DETRASMUYLEJOS = "img/IMG_20160418_185543.jpg";
	private static final String DNIE1PABLO = "img/IMG_20160422_125725.jpg";
	private static final String DNIE1PABLODETRAS = "img/IMG_20160422_125734.jpg";
	
	// CAMARA 13MPX
	private static final String DNIE2NORMAL = "img/IMG_20160412_144912773.jpg";
	private static final String DNIE2MENOS90 = "img/IMG_20160412_144923366.jpg";
	private static final String DNIE2DETRASMALO = "img/IMG_20160422_154835289.jpg";
	private static final String DNIE2DETRASMALO2 = "img/IMG_20160423_145445125.jpg";
	private static final String DNIE2DETRASMALO3 = "img/DNIE2DETRAS3.jpg";
	
	public static final double CANNY_LOW_THRESHOLD = 100;
	public static final double CANNY_RATIO = 3.0;
	public static final int CANNY_KERNEL_SIZE = 3;

	public static final int THRESHOLD_OCR = 90;
	/**
	 * MOST IMPORTANT PARAMETER
	 * 
	 * if too high this detect the background as part of the ID if too low only
	 * detect a part of the ID
	 */
	public static final int THRESHOLD_THRESH = 142;
	

	private RotatedRect rectangulo;
	private double correctedAngle;
	
	private Scanner sc;
	
	
	public ImageProcessing ()
	{
		
		this.rectangulo = new RotatedRect();
		this.correctedAngle = 0;
		
		this.sc = new Scanner(System.in);
	}
	/**
	 * Poner las referencias al finding contours
	 * 
	 * @References -Finding contours:
	 *             http://opencvexamples.blogspot.com/2013/09/find-contour.html
	 *             -Bounding RotatedRect:
	 *             http://docs.opencv.org/2.4/doc/tutorials/imgproc/
	 *             shapedescriptors/bounding_rotated_ellipses/
	 *             bounding_rotated_ellipses.html
	 * 
	 *             -Detect Skew angle and correct it:
	 *             http://felix.abecassis.me/2011/10/opencv-bounding-box-skew-
	 *             angle/
	 *             http://felix.abecassis.me/2011/09/opencv-detect-skew-angle/
	 *             http://felix.abecassis.me/2011/10/opencv-rotation-deskewing/
	 */
	
	
	public boolean imageProcessingAndOCR(File dniFrontal, File dniPosterior) {

		
	
		Mat  dniCortadoFrontal = new Mat(), dniCortadoPosterior = new Mat();

		
		// Loading the Image
		Mat dni = Imgcodecs.imread(dniFrontal.getAbsolutePath(), Imgcodecs.CV_LOAD_IMAGE_UNCHANGED);
		Mat dniDetras = Imgcodecs.imread(dniPosterior.getAbsolutePath(), Imgcodecs.CV_LOAD_IMAGE_UNCHANGED);
		if (dni.empty() == true || dniDetras.empty() == true) {
			System.err.println("Error abriendo la imagen\n");
			return false;
		}
		
		Hilo hFrontal = new Hilo(dni, this, CaraDni.FRONTAL);
		Hilo hPosterior = new Hilo(dniDetras, new ImageProcessing(), CaraDni.POSTERIOR);
	
		new Thread(hFrontal).start();
		new Thread(hPosterior).start();
		
		try {
			if (Hilo.getSemaforo().tryAcquire(2, 150, TimeUnit.SECONDS)==false) {
				System.err.println("Alguno de los hilos ha fallado en su tarea");
				
				
				return false;
			}
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		if (!hFrontal.isExito() || !hPosterior.isExito()) {
			//Alguno de los hilos no ha logrado detectar texto
			
			System.err.println("Alguno de los hilos ha fallado en su tarea");
			System.err.println("Exito hilo frontal: "+hFrontal.isExito());
			System.err.println("Exito hilo posterior: "+hPosterior.isExito());
			
			
			return false;
		}
		String numDni = hFrontal.getNumDni();
		String nombre = hPosterior.getNombre();
		String apellidos = hPosterior.getApellidos();
		
		dniCortadoFrontal = hFrontal.getDniCortado();
		dniCortadoPosterior = hPosterior.getDniCortado();
		
		File dniFrontalRecordado = new File(dniFrontal.getAbsolutePath().replace(".jpg", "(recortado).jpg"));
		File dniPosteriorRecordado = new File(dniPosterior.getAbsolutePath().replace(".jpg", "(recortado).jpg"));
		
		Imgcodecs.imwrite(dniFrontalRecordado.getAbsolutePath(), dniCortadoFrontal);
		Imgcodecs.imwrite(dniPosteriorRecordado.getAbsolutePath(), dniCortadoPosterior);
		
		Firma firma = new Firma(dniFrontalRecordado.getAbsoluteFile(), dniPosteriorRecordado.getAbsoluteFile(), nombre, apellidos, numDni);
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(firma);
		System.out.println("\n"+json);
		try {
			Writer wr = new OutputStreamWriter( new FileOutputStream( PROJECT_ABSOLUTE_ROUTE+ "/file.json", true));
			gson.toJson(firma, wr);
			
			wr.flush();
			wr.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return true;
		
	}

	
	double detectaDni(Mat dni, Size s, double thresh) {
		Mat dniGauss = new Mat(), dniThresh = new Mat(), dniResize = new Mat(), 
				dniGray = new Mat(), dniContours = new Mat();
		double largestArea = 0;
		int largestAreaIndex = -1;
		// RotatedRect rectangulo;

		// Convert to gray and gaussianblur it
		Imgproc.cvtColor(dni, dniGray, Imgproc.COLOR_BGR2GRAY);

		// try normal blur filter if this doesn't works
		Imgproc.GaussianBlur(dniGray, dniGauss, new Size(3, 3), 0.5, 1.5);
		// Imgproc.blur(dniGray, dniGauss, new Size(3, 3));

		Imgproc.threshold(dniGauss, dniThresh, thresh, 255, Imgproc.THRESH_BINARY_INV);

		if (TOTAL_DEBUG) {
			Imgproc.resize(dniThresh, dniResize, s);
			displayImage(Mat2BufferedImage(dniResize), "Imagen threshold y filtrada");
		}

		// Imgproc.Canny(dniThresh, dniCanny, CANNY_LOW_THRESHOLD,
		// CANNY_LOW_THRESHOLD * 2, CANNY_KERNEL_SIZE, false);

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

		Point[] rectPoints = new Point[4];
		rectangulo.points(rectPoints);

		// Draw the bounding box in green
		if (rectPoints != null) {
			for (int i = 0; i < rectPoints.length; i++) {
				Imgproc.line(dniContours, rectPoints[i], rectPoints[(i + 1) % rectPoints.length], new Scalar(0, 255, 0),
						3);
			}
		}

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

			// I don't know if is rotated +90 or -90
			correctedAngle += 90;

			// Swap width and height
			double temp = rectangulo.size.height;
			rectangulo.size.height = rectangulo.size.width;
			rectangulo.size.width = temp;
		}
		
		double aspectRatio = rectangulo.size.width / rectangulo.size.height;
		
		if (TOTAL_DEBUG) {
			System.out.println("\n\nAngulo: " + correctedAngle + "\tAngulo sin corregir: " + rectangulo.angle);
			System.out.println("Ancho: " + rectangulo.size.width + "\tAlto: " + rectangulo.size.height);
			System.out.println("Relacion de aspecto: " + aspectRatio);
		}
		// Drawit the contour in blue
		Imgproc.drawContours(dniContours, contours, largestAreaIndex, new Scalar(255, 0, 0), 3);

		if (DEBUG) {
			Imgproc.resize(dniContours, dniResize, s);
			displayImage(Mat2BufferedImage(dniResize), "Contours");

		}
		return aspectRatio;
	}

	Mat rotateAndCropDni (Mat dni)
	{
		Mat dniResize = new Mat(), rotationMat = new Mat(), dniRotated = new Mat(), dniCortado = new Mat();
		
		//Now copy the original image to a temp Mat which has the double size and add dni.size/2 padding
		//to rotate the image without loss of data
		Size rotatedSize = new Size(dni.cols()*2, dni.rows()*2);
		
		Mat temp = new Mat(rotatedSize, CvType.CV_8SC3);
		
		Core.copyMakeBorder(dni, temp, dni.rows()/2, dni.rows()/2, dni.cols()/2, dni.cols()/2, Core.BORDER_CONSTANT, new Scalar(0, 0, 0));
		
		if (TOTAL_DEBUG) {
			Imgproc.resize(temp, dniResize, new Size(rotatedSize.width / 8 , rotatedSize.height / 8));
			displayImage(Mat2BufferedImage(dniResize), "pre-girado");
		}
		
		
		// Obtain the rotation matrix from the RotatedRect, its necessary to correct the bounding box center
		//due to the padding added in the previous step
		rotationMat = Imgproc.getRotationMatrix2D(new Point(rectangulo.center.x + dni.cols()/2, rectangulo.center.y + dni.rows()/2), correctedAngle, 1);
				
		// Rotate the image
		Imgproc.warpAffine(temp, dniRotated, rotationMat, rotatedSize, Imgproc.INTER_CUBIC);

		if (TOTAL_DEBUG) {
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
	Mat imageProcessingPreOCR (Mat dniCortado, double valorThresh)
	{
		
		Mat dniGray = new Mat(), dniGauss= new Mat(), dniThresh = new Mat(), dniResize = new Mat();
		// Convert to gray and gaussianblur it
		Imgproc.cvtColor(dniCortado, dniGray, Imgproc.COLOR_BGR2GRAY);

		// try normal blur filter if this doesn't works
		Imgproc.GaussianBlur(dniGray, dniGauss, new Size(3, 3), 0.5, 1.5);

		Imgproc.threshold(dniGauss, dniThresh, valorThresh, 255, Imgproc.THRESH_BINARY);
		
		if(DEBUG){
			Imgproc.resize(dniThresh, dniResize, new Size(dniCortado.size().width / 2, dniCortado.size().height / 2));
			displayImage(Mat2BufferedImage(dniResize), "Pre canny");
		}
		return dniThresh;
	}
	
	String[] OCRMat (Mat m, List<String> config)
	{
		
		ITesseract instance = new Tesseract(); // JNA Interface Mapping
		// ITesseract instance = new Tesseract1(); // JNA Direct Mapping
		// File tessDataFolder = LoadLibs.extractTessResources("tessdata"); //
		// Maven build bundles English data
		instance.setDatapath(PROJECT_ABSOLUTE_ROUTE);

		if (config != null) {
			instance.setConfigs(config);
		}
	
		instance.setLanguage("spa");
		try {
			String result = instance.doOCR(Mat2BufferedImage(m));
			String[] resultados = result.split("\n");
			return resultados;
		} catch (TesseractException e) {
			System.err.println(e.getMessage());
			return null;
		}
	}
 	
	public BufferedImage Mat2BufferedImage(Mat m) {
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

	public void displayImage(Image img2, String title) {

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
	public Scanner getSc() {
		return sc;
	}
}
 

