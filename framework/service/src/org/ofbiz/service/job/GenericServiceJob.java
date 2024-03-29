/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.ofbiz.service.job;

import java.io.Serializable;
import java.util.Map;

import org.ofbiz.base.util.Assert;
import org.ofbiz.base.util.Debug;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.service.AsyncOptions;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.MemoryAsyncOptions;
import org.ofbiz.service.GenericRequester;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ModelService;
import org.ofbiz.service.ServiceResult;
import org.ofbiz.service.ServiceUtil;
import org.ofbiz.service.semaphore.SemaphoreFailException;
import org.ofbiz.service.semaphore.SemaphoreWaitException;
/**
 * A generic async-service job.
 */
@SuppressWarnings("serial")
public class GenericServiceJob extends AbstractJob implements Serializable {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    protected final transient GenericRequester requester;
    protected final transient DispatchContext dctx;
    private final String service;
    private final long priority; // SCIPIO
    private final String jobPool; // SCIPIO
    private final Map<String, Object> context;

    public GenericServiceJob(DispatchContext dctx, String jobId, String jobName, ModelService modelService,
                             AsyncOptions serviceOptions, Map<String, Object> context, GenericRequester req) { // SCIPIO: Refactored
        super(jobId, jobName);
        if (serviceOptions == null) {
            serviceOptions = MemoryAsyncOptions.DEFAULT;
        }
        Assert.notNull("dctx", dctx);
        this.dctx = dctx;
        this.service = modelService.name;
        // SCIPIO: NOTE: serviceOptions could be a PersistAsyncOptions instance in this class design so if need MemoryAsyncOptions check AsyncOptions.isPersisted/instance
        this.priority = modelService.determinePriority(serviceOptions, JobPriority.NORMAL);
        this.jobPool = serviceOptions.jobPool();
        this.context = context;
        this.requester = req;
    }

    /**
     * Invokes the service.
     */
    @Override
    public void exec() throws InvalidJobException {
        if (currentState != State.QUEUED) {
            throw new InvalidJobException("Illegal state change");
        }
        currentState = State.RUNNING;
        init();
        Throwable thrown = null;
        ServiceResult result = null;
        // SCIPIO: stats
        long startTime = System.currentTimeMillis();
        String serviceName = getServiceName();
        JobPoller jobPoller = JobPoller.getInstance();
        JobPoller.CurrentServiceStats currentServiceStats = null;
        // no transaction is necessary since runSync handles this
        try {
            try {
                currentServiceStats = jobPoller.registerCurrentServiceCall(serviceName, this, startTime);
                // get the dispatcher and invoke the service via runSync -- will run all ECAs
                LocalDispatcher dispatcher = dctx.getDispatcher();
                result = dispatcher.runSync(serviceName, getContext());
            } finally {
                jobPoller.deregisterCurrentServiceCall(currentServiceStats);
            }
            jobPoller.registerGlobalServiceCall(serviceName, this, result, null, startTime, System.currentTimeMillis() - startTime);
            // check for a failure
            if (ServiceUtil.isError(result)) {
                thrown = new Exception(ServiceUtil.getErrorMessage(result));
            }
            if (requester != null) {
                requester.receiveResult(result);
            }
        } catch (Throwable t) {
            JobPoller.getInstance().registerGlobalServiceCall(serviceName, this, result, t, startTime, System.currentTimeMillis() - startTime);
            if (requester != null) {
                // pass the exception back to the requester.
                requester.receiveThrowable(t);
            }
            thrown = t;
        }
        if (thrown == null) {
            finish(result);
        } else {
            failed(thrown);
        }
    }

    /**
     * Method is called prior to running the service.
     */
    protected void init() throws InvalidJobException {
        if (Debug.verboseOn()) {
            Debug.logVerbose("Async-Service initializing.", module);
        }
    }

    /**
     * Method is called after the service has finished successfully.
     */
    protected void finish(Map<String, Object> result) throws InvalidJobException {
        if (currentState != State.RUNNING) {
            throw new InvalidJobException("Illegal state change");
        }
        currentState = State.FINISHED;
        if (Debug.verboseOn()) {
            Debug.logVerbose("Async-Service finished.", module);
        }
    }

    /**
     * Method is called when the service fails.
     * @param t Throwable
     */
    protected void failed(Throwable t) throws InvalidJobException {
        if (t instanceof SemaphoreWaitException || t instanceof SemaphoreFailException) {
            Debug.logError("Async-Service failed due to " + t, module);
        } else {
            Debug.logError(t, "Async-Service failed.", module);
        }
        currentState = State.FAILED;
    }

    /**
     * Gets the context for the service invocation.
     * @return Map of name value pairs making up the service context.
     */
    protected Map<String, Object> getContext() throws InvalidJobException {
        return context;
    }

    /**
     * Gets the name of the service as defined in the definition file.
     * SCIPIO: Now public.
     * @return The name of the service to be invoked.
     */
    @Override
    public String getServiceName() {
        return service;
    }

    /**
     * Returns the service model (SCIPIO).
     */
    protected ModelService getModelService() throws GenericServiceException {
        String serviceName = getServiceName();
        return (serviceName != null) ? dctx.getModelService(serviceName) : null;
    }

    @Override
    public boolean isValid() {
        return currentState == State.CREATED;
    }

    @Override
    public void deQueue() throws InvalidJobException {
        super.deQueue();
        throw new InvalidJobException("Unable to queue job [" + toLogId() + "]"); // SCIPIO: improved logging
    }

    @Override
    public long getPriority() { // SCIPIO
        return priority;
    }

    @Override
    public String getJobType() { // SCIPIO
        return "generic";
    }

    @Override
    public boolean isPersist() { // SCIPIO
        return false;
    }

    @Override
    public GenericValue getJobValue() { // SCIPIO
        return null;
    }

    @Override
    public String getJobPool() { return jobPool; }

}
