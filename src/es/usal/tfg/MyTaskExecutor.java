package es.usal.tfg;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper class to control ScheduledExecutorService taking into account the
 * daylight saving issues because it calculate the delay until the desired time
 * every day
 * 
 * @author Sage
 * @see http://stackoverflow.com/a/20388073/6441806
 */
public class MyTaskExecutor
{
	private ScheduledThreadPoolExecutor executorScheduled = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1);
	private static final int NUM_PDF_THREADS = 1;
	private static ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(NUM_PDF_THREADS);
	private ScheduledFuture<?> futureScheduleTask;
    private MaintenanceService maintenanceTask;
    

    public MyTaskExecutor(MaintenanceService myTask$) 
    {
        maintenanceTask = myTask$;
        /**
         * https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ScheduledThreadPoolExecutor.html
         */
        executorScheduled.setRemoveOnCancelPolicy(true);
    }
    
    /**
     * @param task
     * @see https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html
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
     * time
     * 
     * @param targetHour
     * @param targetMin
     * @param targetSec
     */
    public void startScheduleExecutionAt(int targetHour, int targetMin, int targetSec)
    {
        Runnable taskWrapper = new Runnable(){

            @Override
            public void run() 
            {
            	
                maintenanceTask.run();
                
                System.out.println("["+new Date().toString()+"] "+Thread.currentThread().getName()+" Mantenimiento: acabado");
                startScheduleExecutionAt(targetHour, targetMin, targetSec);
            }

        };
        long delay = computeNextDelay(targetHour, targetMin, targetSec);
        futureScheduleTask= executorScheduled.schedule(taskWrapper, delay, TimeUnit.SECONDS);
       
    }

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