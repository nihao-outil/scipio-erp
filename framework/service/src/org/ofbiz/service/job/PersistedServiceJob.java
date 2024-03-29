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

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringUtils;
import org.ofbiz.base.config.GenericConfigException;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.serialize.SerializeException;
import org.ofbiz.entity.serialize.XmlSerializer;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.GenericRequester;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.ModelService;
import org.ofbiz.service.PersistAsyncOptions;
import org.ofbiz.service.ServiceUtil;
import org.ofbiz.service.calendar.RecurrenceInfo;
import org.ofbiz.service.calendar.RecurrenceInfoException;
import org.ofbiz.service.calendar.TemporalExpression;
import org.ofbiz.service.calendar.TemporalExpressionWorker;
import org.ofbiz.service.config.ServiceConfigUtil;
import org.xml.sax.SAXException;

import com.ibm.icu.util.Calendar;

/**
 * A {@link Job} that is backed by the entity engine. Job data is stored
 * in the JobSandbox entity.
 * <p>When the job is queued, this object "owns" the entity value. Any external changes
 * are ignored except the cancelDateTime field - jobs can be canceled after they are queued.</p>
 */
@SuppressWarnings("serial")
public class PersistedServiceJob extends GenericServiceJob {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    private final transient Delegator delegator;
    private long nextRecurrence = -1;
    private final long maxRetry;
    private final long currentRetryCount;
    private final GenericValue jobValue;
    private final long startTime;

    /**
     * Creates a new PersistedServiceJob.
     * SCIPIO: minimalJob boolean indicates to avoid unnecessary lookups unnecessary for the basic Job interface (slight ofbiz kludge).
     * NOTE: Passing PersistAsyncOptions here is confusing because jobValue should be configured with the settings already,
     *  but may be needed later...
     */
    protected PersistedServiceJob(DispatchContext dctx, GenericValue jobValue, GenericRequester req, boolean minimalJob, PersistAsyncOptions serviceOptions) {
        // SCIPIO: TODO: REVIEW: DO NOT pass serviceOptions to super here because we're only exploiting its implementation... abstraction issues
        super(dctx, jobValue.getString("jobId"), jobValue.getString("jobName"),
                dctx.getModelServiceAlways(jobValue.getString("serviceName")), null, null, req); // SCIPIO: jobPool, overridden in getJobPool()
        this.delegator = dctx.getDelegator();
        this.jobValue = jobValue;
        Timestamp storedDate = jobValue.getTimestamp("runTime");
        this.startTime = storedDate.getTime();
        this.maxRetry = jobValue.get("maxRetry") != null ? jobValue.getLong("maxRetry") : -1;
        Long retryCount = jobValue.getLong("currentRetryCount");
        if (retryCount != null) {
            this.currentRetryCount = retryCount;
        } else {
            if (minimalJob) {
                this.currentRetryCount = 0;
            } else {
                // backward compatibility
                this.currentRetryCount = getRetries(this.delegator);
            }
        }
    }

    /**
     * Creates a new PersistedServiceJob
     * @param dctx
     * @param jobValue
     * @param req
     */
    public PersistedServiceJob(DispatchContext dctx, GenericValue jobValue, GenericRequester req) {
        this(dctx, jobValue, req, false, null);
    }

    /**
     * Makes a lightweight result job for the improved {@link org.ofbiz.service.LocalDispatcher} interface (SCIPIO).
     */
    public static PersistedServiceJob makeResultJob(DispatchContext dctx, GenericValue jobValue, PersistAsyncOptions serviceOptions) {
        return new PersistedServiceJob(dctx, jobValue, null, true, serviceOptions);
    }

