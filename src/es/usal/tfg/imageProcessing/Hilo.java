package es.usal.tfg.imageProcessing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.opencv.core.Mat;
import org.opencv.core.Size;

import com.lowagie.text.html.simpleparser.Img;

public class Hilo implements Runnable {

	public enum CaraDni {FRONTAL, POSTERIOR};
	
	private static Semaphore semaforo = new Semaphore(0);
	
	private static final int MAX_OCR = 5;
	private static final int MAX_DETECTION_ATTEMPTS= 30;
	
	
	private Mat dni;
	private CaraDni cara;
	private ImageProcessing imProcessing;
	private Mat dniCortado;
	private String numDni;
	private String nombre;
	private String apellidos;
	private boolean exito;
	
	public Hilo(Mat dni, ImageProcessing imProcessing, CaraDni cara) {
		
		this.dni = dni;
		this.imProcessing = imProcessing;
		this.cara = cara;
		this.dniCortado = new Mat();
		this.exito=true;
		this.numDni =new String();
		this.nombre = new String();
		this.apellidos = new String();
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		Size s;
		double aspectRatio = -1;
		double correccionThresh = 0;
		Mat dniThresh = new Mat();
		
		
		s = new Size(dni.size().width/4, dni.size().height/4);
		
		
		
		//Trying to detect the dni until the detected contour has a similar aspect ratio than the original dni
		do {
			if ((aspectRatio = imProcessing.detectaDni(dni, s, ImageProcessing.THRESHOLD_THRESH-correccionThresh)) == -1) {
				return;
			}
			
			if ((aspectRatio > ImageProcessing.RELACIONDEASPECTO + ImageProcessing.MARGENRATIO || aspectRatio < ImageProcessing.RELACIONDEASPECTO - ImageProcessing.MARGENRATIO)) {
				
				if (ImageProcessing.DEBUG) {
					System.err.println("Error detectando el dni. Valor Thresh: "+ (ImageProcessing.THRESHOLD_THRESH-correccionThresh));
					
					imProcessing.getSc().nextLine();
				}
				if (correccionThresh/2 >= MAX_DETECTION_ATTEMPTS) {
					exito = false;
					break;
				}
				correccionThresh = correccionThresh+2;
			}
			
		} while (aspectRatio > ImageProcessing.RELACIONDEASPECTO + ImageProcessing.MARGENRATIO || aspectRatio < ImageProcessing.RELACIONDEASPECTO - ImageProcessing.MARGENRATIO);
		
		
		if (exito) {
			dniCortado = imProcessing.rotateAndCropDni(dni);
			String[] ocr;
			List<String> config = new ArrayList<>();
			if (cara == CaraDni.FRONTAL) {

				config.add("numdni");
			} else if (cara == CaraDni.POSTERIOR) {
				config.add("letters");
			}
			String[] nombreYApellidos;
			ArrayList<String> nombreYApellidosLimpio = new ArrayList<>();
			correccionThresh = 0;
			if (cara == CaraDni.FRONTAL) {

				do {
					dniThresh = imProcessing.imageProcessingPreOCR(dniCortado,
							ImageProcessing.THRESHOLD_OCR - correccionThresh);
					if ((ocr = imProcessing.OCRMat(dniThresh, config)) == null) {
						return;
					}
					if (ImageProcessing.DEBUG) {
						System.out.println("\n\nOCR " + cara.toString());
						for (int i = 0; i < ocr.length; i++) {
							String string = ocr[i];
							System.out.println(string + "\n");
						}
					}

					if (ocr.length > 5) {

						for (int i = 1; i < 5; i++) {
							numDni = ocr[ocr.length - i].replace(" ", "");
							int tamañoLinea = numDni.length();

							if (tamañoLinea > 9) {
								for (int j = 0; j < (tamañoLinea - 9); j++) {
									String numDniTemp = numDni.substring(j, j + 9);
									
									if (numDniTemp.matches("\\d{8}[A-Z]")) {
										numDni = numDniTemp;
										break;
									}
								}
							}

							if (numDni.matches("\\d{8}[A-Z]")) {
								break;
							}
						}
					}

					if (numDni.isEmpty() || !numDni.matches("\\d{8}[A-Z]")) {
						if (ImageProcessing.DEBUG) {
							System.err.println("Error en el OCR, valor de Thresh: "
									+ (ImageProcessing.THRESHOLD_OCR - correccionThresh));
						}
						if (correccionThresh / 2 >= MAX_OCR) {
							exito = false;
							break;
						}
						correccionThresh += 2;
					}

				} while (numDni.isEmpty() || !numDni.matches("\\d{8}[A-Z]"));
				if (ImageProcessing.DEBUG) {
					System.out.println("numero de dni: " + numDni);
					if (!numDni.isEmpty()) {
						System.out.println("coincide con el patron: " + numDni.matches("\\d{8}[A-Z]"));
					}

				}

			}

			else if (cara == CaraDni.POSTERIOR) {

				do {
					dniThresh = imProcessing.imageProcessingPreOCR(dniCortado,
							ImageProcessing.THRESHOLD_OCR - correccionThresh);
					if ((ocr = imProcessing.OCRMat(dniThresh, config)) == null) {
						return;
					}
					if (ImageProcessing.DEBUG) {
						System.out.println("\n\nOCR " + cara.toString());
						for (int i = 0; i < ocr.length; i++) {
							String string = ocr[i];
							System.out.println(string + "\n");
						}
					}

					nombreYApellidos = ocr[ocr.length - 1].split("<");
					int j = 0;

					if (nombreYApellidos.length > 2) {

						for (int i = 0; i < nombreYApellidos.length; i++) {

							if (!nombreYApellidos[i].equals("")) {

								nombreYApellidosLimpio.add(nombreYApellidos[i]);

							} else {
								//nos encontramos ante la separacion entre nombre y apellidos
								nombreYApellidosLimpio.add(nombreYApellidos[i + 1]);
								break;
							}
						}
					}
					if (!nombreYApellidosLimpio.isEmpty()) {
						for (String elemento : nombreYApellidosLimpio) {
							if (!elemento.matches("[A-Z]+") || elemento == null) {
								if (ImageProcessing.DEBUG) {
									System.err.println("Error en el OCR, valor de Thresh: "
											+ (ImageProcessing.THRESHOLD_OCR - correccionThresh));
								}

							}
						}
					} else {
						if (correccionThresh / 2 >= MAX_OCR) {
							exito = false;
							break;
						}
						correccionThresh += 2;
					}

				} while (nombreYApellidosLimpio.isEmpty() || (!nombreYApellidosLimpio.get(0).matches("[A-Z]+")
						&& !nombreYApellidosLimpio.get(1).matches("[A-Z]+")
						&& !nombreYApellidosLimpio.get(2).matches("[A-Z]+")));

				if (ImageProcessing.DEBUG) {
					System.out.println("nombre y apellidos:");
					if (nombreYApellidosLimpio.size() > 0) {
						for (int i = 0; i < nombreYApellidosLimpio.size(); i++) {

							System.out.println(nombreYApellidosLimpio.get(i));
							System.out.println(
									"coincide con el patron: " + nombreYApellidosLimpio.get(i).matches("[A-Z]+"));

						}
					}

				}
				if (nombreYApellidosLimpio.size() > 1) {
					apellidos = nombreYApellidosLimpio.get(0);
					for (int i = 1; i < nombreYApellidosLimpio.size() - 1; i++) {
						apellidos = apellidos.concat(" " + nombreYApellidosLimpio.get(i));
					}
					nombre = nombreYApellidosLimpio.get(nombreYApellidosLimpio.size() - 1);
				}
			} 
		}
		Hilo.semaforo.release();
		
		
	}

	public Mat getDniCortado() {
		return dniCortado;
	}

	public String getNumDni() {
		return numDni;
	}

	public String getNombre() {
		return nombre;
	}

	public String getApellidos() {
		return apellidos;
	}

	public static Semaphore getSemaforo() {
		return semaforo;
	}

	public boolean isExito() {
		return exito;
	}
	
}
