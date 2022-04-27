/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

public enum ResetTools {
  ALL(Messages.getString("ResetTools.all")),

  WL(Messages.getString("ResetTools.wl")),

  ZOOM(Messages.getString("ViewerPrefView.zoom")),

  ROTATION(Messages.getString("ResetTools.rotation")),

  PAN(Messages.getString("ResetTools.pan"));

  private final String name;

  private ResetTools(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return name;
  }
}
