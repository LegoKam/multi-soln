package com.showcase.core.workflows;


import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ContentFragmentManager;
import com.adobe.granite.asset.api.Asset;
import com.adobe.granite.asset.api.AssetManager;
import com.adobe.granite.asset.api.Rendition;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;
import com.day.text.csv.Csv;
import com.showcase.core.util.AEMUtil;
import org.apache.commons.compress.utils.FileNameUtils;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import java.io.InputStream;
import java.io.InputStreamReader;

@Component(service = WorkflowProcess.class, property = {"process.label=CSV 2 CF Process"})
public class CsvFileToContentFragment implements WorkflowProcess {


    private static final Logger log = LoggerFactory.getLogger(CsvFileToContentFragment.class);

    @Reference
    ResourceResolverFactory resourceResolverFactory;

    @Reference
    private ContentFragmentManager fragmentManager;


    @Override
    public void execute(WorkItem item, WorkflowSession session, MetaDataMap map) throws WorkflowException {

        log.info("*****CSV 2 CF Services Start*****");
        String payloadPath = AEMUtil.getPayload(item.getWorkflowData());
        log.info("Log path::" + payloadPath);
        String baseFolderPath = null;

        ResourceResolver resourceResolver = null;
        try {
            resourceResolver = AEMUtil.getResourceResolver(resourceResolverFactory);
            AssetManager assetManager = resourceResolver.adaptTo(AssetManager.class);
            if (payloadPath != null && payloadPath.endsWith("metadata")) {
                Resource csvFileResource = resourceResolver.getResource(payloadPath).getParent().getParent();
                String csvFilePath = csvFileResource.getPath();
                String pagePath = null;
                String pageTemplate = null;
                Asset csvAsset = assetManager.getAsset(csvFilePath);
                Rendition originalCSVRendition = csvAsset.getRendition("original");
                InputStream is = originalCSVRendition.getStream();

                Csv csv = new Csv();
                csv.setFieldDelimiter(',');
                csv.setLineSeparator("\r\n");
                ContentFragment contentFragment = null;

                java.util.Iterator<java.lang.String[]> rowIterator = csv.read(new InputStreamReader(is));
                while (rowIterator.hasNext()) {
                    String[] kv = rowIterator.next();
                    String key = kv[0];
                    String value = kv[1];
                    log.info("KEY ::" + kv[0]);
                    log.info("VALUE ::" + kv[1]);

                    if (key != null && key.equals("name")) {
                        continue;
                    }
                    if (key != null && key.equals("pageTemplate")) {
                        pageTemplate = value;
                        continue;
                    }
                    if (key != null && key.equals("pagePath")) {
                        pagePath = value;
                        continue;
                    }
                    if (key != null && key.equals("product")) {
                        createPage(pagePath, value, pageTemplate, value, resourceResolver);
                        continue;
                    }
                    if (key != null && key.equals("cfTemplate")) {

                        String fileName = resourceResolver.getResource(csvFilePath).getName();
                        String file = FileNameUtils.getBaseName(fileName);
                        Node sourceCFNode = resourceResolver.getResource(value).adaptTo(Node.class);
                        Node destCFNode = resourceResolver.getResource(csvFilePath).getParent().adaptTo(Node.class);
                        Node destNode = JcrUtil.copy(sourceCFNode, destCFNode, file);

                        contentFragment = AEMUtil.getContentFragment(destNode.getPath(), resourceResolver);
                        resourceResolver.commit();

                        item.getWorkflowData().getMetaDataMap().put("ContentFragment", destNode.getPath());

                        continue;
                    }
                    if (key != null && contentFragment != null) {
                        ContentElement textField = contentFragment.getElement(key);
                        textField.setContent(value, "text/plain");
                    }
                }
                resourceResolver.commit();
            }
        } catch (Exception e) {
            log.error("Exception::", e);
        } finally {
            if (resourceResolver != null) {
                resourceResolver.close();
            }
        }
    }

    private void createPage(String pagePath, String pageName, String pageTemplate, String title, ResourceResolver resourceResolver) throws WCMException, PersistenceException {
        PageManager pageManager = resourceResolver.adaptTo(PageManager.class);
        Page newPage = pageManager.getPage(pagePath + "/" + pageName);
        if (newPage == null) {
            pageManager.create(pagePath, pageName, pageTemplate, title);
            resourceResolver.commit();
            log.info("Create a page for iPhone");
        } else {
            log.info("this page----" + pagePath + "/" + pageName + " ----- already exists!");
        }

    }


}