    @Override
    public void queue() throws InvalidJobException {
        super.queue();
        try {
            jobValue.refresh();
        } catch (GenericEntityException e) {
            throw new InvalidJobException("Unable to refresh JobSandbox value", e);
        }
        if (!JobManager.instanceId.equals(jobValue.getString("runByInstanceId"))) {
            throw new InvalidJobException("Job has been accepted by a different instance");
        }
        Timestamp cancelTime = jobValue.getTimestamp("cancelDateTime");
        Timestamp startTime = jobValue.getTimestamp("startDateTime");
        if (cancelTime != null || startTime != null) {
            // job not available
            throw new InvalidJobException("Job [" + toLogId() + "] is not available"); // SCIPIO: improved logging
        }
        jobValue.set("statusId", "SERVICE_QUEUED");
        try {
            jobValue.store();
        } catch (GenericEntityException e) {
            throw new InvalidJobException("Unable to set the startDateTime and statusId (SERVICE_QUEUED) on the current job [" + toLogId() + "]; not queueing", e); // SCIPIO: improved logging
        }
        if (Debug.verboseOn()) {
            Debug.logVerbose("Placing job [" + toLogId() + "] in queue", module); // SCIPIO: improved logging
        }
    }

    @Override
    protected void init() throws InvalidJobException {
        super.init();
        try {
            jobValue.refresh();
        } catch (GenericEntityException e) {
            throw new InvalidJobException("Unable to refresh JobSandbox value", e);
        }
        if (!JobManager.instanceId.equals(jobValue.getString("runByInstanceId"))) {
            throw new InvalidJobException("Job has been accepted by a different instance");
        }
        if (jobValue.getTimestamp("cancelDateTime") != null) {
            // Job cancelled
            throw new InvalidJobException("Job [" + toLogId() + "] was cancelled"); // SCIPIO: improved logging
        }
        jobValue.set("startDateTime", UtilDateTime.nowTimestamp());
        jobValue.set("statusId", "SERVICE_RUNNING");
        try {
            jobValue.store();
        } catch (GenericEntityException e) {
            throw new InvalidJobException("Unable to set the startDateTime and statusId on the current job [" + toLogId() + "]; not running!", e); // SCIPIO: improved logging
        }
        if (Debug.verboseOn()) {
            Debug.logVerbose("Job [" + toLogId() + "] running", module); // SCIPIO: improved logging
        }
        determineCreateRecurrenceRegular(null); // SCIPIO: Refactored
        if (Debug.infoOn()) {
            // SCIPIO: 2018-10-17: detect -1 case and try to make this less confusing
            // NOTE: this is only called in GenericServiceJob.exec, so we can say "Running Job" here, should be always true...
            //Debug.logInfo("Job  [" + toLogId() + "] -- Next runtime: " + new Date(nextRecurrence), module);
            Debug.logInfo("Running Job [" + toLogId() + "] Retries [" + currentRetryCount + "/" + maxRetry + "] -- Next recurrence: "
                    + ((nextRecurrence != -1) ? new Date(nextRecurrence) : "(none)"), module); // SCIPIO: improved logging
        }
    }

    public long getNextRecurrence() { // SCIPIO
        return nextRecurrence;
    }

    public long getMaxRetry() { // SCIPIO
        return maxRetry;
    }

