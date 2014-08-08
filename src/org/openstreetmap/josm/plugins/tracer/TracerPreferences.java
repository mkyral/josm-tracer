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
import javax.swing.JFormattedTextField;
import java.text.DecimalFormat;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;


public class TracerPreferences extends DefaultTabPreferenceSetting {

    public JPanel thisPanel;

    boolean  m_classicModuleEnabled;
    boolean  m_ruianModuleEnabled;
    boolean  m_lpisModuleEnabled;

    private String KEY_CLASSICENABLED = "tracer.classicmoduleenabled";
    private String KEY_RUIANENABLED = "tracer.ruianmoduleenabled";
    private String KEY_LPISENABLED = "tracer.lpismoduleenabled";

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

    boolean  m_customRuianUrl;
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

    private javax.swing.JPanel jPanel1;
    private javax.swing.JLabel enabledModulesLabel;
    private javax.swing.JCheckBox classicCheckBox;
    private javax.swing.JCheckBox ruianCheckBox;
    private javax.swing.JCheckBox lpisCheckBox;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JCheckBox tracerUrlCheckBox;
    private javax.swing.JTextField tracerUrlValueField;
    private javax.swing.JCheckBox tracerAdjustCheckBox;
    private javax.swing.JSpinner tracerAdjustLatSpinner;
    private javax.swing.JSpinner tracerAdjustLonSpinner;
    private javax.swing.JLabel tracerLatLabel;
    private javax.swing.JComboBox tracerLatSignComboBox;
    private javax.swing.JLabel tracerLatZeroLabel;
    private javax.swing.JLabel tracerLonLabel;
    private javax.swing.JComboBox tracerLonSignComboBox;
    private javax.swing.JLabel tracerLonZeroLabel;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JCheckBox ruianUrlCheckBox;
    private javax.swing.JTextField ruianUrlValueField;
    private javax.swing.JCheckBox ruianAdjustCheckBox;
    private javax.swing.JSpinner ruianAdjustLatSpinner;
    private javax.swing.JSpinner ruianAdjustLonSpinner;
    private javax.swing.JLabel ruianLatLabel;
    private javax.swing.JComboBox ruianLatSignComboBox;
    private javax.swing.JLabel ruianLatZeroLabel;
    private javax.swing.JLabel ruianLonLabel;
    private javax.swing.JComboBox ruianLonSignComboBox;
    private javax.swing.JLabel ruianLonZeroLabel;

    private javax.swing.JPanel mainPanel;

    private static TracerPreferences singleton = null;

    public static TracerPreferences getInstance() {
        if (singleton == null)
            singleton = new TracerPreferences();
        return singleton;
    }

    /** Creates new form Preferences */
    private TracerPreferences() {
        super(   "tracer-ruian-sml",
              tr("Tracer plugin settings"),
              tr("Customize Tracer plugin behaviour"));
        thisPanel = new JPanel();
        initComponents();

        // Enabled modules
        m_classicModuleEnabled        = Main.pref.getBoolean(KEY_CLASSICENABLED,    classicCheckBox.isSelected());
        m_ruianModuleEnabled          = Main.pref.getBoolean(KEY_RUIANENABLED,      ruianCheckBox.isSelected());
        m_lpisModuleEnabled           = Main.pref.getBoolean(KEY_LPISENABLED,       lpisCheckBox.isSelected());

        // Tracer
        m_customTracerUrl             = Main.pref.getBoolean(KEY_TRACERURL,         tracerUrlCheckBox.isSelected());
        m_customTracerUrlText         = Main.pref.get       (KEY_TRACERURLVALUE,    tracerUrlValueField.getText());
        m_tracerAdjustPosition        = Main.pref.getBoolean(KEY_TRACERADJUSTPOSITION,    tracerAdjustCheckBox.isSelected());
        m_tracerAdjustPositionLatSign = Main.pref.getInteger(KEY_TRACERADJUSTPOSITIONLATSIGN, tracerLatSignComboBox.getSelectedIndex());
        m_tracerAdjustPositionLonSign = Main.pref.getInteger(KEY_TRACERADJUSTPOSITIONLONSIGN, tracerLonSignComboBox.getSelectedIndex());
        m_tracerAdjustPositionLatVal  = Main.pref.getInteger(KEY_TRACERADJUSTPOSITIONLAT, (Integer) tracerAdjustLatSpinner.getValue());
        m_tracerAdjustPositionLonVal  = Main.pref.getInteger(KEY_TRACERADJUSTPOSITIONLON, (Integer) tracerAdjustLonSpinner.getValue());
        // RUIAN
        m_customRuianUrl             = Main.pref.getBoolean(KEY_RUIANURL,          ruianUrlCheckBox.isSelected());
        m_customRuianUrlText         = Main.pref.get       (KEY_RUIANURLVALUE,     ruianUrlValueField.getText());
        m_ruianAdjustPosition        = Main.pref.getBoolean(KEY_RUIANADJUSTPOSITION,    ruianAdjustCheckBox.isSelected());
        m_ruianAdjustPositionLatSign = Main.pref.getInteger(KEY_RUIANADJUSTPOSITIONLATSIGN, ruianLatSignComboBox.getSelectedIndex());
        m_ruianAdjustPositionLonSign = Main.pref.getInteger(KEY_RUIANADJUSTPOSITIONLONSIGN, ruianLonSignComboBox.getSelectedIndex());
        m_ruianAdjustPositionLatVal  = Main.pref.getInteger(KEY_RUIANADJUSTPOSITIONLAT, (Integer) ruianAdjustLatSpinner.getValue());
        m_ruianAdjustPositionLonVal  = Main.pref.getInteger(KEY_RUIANADJUSTPOSITIONLON, (Integer) ruianAdjustLonSpinner.getValue());
        setLatLonAdjust();
    }

