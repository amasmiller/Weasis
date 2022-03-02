/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import com.formdev.flatlaf.FlatClientProperties;
import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;
import com.github.lgooddatepicker.optionalusertools.DateChangeListener;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.QueryRetrieveLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.auth.OAuth2ServiceFactory;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GroupCheckBoxMenu;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.ui.util.CalendarUtil;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ImportDicom;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.RetrieveType;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;
import org.weasis.dicom.explorer.pref.node.AuthenticationPersistence;
import org.weasis.dicom.explorer.pref.node.DefaultDicomNode;
import org.weasis.dicom.explorer.pref.node.DicomWebNode;
import org.weasis.dicom.explorer.pref.node.DicomWebNode.WebType;
import org.weasis.dicom.explorer.rs.RsQueryParams;
import org.weasis.dicom.op.CFind;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.tool.DicomListener;

public class DicomQrView extends AbstractItemDialogPage implements ImportDicom {

  private static final Logger LOGGER = LoggerFactory.getLogger(DicomQrView.class);
  private DicomWebNode retrieveNode;

  public enum Period {
    ALL(Messages.getString("DicomQrView.all_dates"), null),

    TODAY(Messages.getString("DicomQrView.today"), LocalDate.now()),

    YESTERDAY(Messages.getString("DicomQrView.yesterday"), LocalDate.now().minusDays(1)),

    BEFORE_YESTERDAY(
        Messages.getString("DicomQrView.day_before_yest"), LocalDate.now().minusDays(2)),

    CUR_WEEK(
        Messages.getString("DicomQrView.this_week"),
        LocalDate.now().with(WeekFields.of(LocalUtil.getLocaleFormat()).dayOfWeek(), 1),
        LocalDate.now()),

    CUR_MONTH(
        Messages.getString("DicomQrView.this_month"),
        LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()),
        LocalDate.now()),

    CUR_YEAR(
        Messages.getString("DicomQrView.this_year"),
        LocalDate.now().with(TemporalAdjusters.firstDayOfYear()),
        LocalDate.now()),

    LAST_DAY(
        Messages.getString("DicomQrView.last_24h"), LocalDate.now().minusDays(1), LocalDate.now()),

    LAST_2_DAYS(
        Messages.getString("DicomQrView.last_2_d"), LocalDate.now().minusDays(2), LocalDate.now()),

    LAST_3_DAYS(
        Messages.getString("DicomQrView.last_3_d"), LocalDate.now().minusDays(3), LocalDate.now()),

    LAST_WEEK(
        Messages.getString("DicomQrView.last_w"), LocalDate.now().minusWeeks(1), LocalDate.now()),

    LAST_2_WEEKS(
        Messages.getString("DicomQrView.last_2_w"), LocalDate.now().minusWeeks(2), LocalDate.now()),

    LAST_MONTH(
        Messages.getString("DicomQrView.last_m"), LocalDate.now().minusMonths(1), LocalDate.now()),

    LAST_3_MONTHS(
        Messages.getString("DicomQrView.last_3_m"),
        LocalDate.now().minusMonths(3),
        LocalDate.now()),

    LAST_6_MONTHS(
        Messages.getString("DicomQrView.last_6_m"),
        LocalDate.now().minusMonths(6),
        LocalDate.now()),

    LAST_YEAR(
        Messages.getString("DicomQrView.last_year"),
        LocalDate.now().minusYears(1),
        LocalDate.now());

    private final String displayName;
    private final LocalDate start;
    private final LocalDate end;

    Period(String name, LocalDate date) {
      this.displayName = name;
      this.start = date;
      this.end = date;
    }

    Period(String name, LocalDate start, LocalDate end) {
      this.displayName = name;
      this.start = start;
      this.end = end;
    }

    public LocalDate getStart() {
      return start;
    }

    public LocalDate getEnd() {
      return end;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }

  private static final String LAST_SEL_NODE = "lastSelNode";
  private static final String LAST_CALLING_NODE = "lastCallingNode";
  private static final String LAST_RETRIEVE_TYPE = "lastRetrieveType";
  static final File tempDir =
      FileUtil.createTempDir(AppProperties.buildAccessibleTempDirectory("tmp", "qr")); // NON-NLS

  private final Border spaceY = BorderFactory.createEmptyBorder(10, 3, 0, 3);

