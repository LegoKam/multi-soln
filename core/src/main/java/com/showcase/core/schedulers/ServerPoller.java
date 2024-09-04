package com.showcase.core.schedulers;

import com.showcase.core.config.PollerConfig;
import com.showcase.core.config.SyncConfig;
import org.apache.sling.api.resource.ResourceResolverFactory;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component(service = Job.class, immediate = true)
public class ServerPoller implements Job {


    @Reference
    ResourceResolverFactory resourceResolverFactory;
    String username = null;
    String password = null;
    String authorServer = null;

    @Reference
    Scheduler scheduler;

    private static final Logger logger = LoggerFactory.getLogger(ServerPoller.class);

    @Activate
    private void activate(PollerConfig configuration) {

        Map<String, Serializable> enMap = new HashMap<String, Serializable>();

        logger.info("*******************Activate method");
        ScheduleOptions scheduleOptions = scheduler.EXPR(configuration.cronExp());
        logger.info("*******************cronExp ::" + configuration.cronExp());

        username = configuration.username();
        password = configuration.password();
        authorServer = configuration.authorServer();

        logger.info("*******************username ::" + configuration.username());
        logger.info("*******************password::" + configuration.password());
        logger.info("*******************authorServer::" + configuration.authorServer());


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
            URL url = new URL(authorServer);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            String credentials = username + ":" + password;
            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
            connection.setRequestProperty("Authorization", "Basic " + encodedCredentials);
            connection.setRequestProperty("Accept", "application/json");
            int responseCode = connection.getResponseCode();
            logger.info("Response Code : " + responseCode);
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            logger.info(response.toString());

        } catch (IOException malformedURLException) {
            logger.debug("Error: " + malformedURLException);
        }

    }
}