package org.rhq.enterprise.gui.inventory.browse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.resource.InventorySummary;
import org.rhq.enterprise.gui.util.EnterpriseFacesContextUtility;
import org.rhq.enterprise.server.resource.ResourceBossLocal;
import org.rhq.enterprise.server.util.LookupUtil;

public class InventorySummaryUIBean {

    protected final Log log = LogFactory.getLog(InventorySummaryUIBean.class);

    private boolean showCounts = false;

    private int platformCount;
    private int serverCount;
    private int serviceCount;
    private int compatibleCount;
    private int mixedCount;
    private int groupDefinitionCount;

    private Subject subject;

    public InventorySummaryUIBean() {
        try {
            subject = EnterpriseFacesContextUtility.getSubject();
            load();
        } catch (Throwable t) {
            log.error("InventorySummaryUIBean constructor experienced an error: " + t.getMessage());
        }
    }

    private void load() {
        try {
            ResourceBossLocal resourceBoss = LookupUtil.getResourceBoss();
            InventorySummary summary = resourceBoss.getInventorySummary(subject);
            loadFromSummary(summary);
            showCounts = true;
        } catch (Throwable t) {
            log.error("InventorySummaryUIBean loader experienced an error: " + t.getMessage());
            showCounts = false;
        }
    }

    private void loadFromSummary(InventorySummary summary) {
        this.platformCount = summary.getPlatformCount();
        this.serverCount = summary.getServerCount();
        this.serviceCount = summary.getServiceCount();
        this.compatibleCount = summary.getCompatibleGroupCount();
        this.mixedCount = summary.getMixedGroupCount();
        this.groupDefinitionCount = summary.getGroupDefinitionCount();
    }

    public String getResourceCount() {
        return format(platformCount + serverCount + serviceCount);
    }

    public String getPlatformCount() {
        return format(platformCount);
    }

    public String getServerCount() {
        return format(serverCount);
    }

    public String getServiceCount() {
        return format(serviceCount);
    }

    public String getGroupCount() {
        return format(compatibleCount + mixedCount);
    }

    public String getCompatibleCount() {
        return format(compatibleCount);
    }

    public String getMixedCount() {
        return format(mixedCount);
    }

    public String getGroupDefinitionCount() {
        return format(groupDefinitionCount);
    }

    private String format(int count) {
        if (showCounts) {
            return "(" + count + ")";
        }
        return "";
    }
}
