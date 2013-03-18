/*
 * RHQ Management Platform
 * Copyright (C) 2005-2010 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.enterprise.gui.coregui.client.inventory.common.detail.operation.history;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.smartgwt.client.data.Criteria;
import com.smartgwt.client.data.DSRequest;
import com.smartgwt.client.data.Record;
import com.smartgwt.client.data.SortSpecifier;
import com.smartgwt.client.types.Alignment;
import com.smartgwt.client.types.Overflow;
import com.smartgwt.client.types.SortDirection;
import com.smartgwt.client.widgets.Canvas;
import com.smartgwt.client.widgets.HTMLPane;
import com.smartgwt.client.widgets.Window;
import com.smartgwt.client.widgets.events.CloseClickEvent;
import com.smartgwt.client.widgets.events.CloseClickHandler;
import com.smartgwt.client.widgets.grid.CellFormatter;
import com.smartgwt.client.widgets.grid.HoverCustomizer;
import com.smartgwt.client.widgets.grid.ListGridField;
import com.smartgwt.client.widgets.grid.ListGridRecord;
import com.smartgwt.client.widgets.grid.events.RecordClickEvent;
import com.smartgwt.client.widgets.grid.events.RecordClickHandler;

import org.rhq.core.domain.operation.OperationHistory;
import org.rhq.core.domain.operation.OperationRequestStatus;
import org.rhq.enterprise.gui.coregui.client.CoreGUI;
import org.rhq.enterprise.gui.coregui.client.ImageManager;
import org.rhq.enterprise.gui.coregui.client.LinkManager;
import org.rhq.enterprise.gui.coregui.client.components.table.TableAction;
import org.rhq.enterprise.gui.coregui.client.components.table.TableSection;
import org.rhq.enterprise.gui.coregui.client.components.table.TimestampCellFormatter;
import org.rhq.enterprise.gui.coregui.client.gwt.GWTServiceLookup;
import org.rhq.enterprise.gui.coregui.client.gwt.OperationGWTServiceAsync;
import org.rhq.enterprise.gui.coregui.client.inventory.resource.AncestryUtil;
import org.rhq.enterprise.gui.coregui.client.operation.OperationHistoryDataSource;
import org.rhq.enterprise.gui.coregui.client.util.message.Message;
import org.rhq.enterprise.gui.coregui.client.util.message.Message.Option;
import org.rhq.enterprise.gui.coregui.client.util.message.Message.Severity;

/**
 * @author Greg Hinkle
 * @author John Mazzitelli
 * @author Ian Springer
 */
