/**
 *  Tracer - plugin for JOSM
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.openstreetmap.josm.plugins.tracer;

import static org.openstreetmap.josm.tools.I18n.tr;

import javax.swing.JPanel;
import javax.swing.DefaultListModel;
import java.util.Enumeration;
import java.util.List;
import java.util.LinkedList;

import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.spi.preferences.Config;


public class TracerPreferences extends DefaultTabPreferenceSetting {

    public JPanel thisPanel;

    private DefaultListModel m_modulesAvailableModel;
    private DefaultListModel m_modulesEnabledModel;

    private String KEY_MODULESAVAILABLE = "tracer.modulesavailable";
    private String KEY_MODULESENABLED = "tracer.modulesenabled";

    boolean  m_customTracerUrl;
    String   m_customTracerUrlText;

    private String KEY_TRACERURL = "tracer.customurl";
    private String KEY_TRACERURLVALUE = "tracer.customurlvalue";

    boolean  m_tracerAdjustPosition;
    double   m_tracerAdjustPositionLat;
    double   m_tracerAdjustPositionLon;

    private int m_tracerAdjustPositionLatVal;
    private int m_tracerAdjustPositionLonVal;
    private int m_tracerAdjustPositionLatSign;
    private int m_tracerAdjustPositionLonSign;


    private String KEY_TRACERADJUSTPOSITION = "tracer.adjustposition";
    private String KEY_TRACERADJUSTPOSITIONLAT = "tracer.adjustpositionlatvalue";
    private String KEY_TRACERADJUSTPOSITIONLON = "tracer.adjustpositionlonvalue";
    private String KEY_TRACERADJUSTPOSITIONLATSIGN = "tracer.adjustpositionlatvaluesign";
    private String KEY_TRACERADJUSTPOSITIONLONSIGN = "tracer.adjustpositionlonvaluesign";

    public boolean  m_customRuianUrl;
    String   m_customRuianUrlText;

    private String KEY_RUIANURL = "tracer.customruianurl";
    private String KEY_RUIANURLVALUE = "tracer.customurlruianvalue";

    boolean  m_ruianAdjustPosition;
    double   m_ruianAdjustPositionLat;
    double   m_ruianAdjustPositionLon;

    private int m_ruianAdjustPositionLatVal;
    private int m_ruianAdjustPositionLonVal;
    private int m_ruianAdjustPositionLatSign;
    private int m_ruianAdjustPositionLonSign;


    private String KEY_RUIANADJUSTPOSITION = "tracer.ruianadjustposition";
    private String KEY_RUIANADJUSTPOSITIONLAT = "tracer.ruianadjustpositionlatvalue";
    private String KEY_RUIANADJUSTPOSITIONLON = "tracer.ruianadjustpositionlonvalue";
    private String KEY_RUIANADJUSTPOSITIONLATSIGN = "tracer.ruianadjustpositionlatvaluesign";
    private String KEY_RUIANADJUSTPOSITIONLONSIGN = "tracer.ruianadjustpositionlonvaluesign";

//     private DefaultListModel AvailableModulesModel;
//     private DefaultListModel EnabledModulesModel;


    private javax.swing.JList AvailableModulesList;
    private javax.swing.JLabel AvilableModulesLabel;
    private javax.swing.JLabel EnabledModulesLabel;
    private javax.swing.JList EnabledModulesList;
    private javax.swing.JPanel MainConfigPanel;
    private javax.swing.JPanel ModulesConfigPanel;
    private javax.swing.JButton ModulesDownButton;
    private javax.swing.JButton ModulesLeftButton;
    private javax.swing.JButton ModulesRightButton;
    private javax.swing.JButton ModulesUpButton;
    private javax.swing.JCheckBox RuianAdjustCheckBox;
    private javax.swing.JSpinner RuianAdjustLatSpinner;
    private javax.swing.JSpinner RuianAdjustLonSpinner;
    private javax.swing.JPanel RuianConfigPanel;
    private javax.swing.JLabel RuianLatLabel;
    private javax.swing.JComboBox RuianLatSignComboBox;
    private javax.swing.JLabel RuianLatZeroLabel;
    private javax.swing.JLabel RuianLonLabel;
    private javax.swing.JComboBox RuianLonSignComboBox;
    private javax.swing.JLabel RuianLonZeroLabel;
    private javax.swing.JCheckBox RuianUrlCheckBox;
    private javax.swing.JTextField RuianUrlValueField;
    private javax.swing.JCheckBox TracerAdjustCheckBox;
    private javax.swing.JSpinner TracerAdjustLatSpinner;
    private javax.swing.JSpinner TracerAdjustLonSpinner;
    private javax.swing.JPanel TracerConfigPanel;
    private javax.swing.JLabel TracerLatLabel;
    private javax.swing.JComboBox TracerLatSignComboBox;
    private javax.swing.JLabel TracerLatZeroLabel;
    private javax.swing.JLabel TracerLonLabel;
    private javax.swing.JComboBox TracerLonSignComboBox;
    private javax.swing.JLabel TracerLonZeroLabel;
    private javax.swing.JCheckBox TracerUrlCheckBox;
    private javax.swing.JTextField TracerUrlValueField;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JPanel mainPanel;

    private static TracerPreferences singleton = null;

    public static TracerPreferences getInstance() {
        if (singleton == null)
            singleton = new TracerPreferences();
        return singleton;
    }

    /** Creates new form Preferences */
    @SuppressWarnings("unchecked")
    private TracerPreferences() {
        super(   "tracer-ruian-sml",
              tr("Tracer plugin settings"),
              tr("Customize Tracer plugin behaviour"));
        thisPanel = new JPanel();
        initComponents();

        // Enabled modules
        for (String s : Config.getPref().getList (KEY_MODULESAVAILABLE)) {
          m_modulesAvailableModel.addElement(s);
        }

        for (String s : Config.getPref().getList (KEY_MODULESENABLED)) {
          m_modulesEnabledModel.addElement(s);
        }

        // Tracer
        m_customTracerUrl             = Config.getPref().getBoolean(KEY_TRACERURL,         TracerUrlCheckBox.isSelected());
        m_customTracerUrlText         = Config.getPref().get       (KEY_TRACERURLVALUE,    TracerUrlValueField.getText());
        m_tracerAdjustPosition        = Config.getPref().getBoolean(KEY_TRACERADJUSTPOSITION,    TracerAdjustCheckBox.isSelected());
        m_tracerAdjustPositionLatSign = Config.getPref().getInt(KEY_TRACERADJUSTPOSITIONLATSIGN, TracerLatSignComboBox.getSelectedIndex());
        m_tracerAdjustPositionLonSign = Config.getPref().getInt(KEY_TRACERADJUSTPOSITIONLONSIGN, TracerLonSignComboBox.getSelectedIndex());
        m_tracerAdjustPositionLatVal  = Config.getPref().getInt(KEY_TRACERADJUSTPOSITIONLAT, (Integer) TracerAdjustLatSpinner.getValue());
        m_tracerAdjustPositionLonVal  = Config.getPref().getInt(KEY_TRACERADJUSTPOSITIONLON, (Integer) TracerAdjustLonSpinner.getValue());
        // RUIAN
        m_customRuianUrl             = Config.getPref().getBoolean(KEY_RUIANURL,          RuianUrlCheckBox.isSelected());
        m_customRuianUrlText         = Config.getPref().get       (KEY_RUIANURLVALUE,     RuianUrlValueField.getText());
        m_ruianAdjustPosition        = Config.getPref().getBoolean(KEY_RUIANADJUSTPOSITION,    RuianAdjustCheckBox.isSelected());
        m_ruianAdjustPositionLatSign = Config.getPref().getInt(KEY_RUIANADJUSTPOSITIONLATSIGN, RuianLatSignComboBox.getSelectedIndex());
        m_ruianAdjustPositionLonSign = Config.getPref().getInt(KEY_RUIANADJUSTPOSITIONLONSIGN, RuianLonSignComboBox.getSelectedIndex());
        m_ruianAdjustPositionLatVal  = Config.getPref().getInt(KEY_RUIANADJUSTPOSITIONLAT, (Integer) RuianAdjustLatSpinner.getValue());
        m_ruianAdjustPositionLonVal  = Config.getPref().getInt(KEY_RUIANADJUSTPOSITIONLON, (Integer) RuianAdjustLonSpinner.getValue());
        setLatLonAdjust();
    }

    public void reloadSettings() {
        TracerUrlCheckBox.setSelected(m_customTracerUrl);
        TracerUrlValueField.setText(m_customTracerUrlText);

        TracerAdjustCheckBox.setSelected(m_tracerAdjustPosition);
        TracerAdjustLatSpinner.setValue(new Integer(m_tracerAdjustPositionLatVal));
        TracerAdjustLonSpinner.setValue(new Integer(m_tracerAdjustPositionLonVal));
        TracerLatSignComboBox.setSelectedIndex(m_tracerAdjustPositionLatSign);
        TracerLonSignComboBox.setSelectedIndex(m_tracerAdjustPositionLonSign);

        RuianUrlCheckBox.setSelected(m_customRuianUrl);
        RuianUrlValueField.setText(m_customRuianUrlText);

        RuianAdjustCheckBox.setSelected(m_ruianAdjustPosition);
        RuianAdjustLatSpinner.setValue(new Integer(m_ruianAdjustPositionLatVal));
        RuianAdjustLonSpinner.setValue(new Integer(m_ruianAdjustPositionLonVal));
        RuianLatSignComboBox.setSelectedIndex(m_ruianAdjustPositionLatSign);
        RuianLonSignComboBox.setSelectedIndex(m_ruianAdjustPositionLonSign);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     */
    @SuppressWarnings("unchecked")
     private void initComponents() {

        mainPanel = new javax.swing.JPanel();

        MainConfigPanel = new javax.swing.JPanel();

        ModulesConfigPanel = new javax.swing.JPanel();
        AvilableModulesLabel = new javax.swing.JLabel();
        AvailableModulesList = new javax.swing.JList();
        jScrollPane3 = new javax.swing.JScrollPane();
        EnabledModulesLabel = new javax.swing.JLabel();
        EnabledModulesList = new javax.swing.JList();
        jScrollPane4 = new javax.swing.JScrollPane();

        ModulesRightButton = new javax.swing.JButton();
        ModulesLeftButton = new javax.swing.JButton();
        ModulesUpButton = new javax.swing.JButton();
        ModulesDownButton = new javax.swing.JButton();

        TracerConfigPanel = new javax.swing.JPanel();
        TracerUrlCheckBox = new javax.swing.JCheckBox();
        TracerUrlValueField = new javax.swing.JTextField();
        TracerAdjustCheckBox = new javax.swing.JCheckBox();
        TracerLatLabel = new javax.swing.JLabel();
        TracerLonLabel = new javax.swing.JLabel();
        TracerLatSignComboBox = new javax.swing.JComboBox();
        TracerLonSignComboBox = new javax.swing.JComboBox();
        TracerLatZeroLabel = new javax.swing.JLabel();
        TracerLonZeroLabel = new javax.swing.JLabel();
        TracerAdjustLatSpinner = new javax.swing.JSpinner();
        TracerAdjustLonSpinner = new javax.swing.JSpinner();

        RuianConfigPanel = new javax.swing.JPanel();
        RuianUrlCheckBox = new javax.swing.JCheckBox();
        RuianUrlValueField = new javax.swing.JTextField();
        RuianAdjustCheckBox = new javax.swing.JCheckBox();
        RuianLonLabel = new javax.swing.JLabel();
        RuianLatLabel = new javax.swing.JLabel();
        RuianLatSignComboBox = new javax.swing.JComboBox();
        RuianLonSignComboBox = new javax.swing.JComboBox();
        RuianLatZeroLabel = new javax.swing.JLabel();
        RuianLonZeroLabel = new javax.swing.JLabel();
        RuianAdjustLatSpinner = new javax.swing.JSpinner();
        RuianAdjustLonSpinner = new javax.swing.JSpinner();

        ModulesConfigPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        thisPanel.setLayout(new java.awt.GridLayout(1, 0));

        // Modules selection
        AvilableModulesLabel.setText(tr("Available modules:"));

        m_modulesAvailableModel = new DefaultListModel();
        AvailableModulesList.setModel(m_modulesAvailableModel);
        jScrollPane3.setViewportView(AvailableModulesList);

        AvailableModulesList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                AvailableModulesListValueChanged(evt);
            }
        });

        ModulesRightButton.setText(">>");
        ModulesRightButton.setEnabled(false);
        ModulesRightButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ModulesRightButtonActionPerformed(evt);
            }
        });

        ModulesLeftButton.setText("<<");
        ModulesLeftButton.setEnabled(false);
        ModulesLeftButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ModulesLeftButtonActionPerformed(evt);
            }
        });

        ModulesUpButton.setText(tr("Up"));
        ModulesUpButton.setEnabled(false);
        ModulesUpButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ModulesUpButtonActionPerformed(evt);
            }
        });

        ModulesDownButton.setText(tr("Down"));
        ModulesDownButton.setEnabled(false);
        ModulesDownButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ModulesDownButtonActionPerformed(evt);
            }
        });

        EnabledModulesLabel.setText(tr("Enabled modules:"));

        m_modulesEnabledModel = new DefaultListModel();
        EnabledModulesList.setModel(m_modulesEnabledModel);
        jScrollPane4.setViewportView(EnabledModulesList);

        EnabledModulesList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                EnabledModulesListValueChanged(evt);
            }
        });


        javax.swing.GroupLayout ModulesConfigPanelLayout = new javax.swing.GroupLayout(ModulesConfigPanel);
        ModulesConfigPanel.setLayout(ModulesConfigPanelLayout);
        ModulesConfigPanelLayout.setHorizontalGroup(
            ModulesConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ModulesConfigPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(ModulesConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 126, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(AvilableModulesLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(ModulesConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(ModulesUpButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(ModulesDownButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(ModulesRightButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(ModulesLeftButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(14, 14, 14)
                .addGroup(ModulesConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(EnabledModulesLabel))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        ModulesConfigPanelLayout.setVerticalGroup(
            ModulesConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ModulesConfigPanelLayout.createSequentialGroup()
                .addGroup(ModulesConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(ModulesConfigPanelLayout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addGroup(ModulesConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(AvilableModulesLabel)
                            .addComponent(EnabledModulesLabel)))
                    .addGroup(ModulesConfigPanelLayout.createSequentialGroup()
                        .addGap(27, 27, 27)
                        .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 131, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(ModulesConfigPanelLayout.createSequentialGroup()
                        .addGap(27, 27, 27)
                        .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 131, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(ModulesConfigPanelLayout.createSequentialGroup()
                        .addGap(33, 33, 33)
                        .addComponent(ModulesUpButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(ModulesRightButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(ModulesLeftButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(ModulesDownButton)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        // Tracer settins
        TracerConfigPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        TracerUrlCheckBox.setText(tr("Custom Classic Tracer server - requires TracerServer (.NET or Mono)"));
        TracerUrlCheckBox.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                tracerUrlChanged(evt);
            }
        });

        TracerUrlValueField.setText("http://localhost:5050/");
        TracerUrlValueField.setEnabled(false);


        TracerAdjustCheckBox.setText(tr("Adjust traced object position"));
        TracerAdjustCheckBox.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                tracerAdjustChanged(evt);
            }
        });

        TracerLatLabel.setText(tr("Lat:"));
        TracerLonLabel.setText(tr("Lon:"));
        TracerLatZeroLabel.setText("0.0000");
        TracerLonZeroLabel.setText("0.0000");


        TracerLatSignComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "+", "-" }));
        TracerLatSignComboBox.setSelectedIndex(0);
        TracerLatSignComboBox.setEnabled(false);

        TracerLonSignComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "+", "-" }));
        TracerLonSignComboBox.setSelectedIndex(0);
        TracerLonSignComboBox.setEnabled(false);

        TracerAdjustLatSpinner.setModel(new javax.swing.SpinnerNumberModel(0, 0, 999, 1));
        TracerAdjustLatSpinner.setToolTipText(tr("Set Lat adjustment. Interval <0;999>"));
        TracerAdjustLatSpinner.setEnabled(false);

        TracerAdjustLonSpinner.setModel(new javax.swing.SpinnerNumberModel(0, 0, 999, 1));
        TracerAdjustLonSpinner.setToolTipText(tr("Set Lon adjustment. Interval <0;999>"));
        TracerAdjustLonSpinner.setEnabled(false);

        javax.swing.GroupLayout TracerConfigPanelLayout = new javax.swing.GroupLayout(TracerConfigPanel);
        TracerConfigPanel.setLayout(TracerConfigPanelLayout);
        TracerConfigPanelLayout.setHorizontalGroup(
            TracerConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(TracerConfigPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(TracerConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(TracerUrlCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(TracerConfigPanelLayout.createSequentialGroup()
                        .addGroup(TracerConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(TracerAdjustCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 384, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(TracerConfigPanelLayout.createSequentialGroup()
                                .addGap(21, 21, 21)
                                .addGroup(TracerConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(TracerLatLabel)
                                    .addComponent(TracerLonLabel))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(TracerConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(TracerLatSignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(TracerLonSignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(12, 12, 12)
                                .addGroup(TracerConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(TracerConfigPanelLayout.createSequentialGroup()
                                        .addComponent(TracerLonZeroLabel)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(TracerAdjustLonSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(TracerConfigPanelLayout.createSequentialGroup()
                                        .addComponent(TracerLatZeroLabel)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(TracerAdjustLatSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                        .addGap(0, 21, Short.MAX_VALUE))
                    .addGroup(TracerConfigPanelLayout.createSequentialGroup()
                        .addGap(21, 21, 21)
                        .addComponent(TracerUrlValueField)
                        .addGap(3, 3, 3)))
                .addContainerGap())
        );
        TracerConfigPanelLayout.setVerticalGroup(
            TracerConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(TracerConfigPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(TracerUrlCheckBox)
                .addGap(4, 4, 4)
                .addComponent(TracerUrlValueField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(TracerAdjustCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(TracerConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(TracerConfigPanelLayout.createSequentialGroup()
                        .addComponent(TracerLatLabel)
                        .addGap(12, 12, 12)
                        .addComponent(TracerLonLabel))
                    .addGroup(TracerConfigPanelLayout.createSequentialGroup()
                        .addGroup(TracerConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(TracerLatSignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(TracerConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(TracerLatZeroLabel)
                                .addComponent(TracerAdjustLatSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGroup(TracerConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(TracerConfigPanelLayout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(TracerLonSignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(TracerConfigPanelLayout.createSequentialGroup()
                                .addGap(15, 15, 15)
                                .addComponent(TracerLonZeroLabel))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, TracerConfigPanelLayout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(TracerAdjustLonSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        // RUIAN settins
        RuianConfigPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        RuianUrlCheckBox.setText(tr("Custom Ruian server url"));
        RuianUrlCheckBox.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                ruianUrlChanged(evt);
            }
        });

        RuianUrlValueField.setText("http://josm.poloha.net/");
        RuianUrlValueField.setEnabled(false);

        RuianAdjustCheckBox.setText(tr("Adjust traced object position"));
        RuianAdjustCheckBox.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                ruianAdjustChanged(evt);
            }
        });

        RuianLatLabel.setText(tr("Lat:"));
        RuianLonLabel.setText(tr("Lon:"));
        RuianLatZeroLabel.setText("0.0000");
        RuianLonZeroLabel.setText("0.0000");

        RuianLatSignComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "+", "-" }));
        RuianLatSignComboBox.setSelectedIndex(0);
        RuianLatSignComboBox.setEnabled(false);

        RuianLonSignComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "+", "-" }));
        RuianLonSignComboBox.setSelectedIndex(0);
        RuianLonSignComboBox.setEnabled(false);

        RuianAdjustLatSpinner.setModel(new javax.swing.SpinnerNumberModel(0, 0, 999, 1));
        RuianAdjustLatSpinner.setToolTipText(tr("Set Lat adjustment. Interval <0;999>"));
        RuianAdjustLatSpinner.setEnabled(false);

        RuianAdjustLonSpinner.setModel(new javax.swing.SpinnerNumberModel(0, 0, 999, 1));
        RuianAdjustLonSpinner.setToolTipText(tr("Set Lon adjustment. Interval <0;999>"));
        RuianAdjustLonSpinner.setEnabled(false);

        // ==============
        // Dialog Layout
        // ==============
        javax.swing.GroupLayout RuianConfigPanelLayout = new javax.swing.GroupLayout(RuianConfigPanel);
        RuianConfigPanel.setLayout(RuianConfigPanelLayout);
        RuianConfigPanelLayout.setHorizontalGroup(
            RuianConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(RuianConfigPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(RuianConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(RuianAdjustCheckBox)
                    .addGroup(RuianConfigPanelLayout.createSequentialGroup()
                        .addGap(32, 32, 32)
                        .addGroup(RuianConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(RuianLatLabel)
                            .addComponent(RuianLonLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(RuianConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(RuianLatSignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(RuianLonSignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(12, 12, 12)
                        .addGroup(RuianConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(RuianLatZeroLabel)
                            .addComponent(RuianLonZeroLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(RuianConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(RuianAdjustLatSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(RuianAdjustLonSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(RuianConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(RuianUrlCheckBox, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 397, Short.MAX_VALUE)
                        .addGroup(RuianConfigPanelLayout.createSequentialGroup()
                            .addGap(21, 21, 21)
                            .addComponent(RuianUrlValueField))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        RuianConfigPanelLayout.setVerticalGroup(
            RuianConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(RuianConfigPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(RuianUrlCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(RuianUrlValueField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(RuianAdjustCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(RuianConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(RuianConfigPanelLayout.createSequentialGroup()
                        .addGroup(RuianConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(RuianConfigPanelLayout.createSequentialGroup()
                                .addComponent(RuianAdjustLatSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(4, 4, 4))
                            .addComponent(RuianLatLabel))
                        .addGroup(RuianConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(RuianConfigPanelLayout.createSequentialGroup()
                                .addGap(7, 7, 7)
                                .addComponent(RuianAdjustLonSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, RuianConfigPanelLayout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(RuianLonLabel))))
                    .addGroup(RuianConfigPanelLayout.createSequentialGroup()
                        .addGroup(RuianConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(RuianLatSignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(RuianLatZeroLabel))
                        .addGroup(RuianConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(RuianConfigPanelLayout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(RuianLonSignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(RuianConfigPanelLayout.createSequentialGroup()
                                .addGap(15, 15, 15)
                                .addComponent(RuianLonZeroLabel)))))
                .addContainerGap(25, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout MainConfigPanelLayout = new javax.swing.GroupLayout(MainConfigPanel);
        MainConfigPanel.setLayout(MainConfigPanelLayout);
        MainConfigPanelLayout.setHorizontalGroup(
            MainConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(MainConfigPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(MainConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(RuianConfigPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(TracerConfigPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ModulesConfigPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        MainConfigPanelLayout.setVerticalGroup(
            MainConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(MainConfigPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(ModulesConfigPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(TracerConfigPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(RuianConfigPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

//         javax.swing.GroupLayout layout = new javax.swing.GroupLayout(MainConfigPanel);
//         this.setLayout(layout);
//         layout.setHorizontalGroup(
//             layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
//             .addGroup(layout.createSequentialGroup()
//                 .addGap(0, 0, Short.MAX_VALUE)
//                 .addComponent(MainConfigPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
//                 .addGap(0, 0, Short.MAX_VALUE))
//         );
//         layout.setVerticalGroup(
//             layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
//             .addGroup(layout.createSequentialGroup()
//                 .addContainerGap()
//                 .addComponent(MainConfigPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
//         );
        thisPanel.add(MainConfigPanel);
    }

    private void tracerUrlChanged(javax.swing.event.ChangeEvent evt) {
        TracerUrlValueField.setEnabled(TracerUrlCheckBox.isSelected());
    }

    private void ruianUrlChanged(javax.swing.event.ChangeEvent evt) {
        RuianUrlValueField.setEnabled(RuianUrlCheckBox.isSelected());
    }

    private void tracerAdjustChanged(javax.swing.event.ChangeEvent evt) {
        TracerAdjustLatSpinner.setEnabled(TracerAdjustCheckBox.isSelected());
        TracerAdjustLonSpinner.setEnabled(TracerAdjustCheckBox.isSelected());
        TracerLatSignComboBox.setEnabled(TracerAdjustCheckBox.isSelected());
        TracerLonSignComboBox.setEnabled(TracerAdjustCheckBox.isSelected());
    }

    private void ruianAdjustChanged(javax.swing.event.ChangeEvent evt) {
        RuianAdjustLatSpinner.setEnabled(RuianAdjustCheckBox.isSelected());
        RuianAdjustLonSpinner.setEnabled(RuianAdjustCheckBox.isSelected());
        RuianLatSignComboBox.setEnabled(RuianAdjustCheckBox.isSelected());
        RuianLonSignComboBox.setEnabled(RuianAdjustCheckBox.isSelected());
    }

    private void setLatLonAdjust() {
      // Tracer
      if (m_tracerAdjustPosition) {
        m_tracerAdjustPositionLat = (double) m_tracerAdjustPositionLatVal / 10000000.0d;
        m_tracerAdjustPositionLon = (double) m_tracerAdjustPositionLonVal / 10000000.0d;
        if (m_tracerAdjustPositionLatSign == 1)
          m_tracerAdjustPositionLat *= -1;
        if (m_tracerAdjustPositionLonSign == 1)
          m_tracerAdjustPositionLon *= -1;
      } else {
        m_tracerAdjustPositionLat = 0;
        m_tracerAdjustPositionLon = 0;
      }
      // RUIAN
      if (m_ruianAdjustPosition) {
        m_ruianAdjustPositionLat = (double) m_ruianAdjustPositionLatVal / 10000000.0d;
        m_ruianAdjustPositionLon = (double) m_ruianAdjustPositionLonVal / 10000000.0d;
        if (m_ruianAdjustPositionLatSign == 1)
          m_ruianAdjustPositionLat *= -1;
        if (m_ruianAdjustPositionLonSign == 1)
          m_ruianAdjustPositionLon *= -1;
      } else {
        m_ruianAdjustPositionLat = 0;
        m_ruianAdjustPositionLon = 0;
      }
    }

    @SuppressWarnings("unchecked")
    private void ModulesUpButtonActionPerformed(java.awt.event.ActionEvent evt) {
      int currentIndex = EnabledModulesList.getSelectedIndex();
      String currentValue = (String) EnabledModulesList.getSelectedValue();
      m_modulesEnabledModel.add(currentIndex - 1, currentValue);
      m_modulesEnabledModel.remove(currentIndex + 1);
      EnabledModulesList.setSelectedIndex(currentIndex - 1);
    }

    @SuppressWarnings("unchecked")
    private void ModulesDownButtonActionPerformed(java.awt.event.ActionEvent evt) {
      int currentIndex = EnabledModulesList.getSelectedIndex();
      String currentValue = (String) EnabledModulesList.getSelectedValue();
      m_modulesEnabledModel.remove(currentIndex);
      m_modulesEnabledModel.add(currentIndex + 1, currentValue);
      EnabledModulesList.setSelectedIndex(currentIndex + 1);
    }

    @SuppressWarnings("unchecked")
    private void ModulesLeftButtonActionPerformed(java.awt.event.ActionEvent evt) {
      for (String s : (List<String>) EnabledModulesList.getSelectedValuesList()) {
        m_modulesAvailableModel.addElement(s);
        m_modulesEnabledModel.remove(m_modulesEnabledModel.indexOf(s));
      }
    }

    @SuppressWarnings("unchecked")
    private void ModulesRightButtonActionPerformed(java.awt.event.ActionEvent evt) {
      for (String s : (List<String>) AvailableModulesList.getSelectedValuesList()) {
        m_modulesEnabledModel.addElement(s);
        m_modulesAvailableModel.remove(m_modulesAvailableModel.indexOf(s));
      }
    }

    @SuppressWarnings("unchecked")
    private void EnabledModulesListValueChanged(javax.swing.event.ListSelectionEvent evt) {
      int cnt = EnabledModulesList.getSelectedIndices().length;
      switch (cnt) {
        case 0: ModulesLeftButton.setEnabled(false);
                ModulesUpButton.setEnabled(false);
                ModulesDownButton.setEnabled(false);
                break;
        case 1: ModulesLeftButton.setEnabled(true);
                ModulesUpButton.setEnabled(true);
                ModulesDownButton.setEnabled(true);
                break;
       default: ModulesLeftButton.setEnabled(true);
                ModulesUpButton.setEnabled(false);
                ModulesDownButton.setEnabled(false);
                break;
      }

      if (cnt == 1) {
        int selIndex = EnabledModulesList.getSelectedIndex();
        if (selIndex == 0) {
          ModulesUpButton.setEnabled(false);
        } else if (selIndex == m_modulesEnabledModel.getSize() - 1) {
          ModulesDownButton.setEnabled(false);
        }
      }
    }

    @SuppressWarnings("unchecked")
    private void AvailableModulesListValueChanged(javax.swing.event.ListSelectionEvent evt) {
      int cnt = AvailableModulesList.getSelectedIndices().length;
      if (cnt > 0) {
        ModulesRightButton.setEnabled(true);
      } else {
        ModulesRightButton.setEnabled(false);
      }
    }

    /**
     *  Sets Available modules
     *  @param modules - List of strings to add
     */
    @SuppressWarnings("unchecked")
    public void setAvailableModules(List<String> modules) {

      // Clean up Enabled modules - remove not existing modules
      for (int i = 0; i< m_modulesEnabledModel.getSize(); i++) {
        if (modules.indexOf(m_modulesEnabledModel.get(i)) < 0) {
          m_modulesEnabledModel.remove(i);
        }
      }

      // Add remaining modules as Available
      m_modulesAvailableModel.clear();
      for (int i = 0; i < modules.size(); i++) {
        String m = modules.get(i);
        if (m_modulesEnabledModel.indexOf(m) < 0)
          m_modulesAvailableModel.addElement(m);
      }
    }

    /**
     *  Return list of enabled modules
     *  @return List of enabled modules
     */
    @SuppressWarnings("unchecked")
    public List<String> getActiveModules() {
      List<String> list = new LinkedList<String>();
      for (Enumeration<String> e = m_modulesEnabledModel.elements(); e.hasMoreElements();) {
        list.add(e.nextElement());
      }
      return list;
    }

    /**
     *  Return whether is given module enabled
     *  @return True if module is enabled
     */
    public boolean isModuleEnabled(String module) {
      if (m_modulesEnabledModel.indexOf(module) >= 0) {
        return true;
      }
      return false;
    }

    /**
     *  Return whether custom classic url is enabled
     *  @return True if custom classic url is enabled
     */
    public boolean isCustomTracerUrlEnabled() {
      return m_customTracerUrl;
    }

    /**
     *  Return custom Classic url
     *  @return Custom Classic url
     */
    public String getCustomTracerUrl() {
      return m_customTracerUrlText;
    }

    /**
     *  Return whether custom Ruian url is enabled
     *  @return True if custom Ruian url is enabled
     */
    public boolean isCustomRuainUrlEnabled() {
      return m_customRuianUrl;
    }

    /**
     *  Return custom Ruian url
     *  @return Custom Ruian url
     */
    public String getCustomRuainUrl() {
      return m_customRuianUrlText;
    }

    /**
     *  Return whether Classic Tracer adjust position is enabled
     *  @return True if Classic Tracer adjust position is enabled
     */
    public boolean isTracerAdjustPositionEnabled() {
      return m_tracerAdjustPosition;
    }

    /**
     *  Return Classic module Lon position adjust
     *  @return Classic module Lon position adjust
     */
    public double getTracerAdjustPositionLon() {
      return m_tracerAdjustPositionLon;
    }

    /**
     *  Return Classic module Lat position adjust
     *  @return Classic module Lat position adjust
     */
    public double getTracerAdjustPositionLat() {
      return m_tracerAdjustPositionLat;
    }

    /**
     *  Return whether Ruian adjust position is enabled
     *  @return True if Ruian adjust position is enabled
     */
    public boolean isRuianAdjustPositionEnabled() {
      return m_ruianAdjustPosition;
    }

    /**
     *  Return Ruian module Lon position adjust
     *  @return Ruian module Lon position adjust
     */
    public double getRuianAdjustPositionLon() {
      return m_ruianAdjustPositionLon;
    }

    /**
     *  Return Ruian module Lat position adjust
     *  @return Ruian module Lat position adjust
     */
    public double getRuianAdjustPositionLat() {
      return m_ruianAdjustPositionLat;
    }


    public void addGui(PreferenceTabbedPane gui) {
        createPreferenceTabWithScrollPane(gui, MainConfigPanel);
        reloadSettings();
    }

    @SuppressWarnings("unchecked")
    public boolean ok() {
        // Modules
        List<String> availableModules = new LinkedList<String>();
        for (Enumeration<String> e = m_modulesAvailableModel.elements(); e.hasMoreElements();) {
          availableModules.add(e.nextElement());
        }
        List<String> enabledModules =  new LinkedList<String>();
        for (Enumeration<String> e = m_modulesEnabledModel.elements(); e.hasMoreElements();) {
          enabledModules.add(e.nextElement());
        }

        Config.getPref().putList(KEY_MODULESAVAILABLE, availableModules);
        Config.getPref().putList(KEY_MODULESENABLED, enabledModules);

        // Tracer
        m_customTracerUrl = TracerUrlCheckBox.isSelected();
        Config.getPref().putBoolean(KEY_TRACERURL, m_customTracerUrl);

        m_customTracerUrlText = TracerUrlValueField.getText();
        Config.getPref().put(KEY_TRACERURLVALUE, m_customTracerUrlText);

        m_tracerAdjustPosition = TracerAdjustCheckBox.isSelected();
        Config.getPref().putBoolean(KEY_TRACERADJUSTPOSITION, m_tracerAdjustPosition);

        m_tracerAdjustPositionLatVal = (Integer) TracerAdjustLatSpinner.getValue();
        Config.getPref().put(KEY_TRACERADJUSTPOSITIONLAT, Integer.toString(m_tracerAdjustPositionLatVal));

        m_tracerAdjustPositionLonVal = (Integer) TracerAdjustLonSpinner.getValue();
        Config.getPref().put(KEY_TRACERADJUSTPOSITIONLON, Integer.toString(m_tracerAdjustPositionLonVal));

        m_tracerAdjustPositionLatSign = TracerLatSignComboBox.getSelectedIndex();
        Config.getPref().putInt(KEY_TRACERADJUSTPOSITIONLATSIGN, m_tracerAdjustPositionLatSign);

        m_tracerAdjustPositionLonSign = TracerLonSignComboBox.getSelectedIndex();
        Config.getPref().putInt(KEY_TRACERADJUSTPOSITIONLONSIGN, m_tracerAdjustPositionLonSign);

        // RUIAN
        m_customRuianUrl = RuianUrlCheckBox.isSelected();
        Config.getPref().putBoolean(KEY_RUIANURL, m_customRuianUrl);

        m_customRuianUrlText = RuianUrlValueField.getText();
        Config.getPref().put(KEY_RUIANURLVALUE, m_customRuianUrlText);

        m_ruianAdjustPosition = RuianAdjustCheckBox.isSelected();
        Config.getPref().putBoolean(KEY_RUIANADJUSTPOSITION, m_ruianAdjustPosition);

        m_ruianAdjustPositionLatVal = (Integer) RuianAdjustLatSpinner.getValue();
        Config.getPref().put(KEY_RUIANADJUSTPOSITIONLAT, Integer.toString(m_ruianAdjustPositionLatVal));

        m_ruianAdjustPositionLonVal = (Integer) RuianAdjustLonSpinner.getValue();
        Config.getPref().put(KEY_RUIANADJUSTPOSITIONLON, Integer.toString(m_ruianAdjustPositionLonVal));

        m_ruianAdjustPositionLatSign = RuianLatSignComboBox.getSelectedIndex();
        Config.getPref().putInt(KEY_RUIANADJUSTPOSITIONLATSIGN, m_ruianAdjustPositionLatSign);

        m_ruianAdjustPositionLonSign = RuianLonSignComboBox.getSelectedIndex();
        Config.getPref().putInt(KEY_RUIANADJUSTPOSITIONLONSIGN, m_ruianAdjustPositionLonSign);

        setLatLonAdjust();

        return false;
    }
}