    public void reloadSettings() {
        classicCheckBox.setSelected(m_classicModuleEnabled);
        ruianCheckBox.setSelected(m_ruianModuleEnabled);
        lpisCheckBox.setSelected(m_lpisModuleEnabled);

        tracerUrlCheckBox.setSelected(m_customTracerUrl);
        tracerUrlValueField.setText(m_customTracerUrlText);

        tracerAdjustCheckBox.setSelected(m_tracerAdjustPosition);
        tracerAdjustLatSpinner.setValue(new Integer(m_tracerAdjustPositionLatVal));
        tracerAdjustLonSpinner.setValue(new Integer(m_tracerAdjustPositionLonVal));
        tracerLatSignComboBox.setSelectedIndex(m_tracerAdjustPositionLatSign);
        tracerLonSignComboBox.setSelectedIndex(m_tracerAdjustPositionLonSign);

        ruianUrlCheckBox.setSelected(m_customRuianUrl);
        ruianUrlValueField.setText(m_customRuianUrlText);

        ruianAdjustCheckBox.setSelected(m_ruianAdjustPosition);
        ruianAdjustLatSpinner.setValue(new Integer(m_ruianAdjustPositionLatVal));
        ruianAdjustLonSpinner.setValue(new Integer(m_ruianAdjustPositionLonVal));
        ruianLatSignComboBox.setSelectedIndex(m_ruianAdjustPositionLatSign);
        ruianLonSignComboBox.setSelectedIndex(m_ruianAdjustPositionLonSign);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     */
    @SuppressWarnings("unchecked")
     private void initComponents() {

        mainPanel = new javax.swing.JPanel();

        jPanel1 = new javax.swing.JPanel();
        enabledModulesLabel = new javax.swing.JLabel();
        classicCheckBox = new javax.swing.JCheckBox();
        ruianCheckBox = new javax.swing.JCheckBox();
        lpisCheckBox = new javax.swing.JCheckBox();
        jSeparator2 = new javax.swing.JSeparator();
        tracerUrlCheckBox = new javax.swing.JCheckBox();
        tracerUrlValueField = new javax.swing.JTextField();
        jSeparator1 = new javax.swing.JSeparator();
        tracerAdjustLatSpinner = new javax.swing.JSpinner();
        tracerAdjustLonSpinner = new javax.swing.JSpinner();
        tracerLatSignComboBox = new javax.swing.JComboBox();
        tracerLonSignComboBox = new javax.swing.JComboBox();
        tracerLatZeroLabel = new javax.swing.JLabel();
        tracerLonZeroLabel = new javax.swing.JLabel();
        tracerLatLabel = new javax.swing.JLabel();
        tracerAdjustCheckBox = new javax.swing.JCheckBox();
        tracerLonLabel = new javax.swing.JLabel();
        ruianUrlCheckBox = new javax.swing.JCheckBox();
        ruianUrlValueField = new javax.swing.JTextField();
        ruianAdjustCheckBox = new javax.swing.JCheckBox();
        ruianLatLabel = new javax.swing.JLabel();
        ruianLonLabel = new javax.swing.JLabel();
        ruianLatZeroLabel = new javax.swing.JLabel();
        ruianLonZeroLabel = new javax.swing.JLabel();
        ruianLatSignComboBox = new javax.swing.JComboBox();
        ruianLonSignComboBox = new javax.swing.JComboBox();
        ruianAdjustLatSpinner = new javax.swing.JSpinner();
        ruianAdjustLonSpinner = new javax.swing.JSpinner();


        thisPanel.setLayout(new java.awt.GridLayout(1, 0));

        // Enabled modules
        enabledModulesLabel.setText(tr("Enabled modules:"));

        classicCheckBox.setText(tr("Classic"));

        ruianCheckBox.setSelected(true);
        ruianCheckBox.setText(tr("RUIAN"));

        lpisCheckBox.setText(tr("LPIS"));

        enabledModulesLabel.getAccessibleContext().setAccessibleDescription(tr("Select modules available for switching"));
        classicCheckBox.getAccessibleContext().setAccessibleDescription(tr("Use Classic (the original) tracer module"));
        ruianCheckBox.getAccessibleContext().setAccessibleDescription(tr("Use RUIAN Tracer module"));
        lpisCheckBox.getAccessibleContext().setAccessibleDescription(tr("Use LPIS Tracer module"));

        // Tracer settins
        tracerUrlCheckBox.setText(tr("Custom Classic Tracer server - requires TracerServer (.NET or Mono)"));
        tracerUrlCheckBox.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                tracerUrlChanged(evt);
            }
        });

        tracerUrlValueField.setText("http://localhost:5050/");
        tracerUrlValueField.setEnabled(false);


        tracerAdjustCheckBox.setText(tr("Adjust traced object position"));