public abstract class AbstractOperationHistoryListView<T extends AbstractOperationHistoryDataSource> extends
    TableSection<T> {

    private static final String HEADER_ICON = "subsystems/control/Operation_24.png";

    public AbstractOperationHistoryListView(T dataSource, String title) {
        super(title);
        setDataSource(dataSource);
        setHeaderIcon(HEADER_ICON);
    }

    public AbstractOperationHistoryListView(T dataSource, String title, Criteria criteria) {
        super(title, criteria);
        setDataSource(dataSource);
    }

    protected abstract boolean hasControlPermission();

    @Override
    protected void configureTable() {
        List<ListGridField> fields = createFields();
        setListGridFields(fields.toArray(new ListGridField[fields.size()]));

        // explicitly sort on started time so the user can see the last operation at the top and is sorted descendingly
        SortSpecifier sortSpec = new SortSpecifier(AbstractOperationHistoryDataSource.Field.STARTED_TIME,
            SortDirection.DESCENDING);
        getListGrid().setSort(new SortSpecifier[] { sortSpec });

        // the below addTableAction and the enclosing TableAction anon class code is taken from
        // OperationHistoryView. I don't know why we have an abstract operation history list hierarchy separate
        // from OperationHistoryView. Perhaps independently developed and the developer of one didn't know the other
        // existed. In any case, this code is almost identical as the table action in OperationHistoryView with the
        // exception that this code uses AbstractOperationHistoryDataSource.Field constants.
        addTableAction(MSG.common_button_cancel(), MSG.view_operationHistoryList_cancelConfirm(), new TableAction() {
            public boolean isEnabled(ListGridRecord[] selection) {
                int count = selection.length;
                for (ListGridRecord item : selection) {
                    if (!OperationRequestStatus.INPROGRESS.name().equals(
                        item.getAttribute(AbstractOperationHistoryDataSource.Field.STATUS))) {
                        count--; // one selected item was not in-progress, it doesn't count
                    }
                }
                return (count >= 1 && hasControlPermission());
            }

            public void executeAction(ListGridRecord[] selection, Object actionValue) {
                int numCancelRequestsSubmitted = 0;
                OperationGWTServiceAsync opService = GWTServiceLookup.getOperationService();
                for (ListGridRecord toBeCanceled : selection) {
                    // only cancel those selected operations that are currently in progress
                    if (OperationRequestStatus.INPROGRESS.name().equals(
                        toBeCanceled.getAttribute(AbstractOperationHistoryDataSource.Field.STATUS))) {
                        numCancelRequestsSubmitted++;
                        final int historyId = toBeCanceled.getAttributeAsInt(OperationHistoryDataSource.Field.ID);
                        opService.cancelOperationHistory(historyId, false, new AsyncCallback<Void>() {
                            public void onSuccess(Void result) {
                                Message msg = new Message(MSG.view_operationHistoryList_cancelSuccess(String
                                    .valueOf(historyId)), Severity.Info, EnumSet.of(Option.BackgroundJobResult));
                                CoreGUI.getMessageCenter().notify(msg);
                            };

                            public void onFailure(Throwable caught) {
                                Message msg = new Message(MSG.view_operationHistoryList_cancelFailure(String
                                    .valueOf(historyId)), caught, Severity.Error, EnumSet
                                    .of(Option.BackgroundJobResult));
                                CoreGUI.getMessageCenter().notify(msg);
                            };
                        });
                    }
                }
                CoreGUI.getMessageCenter().notify(
                    new Message(MSG.view_operationHistoryList_cancelSubmitted(String
                        .valueOf(numCancelRequestsSubmitted)), Severity.Info));
                refreshTableInfo();
            }
        });

        addTableAction(MSG.common_button_delete(), getDeleteConfirmMessage(), new TableAction() {
            public boolean isEnabled(ListGridRecord[] selection) {
                int count = selection.length;
                return (count >= 1 && hasControlPermission());
            }

            public void executeAction(ListGridRecord[] selection, Object actionValue) {
                deleteSelectedRecords();
            }
        });

        addTableAction(MSG.view_operationHistoryList_button_forceDelete(), getDeleteConfirmMessage(),
            new TableAction() {
                public boolean isEnabled(ListGridRecord[] selection) {
                    int count = selection.length;
                    return (count >= 1 && hasControlPermission());
                }

                public void executeAction(ListGridRecord[] selection, Object actionValue) {
                    DSRequest requestProperties = new DSRequest();
                    requestProperties.setAttribute("force", true);
                    deleteSelectedRecords(requestProperties);
                }
            });

        super.configureTable();
    }

    protected List<ListGridField> createFields() {
        List<ListGridField> fields = new ArrayList<ListGridField>();

        ListGridField idField = new ListGridField(AbstractOperationHistoryDataSource.Field.ID);
        idField.setWidth(38);
        fields.add(idField);

        ListGridField opNameField = new ListGridField(AbstractOperationHistoryDataSource.Field.OPERATION_NAME);
        opNameField.setWidth("34%");
        fields.add(opNameField);

        ListGridField subjectField = new ListGridField(AbstractOperationHistoryDataSource.Field.SUBJECT);
        subjectField.setWidth("33%");
        fields.add(subjectField);

        ListGridField statusField = createStatusField();
        fields.add(statusField);

        ListGridField startedTimeField = createStartedTimeField();
        startedTimeField.setWidth("33%");
        fields.add(startedTimeField);

        return fields;
    }

    protected ListGridField createStartedTimeField() {
        ListGridField startedTimeField = new ListGridField(AbstractOperationHistoryDataSource.Field.STARTED_TIME);
        startedTimeField.setAlign(Alignment.LEFT);
        startedTimeField.setCellAlign(Alignment.LEFT);
        startedTimeField.setCellFormatter(new TimestampCellFormatter() {
            public String format(Object value, ListGridRecord record, int rowNum, int colNum) {
                if (value != null) {
                    return super.format(value, record, rowNum, colNum);
                } else {
                    return "<i>" + MSG.view_operationHistoryList_notYetStarted() + "</i>";
                }
            }
        });
        startedTimeField.setShowHover(true);
        startedTimeField.setHoverCustomizer(TimestampCellFormatter
            .getHoverCustomizer(AbstractOperationHistoryDataSource.Field.STARTED_TIME));

        return startedTimeField;
    }

    protected ListGridField createStatusField() {
        ListGridField statusField = new ListGridField(AbstractOperationHistoryDataSource.Field.STATUS);
        statusField.setAlign(Alignment.CENTER);
        statusField.setCellAlign(Alignment.CENTER);
        statusField.setShowHover(true);
        statusField.setHoverCustomizer(new HoverCustomizer() {
            @Override
            public String hoverHTML(Object value, ListGridRecord record, int rowNum, int colNum) {
                String statusStr = record.getAttribute(AbstractOperationHistoryDataSource.Field.STATUS);
                OperationRequestStatus status = OperationRequestStatus.valueOf(statusStr);
                switch (status) {
                case SUCCESS: {
                    return MSG.common_status_success();
                }
                case FAILURE: {
                    return MSG.common_status_failed();
                }
                case INPROGRESS: {
                    return MSG.common_status_inprogress();
                }
                case CANCELED: {
                    return MSG.common_status_canceled();
                }
                }
                // should never get here
                return MSG.common_status_unknown();
            }
        });
        statusField.setCellFormatter(new CellFormatter() {
            public String format(Object o, ListGridRecord listGridRecord, int i, int i1) {
                OperationRequestStatus status = OperationRequestStatus.valueOf((String) o);
                String icon = ImageManager.getOperationResultsIcon(status);
                return Canvas.imgHTML(icon, 16, 16);
            }
        });
        statusField.addRecordClickHandler(new RecordClickHandler() {
            @Override
            public void onRecordClick(RecordClickEvent event) {
                Record record = event.getRecord();
                String statusStr = record.getAttribute(AbstractOperationHistoryDataSource.Field.STATUS);
                OperationRequestStatus status = OperationRequestStatus.valueOf(statusStr);
                if (status == OperationRequestStatus.FAILURE) {
                    final Window winModal = new Window();
                    winModal.setTitle(MSG.common_title_details());
                    winModal.setOverflow(Overflow.VISIBLE);
                    winModal.setShowMinimizeButton(false);
                    winModal.setShowMaximizeButton(true);
                    winModal.setIsModal(true);
                    winModal.setShowModalMask(true);
                    winModal.setAutoSize(true);
                    winModal.setAutoCenter(true);
                    winModal.setShowResizer(true);
                    winModal.setCanDragResize(true);
                    winModal.centerInPage();
                    winModal.addCloseClickHandler(new CloseClickHandler() {
                        @Override
                        public void onCloseClick(CloseClickEvent event) {
                            winModal.markForDestroy();
                        }
                    });

                    HTMLPane htmlPane = new HTMLPane();
                    htmlPane.setMargin(10);
                    htmlPane.setDefaultWidth(500);
                    htmlPane.setDefaultHeight(400);
                    String errorMsg = record.getAttribute(AbstractOperationHistoryDataSource.Field.ERROR_MESSAGE);
                    if (errorMsg == null) {
                        errorMsg = MSG.common_status_failed();
                    }
                    htmlPane.setContents("<pre>" + errorMsg + "</pre>");
                    winModal.addItem(htmlPane);
                    winModal.show();
                }
            }
        });
        statusField.setWidth(44);

        return statusField;
    }

    protected ListGridField createResourceField() {
        ListGridField resourceField = new ListGridField(AncestryUtil.RESOURCE_NAME, MSG.common_title_resource());
        resourceField.setAlign(Alignment.LEFT);
        resourceField.setCellAlign(Alignment.LEFT);
        resourceField.setCellFormatter(new CellFormatter() {
            public String format(Object o, ListGridRecord listGridRecord, int i, int i1) {
                String url = LinkManager.getResourceLink(listGridRecord.getAttributeAsInt(AncestryUtil.RESOURCE_ID));
                return LinkManager.getHref(url, o.toString());
            }
        });
        resourceField.setShowHover(true);
        resourceField.setHoverCustomizer(new HoverCustomizer() {

            public String hoverHTML(Object value, ListGridRecord listGridRecord, int rowNum, int colNum) {
                return AncestryUtil.getResourceHoverHTML(listGridRecord, 0);
            }
        });

        return resourceField;
    }

    protected ListGridField createAncestryField() {
        ListGridField ancestryField = AncestryUtil.setupAncestryListGridField();
        return ancestryField;
    }

    @Override
    protected void deleteSelectedRecords(DSRequest requestProperties) {
        final ListGridRecord[] recordsToBeDeleted = getListGrid().getSelectedRecords();
        final int numberOfRecordsToBeDeleted = recordsToBeDeleted.length;
        Boolean forceValue = (requestProperties != null && requestProperties
            .getAttributeAsBoolean(AbstractOperationHistoryDataSource.RequestAttribute.FORCE));
        boolean force = ((forceValue != null) && forceValue);
        final List<Integer> successIds = new ArrayList<Integer>();
        final List<Integer> failureIds = new ArrayList<Integer>();
        for (ListGridRecord record : recordsToBeDeleted) {
            final OperationHistory operationHistoryToRemove = getDataSource().copyValues(record);
            GWTServiceLookup.getOperationService().deleteOperationHistory(operationHistoryToRemove.getId(), force,
                new AsyncCallback<Void>() {
                    public void onSuccess(Void result) {
                        successIds.add(operationHistoryToRemove.getId());
                        handleCompletion(successIds, failureIds, numberOfRecordsToBeDeleted);
                    }

                    public void onFailure(Throwable caught) {
                        // TODO: i18n
                        CoreGUI.getErrorHandler().handleError("Failed to delete " + operationHistoryToRemove + ".",
                            caught);
                        failureIds.add(operationHistoryToRemove.getId());
                        handleCompletion(successIds, failureIds, numberOfRecordsToBeDeleted);
                    }
                });
        }
    }

    private void handleCompletion(List<Integer> successIds, List<Integer> failureIds, int numberOfRecordsToBeDeleted) {
        if ((successIds.size() + failureIds.size()) == numberOfRecordsToBeDeleted) {
            // TODO: i18n
            if (successIds.size() == numberOfRecordsToBeDeleted) {
                CoreGUI.getMessageCenter().notify(
                    new Message("Deleted " + numberOfRecordsToBeDeleted + " operation history items."));
            } else {
                CoreGUI.getMessageCenter().notify(
                    new Message("Deleted " + successIds.size()
                        + " operation history items, but failed to delete the items with the following IDs: "
                        + failureIds));
            }
            refresh();
        }
    }

    @Override
    protected String getDetailsLinkColumnName() {
        return AbstractOperationHistoryDataSource.Field.OPERATION_NAME;
    }

}
