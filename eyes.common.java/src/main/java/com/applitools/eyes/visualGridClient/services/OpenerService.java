package com.applitools.eyes.visualGridClient.services;

import com.applitools.eyes.Logger;
import com.applitools.eyes.TestResults;
import com.applitools.utils.GeneralUtils;

import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

public class OpenerService extends EyesService{

    private AtomicInteger concurrentSession = new AtomicInteger();
    final Object concurrencyLock;

    public OpenerService(String serviceName, ThreadGroup servicesGroup, Logger logger, int threadPoolSize, Object openerServiceLock, EyesServiceListener listener, Object debugLock, Tasker tasker) {
        super(serviceName, servicesGroup, logger, threadPoolSize, debugLock, listener, tasker);
        this.concurrencyLock = openerServiceLock;
    }

    void runNextTask() {
        if (!isServiceOn) return;
        if (this.threadPoolSize > concurrentSession.get()) {
            final FutureTask<TestResults> task = this.listener.getNextTask(tasker, new Task.TaskListener() {
                @Override
                public void onTaskComplete(Task task) {
                }

                @Override
                public void onTaskFailed(Exception e, Task task) {
                }

                @Override
                public void onRenderComplete() {
                }
            });
            if (task != null) {
                this.concurrentSession.incrementAndGet();
                pauseIfNeeded();
                this.executor.submit(task);
            }
        }
        else{
            synchronized (concurrencyLock){
                try {
                    logger.verbose("Waiting for concurrency to be free");
                    concurrencyLock.wait();
                    logger.verbose("concurrency free");
                } catch (InterruptedException e) {
                    GeneralUtils.logExceptionStackTrace(logger ,e);
                }
            }
        }
    }

    @Override
    void stopService() {
        logger.verbose("concurrency on stop = "+this.concurrentSession);
        super.stopService();
    }

    public synchronized int decrementConcurrency(){
        return this.concurrentSession.decrementAndGet();
    }
}
