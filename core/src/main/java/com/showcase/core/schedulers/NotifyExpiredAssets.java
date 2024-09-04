package com.showcase.core.schedulers;

import com.adobe.granite.taskmanagement.Task;
import com.adobe.granite.taskmanagement.TaskManager;
import com.adobe.granite.taskmanagement.TaskManagerException;
import com.adobe.granite.taskmanagement.TaskManagerFactory;
import com.adobe.granite.workflow.exec.InboxItem;
import com.showcase.core.config.SyncConfig;
import com.showcase.core.util.AEMUtil;
import org.apache.sling.api.resource.*;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.commons.scheduler.Job;
import org.apache.sling.commons.scheduler.JobContext;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component(service = Job.class, immediate = true)
public class NotifyExpiredAssets implements Job {


    @Reference
    ResourceResolverFactory resourceResolverFactory;

    @Reference
    Scheduler scheduler;

    private static final Logger logger = LoggerFactory.getLogger(NotifyExpiredAssets.class);

    @Activate
    private void activate(SyncConfig configuration) {

        logger.info("*******************Activate method");
        ScheduleOptions scheduleOptions = scheduler.EXPR(configuration.cronExp());
        logger.info("*******************cronExp ::" + configuration.cronExp());

        Map<String, Serializable> enMap = new HashMap<String, Serializable>();
        enMap.put("assetPath", configuration.syncPath());
        logger.info("*******************syncPath ::" + configuration.syncPath());

        scheduleOptions.config(enMap);
        scheduleOptions.canRunConcurrently(false);
        scheduler.schedule(this, scheduleOptions);

        logger.info("*******************Activated::::" + scheduleOptions);
    }

    @Deactivate
    protected void deactivated(SyncConfig configuration) {
        logger.info("Deactivated!!");
    }

    @Override
    public void execute(JobContext jobContext) {
        logger.info("^^^^^^^^^^^^^^^^^^Job in progress----!!" + new Date());
        try {

            LocalDateTime dateTime = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            String formattedDate = dateTime.atOffset(ZoneOffset.UTC).format(formatter);

            String queryString = "SELECT * FROM [dam:Asset] AS asset" +
                    " WHERE ISDESCENDANTNODE([/content/dam])" +
                    "  AND asset.[jcr:content/metadata/prism:expirationDate] <= CAST('%s' AS DATE)";
            String finalQueryString = String.format(queryString, formattedDate);

            logger.debug("Query String:::" + finalQueryString);

            ResourceResolver resourceResolver = null;

            resourceResolver = AEMUtil.getResourceResolver(resourceResolverFactory);

            Session session = resourceResolver.adaptTo(Session.class);
            QueryManager queryManager = session.getWorkspace().getQueryManager();

            Query query = queryManager.createQuery(finalQueryString, Query.JCR_SQL2);
            QueryResult queryResult = query.execute();
            NodeIterator nodes = queryResult.getNodes();

            TaskManager taskManager = resourceResolver.adaptTo(TaskManager.class);
            TaskManagerFactory taskManagerFactory = taskManager.getTaskManagerFactory();

            while (nodes.hasNext()) {
                Node expiredAssetNode = (Node) nodes.next();
                logger.debug("Expired Assets???>>>" + expiredAssetNode);

                Resource auditAssetResource = resourceResolver.getResource("/var/dam/downloadaudit/" + expiredAssetNode.getPath() + "/track");
                if (auditAssetResource != null) {

                    logger.debug("auditAssetResource???>>>" + auditAssetResource);

                    Node auditAssetNode = auditAssetResource.adaptTo(Node.class);
                    Property usersProperty = auditAssetNode.getProperty("users");
                    if (auditAssetNode.hasProperty("lastAlert")) {
                        logger.debug("lastAlert???>>>exists on node");
                        continue;
                    }
                    Value userNames[] = usersProperty.getValues();
                    logger.debug("userNames???>>>" + userNames);

                    for (Value userValue : userNames) {
                        String userName = userValue.getString();
                        logger.debug("userName???>>>" + userName);
                        Task expiredAssetTask = taskManagerFactory.newTask(Task.DEFAULT_TASK_TYPE);
                        expiredAssetTask.setContentPath(expiredAssetNode.getPath());
                        expiredAssetTask.setDescription("Immediately remove this asset from where its used!!");
                        expiredAssetTask.setDueTime(new Date());
                        expiredAssetTask.setName("Expired asset notification");
                        expiredAssetTask.setPriority(InboxItem.Priority.HIGH);
                        expiredAssetTask.setCurrentAssignee(userName);
                        taskManager.createTask(expiredAssetTask);
                        logger.debug("NOTIFIED???>>>" + userName + "---PATH---" + expiredAssetNode.getPath());
                    }
                    auditAssetNode.setProperty("lastAlert", Calendar.getInstance());
                    resourceResolver.commit();
                    logger.debug("lastAlert set on path:::" + auditAssetNode.getPath());
                }

            }
        } catch (LoginException e) {
            logger.debug("Error", e);
        } catch (InvalidQueryException e) {
            logger.debug("Error", e);
        } catch (RepositoryException e) {
            logger.debug("Error", e);
        } catch (TaskManagerException e) {
            logger.debug("Error", e);
        } catch (PersistenceException e) {
            logger.debug("Error", e);
        }
    }
}