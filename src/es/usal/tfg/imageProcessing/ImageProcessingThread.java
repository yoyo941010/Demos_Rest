/*
 * Archivo: ImageProcessingThread.java 
 * Proyecto: Demos_Rest
 * 
 * Autor: Aythami Estévez Olivas
 * Email: aythae@gmail.com
 * Fecha: 04-jul-2016
 * Repositorio GitHub: https://github.com/AythaE/Demos_Rest
 */
package es.usal.tfg.imageProcessing;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.opencv.core.Mat;
import org.opencv.core.Size;


/**
 * Clase ImageProcessingThread usada para poder hacer la detección y el OCR de
 * los DNI en paralelo con las fotografías de ambas caras. 
 * <p>
 * Es instanciada por {@link ImageProcessing} la cual coordina todo el 
 * procesamiento de los DNI.
 */
public class ImageProcessingThread implements Runnable {

	/**
	 * The Enum CaraDni.
	 */
	public enum CaraDni {
		/** The frontal. */
		FRONTAL, 
		/** The posterior. */
		POSTERIOR};

	/**
	 * Semaforo usado para controlar cuando han acabado ambos hilos, antes de
	 * finalizar estos realizan un {@link Semaphore#release()} y la clase
	 * {@link ImageProcessing} realiza un 
	 * {@link Semaphore#tryAcquire(int, long, java.util.concurrent.TimeUnit)} 
	 * de dos unidades.
	 */
	private static Semaphore semaforo = new Semaphore(0);

	/**
	 * The Constant MAX_OCR que controla el número máximo de intentos del
	 * proceso OCR antes de declarar un OCR como fallido.
	 */
	private static final int MAX_OCR = 10;

	/**
	 * The Constant MAX_DETECTION_ATTEMPTS que controla el número máximo de
	 * intentos de detección del DNI antes de declarar una dectección como
	 * fallida.
	 */
	private static final int MAX_DETECTION_ATTEMPTS= 30;
	
	
	/** Imagen dni sobre la que se debe trabajar */
	private Mat dni;
	
	/** Indica la cara del DNI que tiene que procesar esta instancia. */
	private CaraDni cara;
	
	/**
	 * Instancia de {@link ImageProcessing} necesaria por contener los métodos
	 * de procesado de imagen y OCR.
	 */
	private ImageProcessing imProcessing;
	
	/** Imagen final del DNI cortado. */
	private Mat dniCortado;
	
	/** Número de DNI extraido de la cara frontal mediante OCR. */
	private String numDni;
	
	/** Nombre de DNI extraido de la cara posterior mediante OCR. */
	private String nombre;
	
	/** Apellidos del DNI extraidos de la cara posterior mediante OCR. */
	private String apellidos;
	
	/** 
	 * Flag usado para determinar si ha finalizado el procesamiento de DNI con
	 * exito. 
	 */
	private boolean exito;
	
	/**
	 * Crea una nueva instancia de esta clase con los parametros que se le 
	 * pasan e instanciando el resto de ellos (que son parametros de salida)
	 *
	 * @param dni la imagen del DNI inicial
	 * @param imProcessing Instancia de la clase {@link ImageProcessing}
	 * @param cara Cara del DNI que se deberá procesar
	 */
	public ImageProcessingThread(Mat dni, ImageProcessing imProcessing, CaraDni cara) {
		
		this.dni = dni;
		this.imProcessing = imProcessing;
		this.cara = cara;
		this.dniCortado = new Mat();
		this.exito=true;
		this.numDni =new String();
		this.nombre = new String();
		this.apellidos = new String();
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		Size s;
		double aspectRatio = -1;
		double correccionThresh = 0;
		Mat dniThresh = new Mat(), dniCortadoOCR= new Mat();
		
		
		s = new Size(dni.size().width/4, dni.size().height/4);
		
		
		
		//Trying to detect the dni until the detected contour has a similar aspect ratio than the original dni
		do {
			if ((aspectRatio = imProcessing.detectaDni(dni, s, ImageProcessing.THRESHOLD_THRESH-correccionThresh)) == -1) {
				return;
			}
			
			if ((aspectRatio > ImageProcessing.RELACIONDEASPECTO + ImageProcessing.MARGENRATIO || aspectRatio < ImageProcessing.RELACIONDEASPECTO - ImageProcessing.MARGENRATIO)) {
				
				if (ImageProcessing.DEBUG) {
					System.err.println("["+new Date().toString()+"] ImageProcessingThread "+cara+
							": Error detectando el dni. Valor Thresh: "+ (ImageProcessing.THRESHOLD_THRESH-correccionThresh));
					
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
				
				dniCortadoOCR = imProcessing.cropPreOCRFront(dniCortado);
				do {
					dniThresh = imProcessing.imageProcessingPreOCR(dniCortadoOCR,
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

					for (int i = 1; i <= ocr.length; i++) {
						numDni = ocr[ocr.length - i].replaceAll("\\s","");
						
						int tamañoLinea = numDni.length();

						if (tamañoLinea > 9) {
							for (int j = 0; j <=(tamañoLinea - 9); j++) {
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

				dniCortadoOCR = imProcessing.cropPreOCRBack(dniCortado);
				do {
					dniThresh = imProcessing.imageProcessingPreOCR(dniCortadoOCR,
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

					
					for (int i = 1; i <= ocr.length; i++) {
						
						
						nombreYApellidos = ocr[ocr.length - i].split("<");
						
						if (nombreYApellidos.length > 2) {
	
							for (int j = 0; j < nombreYApellidos.length; j++) {
	
								if (!nombreYApellidos[j].equals("")) {
									if (nombreYApellidos[j].contains(" ")) {
										String [] temp = nombreYApellidos[j].split(" ");
										nombreYApellidos[j] = temp[temp.length-1];
									}
									nombreYApellidosLimpio.add(nombreYApellidos[j]);
	
								} else {
									//nos encontramos ante la separacion entre nombre y apellidos
									nombreYApellidosLimpio.add(nombreYApellidos[j + 1]);
									break;
								}
							}
						}
						if (!nombreYApellidosLimpio.isEmpty()) {
							boolean repetir = false;
							for (String elemento : nombreYApellidosLimpio) {
								if (elemento == null ||!elemento.matches("[A-Z]+") ) {
									
									repetir = true;
								}
							}
							if (!repetir) {
								break;
							}
						}
					}
					
					if (!nombreYApellidosLimpio.isEmpty()) {
						for (String elemento : nombreYApellidosLimpio) {
							if (elemento == null ||!elemento.matches("[A-Z]+") ) {
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
						//TODO constante correccion con valor 4 aqui y en dni ocr
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
		ImageProcessingThread.semaforo.release();
		
		
	}

	/**
	 * Gets the dni cortado.
	 *
	 * @return the dni cortado
	 */
	public Mat getDniCortado() {
		return dniCortado;
	}

	/**
	 * Gets the num dni.
	 *
	 * @return the num dni
	 */
	public String getNumDni() {
		return numDni;
	}

	/**
	 * Gets the nombre.
	 *
	 * @return the nombre
	 */
	public String getNombre() {
		return nombre;
	}

	/**
	 * Gets the apellidos.
	 *
	 * @return the apellidos
	 */
	public String getApellidos() {
		return apellidos;
	}

	/**
	 * Gets the semaforo.
	 *
	 * @return the semaforo
	 */
	public static Semaphore getSemaforo() {
		return semaforo;
	}

	/**
	 * Checks if is exito.
	 *
	 * @return true, if is exito
	 */
	public boolean isExito() {
		return exito;
	}
	
}
