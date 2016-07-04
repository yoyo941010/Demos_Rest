/*
 * Archivo: MyTaskExecutor.java 
 * Proyecto: Demos_Rest
 * 
 * Autor: Aythami Estévez Olivas
 * Email: aythae@gmail.com
 * Fecha: 04-jul-2016
 * Repositorio GitHub: https://github.com/AythaE/Demos_Rest
 */
package es.usal.tfg;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import es.usal.tfg.files.PDFThread;

/**
 * Wrapper class to control ScheduledExecutorService taking into account the
 * daylight saving issues because it calculate the delay until the desired time
 * every day.
 *
 * @author Sage
 * @see <a href="http://stackoverflow.com/a/20388073/6441806">Referencia</a>
 */
public class MyTaskExecutor
{
	
	/** The executor scheduled encargado de ejecutar la tarea periodica. */
	private ScheduledThreadPoolExecutor executorScheduled = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1);
	
	/**
	 * The Constant NUM_PDF_THREADS que controla el número máximo de hilos PDF
	 * que puede haber simultaneamente.
	 * 
	 * @see PDFThread
	 */
	private static final int NUM_PDF_THREADS = 5;
	
	/** 
	 * The executor encargado de ejecutar los hilos PDF.
	 * @see PDFThread
	 */
	private static ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(NUM_PDF_THREADS);
	
	/** The future schedule task generada al programar la tarea periódica*/
	private ScheduledFuture<?> futureScheduleTask;
    
    /** The maintenance task, instancia de {@link MaintenanceService} */
    private MaintenanceService maintenanceTask;
    

    /**
     * Instantiates a new my task executor.
     *
     * @param myTask$ instancia de {@link MaintenanceService}
     */
    public MyTaskExecutor(MaintenanceService myTask$) 
    {
        maintenanceTask = myTask$;
        /**
         * https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ScheduledThreadPoolExecutor.html
         */
        executorScheduled.setRemoveOnCancelPolicy(true);
    }
    
	/**
	 * Inicia la ejecución de un hilo PDF.
	 *
	 * @param task
	 *            FutureTask que ejecutará el PDFThread
	 * @return true, si arranca correctamente, o false, en caso contrario
	 * @see <a href=
	 *      "https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html">
	 *      Referencia</a>
	 * @see PDFThread
	 */
    public static boolean startExecution (FutureTask< File > task){
    	if (executor.getActiveCount() >= NUM_PDF_THREADS) {
			return false;
		}
    	executor.execute(task);
    	return true;
    }
    
    /**
     * Schedule a thread that execute the maintenanceTask and at the end 
     * it calculate the next delay and program itself to execute at that
     * time.
     *
     * @param targetHour the target hour
     * @param targetMin the target min
     * @param targetSec the target sec
     */
    public void startScheduleExecutionAt(int targetHour, int targetMin, int targetSec)
    {
        Runnable taskWrapper = new Runnable(){

            @Override
            public void run() 
            {
            	
                maintenanceTask.run();
                
                System.out.println("["+new Date().toString()+"] Mantenimiento: acabado");
                startScheduleExecutionAt(targetHour, targetMin, targetSec);
            }

        };
        long delay = computeNextDelay(targetHour, targetMin, targetSec);
        futureScheduleTask= executorScheduled.schedule(taskWrapper, delay, TimeUnit.SECONDS);
       
    }

    /**
     * Compute next delay.
     *
     * @param targetHour the target hour
     * @param targetMin the target min
     * @param targetSec the target sec
     * @return the delay in seconds
     */
    private long computeNextDelay(int targetHour, int targetMin, int targetSec) 
    {
        LocalDateTime localNow = LocalDateTime.now();
        ZoneId currentZone = ZoneId.systemDefault();
        ZonedDateTime zonedNow = ZonedDateTime.of(localNow, currentZone);
        ZonedDateTime zonedNextTarget = zonedNow.withHour(targetHour).withMinute(targetMin).withSecond(targetSec);
        if(zonedNow.compareTo(zonedNextTarget) >= 0)
            zonedNextTarget = zonedNextTarget.plusDays(1);

        Duration duration = Duration.between(zonedNow, zonedNextTarget);
        return duration.getSeconds();
    }

    /**
     * Para todos los hilos creados y apaga 
     * {@link MyTaskExecutor#executorScheduled} y {@link MyTaskExecutor#executor}
     * despues espera un tiempo a que sus hilos acaben en caso de existir alguno
     * ejecutandose. Si tras ese tiempo aún no han acabado sale pero se 
     * produciria un MemoryLeak que Tomcat detectaría al apagarse
     */
    public void stop()
    {
        executorScheduled.shutdown();
        if (futureScheduleTask != null && !futureScheduleTask.isCancelled() && !futureScheduleTask.isDone()) {
            futureScheduleTask.cancel(true);
		}
        executor.shutdown();
        
        Collection<FutureTask<File>> futurePDFs = CampaignManagement.getAllPDFFuture();
        for (FutureTask<File> futurePDF : futurePDFs) {
			if (futurePDF!=null && !futurePDF.isCancelled() && !futurePDF.isDone()) {
				futurePDF.cancel(true);
			}
		}
        try {
            executorScheduled.awaitTermination(5, TimeUnit.SECONDS);
            executor.awaitTermination(20, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            System.err.println("Error parando la tareas");
        	ex.printStackTrace();
        }
    }
}