//         tracerAdjustCheckBox.setToolTipText("");
        tracerAdjustCheckBox.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                tracerAdjustChanged(evt);
            }
        });

        tracerLatLabel.setText(tr("Lat:"));
        tracerLonLabel.setText(tr("Lon:"));
        tracerLatZeroLabel.setText("0.0000");
        tracerLonZeroLabel.setText("0.0000");


        tracerLatSignComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "+", "-" }));
        tracerLatSignComboBox.setSelectedIndex(0);
        tracerLatSignComboBox.setEnabled(false);

        tracerLonSignComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "+", "-" }));
        tracerLonSignComboBox.setSelectedIndex(0);
        tracerLonSignComboBox.setEnabled(false);

        tracerAdjustLatSpinner.setModel(new javax.swing.SpinnerNumberModel(0, 0, 999, 1));
        tracerAdjustLatSpinner.setToolTipText(tr("Set Lat adjustment. Interval <0;999>"));
        tracerAdjustLatSpinner.setEnabled(false);

        tracerAdjustLonSpinner.setModel(new javax.swing.SpinnerNumberModel(0, 0, 999, 1));
        tracerAdjustLonSpinner.setToolTipText(tr("Set Lon adjustment. Interval <0;999>"));
        tracerAdjustLonSpinner.setEnabled(false);

        // RUIAN settins
        ruianUrlCheckBox.setText(tr("Custom Ruian server url"));
        ruianUrlCheckBox.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                ruianUrlChanged(evt);
            }
        });

        ruianUrlValueField.setText("http://josm.poloha.net/");
        ruianUrlValueField.setEnabled(false);

        ruianAdjustCheckBox.setText(tr("Adjust traced object position"));