    /** Refactored from {@link #init()} (SCIPIO). NOTE: This must not modify+store the current value because used from {@link JobManager#reloadCrashedJobs()}. */
    public void determineCreateRecurrenceRegular(Timestamp fromDate) throws InvalidJobException {
        // configure any additional recurrences
        long maxRecurrenceCount = -1;
        long currentRecurrenceCount = 0;
        TemporalExpression expr = null;
        RecurrenceInfo recurrence = getRecurrenceInfo();
        if (recurrence != null) {
            // SCIPIO: In all likelihood we will never deprecate the old RecurrenceInfo code, and we have stock seed/demo
            // data using it, so this should not be a warning for us.
            //Debug.logWarning("Persisted Job [" + toLogId() + "] references a RecurrenceInfo, recommend using TemporalExpression instead", module);
            // Updated: require verbose for this because even , clogs logs for nothing
            Debug.logInfo("Persisted Job [" + toLogId() + "] references a RecurrenceInfo (recommend using TemporalExpression instead)", module); // SCIPIO: improved logging
            currentRecurrenceCount = recurrence.getCurrentCount();
            expr = RecurrenceInfo.toTemporalExpression(recurrence);
        }
        if (expr == null && UtilValidate.isNotEmpty(jobValue.getString("tempExprId"))) {
            try {
                expr = TemporalExpressionWorker.getTemporalExpression(this.delegator, jobValue.getString("tempExprId"));
            } catch (GenericEntityException e) {
                throw new RuntimeException(e.getMessage());
            }
        }
        if (jobValue.get("maxRecurrenceCount") != null) {
            maxRecurrenceCount = jobValue.getLong("maxRecurrenceCount");
        }
        if (jobValue.get("currentRecurrenceCount") != null) {
            currentRecurrenceCount = jobValue.getLong("currentRecurrenceCount");
        }
        if (maxRecurrenceCount != -1) {
            currentRecurrenceCount++;
            jobValue.set("currentRecurrenceCount", currentRecurrenceCount);
        }
        try {
            if (expr != null && (maxRecurrenceCount == -1 || currentRecurrenceCount <= maxRecurrenceCount)) {
                if (recurrence != null) {
                    recurrence.incrementCurrentCount();
                }
                // SCIPIO
                //Calendar next = expr.next(Calendar.getInstance());
                Calendar next = expr.next(fromDate != null ? UtilDateTime.toCalendar(fromDate) : Calendar.getInstance());
                if (next != null) {
                    createRecurrence(next.getTimeInMillis(), false);
                }
            }
        } catch (GenericEntityException e) {
            throw new InvalidJobException(e);
        }
    }

    private void createRecurrence(long next, boolean isRetryOnFailure) throws GenericEntityException {
        if (Debug.verboseOn()) {
            Debug.logVerbose("Next runtime returned: " + next, module);
        }
        if (next > startTime) {
            String pJobId = jobValue.getString("parentJobId");
            if (pJobId == null) {
                pJobId = jobValue.getString("jobId");
            }
            GenericValue newJob = GenericValue.create(jobValue);
            newJob.remove("jobId");
            newJob.set("previousJobId", jobValue.getString("jobId"));
            newJob.set("parentJobId", pJobId);
            newJob.set("statusId", "SERVICE_PENDING");
            newJob.set("startDateTime", null);
            newJob.set("runByInstanceId", null);
            newJob.set("runTime", new java.sql.Timestamp(next));
            if (isRetryOnFailure) {
                newJob.set("currentRetryCount", currentRetryCount + 1);
            } else {
                newJob.set("currentRetryCount", 0L);
            }
            newJob.set("runtimeDataId", jobValue.getString("runtimeDataId"));
            nextRecurrence = next;
            // Set priority if missing
            // SCIPIO: DON'T: null will indicate normal OR ModelService.priority, and this is unnecessary anyway
            //if (newJob.getLong("priority") == null) {
            //    newJob.set("priority", JobPriority.NORMAL);
            //}

            // SCIPIO: Transfer the special new eventId field
            // 2017-08-30: copy eventId (e.g. SCH_EVENT_STARTUP) ONLY if this is
            // a regular recurrence and not a failure retry.
            String eventId = jobValue.getString("eventId");
            newJob.set("eventId", isRetryOnFailure ? null : eventId);
            if (isRetryOnFailure && UtilValidate.isNotEmpty(eventId)) {
                // SCIPIO: 2017-09-11: if this is a retry for event-specific job,
                // do not create any recurrences for the retry other than the retries themselves;
                // otherwise, a successful retry may trigger inappropriate recurrences (at times other than the eventId)
                newJob.put("maxRecurrenceCount", 0L);
                newJob.put("currentRecurrenceCount", null);
                newJob.put("recurrenceInfoId", null);
                newJob.put("tempExprId", null);
            }

            delegator.createSetNextSeqId(newJob);
            if (Debug.verboseOn()) {
                Debug.logVerbose("Created next job entry: " + newJob, module);
            }
        }
    }

