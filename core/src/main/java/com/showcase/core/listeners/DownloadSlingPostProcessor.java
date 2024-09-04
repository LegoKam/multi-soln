package com.showcase.core.listeners;

import com.adobe.cq.dam.download.api.DownloadException;
import com.adobe.cq.dam.download.api.DownloadFile;
import com.adobe.cq.dam.download.api.DownloadTarget;
import com.adobe.cq.dam.download.spi.DownloadTargetProcessor;
import com.day.cq.dam.api.Asset;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component(service = DownloadTargetProcessor.class)
public class DownloadSlingPostProcessor implements DownloadTargetProcessor {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final String PARAM_PATH = "path";



    @Override
    public Collection<DownloadFile> processTarget(DownloadTarget target, ResourceResolver resourceResolver) throws DownloadException  {
        List<DownloadFile> answer = new ArrayList<>();
        String path = target.getParameter(PARAM_PATH, String.class);

        Resource assetResource = resourceResolver.getResource(path);
        Asset asset = assetResource.adaptTo(Asset.class);

        return null;
    }

    @Override
    public String getTargetType() {
        return null;
    }

    @Override
    public Map<String, Boolean> getValidParameters() {
        return null;
    }
}