//         ruianAdjustCheckBox.setToolTipText("");
        ruianAdjustCheckBox.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                ruianAdjustChanged(evt);
            }
        });

        ruianLatLabel.setText(tr("Lat:"));
        ruianLonLabel.setText(tr("Lon:"));
        ruianLatZeroLabel.setText("0.0000");
        ruianLonZeroLabel.setText("0.0000");

        ruianLatSignComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "+", "-" }));
        ruianLatSignComboBox.setSelectedIndex(0);
        ruianLatSignComboBox.setEnabled(false);

        ruianLonSignComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "+", "-" }));
        ruianLonSignComboBox.setSelectedIndex(0);
        ruianLonSignComboBox.setEnabled(false);

        ruianAdjustLatSpinner.setModel(new javax.swing.SpinnerNumberModel(0, 0, 999, 1));
        ruianAdjustLatSpinner.setToolTipText(tr("Set Lat adjustment. Interval <0;999>"));
        ruianAdjustLatSpinner.setEnabled(false);

        ruianAdjustLonSpinner.setModel(new javax.swing.SpinnerNumberModel(0, 0, 999, 1));
        ruianAdjustLonSpinner.setToolTipText(tr("Set Lon adjustment. Interval <0;999>"));
        ruianAdjustLonSpinner.setEnabled(false);

        // Border around modules selection
        jPanel1.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(enabledModulesLabel)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(classicCheckBox)
                        .addGap(34, 34, 34)
                        .addComponent(ruianCheckBox)
                        .addGap(34, 34, 34)
                        .addComponent(lpisCheckBox)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(enabledModulesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(classicCheckBox)
                    .addComponent(ruianCheckBox)
                    .addComponent(lpisCheckBox))
                .addGap(0, 7, Short.MAX_VALUE))
        );

        // Dialog Layout
        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(tracerUrlCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, 388, Short.MAX_VALUE)
                            .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(tracerAdjustCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 384, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(21, 21, 21)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(tracerLatLabel)
                                    .addComponent(tracerLonLabel))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(tracerLatSignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(tracerLonSignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(12, 12, 12)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(tracerLonZeroLabel)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(tracerAdjustLonSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(tracerLatZeroLabel)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(tracerAdjustLatSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                            .addComponent(ruianAdjustCheckBox)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(32, 32, 32)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(ruianLatLabel)
                                    .addComponent(ruianLonLabel))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(ruianLatSignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(ruianLonSignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(12, 12, 12)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(ruianLatZeroLabel)
                                    .addComponent(ruianLonZeroLabel))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(ruianAdjustLatSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(ruianAdjustLonSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                        .addContainerGap(24, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(jSeparator1, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ruianUrlCheckBox, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 397, Short.MAX_VALUE)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(21, 21, 21)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(tracerUrlValueField)
                                    .addComponent(ruianUrlValueField))))
                        .addGap(0, 0, Short.MAX_VALUE))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(tracerUrlCheckBox)
                .addGap(4, 4, 4)
                .addComponent(tracerUrlValueField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(tracerAdjustCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(tracerLatLabel)
                        .addGap(12, 12, 12)
                        .addComponent(tracerLonLabel))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(tracerLatSignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(tracerLatZeroLabel)
                                .addComponent(tracerAdjustLatSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(tracerLonSignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addGap(15, 15, 15)
                                .addComponent(tracerLonZeroLabel))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(tracerAdjustLonSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 8, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(ruianUrlCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ruianUrlValueField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(ruianAdjustCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(ruianAdjustLatSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(4, 4, 4))
                            .addComponent(ruianLatLabel))
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(7, 7, 7)
                                .addComponent(ruianAdjustLonSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ruianLonLabel))))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(ruianLatSignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(ruianLatZeroLabel))
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ruianLonSignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addGap(15, 15, 15)
                                .addComponent(ruianLonZeroLabel)))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        thisPanel.add(mainPanel);
    }

    private void tracerUrlChanged(javax.swing.event.ChangeEvent evt) {
        tracerUrlValueField.setEnabled(tracerUrlCheckBox.isSelected());
    }

    private void ruianUrlChanged(javax.swing.event.ChangeEvent evt) {
        ruianUrlValueField.setEnabled(ruianUrlCheckBox.isSelected());
    }

    private void tracerAdjustChanged(javax.swing.event.ChangeEvent evt) {
        tracerAdjustLatSpinner.setEnabled(tracerAdjustCheckBox.isSelected());
        tracerAdjustLonSpinner.setEnabled(tracerAdjustCheckBox.isSelected());
        tracerLatSignComboBox.setEnabled(tracerAdjustCheckBox.isSelected());
        tracerLonSignComboBox.setEnabled(tracerAdjustCheckBox.isSelected());
    }

    private void ruianAdjustChanged(javax.swing.event.ChangeEvent evt) {
        ruianAdjustLatSpinner.setEnabled(ruianAdjustCheckBox.isSelected());
        ruianAdjustLonSpinner.setEnabled(ruianAdjustCheckBox.isSelected());
        ruianLatSignComboBox.setEnabled(ruianAdjustCheckBox.isSelected());
        ruianLonSignComboBox.setEnabled(ruianAdjustCheckBox.isSelected());
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

    public void addGui(PreferenceTabbedPane gui) {
        createPreferenceTabWithScrollPane(gui, mainPanel);
//         JPanel p = gui.createPreferenceTab(this);
//         p.add(mainPanel);
        reloadSettings();
    }

    public boolean ok() {
        // Enabled modules
        m_classicModuleEnabled = classicCheckBox.isSelected();
        Main.pref.put(KEY_CLASSICENABLED, m_classicModuleEnabled);

        m_ruianModuleEnabled = ruianCheckBox.isSelected();
        Main.pref.put(KEY_RUIANENABLED, m_ruianModuleEnabled);

        m_lpisModuleEnabled = lpisCheckBox.isSelected();
        Main.pref.put(KEY_LPISENABLED, m_lpisModuleEnabled);

        // Tracer
        m_customTracerUrl = tracerUrlCheckBox.isSelected();
        Main.pref.put(KEY_TRACERURL, m_customTracerUrl);

        m_customTracerUrlText = tracerUrlValueField.getText();
        Main.pref.put(KEY_TRACERURLVALUE, m_customTracerUrlText);

        m_tracerAdjustPosition = tracerAdjustCheckBox.isSelected();
        Main.pref.put(KEY_TRACERADJUSTPOSITION, m_tracerAdjustPosition);

        m_tracerAdjustPositionLatVal = (Integer) tracerAdjustLatSpinner.getValue();
        Main.pref.put(KEY_TRACERADJUSTPOSITIONLAT, Integer.toString(m_tracerAdjustPositionLatVal));

        m_tracerAdjustPositionLonVal = (Integer) tracerAdjustLonSpinner.getValue();
        Main.pref.put(KEY_TRACERADJUSTPOSITIONLON, Integer.toString(m_tracerAdjustPositionLonVal));

        m_tracerAdjustPositionLatSign = tracerLatSignComboBox.getSelectedIndex();
        Main.pref.putInteger(KEY_TRACERADJUSTPOSITIONLATSIGN, m_tracerAdjustPositionLatSign);

        m_tracerAdjustPositionLonSign = tracerLonSignComboBox.getSelectedIndex();
        Main.pref.putInteger(KEY_TRACERADJUSTPOSITIONLONSIGN, m_tracerAdjustPositionLonSign);

        // RUIAN
        m_customRuianUrl = ruianUrlCheckBox.isSelected();
        Main.pref.put(KEY_RUIANURL, m_customRuianUrl);

        m_customRuianUrlText = ruianUrlValueField.getText();
        Main.pref.put(KEY_RUIANURLVALUE, m_customRuianUrlText);

        m_ruianAdjustPosition = ruianAdjustCheckBox.isSelected();
        Main.pref.put(KEY_RUIANADJUSTPOSITION, m_ruianAdjustPosition);

        m_ruianAdjustPositionLatVal = (Integer) ruianAdjustLatSpinner.getValue();
        Main.pref.put(KEY_RUIANADJUSTPOSITIONLAT, Integer.toString(m_ruianAdjustPositionLatVal));

        m_ruianAdjustPositionLonVal = (Integer) ruianAdjustLonSpinner.getValue();
        Main.pref.put(KEY_RUIANADJUSTPOSITIONLON, Integer.toString(m_ruianAdjustPositionLonVal));

        m_ruianAdjustPositionLatSign = ruianLatSignComboBox.getSelectedIndex();
        Main.pref.putInteger(KEY_RUIANADJUSTPOSITIONLATSIGN, m_ruianAdjustPositionLatSign);

        m_ruianAdjustPositionLonSign = ruianLonSignComboBox.getSelectedIndex();
        Main.pref.putInteger(KEY_RUIANADJUSTPOSITIONLONSIGN, m_ruianAdjustPositionLonSign);

        setLatLonAdjust();

        return false;
    }
}