  private final JComboBox<AbstractDicomNode> comboDestinationNode = new JComboBox<>();
  private final JTextField tfSearch = new JTextField(25);
  private final RetrieveTree tree = new RetrieveTree();

  private final JComboBox<TagW> comboTags =
      new JComboBox<>(
          TagD.getTagFromIDs(
              Tag.PatientName,
              Tag.PatientID,
              Tag.AccessionNumber,
              Tag.StudyID,
              Tag.StudyDescription,
              Tag.InstitutionName,
              Tag.ReferringPhysicianName,
              Tag.PerformingPhysicianName,
              Tag.NameOfPhysiciansReadingStudy));
  private final GroupCheckBoxMenu groupMod = new GroupCheckBoxMenu();
  private final DropDownButton modButton =
      new DropDownButton(
          "search_mod", // NON-NLS
          Messages.getString("DicomQrView.modalities"),
          GuiUtils.getDownArrowIcon(),
          groupMod) {
        @Override
        protected JPopupMenu getPopupMenu() {
          JPopupMenu menu =
              (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
          menu.setInvoker(this);
          return menu;
        }
      };
  private final GroupRadioMenu<Period> groupDate =
      new GroupRadioMenu<>() {
        @Override
        public void contentsChanged(ListDataEvent e) {
          super.contentsChanged(e);
          Period p = getSelectedItem();
          if (p != null) {
            startDatePicker.removeDateChangeListener(dateChangeListener);
            endDatePicker.removeDateChangeListener(dateChangeListener);
            startDatePicker.setDate(p.getStart());
            endDatePicker.setDate(p.getEnd());
            startDatePicker.addDateChangeListener(dateChangeListener);
            endDatePicker.addDateChangeListener(dateChangeListener);
          }
        }
      };
  private final DropDownButton dateButton =
      new DropDownButton(
          "search_date", // NON-NLS
          Messages.getString("DicomQrView.dates"),
          GuiUtils.getDownArrowIcon(),
          groupDate) {
        @Override
        protected JPopupMenu getPopupMenu() {
          JPopupMenu menu =
              (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
          menu.setInvoker(this);
          return menu;
        }
      };
  private final DateChangeListener dateChangeListener = a -> groupDate.setSelected(null);
  private final ActionListener destNodeListener = evt -> applySelectedArchive();
  private final ChangeListener queryListener = e -> dicomQuery();
  private final DatePicker startDatePicker = buildDatePicker();
  private final DatePicker endDatePicker = buildDatePicker();
  private final JComboBox<RetrieveType> comboDicomRetrieveType =
      new JComboBox<>(RetrieveType.values());
  private final JComboBox<AbstractDicomNode> comboCallingNode = new JComboBox<>();
  private final DicomListener dicomListener;
  private final ExecutorService executor =
      ThreadUtil.buildNewFixedThreadExecutor(3, "Dicom Q/R task"); // NON-NLS
  private final JSpinner limitSpinner = new JSpinner();
  private final JSpinner pageSpinner = new JSpinner();
  private QueryProcess process;
  private AuthMethod authMethod;
  private final CircularProgressBar progressBar = new CircularProgressBar();

  public DicomQrView() {
    super(Messages.getString("DicomQrView.title"));
    GuiUtils.setNumberModel(limitSpinner, 10, 0, 999, 5);
    GuiUtils.setNumberModel(pageSpinner, 1, 1, 99999, 1);
    initGUI();
    initialize(true);

    DicomListener dcmListener = null;
    try {
      dcmListener = new DicomListener(tempDir);
    } catch (IOException e) {
      LOGGER.error("Cannot creast DICOM listener", e);
    }
    dicomListener = dcmListener;
  }

  public void initGUI() {
    add(getArchivePanel());
    add(getCallingNodePanel());
    add(getSearchPanel());
    add(GuiUtils.boxVerticalStrut(ITEM_SEPARATOR_LARGE));
    add(getCtrlSearchPanel());
    tree.setBorder(UIManager.getBorder("ScrollPane.border"));
    add(tree);
  }

  public JPanel getArchivePanel() {
    JLabel lblDest = new JLabel(Messages.getString("DicomQrView.arc") + StringUtil.COLON);
    AbstractDicomNode.addTooltipToComboList(comboDestinationNode);
    GuiUtils.setPreferredWidth(comboDestinationNode, 250, 150);

    JLabel lblTetrieve = new JLabel(Messages.getString("DicomQrView.retrieve") + StringUtil.COLON);
    comboDicomRetrieveType.setToolTipText(Messages.getString("DicomQrView.msg_sel_type"));
    return GuiUtils.getFlowLayoutPanel(
        ITEM_SEPARATOR_SMALL,
        ITEM_SEPARATOR,
        lblDest,
        comboDestinationNode,
        GuiUtils.boxHorizontalStrut(BLOCK_SEPARATOR),
        lblTetrieve,
        comboDicomRetrieveType);
  }

  public JPanel getCallingNodePanel() {
    final JLabel lblDest =
        new JLabel(Messages.getString("DicomQrView.calling_node") + StringUtil.COLON);
    GuiUtils.setPreferredWidth(comboCallingNode, 230, 150);
    AbstractDicomNode.addTooltipToComboList(comboCallingNode);

    final JButton btnGerenralOptions = new JButton(Messages.getString("DicomQrView.more_opt"));
    btnGerenralOptions.addActionListener(
        e -> {
          PreferenceDialog dialog = new PreferenceDialog(SwingUtilities.getWindowAncestor(this));
          dialog.showPage(
              org.weasis.dicom.explorer.Messages.getString("DicomNodeListView.node_list"));
          GuiUtils.showCenterScreen(dialog);
          initNodeList();
        });
    return GuiUtils.getFlowLayoutPanel(
        ITEM_SEPARATOR_SMALL,
        ITEM_SEPARATOR,
        lblDest,
        comboCallingNode,
        GuiUtils.boxHorizontalStrut(BLOCK_SEPARATOR),
        btnGerenralOptions);
  }

  public JPanel getCtrlSearchPanel() {
    JLabel labelLimit = new JLabel(Messages.getString("limit") + StringUtil.COLON);
    limitSpinner.setToolTipText(Messages.getString("no.limit"));

    JLabel labelPage = new JLabel(Messages.getString("page") + StringUtil.COLON);
    pageSpinner.addChangeListener(queryListener);
    progressBar.setEnabled(false);

    JButton clearBtn = new JButton(Messages.getString("DicomQrView.clear"));
    clearBtn.setToolTipText(Messages.getString("DicomQrView.clear_search"));
    clearBtn.addActionListener(e -> clearItems());

    JButton searchBtn = new JButton(Messages.getString("DicomQrView.search"));
    searchBtn.setToolTipText(Messages.getString("DicomQrView.tips_dcm_query"));
    searchBtn.addActionListener(e -> dicomQuery());

    return GuiUtils.getFlowLayoutPanel(
        FlowLayout.TRAILING,
        ITEM_SEPARATOR_SMALL,
        ITEM_SEPARATOR,
        labelLimit,
        limitSpinner,
        GuiUtils.boxHorizontalStrut(BLOCK_SEPARATOR),
        labelPage,
        pageSpinner,
        GuiUtils.boxHorizontalStrut(BLOCK_SEPARATOR),
        progressBar,
        GuiUtils.boxHorizontalStrut(BLOCK_SEPARATOR),
        clearBtn,
        GuiUtils.boxHorizontalStrut(ITEM_SEPARATOR_LARGE),
        searchBtn);
  }

  public JPanel getSearchPanel() {
    final JPanel sPanel = GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR);
    sPanel.setLayout(new BoxLayout(sPanel, BoxLayout.Y_AXIS));
    sPanel.setBorder(
        BorderFactory.createCompoundBorder(
            spaceY, GuiUtils.getTitledBorder(Messages.getString("DicomQrView.search"))));
    List<Object> list = Stream.of(Modality.values()).collect(Collectors.toList());
    list.set(0, Messages.getString("DicomQrView.all_mod"));
    groupMod.setModel(list, true, true);
    modButton.setToolTipText(Messages.getString("DicomQrView.select_mod"));

    Period[] listDate = {
      Period.ALL,
      Period.TODAY,
      Period.YESTERDAY,
      Period.BEFORE_YESTERDAY,
      Period.CUR_WEEK,
      Period.CUR_MONTH,
      Period.CUR_YEAR,
      Period.LAST_DAY,
      Period.LAST_2_DAYS,
      Period.LAST_3_DAYS,
      Period.LAST_WEEK,
      Period.LAST_2_WEEKS,
      Period.LAST_MONTH,
      Period.LAST_3_MONTHS,
      Period.LAST_6_MONTHS,
      Period.LAST_YEAR
    };
    ComboBoxModel<Period> dataModel = new DefaultComboBoxModel<>(listDate);
    groupDate.setModel(dataModel);

    JLabel labelFrom = new JLabel(Messages.getString("DicomQrView.from"));
    JLabel labetTo = new JLabel(Messages.getString("DicomQrView.to"));

    sPanel.add(
        GuiUtils.getFlowLayoutPanel(
            ITEM_SEPARATOR_SMALL,
            ITEM_SEPARATOR,
            modButton,
            GuiUtils.boxXLastElement(BLOCK_SEPARATOR),
            dateButton,
            GuiUtils.boxXLastElement(ITEM_SEPARATOR_LARGE),
            labelFrom,
            startDatePicker.getComponentDateTextField(),
            GuiUtils.boxXLastElement(ITEM_SEPARATOR),
            labetTo,
            endDatePicker.getComponentDateTextField()));

    comboTags.setMaximumRowCount(15);
    GuiUtils.setPreferredWidth(comboTags, 230, 150);

    StringBuilder buf = new StringBuilder("<html>");
    buf.append(Messages.getString("DicomQrView.tips_wildcard"));
    buf.append("<br>&nbsp&nbsp&nbsp"); // NON-NLS
    buf.append(Messages.getString("DicomQrView.tips_star"));
    buf.append("<br>&nbsp&nbsp&nbsp"); // NON-NLS
    buf.append(Messages.getString("DicomQrView.tips_question"));
    buf.append("</html>");
    tfSearch.setToolTipText(buf.toString());
    tfSearch.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
    sPanel.add(
        GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR, comboTags, tfSearch));
    return sPanel;
  }

