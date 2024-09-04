/*
 *  Copyright 2015 Adobe Systems Incorporated
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.showcase.core.listeners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.showcase.core.util.AEMUtil;
import com.showcase.core.util.CommonUtil;
import org.apache.jackrabbit.value.StringValue;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.*;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

@Component(immediate = true,
        service = EventHandler.class,
        property = {Constants.SERVICE_DESCRIPTION + "= Download event handler",
                EventConstants.EVENT_TOPIC + "=org/apache/sling/api/resource/Resource/ADDED",
                EventConstants.EVENT_FILTER + "=(path=/var/eventing/jobs/finished/async.download/*)"

        })
public class SimpleResourceEventHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(SimpleResourceEventHandler.class);

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    public void handleEvent(Event event) {
        ResourceResolver resourceResolver = null;
        try {

            resourceResolver = AEMUtil.getResourceResolver(resolverFactory);
            log.info("Event properties : {}", event.getPropertyNames());
            String[] props = event.getPropertyNames();

            if (event.containsProperty("userid") && event.containsProperty("path")) {
                log.info("USER ID::" + event.getProperty("userid").toString());
                log.info("PATH::" + event.getProperty("path").toString());

                String path = event.getProperty("path").toString();

                Resource resource = resourceResolver.getResource(path);
                log.debug("RESOURCE" + resource);

                Node downloadNode = resourceResolver.getResource(path).adaptTo(Node.class);

                if (downloadNode != null && downloadNode.getPrimaryNodeType().isNodeType("slingevent:Job")) {
                    log.info("FOUND THE NODE: PATH::" + event.getProperty("path").toString());
                    log.info("TARGETS ::" + downloadNode.getProperty("targets").getValues().toString());
                    log.info("USER ::" + downloadNode.getProperty("user").getString());

                    String userName = downloadNode.getProperty("user").getString();
                    Value[] jsonTarget = downloadNode.getProperty("targets").getValues();

                    HashSet<String> downloadSet = new HashSet<>();
                    if (jsonTarget != null) {
                        for (Value jsonValue : jsonTarget) {
                            String val = jsonValue.getString();
                            String fullPath = CommonUtil.lookupJson(val, "/binaryUri");
                            log.debug("FULL PATH::" + fullPath);
                            String pathSplit[] = fullPath.split("/jcr:content");
                            if (pathSplit.length > 0) {
                                downloadSet.add(pathSplit[0]);
                            }
                        }
                    }
                    trackDownloads(resourceResolver, downloadSet, userName);
                }
            }


        } catch (LoginException e) {
            log.error("Exception occurred", e);
        } catch (PathNotFoundException e) {
            throw new RuntimeException(e);
        } catch (RepositoryException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (PersistenceException e) {
            throw new RuntimeException(e);
        } finally {
            if (resourceResolver != null) {
                resourceResolver.close();
            }
        }
    }

    private void trackDownloads(ResourceResolver resourceResolver, HashSet<String> downloadSet, String userName) throws LoginException, RepositoryException, PersistenceException {
        Node downloadNode = resourceResolver.getResource("/var/dam/downloadaudit").adaptTo(Node.class);
        for (String download : downloadSet) {
            Node auditNode = createAssetPath(download, downloadNode, resourceResolver);
            if (!auditNode.hasNode("track")) {
                Node trackNode = auditNode.addNode("track", "nt:unstructured");
                resourceResolver.commit();
                Value value = new StringValue(userName);
                ArrayList<Value> valueArrayList = new ArrayList<Value>();
                valueArrayList.add(value);
                trackNode.setProperty("users", valueArrayList.toArray(new Value[0]));
            } else {
                Node trackNode = auditNode.getNode("track");
                Property property = trackNode.getProperty("users");
                Value[] valueArray = property.getValues();
                ArrayList<Value> valueArrayList = new ArrayList<Value>();
                Collections.addAll(valueArrayList, valueArray);
                Value value = new StringValue(userName);
                valueArrayList.add(value);
                trackNode.setProperty("users", valueArrayList.toArray(new Value[0]));
            }
            resourceResolver.commit();
        }
    }

    private Node createAssetPath(String download, Node downloadNode, ResourceResolver resourceResolver) throws RepositoryException, PersistenceException {
        String pathArr[] = download.split("/");
        for (String pathSeg : pathArr) {
            if (!downloadNode.hasNode(pathSeg)) {
                Node assetNode = downloadNode.addNode(pathSeg);
                resourceResolver.commit();
            }
            downloadNode = downloadNode.getNode(pathSeg);
        }
        return downloadNode;
    }


}