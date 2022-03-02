/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui;

import javax.swing.JButton;
import org.weasis.acquire.explorer.Messages;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.util.WtoolBar;

public class AcquireToolBar<DicomImageElement> extends WtoolBar {

  public AcquireToolBar(int index) {
    super(Messages.getString("AcquireToolBar.title"), index);

    // TODO add button for publishing, help...
    final JButton printButton = new JButton(ResourceUtil.getToolBarIcon(ActionIcon.PRINT));
    printButton.setToolTipText("");
    printButton.addActionListener(
        e -> {
          // Do nothing
        });
    add(printButton);
  }
}
