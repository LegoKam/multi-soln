package com.showcase.core.workflows;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.FragmentData;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowData;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.*;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.*;
import java.util.stream.Collectors;

@Component(service = WorkflowProcess.class, property = {
        "process.label=Create Page From Content Fragment"})
public class CreatePageWithContentFragment implements WorkflowProcess {

    private static final Logger log = LoggerFactory.getLogger(CreatePageWithContentFragment.class);
    private static String ARTICLE_PAGE_TEMPLATE;// = "/conf/wknd/settings/wcm/templates/article-page-template";
    private static String VARIATION;// = "master";
    private static String DISPLAY_MODE;// = "singleText";
    private static String FIELD_TO_DISPLAY;// = "main";
    private static String PATH_TO_CONTENTFRAGMENT;// = "root/container/container/contentfragment";
    private static String FALL_BACK_PAGE_LOC;// = "/content/wknd/language-masters/en/magazine";

    @Reference
    ResourceResolverFactory resourceResolverFactory;

    private static final String PROCESS_ARGS = "PROCESS_ARGS";


    @Override
    public void execute(WorkItem item, WorkflowSession session, MetaDataMap map) throws WorkflowException {
        log.info("*****Create Page With Content Fragment Services Start*****");

        initVariables(map);

        ResourceResolver resourceResolver = null;
        try {
            resourceResolver = getResourceResolver();
            String payloadPath = getPayload(item.getWorkflowData());

            Node masterNode = resourceResolver.getResource(payloadPath).adaptTo(Node.class);

            String pageTitle = null;
            try {
                pageTitle = masterNode.getProperty("title").getValue().getString();
            } catch (RepositoryException e) {
                pageTitle = masterNode.getName();
            }
            String pagelocation = null;

            try {
                pagelocation = masterNode.getProperty("pagelocation").getValue().getString();
            } catch (RepositoryException e) {
                pagelocation = FALL_BACK_PAGE_LOC;
            }


//            // Get the content fragment
//            ContentFragment contentFragment = getContentFragment(payloadPath, resourceResolver);
//            log.debug("Content Fragment:: " + contentFragment);
//
//            // get content fragment elements
//            Iterator<ContentElement> contentElements = contentFragment.getElements();
//

//
//            while (contentElements.hasNext()) {
//                ContentElement contentElement = (ContentElement) contentElements.next();
//                String elementName = contentElement.getName();
//                FragmentData fragmentData = contentElement.getValue();
//                String elementValue = (String) fragmentData.getValue();
//                if (elementName.toLowerCase().contains("title")) {
//                    pageTitle = elementValue;
//                }
//
//                if (elementName.toLowerCase().contains("pagelocation")) {
//                    pagelocation = elementValue;
//                }
//            }

            String cfReference = payloadPath;
            Node cfNode = null;
            if (payloadPath.contains("/jcr:content/data/master")) {
                cfReference = resourceResolver.getResource(payloadPath).getParent().getParent().getParent().getPath();
            }
            cfNode = resourceResolver.getResource(cfReference).adaptTo(Node.class);
            String pageName = cfNode.getName();

            Node pageNode = createPageWithCF(resourceResolver, pagelocation, pageTitle, pageName, cfReference);
            syncTags(resourceResolver, cfNode, pageNode);

        } catch (LoginException | RepositoryException e) {
            throw new RuntimeException(e);
        } finally {
            resourceResolver.close();
        }
    }

    private void syncTags(ResourceResolver resourceResolver, Node cfNode, Node pageNode) {
        try {
            Node cfMetadataNode = cfNode.getNode("jcr:content").getNode("metadata");
            if (cfMetadataNode.hasProperty("cq:tags")) {
                Property cfTagProperty = cfMetadataNode.getProperty("cq:tags");
                Node pageContentNode = pageNode.getNode("jcr:content");
                pageContentNode.setProperty("cq:tags", cfTagProperty.getValues());
                resourceResolver.commit();
            }
        } catch (RepositoryException e) {
            log.error("ERROR", e);
            throw new RuntimeException(e);
        } catch (PersistenceException e) {
            log.error("ERROR", e);
            throw new RuntimeException(e);
        }
    }

