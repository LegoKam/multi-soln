package com.showcase.core.config;


/*
 * This a proof of concept that demonstrates AEM - Pimcore integration. Do not use this code in Production.
 * At a high level it demonstrates the pulling and pushing of data from PimCore over to AEM.
 * POC demonstrates:
 *  1. Read Published Product SKUs from PimCore and create SKU folders in AEM under /content/dam/breville.
 *  2. Once an Asset from a Product SKU folder is approved, AEM syncs the Dynamic Media URL of the asset into PimCore.
 *
 * The below values can be overridden using an OSGi config file.
 */

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;

public @interface PollerConfig {

    @AttributeDefinition(name = "CRON Expression", description = "CRON Expression", type = AttributeType.STRING)
    String cronExp() default "0 * * * * ?";

    @AttributeDefinition(name = "username", description = "username", type = AttributeType.STRING)
    String username() default "kam";

    @AttributeDefinition(name = "password", description = "username", type = AttributeType.STRING)
    String password() default "kam";

    @AttributeDefinition(name = "authorServer", description = "authorServer", type = AttributeType.STRING)
    String authorServer() default "https://author-p129970-e1316086.adobeaemcloud.com/mnt/overlay/granite/ui/content/shell/header/actions/pulse.data.json";

}