  private DatePicker buildDatePicker() {
    DatePicker picker = new DatePicker();
    DatePickerSettings settings = picker.getSettings();
    CalendarUtil.adaptCalendarColors(settings);

    JTextField textField = picker.getComponentDateTextField();
    settings.setFormatForDatesCommonEra(LocalUtil.getDateFormatter(FormatStyle.SHORT));
    settings.setFormatForDatesBeforeCommonEra(LocalUtil.getDateFormatter(FormatStyle.SHORT));
    GuiUtils.setPreferredWidth(textField, 145);
    picker.addDateChangeListener(dateChangeListener);

    textField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
    JButton calendarButton = picker.getComponentToggleCalendarButton();
    calendarButton.setMargin(null);
    calendarButton.setText(null);
    calendarButton.setIcon(ResourceUtil.getIcon(OtherIcon.CALENDAR));
    calendarButton.setFocusPainted(true);
    calendarButton.setFocusable(true);
    calendarButton.revalidate();
    Arrays.stream(calendarButton.getMouseListeners()).forEach(calendarButton::removeMouseListener);
    calendarButton.addActionListener(e -> picker.openPopup());
    textField.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, calendarButton);
    return picker;
  }

  private void clearItems() {
    tfSearch.setText(null);
    groupMod.selectAll();
    startDatePicker.setDate(null);
    endDatePicker.setDate(null);
    pageSpinner.removeChangeListener(queryListener);
    pageSpinner.setValue(1);
    pageSpinner.addChangeListener(queryListener);
    limitSpinner.setValue(10);
    stopCurrentProcess();
  }

  private void dicomQuery() {
    stopCurrentProcess();
    SearchParameters searchParams = buildCurrentSearchParameters();
    List<DicomParam> p = searchParams.getParameters();

    if (p.isEmpty() && (Integer) limitSpinner.getValue() < 1) {
      String message = Messages.getString("DicomQrView.msg_empty_query");
      int response =
          JOptionPane.showOptionDialog(
              WinUtil.getParentDialog(this),
              message,
              getTitle(),
              JOptionPane.YES_NO_OPTION,
              JOptionPane.WARNING_MESSAGE,
              null,
              null,
              null);
      if (response != 0) {
        return;
      }
    }

    AtomicBoolean running = new AtomicBoolean(true);

    AbstractDicomNode selectedItem = (AbstractDicomNode) comboDestinationNode.getSelectedItem();
    if (selectedItem instanceof final DefaultDicomNode node) {
      DefaultDicomNode callingNode = (DefaultDicomNode) comboCallingNode.getSelectedItem();

      // see http://dicom.nema.org/medical/dicom/current/output/html/part04.html#sect_C.6
      addReturnTags(p, CFind.PatientName);
      addReturnTags(p, CFind.PatientID);
      addReturnTags(p, CFind.PatientSex);
      addReturnTags(p, CFind.PatientBirthDate);
      addReturnTags(p, CFind.IssuerOfPatientID);

      addReturnTags(p, CFind.StudyInstanceUID);
      addReturnTags(p, CFind.StudyDescription);
      addReturnTags(p, CFind.StudyDate);
      addReturnTags(p, CFind.StudyTime);
      addReturnTags(p, CFind.AccessionNumber);
      addReturnTags(p, CFind.ReferringPhysicianName);
      addReturnTags(p, CFind.StudyID);
      addReturnTags(p, new DicomParam(Tag.InstitutionName));
      addReturnTags(p, new DicomParam(Tag.ModalitiesInStudy));
      addReturnTags(p, new DicomParam(Tag.NumberOfStudyRelatedSeries));
      addReturnTags(p, new DicomParam(Tag.NumberOfStudyRelatedInstances));

      AdvancedParams params = new AdvancedParams();
      ConnectOptions connectOptions = new ConnectOptions();
      connectOptions.setConnectTimeout(3000);
      connectOptions.setAcceptTimeout(5000);
      params.setConnectOptions(connectOptions);

      Runnable runnable =
          () -> {
            GuiExecutor.instance()
                .execute(
                    () -> {
                      progressBar.setEnabled(true);
                      progressBar.setIndeterminate(true);
                    });
            final DicomState state =
                CFind.process(
                    params,
                    callingNode.getDicomNodeWithOnlyAET(),
                    node.getDicomNode(),
                    (Integer) limitSpinner.getValue(),
                    QueryRetrieveLevel.STUDY,
                    p.toArray(new DicomParam[0]));

            if (running.get()) {
              GuiExecutor.instance()
                  .execute(
                      () -> {
                        progressBar.setEnabled(false);
                        progressBar.setIndeterminate(false);
                        if (state.getStatus() == Status.Success) {
                          displayResult(state);
                        } else {
                          LOGGER.error("Dicom cfind error: {}", state.getMessage());
                          JOptionPane.showMessageDialog(
                              this, state.getMessage(), null, JOptionPane.ERROR_MESSAGE);
                        }
                      });
            }
          };
      process = new QueryProcess(runnable, "DICOM C-FIND", running); // $NON-NLS-1$
      process.start();
    } else if (selectedItem instanceof final DicomWebNode node) {
      AuthMethod auth = AuthenticationPersistence.getAuthMethod(node.getAuthMethodUid());
      if (!OAuth2ServiceFactory.noAuth.equals(auth)) {
        String oldCode = auth.getCode();
        authMethod = auth;
        if (authMethod.getToken() == null) {
          return;
        }
        if (!Objects.equals(oldCode, authMethod.getCode())) {
          AuthenticationPersistence.saveMethod();
        }
      }

      Properties props = new Properties();
      props.setProperty(RsQueryParams.P_DICOMWEB_URL, node.getUrl().toString());
      Integer limit = (Integer) limitSpinner.getValue();
      Integer page = (Integer) pageSpinner.getValue();
      if (limit > 0) {
        props.setProperty(
            RsQueryParams.P_PAGE_EXT,
            String.format("&limit=%d&offset=%d", limit, (page - 1) * limit)); // NON-NLS
      }
      // props.setProperty(RsQueryParams.P_QUERY_EXT, "&includedefaults=false");
      this.retrieveNode = node;
      RsQuery rsquery = new RsQuery(new DicomModel(), props, p, authMethod, node.getHeaders());
      Runnable runnable =
          () -> {
            try {
              GuiExecutor.instance()
                  .execute(
                      () -> {
                        progressBar.setEnabled(true);
                        progressBar.setIndeterminate(true);
                      });
              rsquery.call();
              if (running.get()) {
                GuiExecutor.instance()
                    .execute(
                        () -> {
                          progressBar.setEnabled(false);
                          progressBar.setIndeterminate(false);
                          tree.setRetrieveTreeModel(new RetrieveTreeModel(rsquery.getDicomModel()));
                          tree.revalidate();
                          tree.repaint();
                        });
              }
            } catch (Exception e) {
              LOGGER.error("", e);
            }
          };
      process = new QueryProcess(runnable, "QIDO-RS", running); // $NON-NLS-1$
      process.start();
    }
  }

  private static void addReturnTags(List<DicomParam> list, DicomParam p) {
    if (list.stream().noneMatch(d -> d.getTag() == p.getTag())) {
      list.add(p);
    }
  }

  private void displayResult(DicomState state) {
    List<Attributes> items = state.getDicomRSP();
    DicomModel dicomModel = new DicomModel();
    if (items != null) {
      for (int i = 0; i < items.size(); i++) {
        Attributes item = items.get(i);
        LOGGER.trace("===========================================");
        LOGGER.trace("CFind Item {}", (i + 1));
        LOGGER.trace("===========================================");
        LOGGER.trace("{}", item.toString(100, 150));

        RsQuery.populateDicomModel(dicomModel, item);
      }
    }
    tree.setRetrieveTreeModel(new RetrieveTreeModel(dicomModel));
    tree.revalidate();
    tree.repaint();
  }

  protected void initialize(boolean afirst) {
    if (afirst) {
      initNodeList();
    }
  }

  private void initNodeList() {
    comboCallingNode.removeAllItems();
    AbstractDicomNode.loadDicomNodes(
        comboCallingNode,
        AbstractDicomNode.Type.DICOM_CALLING,
        AbstractDicomNode.UsageType.RETRIEVE);
    restoreNodeSelection(comboCallingNode.getModel(), LAST_CALLING_NODE);

    comboDestinationNode.removeActionListener(destNodeListener);
    comboDestinationNode.removeAllItems();
    AbstractDicomNode.loadDicomNodes(
        comboDestinationNode, AbstractDicomNode.Type.DICOM, UsageType.RETRIEVE);
    AbstractDicomNode.loadDicomNodes(
        comboDestinationNode, AbstractDicomNode.Type.WEB, UsageType.RETRIEVE, WebType.QIDORS);
    restoreNodeSelection(comboDestinationNode.getModel(), LAST_SEL_NODE);
    String lastType = DicomQrFactory.IMPORT_PERSISTENCE.getProperty(LAST_RETRIEVE_TYPE);
    if (lastType != null) {
      try {
        comboDicomRetrieveType.setSelectedItem(RetrieveType.valueOf(lastType));
      } catch (Exception e) {
        // Do nothing
      }
    }
    applySelectedArchive();
    comboDestinationNode.addActionListener(destNodeListener);
  }

  private void applySelectedArchive() {
    Object selectedItem = comboDestinationNode.getSelectedItem();
    boolean dcmOption = selectedItem instanceof DefaultDicomNode;
    comboDicomRetrieveType.setEnabled(dcmOption);
    comboCallingNode.setEnabled(dcmOption);
    pageSpinner.setEnabled(!dcmOption);
    pageSpinner.removeChangeListener(queryListener);
    pageSpinner.setValue(1);
    pageSpinner.addChangeListener(queryListener);
    stopCurrentProcess();
  }

  public void resetSettingsToDefault() {
    initialize(false);
  }

  private SearchParameters buildCurrentSearchParameters() {
    SearchParameters p = new SearchParameters(Messages.getString("DicomQrView.custom"));
    // Get value in text field
    String sTagValue = tfSearch.getText();
    TagW item = (TagW) comboTags.getSelectedItem();
    if (StringUtil.hasText(sTagValue) && item != null) {
      p.getParameters().add(new DicomParam(item.getId(), sTagValue));
    }

    // Get modalities selection
    if (groupMod.getModelList().stream().anyMatch(c -> !c.isSelected())) {
      p.getParameters()
          .add(
              new DicomParam(
                  Tag.ModalitiesInStudy,
                  groupMod.getModelList().stream()
                      .filter(c -> c.isSelected() && c.getObject() instanceof Modality)
                      .map(c -> ((Modality) c.getObject()).name())
                      .toArray(String[]::new)));
    }

    LocalDate sDate = startDatePicker.getDate();
    LocalDate eDate = endDatePicker.getDate();
    if (sDate != null || eDate != null) {
      String range = TagD.formatDicomDate(sDate) + "-" + TagD.formatDicomDate(eDate);
      p.getParameters().add(new DicomParam(Tag.StudyDate, range));
    }
    return p;
  }

  public void applyChange() {
    nodeSelectionPersistence(
        (AbstractDicomNode) comboDestinationNode.getSelectedItem(), LAST_SEL_NODE);
    nodeSelectionPersistence(
        (AbstractDicomNode) comboCallingNode.getSelectedItem(), LAST_CALLING_NODE);
    RetrieveType type = (RetrieveType) comboDicomRetrieveType.getSelectedItem();
    if (type != null) {
      DicomQrFactory.IMPORT_PERSISTENCE.setProperty(LAST_RETRIEVE_TYPE, type.name());
    }
  }

  private void nodeSelectionPersistence(AbstractDicomNode node, String key) {
    if (node != null) {
      DicomQrFactory.IMPORT_PERSISTENCE.setProperty(key, node.getDescription());
    }
  }

  private void restoreNodeSelection(ComboBoxModel<AbstractDicomNode> model, String key) {
    if (model != null) {
      String desc = DicomQrFactory.IMPORT_PERSISTENCE.getProperty(key);
      if (StringUtil.hasText(desc)) {
        for (int i = 0; i < model.getSize(); i++) {
          if (desc.equals(model.getElementAt(i).getDescription())) {
            model.setSelectedItem(model.getElementAt(i));
            break;
          }
        }
      }
    }
  }

  protected void updateChanges() {}

  protected void stopCurrentProcess() {
    final QueryProcess t = process;
    if (t != null) {
      process = null;
      t.running.set(false);
      t.interrupt();
    }
    GuiExecutor.instance()
        .execute(
            () -> {
              progressBar.setEnabled(false);
              progressBar.setIndeterminate(false);
              tree.setRetrieveTreeModel(new RetrieveTreeModel());
              tree.revalidate();
              tree.repaint();
            });
  }

  @Override
  public void closeAdditionalWindow() {
    applyChange();
    executor.shutdown();
  }

  @Override
  public void resetToDefaultValues() {}

  private List<String> getCheckedStudies(TreePath[] paths) {
    List<String> studies = new ArrayList<>();
    for (TreePath treePath : paths) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
      if (node.getUserObject() instanceof MediaSeriesGroup study) {
        String uid = TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
        if (StringUtil.hasText(uid)) {
          studies.add(uid);
        }
      }
    }
    return studies;
  }

  @Override
  public void importDICOM(DicomModel explorerDcmModel, JProgressBar info) {
    List<String> studies = getCheckedStudies(tree.getCheckboxTree().getCheckingPaths());
    if (!studies.isEmpty()) {
      Object selectedItem = getComboDestinationNode().getSelectedItem();
      if (selectedItem instanceof DicomWebNode
          && ((DicomWebNode) selectedItem).getWebType() == WebType.QIDORS) {
        List<AbstractDicomNode> webNodes =
            AbstractDicomNode.loadDicomNodes(
                AbstractDicomNode.Type.WEB, AbstractDicomNode.UsageType.RETRIEVE, WebType.WADORS);
        String host = RetrieveTask.getHostname(((DicomWebNode) selectedItem).getUrl().getHost());
        String m1 = Messages.getString("DicomQrView.no.url.matches.with.the.qido");
        DicomWebNode wnode = RetrieveTask.getWadoUrl(this, host, webNodes, m1);
        if (wnode != null) {
          this.retrieveNode = wnode;
        }
      }
      executor.execute(new RetrieveTask(studies, explorerDcmModel, this));
    }
  }

  public JComboBox<AbstractDicomNode> getComboDestinationNode() {
    return comboDestinationNode;
  }

  public JComboBox<RetrieveType> getComboDicomRetrieveType() {
    return comboDicomRetrieveType;
  }

  public JComboBox<AbstractDicomNode> getComboCallingNode() {
    return comboCallingNode;
  }

  public DicomListener getDicomListener() {
    return dicomListener;
  }

  public DicomModel getDicomModel() {
    return tree.getRetrieveTreeModel().getDicomModel();
  }

  public DicomWebNode getRetrieveNode() {
    return retrieveNode;
  }

  public AuthMethod getAuthMethod() {
    return authMethod;
  }

  static class QueryProcess extends Thread {
    AtomicBoolean running;

    public QueryProcess(Runnable runnable, String name, AtomicBoolean running) {
      super(runnable, name);
      this.running = running;
    }
  }
}