    @Override
    protected void finish(Map<String, Object> result) throws InvalidJobException {
        super.finish(result);
        // set the finish date
        jobValue.set("statusId", "SERVICE_FINISHED");
        jobValue.set("finishDateTime", UtilDateTime.nowTimestamp());
        String jobResult = null;
        if (ServiceUtil.isError(result)) {
            jobResult = StringUtils.substring(ServiceUtil.getErrorMessage(result), 0, 255);
        } else {
            jobResult = StringUtils.substring(ServiceUtil.makeSuccessMessage(result, "", "", "", ""), 0, 255);
        }
        if (UtilValidate.isNotEmpty(jobResult)) {
            jobValue.set("jobResult", jobResult);
        }
        try {
            jobValue.store();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Cannot update the job [" + toLogId() + "] sandbox", module); // SCIPIO: improved logging
        }
    }

    @Override
    protected void failed(Throwable t) throws InvalidJobException {
        super.failed(t);
        // if the job has not been re-scheduled; we need to re-schedule and run again

        // SCIPIO: 2017-08-30: SPECIAL CASE: for special events (SCH_EVENT_STARTUP), we need to schedule a retry even
        // if there is already a recurrence, because the recurrence will have been setup for the specific event (SCH_EVENT_STARTUP) only;
        // if we don't make this exception, startup jobs can't easily be configured to do retries within the current execution.
        // (to prevent this, set maxRetry=0)
        // TODO: REVIEW: unclear if this exception should apply only to all event types (current case - 2017-08-30),
        // a subset of possible event types, or only SCH_EVENT_STARTUP ("SCH_EVENT_STARTUP".equals(nextRecurrenceEventId));
        // but because the retries are not given an event (see createRecurrence), it's at least consistent to have roughly same condition here,
        // and this would probably make sense for other event types...
        //if (nextRecurrence == -1) {
        if (nextRecurrence == -1 || UtilValidate.isNotEmpty(jobValue.getString("eventId"))) {
            if (this.canRetry()) {
                // create a recurrence
                Calendar cal = Calendar.getInstance();
                try {
                    cal.add(Calendar.MINUTE, ServiceConfigUtil.getServiceEngine().getThreadPool().getFailedRetryMin());
                } catch (GenericConfigException e) {
                    Debug.logWarning(e, "Unable to get retry minutes for job [" + toLogId() + "], defaulting to now: ", module); // SCIPIO: improved logging
                }
                long next = cal.getTimeInMillis();
                try {
                    createRecurrence(next, true);
                } catch (GenericEntityException e) {
                    Debug.logError(e, "Unable to re-schedule job [" + toLogId() + "]: ", module); // SCIPIO: improved logging
                }
                // SCIPIO: 2018-10-17: display friendly Date format (not just the long value)
                Debug.logInfo("Persisted Job [" + toLogId() + "] Failed. Re-Scheduling : " + new Date(next) + " (" + next + ")", module); // SCIPIO: improved logging
            } else {
                Debug.logWarning("Persisted Job [" + toLogId() + "] Failed. Max Retry Hit, not re-scheduling", module); // SCIPIO: improved logging
            }
        }
        // set the failed status
        jobValue.set("statusId", "SERVICE_FAILED");
        jobValue.set("finishDateTime", UtilDateTime.nowTimestamp());
        jobValue.set("jobResult", StringUtils.substring(t.getMessage(), 0, 255));
        try {
            jobValue.store();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Cannot update the JobSandbox entity", module);
        }
    }

    @Override
    public String getServiceName() { // SCIPIO: Now public
        if (jobValue == null || jobValue.get("serviceName") == null) {
            return null;
        }
        return jobValue.getString("serviceName");
    }