    private Node createPageWithCF(ResourceResolver resourceResolver, String pageLocation, String pageTitle, String pageName, String payloadPath) {

        // Create the page using the template
        try {

            Page createdPage = null;

            PageManager pageManager = resourceResolver.adaptTo(PageManager.class);
            Resource parentPageResource = resourceResolver.getResource(pageLocation);
            Resource templateResource = resourceResolver.getResource(ARTICLE_PAGE_TEMPLATE);

            Node pageParentNode = parentPageResource.adaptTo(Node.class);

            if (pageParentNode.hasNode(pageName)) {
                log.info("Page already exists, hence skipping");
                log.info("Parent Node: " + pageParentNode.getPath());
                log.info("Page Title: " + pageName);
                return pageParentNode.getNode(pageName);
            }

            createdPage = Objects.requireNonNull(pageManager, "Page manager could not be instantiated").create(pageLocation,
                    pageName, Objects.requireNonNull(templateResource, "template resource is null").getPath(), pageTitle);

            // Update page properties if needed
            if (createdPage != null) {
                resourceResolver.commit();

                setCFProperties(resourceResolver, createdPage, payloadPath);

            }
            return resourceResolver.getResource(createdPage.getPath()).adaptTo(Node.class);

        } catch (WCMException | PersistenceException | RepositoryException e) {
            log.error("ERROR", e);
            throw new RuntimeException(e);
        }


    }

    private void setCFProperties(ResourceResolver resourceResolver, Page createdPage, String payloadPath) {
        try {
            Resource cfResource = createdPage.getContentResource().getChild(PATH_TO_CONTENTFRAGMENT);
            Node cfNode = Objects.requireNonNull(cfResource,
                    "Cannot adapt a null page resource to a node - check if the cf component exists").adaptTo(Node.class);
            Objects.requireNonNull(cfNode, "Display mode property is empty").setProperty("displayMode", DISPLAY_MODE);
            cfNode.setProperty("fragmentPath", payloadPath);
            cfNode.setProperty("variationName", VARIATION);
            cfNode.setProperty("elementNames", FIELD_TO_DISPLAY);

            resourceResolver.commit();
        } catch (RepositoryException e) {
            log.error("ERROR", e);
            throw new RuntimeException(e);
        } catch (PersistenceException e) {
            log.error("ERROR", e);
            throw new RuntimeException(e);
        }

    }

    private String getPayload(WorkflowData workflowData) {
        String path = "";
        if ("JCR_PATH".equals(workflowData.getPayloadType())) {
            path = (String) workflowData.getPayload();
        } else {
            path = (String) workflowData.getPayload();
        }
        log.info("***** payload path*****" + path);
        return path;
    }

    private ContentFragment getContentFragment(String path, ResourceResolver resourceResolver) {
        Resource cfResource = resourceResolver.getResource(path);
        return Objects.requireNonNull(cfResource).adaptTo(ContentFragment.class);
    }

    private ResourceResolver getResourceResolver() throws LoginException {
        Map<String, Object> param = new HashMap<>();
        param.put(ResourceResolverFactory.SUBSERVICE, "content-svc-admin");
        return resourceResolverFactory.getServiceResourceResolver(param);
    }

    private static void initVariables(MetaDataMap args) {
        String argumentsString = args.get(PROCESS_ARGS, "string");
        String[] argsArray = StringUtils.split(argumentsString, System.getProperty("line.separator"));
        Map<String, String> argsMap = Arrays.stream(argsArray)
                .map(String::trim)
                .map(s -> s.split(" = "))
                .filter(s -> s.length > 1)
                .collect(Collectors.toMap(s -> s[0].trim(), s -> s[1].trim()));
        ARTICLE_PAGE_TEMPLATE = argsMap.get("ARTICLE_PAGE_TEMPLATE");
        VARIATION = argsMap.get("VARIATION");
        DISPLAY_MODE = argsMap.get("DISPLAY_MODE");
        FIELD_TO_DISPLAY = argsMap.get("FIELD_TO_DISPLAY");
        PATH_TO_CONTENTFRAGMENT = argsMap.get("PATH_TO_CONTENTFRAGMENT");
    }

}