    @Override
    protected Map<String, Object> getContext() throws InvalidJobException {
        Map<String, Object> context = null;
        try {
            if (UtilValidate.isNotEmpty(jobValue.getString("runtimeDataId"))) {
                GenericValue contextObj = jobValue.getRelatedOne("RuntimeData", false);
                if (contextObj != null) {
                    context = UtilGenerics.checkMap(XmlSerializer.deserialize(contextObj.getString("runtimeInfo"), delegator), String.class, Object.class);
                }
            }
            if (context == null) {
                context = new HashMap<>();
            }
            // check the runAsUser
            if (UtilValidate.isNotEmpty(jobValue.getString("runAsUser"))) {
                context.put("userLogin", ServiceUtil.getUserLogin(dctx, context, jobValue.getString("runAsUser")));
            }
            // SCIPIO: scipioJobCtxInterface: If eventId is set, check if the service wants to know what it is
            String eventId = jobValue.getString("eventId");
            if (eventId != null) {
                try {
                    ModelService modelService = getModelService();
                    if (modelService != null && modelService.getParam("scipioJobCtx") != null) {
                        Map<String, Object> scipioJobCtx = new HashMap<>();
                        scipioJobCtx.put("eventId", eventId);
                        context.put("scipioJobCtx", scipioJobCtx);
                    }
                } catch (GenericServiceException e) { // SCIPIO
                    Debug.logError(e, "PersistedServiceJob.getContext(): Error getting model service for job", module);
                }
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, "PersistedServiceJob.getContext(): Entity Exception", module);
        } catch (SerializeException e) {
            Debug.logError(e, "PersistedServiceJob.getContext(): Serialize Exception", module);
        } catch (ParserConfigurationException e) {
            Debug.logError(e, "PersistedServiceJob.getContext(): Parse Exception", module);
        } catch (SAXException e) {
            Debug.logError(e, "PersistedServiceJob.getContext(): SAXException", module);
        } catch (IOException e) {
            Debug.logError(e, "PersistedServiceJob.getContext(): IOException", module);
        }
        if (context == null) {
            Debug.logError("Job context is null", module);
        }
        return context;
    }

    // returns the number of current retries
    private long getRetries(Delegator delegator) {
        String pJobId = jobValue.getString("parentJobId");
        if (pJobId == null) {
            return 0;
        }
        long count = 0;
        try {
            count = EntityQuery.use(delegator).from("JobSandbox").where("parentJobId", pJobId, "statusId", "SERVICE_FAILED").queryCount();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Exception thrown while counting retries: ", module);
        }
        return count + 1; // add one for the parent
    }

    private boolean canRetry() {
        if (maxRetry == -1) {
            return true;
        }
        return currentRetryCount < maxRetry;
    }

    private RecurrenceInfo getRecurrenceInfo() {
        try {
            if (UtilValidate.isNotEmpty(jobValue.getString("recurrenceInfoId"))) {
                GenericValue ri = jobValue.getRelatedOne("RecurrenceInfo", false);
                if (ri != null) {
                    return new RecurrenceInfo(ri);
                }
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, "Problem getting RecurrenceInfo entity from JobSandbox", module);
        } catch (RecurrenceInfoException re) {
            Debug.logError(re, "Problem creating RecurrenceInfo instance: " + re.getMessage(), module);
        }
        return null;
    }

    @Override
    public void deQueue() throws InvalidJobException {
        if (currentState != State.QUEUED) {
            throw new InvalidJobException("Illegal state change");
        }
        currentState = State.CREATED;
        try {
            jobValue.refresh();
            jobValue.set("startDateTime", null);
            jobValue.set("runByInstanceId", null);
            jobValue.set("statusId", "SERVICE_PENDING");
            jobValue.store();
        } catch (GenericEntityException e) {
            throw new InvalidJobException("Unable to dequeue job [" + toLogId() + "]", e); // SCIPIO: improved logging
        }
        if (Debug.verboseOn()) {
            Debug.logVerbose("Job [" + toLogId() + "] not queued, rescheduling", module); // SCIPIO: improved logging
        }
    }

    @Override
    public Date getStartTime() {
        return new Date(startTime);
    }

    /*
     * Returns the priority stored in the JobSandbox.priority field, if no value is present
     * then it defaults to AbstractJob.getPriority() (SCIPIO: actually GenericServiceJob, which does the ModelService lookup)
     */
    @Override
    public long getPriority() {
        Long priority = jobValue.getLong("priority");
        if (priority == null) {
            return super.getPriority();
        }
        return priority;
    }

    @Override
    public String getJobType() { // SCIPIO
        return "persist";
    }

    @Override
    public boolean isPersist() { // SCIPIO
        return true;
    }

    @Override
    public GenericValue getJobValue() { // SCIPIO
        return jobValue;
    }

    @Override
    public String getJobPool() { return getJobValue().getString("poolId"); }
}
