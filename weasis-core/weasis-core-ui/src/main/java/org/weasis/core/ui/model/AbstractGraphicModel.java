/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlType;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.Canvas;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.*;
import org.weasis.core.ui.model.graphic.imp.AnnotationGraphic;
import org.weasis.core.ui.model.graphic.imp.PixelInfoGraphic;
import org.weasis.core.ui.model.graphic.imp.PointGraphic;
import org.weasis.core.ui.model.graphic.imp.angle.AngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.angle.CobbAngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.angle.FourPointsAngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.angle.OpenAngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.area.EllipseGraphic;
import org.weasis.core.ui.model.graphic.imp.area.ObliqueRectangleGraphic;
import org.weasis.core.ui.model.graphic.imp.area.PolygonGraphic;
import org.weasis.core.ui.model.graphic.imp.area.SelectGraphic;
import org.weasis.core.ui.model.graphic.imp.area.ThreePointsCircleGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineWithGapGraphic;
import org.weasis.core.ui.model.graphic.imp.line.ParallelLineGraphic;
import org.weasis.core.ui.model.graphic.imp.line.PerpendicularLineGraphic;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.GraphicModelChangeListener;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.layer.imp.DefaultLayer;
import org.weasis.core.ui.model.utils.imp.DefaultUUID;
import org.weasis.core.ui.util.MouseEventDouble;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.codec.utils.Ultrasound;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@XmlType(propOrder = {"referencedSeries", "layers", "models"})
@XmlAccessorType(XmlAccessType.NONE)
public abstract class AbstractGraphicModel extends DefaultUUID implements GraphicModel {
  private static final long serialVersionUID = 1187916695295007387L;

  private List<ReferencedSeries> referencedSeries;
  private List<GraphicLayer> layers;
  protected List<Graphic> models;

  private final List<GraphicSelectionListener> selectedGraphicsListeners = new ArrayList<>();
  private final List<GraphicModelChangeListener> modelListeners = new ArrayList<>();
  private final List<PropertyChangeListener> graphicsListeners = new ArrayList<>();
  private Boolean changeFireingSuspended = Boolean.FALSE;

  private Function<Graphic, GraphicLayer> getLayer = g -> g.getLayer();
  private Function<Graphic, DragGraphic> castToDragGraphic = DragGraphic.class::cast;

  private Predicate<Graphic> isLayerVisible = g -> g.getLayer().getVisible();
  private Predicate<Graphic> isGraphicSelected = g -> g.getSelected();

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractGraphicModel.class);

  public AbstractGraphicModel() {
    this(null);
  }

  public AbstractGraphicModel(List<ReferencedSeries> referencedSeries) {
    setReferencedSeries(referencedSeries);
    this.layers = Collections.synchronizedList(new ArrayList<>());
    this.models = Collections.synchronizedList(new ArrayList<>());
  }

  @XmlElementWrapper(name = "graphics")
  @XmlElements({
          @XmlElement(name = "point", type = PointGraphic.class),
          @XmlElement(name = "angle", type = AngleToolGraphic.class),
          @XmlElement(name = "annotation", type = AnnotationGraphic.class),
          @XmlElement(name = "pixelInfo", type = PixelInfoGraphic.class),
          @XmlElement(name = "openAngle", type = OpenAngleToolGraphic.class),
          @XmlElement(name = "cobbAngle", type = CobbAngleToolGraphic.class),
          @XmlElement(name = "rectangle", type = ObliqueRectangleGraphic.class),
          @XmlElement(name = "ellipse", type = EllipseGraphic.class),
          @XmlElement(name = "fourPointsAngle", type = FourPointsAngleToolGraphic.class),
          @XmlElement(name = "line", type = LineGraphic.class),
          @XmlElement(name = "lineWithGap", type = LineWithGapGraphic.class),
          @XmlElement(name = "perpendicularLine", type = PerpendicularLineGraphic.class),
          @XmlElement(name = "parallelLine", type = ParallelLineGraphic.class),
          @XmlElement(name = "polygon", type = PolygonGraphic.class),
          @XmlElement(name = "polyline", type = PolylineGraphic.class),
          @XmlElement(name = "threePointsCircle", type = ThreePointsCircleGraphic.class)
  })
  @Override
  public List<Graphic> getModels() {
    return models;
  }

  @XmlElementWrapper(name = "layers")
  @XmlElements({@XmlElement(name = "layer", type = DefaultLayer.class)})
  @Override
  public List<GraphicLayer> getLayers() {
    return layers;
  }

  @XmlElementWrapper(name = "references")
  @XmlElement(name = "series")
  @Override
  public List<ReferencedSeries> getReferencedSeries() {
    return referencedSeries;
  }

  @Override
  public void setReferencedSeries(List<ReferencedSeries> referencedSeries) {
    if (referencedSeries != null
            && !referencedSeries.getClass().getSimpleName().startsWith("Synchronized")) { // NON-NLS
      this.referencedSeries = Collections.synchronizedList(referencedSeries);
    }
    this.referencedSeries =
            Optional.ofNullable(referencedSeries)
                    .orElseGet(() -> Collections.synchronizedList(new ArrayList<>()));
  }

  @Override
  public void setModels(List<Graphic> models) {
    if (models != null) {
      this.models = Collections.synchronizedList(models);
      this.layers = Collections.synchronizedList(getLayerlist());
    }
  }

  @Override
  public void addGraphic(Graphic graphic) {
    if (graphic != null) {
      GraphicLayer layer = graphic.getLayer();
      if (layer == null) {
        layer =
                findLayerByType(graphic.getLayerType())
                        .orElseGet(() -> new DefaultLayer(graphic.getLayerType()));
        graphic.setLayer(layer);
      }
      if (!layers.contains(layer)) {
        layers.add(layer);
      }
      models.add(graphic);
    }
  }

  @Override
  public void removeGraphic(Graphic graphic) {
    if (graphic != null) {
      models.remove(graphic);
      graphic.removeAllPropertyChangeListener();

      GraphicLayer layer = graphic.getLayer();
      if (layer != null) {
        boolean layerExist = false;
        synchronized (models) {
          for (Graphic g : models) {
            if (g.getLayer().equals(layer)) {
              layerExist = true;
              break;
            }
          }
        }
        if (!layerExist) {
          layers.remove(layer);
        }
      }
    }
  }

  private List<GraphicLayer> getLayerlist() {
    return models.parallelStream().map(getLayer).distinct().collect(Collectors.toList());
  }

  @Override
  public void addGraphicChangeHandler(PropertyChangeListener graphicsChangeHandler) {
    if (Objects.nonNull(graphicsChangeHandler)
            && !graphicsListeners.contains(graphicsChangeHandler)) {
      graphicsListeners.add(graphicsChangeHandler);
      models.forEach(g -> g.addPropertyChangeListener(graphicsChangeHandler));
    }
  }

  @Override
  public void removeGraphicChangeHandler(PropertyChangeListener graphicsChangeHandler) {
    if (Objects.nonNull(graphicsChangeHandler)
            && graphicsListeners.contains(graphicsChangeHandler)) {
      graphicsListeners.remove(graphicsChangeHandler);
      models.forEach(g -> g.removePropertyChangeListener(graphicsChangeHandler));
    }
  }

  @Override
  public List<PropertyChangeListener> getGraphicsListeners() {
    return graphicsListeners;
  }

  @Override
  public void updateLabels(Object source, ViewCanvas<? extends ImageElement> view) {
    models.forEach(g -> g.updateLabel(source, view));
  }

  @Override
  public Optional<GraphicLayer> findLayerByType(LayerType type) {
    Objects.requireNonNull(type);
    return layers.stream().filter(isLayerTypeEquals(type)).findFirst();
  }

  @Override
  public List<GraphicLayer> groupLayerByType() {
    if (models.isEmpty()) {
      return Collections.emptyList();
    }

    ArrayList<GraphicLayer> layerType = new ArrayList<>();
    synchronized (models) {
      for (Graphic g : models) {
        LayerType type = g.getLayer().getType();

        boolean notInGroup = true;
        for (GraphicLayer glayer : layerType) {
          if (Objects.equals(glayer.getType(), type)) {
            notInGroup = false;
            break;
          }
        }

        if (notInGroup) {
          layerType.add(g.getLayer());
        }
      }
    }

    return layerType;
  }

  @Override
  public void deleteByLayer(GraphicLayer layer) {
    Objects.requireNonNull(layer);
    if (models.isEmpty()) {
      return;
    }
    synchronized (models) {
      models.removeIf(
              g -> {
                boolean delete = layer.equals(g.getLayer());
                if (delete) {
                  g.removeAllPropertyChangeListener();
                }
                return delete;
              });
      layers.removeIf(l -> Objects.equals(l, layer));
    }
  }

  @Override
  public void deleteByLayerType(LayerType type) {
    Objects.requireNonNull(type);
    if (models.isEmpty()) {
      return;
    }
    synchronized (models) {
      for (Graphic g : models) {
        if (g.getLayer().getType().equals(type)) {
          g.removeAllPropertyChangeListener();
        }
      }
      models.removeIf(g -> Objects.equals(g.getLayer().getType(), type));
      layers.removeIf(l -> Objects.equals(l.getType(), type));
    }
  }

  @Override
  public void deleteNonSerializableGraphics() {
    if (models.isEmpty()) {
      return;
    }
    synchronized (models) {
      for (Graphic g : models) {
        if (!g.getLayer().getSerializable()) {
          g.removeAllPropertyChangeListener();
        }
      }
      models.removeIf(g -> !g.getLayer().getSerializable());
      layers.removeIf(l -> !l.getSerializable());
    }
  }

  @Override
  public boolean hasSerializableGraphics() {
    if (models.isEmpty()) {
      return false;
    }
    synchronized (models) {
      for (Graphic g : models) {
        /*
         * Exclude non serializable layer and graphics without points like NonEditableGraphic (not strictly the
         * jaxb serialization process that use the annotations from getModels())
         */
        if (g.getLayer().getSerializable() && !g.getPts().isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public List<Graphic> getAllGraphics() {
    return models.stream().filter(isLayerVisible).collect(Collectors.toList());
  }

  @Override
  public List<DragGraphic> getAllDragMeasureGraphics() {
    List<DragGraphic> l = new ArrayList<DragGraphic>();
    for (Graphic g : this.getAllGraphics()) {
      if (!(g instanceof DragGraphic) || (g.getLayerType() != LayerType.MEASURE)) { continue; }
      l.add((DragGraphic) g);
    }
    return l;
  }

  @Override
  public List<Graphic> getSelectedAllGraphicsIntersecting(
          Rectangle rectangle, AffineTransform transform) {

    ArrayList<Graphic> selectedGraphicList = new ArrayList<>();
    if (rectangle != null) {
      synchronized (models) {
        for (int i = models.size() - 1; i >= 0; i--) {
          Graphic graphic = models.get(i);
          GraphicLayer layer = graphic.getLayer();
          if (layer.getVisible() && layer.getSelectable()) {

            Rectangle graphBounds = graphic.getBounds(transform);

            if (graphBounds != null && graphBounds.intersects(rectangle)) {
              Area selectionArea = graphic.getArea(transform);

              if (selectionArea != null && selectionArea.intersects(rectangle)) {
                selectedGraphicList.add(graphic);
                continue;
              }
            }

            GraphicLabel graphicLabel = graphic.getGraphicLabel();
            if (graphic.getLabelVisible()
                    && graphicLabel != null
                    && graphicLabel.getLabels() != null) {
              Area selectionArea = graphicLabel.getArea(transform);

              if (selectionArea != null && selectionArea.intersects(rectangle)) {
                selectedGraphicList.add(graphic);
              }
            }
          }
        }
      }
    }
    return selectedGraphicList;
  }

  @Override
  public List<Graphic> getSelectedAllGraphicsIntersecting(
          Rectangle rectangle, AffineTransform transform, boolean onlyFrontGraphic) {
    ArrayList<Graphic> selectedGraphicList = new ArrayList<>();
    if (rectangle != null) {
      synchronized (models) {
        for (int i = models.size() - 1; i >= 0; i--) {
          Graphic graphic = models.get(i);
          GraphicLayer layer = graphic.getLayer();
          if (layer.getVisible() && layer.getSelectable()) {

            List<Area> selectedAreaList = new ArrayList<>();

            Area selectedArea = null;

            Rectangle selectionBounds = graphic.getRepaintBounds(transform);
            if (selectionBounds != null && selectionBounds.intersects(rectangle)) {
              selectedArea = graphic.getArea(transform);
            }

            GraphicLabel graphicLabel = graphic.getGraphicLabel();
            if (graphicLabel != null && graphicLabel.getLabels() != null) {
              Area labelArea = graphicLabel.getArea(transform);
              if (labelArea != null) {
                if (selectedArea != null) {
                  selectedArea.add(labelArea);
                } else if (labelArea.intersects(rectangle)) {
                  selectedArea = graphic.getArea(transform);
                  selectedArea.add(labelArea);
                }
              }
            }

            if (selectedArea != null) {
              if (onlyFrontGraphic) {
                for (Area area : selectedAreaList) {
                  selectedArea.subtract(area); // subtract any areas from front graphics
                  // already selected
                }
              }
              if (selectedArea.intersects(rectangle)) {
                selectedAreaList.add(selectedArea);
                selectedGraphicList.add(graphic);
              }
            }
          }
        }
      }
    }
    return selectedGraphicList;
  }

  /**
   * @param mouseEvent
   * @return first selected graphic intersecting if exist, otherwise simply first graphic
   * intersecting, or null
   */
  @Override
  public Optional<Graphic> getFirstGraphicIntersecting(MouseEventDouble mouseEvent) {
    final Point2D mousePt = mouseEvent.getImageCoordinates();
    Graphic firstSelectedGraph = null;
    synchronized (models) {
      for (int i = models.size() - 1; i >= 0; i--) {
        Graphic g = models.get(i);
        GraphicLayer l = g.getLayer();
        if (l.getVisible() && l.getSelectable()) {
          if (g.isOnGraphicLabel(mouseEvent)) {
            if (g.getSelected()) {
              return Optional.of(g);
            } else if (firstSelectedGraph == null) {
              firstSelectedGraph = g;
            }
          }

          // Improve speed by checking if mousePoint is inside repaintBound before checking if
          // inside Area
          Rectangle2D repaintBound = g.getRepaintBounds(mouseEvent);
          if (repaintBound != null && repaintBound.contains(mousePt)) {
            if ((g.getHandlePointIndex(mouseEvent) >= 0)
                    || (g.getArea(mouseEvent).contains(mousePt))) {
              if (g.getSelected()) {
                return Optional.of(g);
              } else if (firstSelectedGraph == null) {
                firstSelectedGraph = g;
              }
            }
          }
        }
      }
    }
    return Optional.ofNullable(firstSelectedGraph);
  }

  // @Override
  // public List<Graphic> getGraphicsBoundsInArea(Rectangle rect) {
  // List<Graphic> arraylist = new ArrayList<>();
  // if (graphics != null && rect != null) {
  // for (int j = graphics.list.size() - 1; j >= 0; j--) {
  // Graphic graphic = graphics.list.get(j);
  // Rectangle2D graphicBounds = graphic.getRepaintBounds(getAffineTransform());
  // if (graphicBounds != null && graphicBounds.intersects(rect)) {
  // arraylist.add(graphic);
  // }
  // }
  // }
  // return arraylist;
  // }

  // @Override
  // public AbstractDragGraphic getGraphicContainPoint(MouseEventDouble mouseEvt) {
  // final Point2D mousePt = mouseEvt.getImageCoordinates();
  //
  // if (graphics != null && mousePt != null) {
  //
  // for (int j = graphics.list.size() - 1; j >= 0; j--) {
  // if (graphics.list.get(j) instanceof AbstractDragGraphic) {
  //
  // AbstractDragGraphic dragGraph = (AbstractDragGraphic) graphics.list.get(j);
  //
  // if (dragGraph.isOnGraphicLabel(mouseEvt)) {
  // return dragGraph;
  // }
  //
  // // Improve speed by checking if mousePoint is inside repaintBound before checking if inside
  // Area
  // Rectangle2D repaintBound = dragGraph.getRepaintBounds(mouseEvt);
  // if (repaintBound != null && repaintBound.contains(mousePt)) {
  // if ((dragGraph.getHandlePointIndex(mouseEvt) >= 0)
  // || (dragGraph.getArea(mouseEvt).contains(mousePt))) {
  // return dragGraph;
  // }
  // }
  // }
  // }
  // }
  // return null;
  // }

  @Override
  public List<DragGraphic> getSelectedDragableGraphics() {
    return models.stream()
            .filter(isGraphicSelected)
            .filter(DragGraphic.class::isInstance)
            .map(castToDragGraphic)
            .collect(Collectors.toList());
  }

  @Override
  public List<Graphic> getSelectedGraphics() {
    return models.stream().filter(isGraphicSelected).collect(Collectors.toList());
  }

  @Override
  public Optional<SelectGraphic> getSelectGraphic() {
    return models.stream()
            .filter(g -> g instanceof SelectGraphic)
            .map(SelectGraphic.class::cast)
            .findFirst();
  }

  @Override
  public void setSelectedGraphic(List<Graphic> graphicList) {
    synchronized (models) {
      for (Graphic g : models) {
        g.setSelected(false);
      }
    }

    if (graphicList != null) {
      for (Graphic g : graphicList) {
        g.setSelected(true);
      }
    }
  }

  @Override
  public void setSelectedAllGraphics() {
    setSelectedGraphic(getAllGraphics());
  }

  @Override
  public void deleteSelectedGraphics(Canvas canvas, Boolean warningMessage) {
    // gather all graphics to delete, including those duplicated in ultrasound regions
    List<Graphic> list = new ArrayList<Graphic>();
    for (Graphic g1 : getSelectedGraphics()) {
      if (!g1.isInGraphicList(list)) { list.add(g1); } // prevent duplicates

      for (Graphic g2 : this.getAllGraphics()) {
        if (g1.getUuid() == g2.getUuid()) { continue; } // look in the mirror

        // check to see if graphic is part of a region
        if (g1.getUltrasoundRegionGroupID() != "" &&
                g2.getUltrasoundRegionGroupID() != "" &&
                g1.getUltrasoundRegionGroupID() == g2.getUltrasoundRegionGroupID() &&
                !g2.isInGraphicList(list)) {
          list.add(g2);
        }
      }
    }

    if (!list.isEmpty()) {
      int response = 0;
      if (warningMessage) {
        response =
                JOptionPane.showConfirmDialog(
                        canvas.getJComponent(),
                        String.format(Messages.getString("AbstractLayerModel.del_conf"), list.size()),
                        Messages.getString("AbstractLayerModel.del_graphs"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
      }
      if (Objects.equals(response, 0)) {
        //
        // rename the file with the ROI points if it exists, so we
        // know later that it was deleted
        //
        for (Graphic g : list) {
          String filename = g.getUltrasoundRegionPointsFilename();
          File file = new File(filename);
          if (file.exists()) { file.renameTo(new File(filename + ".deleted"));  }
        }

        // do the removal
        list.forEach(Graphic::fireRemoveAction);
        canvas.getJComponent().repaint();
      }
    }
  }

  @Override
  public void clear() {
    models.clear();
  }

  @Override
  public void fireGraphicsSelectionChanged(MeasurableLayer layer) {
    selectedGraphicsListeners.forEach(gl -> gl.handle(getSelectedGraphics(), layer));
  }

  @Override
  public void draw(
          Graphics2D g2d,
          AffineTransform transform,
          AffineTransform inverseTransform,
          Rectangle2D viewClip,
          DefaultView2d view2d) {
    // Get the visible view in real coordinates, note only Sun g2d return consistent clip area with
    // offset
    Shape area =
            inverseTransform.createTransformedShape(viewClip == null ? g2d.getClipBounds() : viewClip);
    Rectangle2D bound = area == null ? null : area.getBounds2D();

    // if they are present, duplicate the graphic to any ultrasound regions
    duplicateToUltrasoundRegions(view2d);

    g2d.translate(0.5, 0.5);
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, DefaultView2d.antialiasingOn);
    models.forEach(g -> applyPaint(g, g2d, transform, bound));
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, DefaultView2d.antialiasingOff);
    g2d.translate(-0.5, -0.5);

  }

  /*
   * If an ultrasound image with multiple regions is being displayed, duplicate any new measurements
   * to each of them.
   */
  void duplicateToUltrasoundRegions(DefaultView2d view2d) {

    for (DragGraphic dg : this.getAllDragMeasureGraphics()) {

      // we already drew the graphic but it is being changed
      if (dg.getResizingOrMoving() && dg.isHandledForUltrasoundRegions()) {
        dg.setHandledForUltrasoundRegions(Boolean.FALSE);
      }

      // only when user done changing graphic
      if (dg.isGraphicComplete() && !dg.isHandledForUltrasoundRegions() && !dg.getResizingOrMoving()) {

        List<Attributes> regions = Ultrasound.getRegions(((DcmMediaReader) view2d.getImageLayer().getSourceImage().getMediaReader()).getDicomObject());

        if (0 == regions.size()) {
          LOGGER.debug("no ultrasound regions found, not replicating");
          dg.setHandledForUltrasoundRegions(Boolean.TRUE);
          continue;
        }

        // we have already drawn it once on the regions, but it changed, so change all the other ones
        if ("" != dg.getUltrasoundRegionGroupID()) {

          File file = new File(dg.getUltrasoundRegionPointsFilename());
          if (file.exists()) { file.delete(); }
          BufferedWriter bw = null;
          try {
            bw = createROIPointsFile(
                    ((DcmMediaReader) view2d.getImageLayer().getSourceImage().getMediaReader()).getDicomObject(),
                    dg.getUltrasoundRegionGroupID(),
                    dg,
                    view2d.getFrameIndex() + 1);
            for (DragGraphic dg2 : this.getAllDragMeasureGraphics()) {

              // record the ROI points to a file, but don't further process the identical graphic
              if (dg2.getUuid() == dg.getUuid()) {
                for (Point2D p : dg.getPts()) {
                  bw.write( findUltrasoundRegionWithMeasurement(regions, dg) + "," + p.getX() + "," + p.getY() + "\n");
                }
                continue;
              }

              if (dg.getUltrasoundRegionGroupID() != dg2.getUltrasoundRegionGroupID()) { continue; } // only process the ones in this group

              // adjust position of graphic
              int i1 = findUltrasoundRegionWithMeasurement(regions, dg); // source
              int i2 = findUltrasoundRegionWithMeasurement(regions, dg2); // destination
              if (-1 == i1 || -1 == i2)
              {
                LOGGER.debug(String.format("either source or destination (%d, %d) is not within an ultrasound region.  skipping graphic", i1, i2));
                continue;
              }
              List<Point2D> newPts = createNewPointsForUltrasoundRegion(regions.get(i1), regions.get(i2), dg);
              LOGGER.debug("due to change of graphic within ultrasound region, redrawing shape with points " + newPts);
              dg2.setPts(newPts);

              for (Point2D p : newPts) {
                bw.write(i2 + "," + p.getX() + "," + p.getY() + "\n");
              }
  
              dg2.setPaint((Color) dg.getColorPaint());
              dg2.setFilled(dg.getFilled());
              dg2.setLineThickness(dg.getLineThickness());

              // adjust measurement label text by creating a fake mouse event
              MouseEventDouble me = new MouseEventDouble(view2d, 0, 0, 0, 0, 0, 0, 0, 0, false, 0);
              dg2.buildShape(me);
            }
          } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
          } finally {
            if (bw != null) {
              try {
                bw.close();
              } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
              }
            }
          }

          dg.setHandledForUltrasoundRegions(Boolean.TRUE);
          continue;
        }

        //
        // find the region that contains all the points in the graphic (possible there may not be one)
        //
        int regionWithMeasurement = findUltrasoundRegionWithMeasurement(regions, dg);
        if (-1 == regionWithMeasurement) {
          LOGGER.debug("region with " + dg.getPts() + " not in one region, not replicating");
          dg.setHandledForUltrasoundRegions(Boolean.TRUE);
          continue;
        }

        // check for a graphic within a graphic, so we can change the graphic color
        for (DragGraphic dg2 : this.getAllDragMeasureGraphics()) {

          if (dg2.getUuid() == dg.getUuid()) {
            continue;
          } // don't process the identical graphic

          if (findUltrasoundRegionWithMeasurement(regions, dg2) != regionWithMeasurement) {
            continue;
          }  // only care about those in the same region

          // #E977AF ("E6") and #CCCC99 ("F6") from color palette as RegionDrawPrimitive.cpp in Imagio SW
          Color pink = Color.decode("#E977AF");
          Color yellow = Color.decode("#CCCC99");
          if (dg.containsGraphic(dg2)) {
            LOGGER.debug("dg contains dg2, changing color.  (" + dg.getPts() + " | " + dg2.getPts() + ")");
            dg.setPaint(pink);
            dg2.setPaint(yellow);

          } else if (dg2.containsGraphic(dg)) {
            LOGGER.debug("dg2 contains dg, changing color.  (" + dg2.getPts() + " | " + dg.getPts() + ")");
            dg.setPaint(yellow);
            dg2.setPaint(pink);

          }

          // fix the colors of all graphics across ultrasound regions
          if (dg2.containsGraphic(dg) || dg.containsGraphic(dg2)) {
            for (DragGraphic dg3 : this.getAllDragMeasureGraphics()) {
              if (dg3.getUltrasoundRegionGroupID() == dg2.getUltrasoundRegionGroupID()) {
                dg3.setPaint((Color) dg2.getColorPaint());
              }
            }
          }

        }

        BufferedWriter bw = null;
        try {

          //
          // create file that contain the ROI point set
          //
          dg.setUltrasoundRegionGroupID(UUID.randomUUID().toString());
          bw = createROIPointsFile(
                  ((DcmMediaReader) view2d.getImageLayer().getSourceImage().getMediaReader()).getDicomObject(),
                  dg.getUltrasoundRegionGroupID(),
                  dg,
                  view2d.getFrameIndex() + 1);
          //
          // draw the graphic on all regions
          //
          int sourceUnits = Ultrasound.getUnitsForXY(regions.get(regionWithMeasurement)); // for scaling
          for (int i = 0; i < regions.size(); i++) {

            if (i == regionWithMeasurement) {
              for (Point2D p : dg.getPts()) {
                bw.write(i + "," + p.getX() + "," + p.getY() + "\n");
              }
              continue;   // don't draw on the one that already has it
            }

            Integer destUnits = Ultrasound.getUnitsForXY(regions.get(i));
            if (sourceUnits != destUnits) {
              LOGGER.warn("destination region " + i + " unit type " + destUnits + " does not equal source unit type " + sourceUnits + ".  not replicating.");
              continue;
            }

            DragGraphic c = dg.copy();
            c.setUltrasoundRegionGroupID(dg.getUltrasoundRegionGroupID());
            List<Point2D> newPts = createNewPointsForUltrasoundRegion(regions.get(regionWithMeasurement), regions.get(i), dg);
            LOGGER.debug("replicating shape to region " + i + " with points " + newPts);
            for (Point2D p : newPts) {
              bw.write(i + "," + p.getX() + "," + p.getY() + "\n");
            }
            c.setPts(newPts);
            c.buildShape(null);
            c.setHandledForUltrasoundRegions(Boolean.TRUE);
            AbstractGraphicModel.addGraphicToModel(view2d, c);
          }
        } catch (IOException e) {
          System.err.println("Error: " + e.getMessage());
        } finally {
          if (bw != null) {
            try {
              bw.close();
            } catch (IOException e) {
              System.err.println("Error: " + e.getMessage());
            }
          }
        }
        dg.setHandledForUltrasoundRegions(Boolean.TRUE);
        fireChanged(); // force a re-draw
      }
    }
  }

  public static BufferedWriter createROIPointsFile(Attributes a, String regionUID, DragGraphic dg, int frameIndex) throws IOException {
    String studyUID = DicomMediaUtils.getStringFromDicomElement(a, Tag.StudyInstanceUID);
    String seriesUID = DicomMediaUtils.getStringFromDicomElement(a, Tag.SeriesInstanceUID);
    String instanceUID = DicomMediaUtils.getStringFromDicomElement(a, Tag.SOPInstanceUID);
    String filename = new String(System.getProperty("user.home") + "\\Desktop\\weasis-roi-points\\study-" + studyUID + "\\series-" + seriesUID + "\\instance-" + instanceUID + "\\" +  "frame-" + frameIndex + "_uid-" +  regionUID + "_" + dg.toString() + ".txt");

    dg.setUltrasoundRegionPointsFilename(filename);
    File file = new File(dg.getUltrasoundRegionPointsFilename());
    file.getParentFile().mkdirs();
    BufferedWriter bw = new BufferedWriter(new FileWriter(file));
    bw.write("region,x,y\n");
    return bw;

  }

  /*
   * Given a list of ultrasound regions, find the one that fully contains all points in the measurement.
   *
   * Returns the index of the ultrasound region in which the measurement is contained, or -1 upon
   * no region being found.
   */
  public static int findUltrasoundRegionWithMeasurement(List<Attributes> regions, DragGraphic dg)
  {
    int regionWithMeasurement = -1; // -1 = no region identified
    for (int i = 0; i < regions.size(); i++) {

      Attributes r = regions.get(i);
      long x0 = Ultrasound.getMinX0(r);
      long y0 = Ultrasound.getMinY0(r);
      long x1 = Ultrasound.getMaxX1(r);
      long y1 = Ultrasound.getMaxY1(r);

      Boolean allPointsInRegion = Boolean.TRUE;
      for (int j = 0; j < dg.getPts().size(); j++) {
        Point2D p = dg.getPts().get(j);
        if (!(p.getX() >= x0 && p.getX() <= x1 && p.getY() >= y0 && p.getY() <= y1)) {
          allPointsInRegion = Boolean.FALSE;
        }
      }

      if (allPointsInRegion) {
        regionWithMeasurement = i;
        break;
      }
    }
    return regionWithMeasurement;
  }

  /*
   * For ultrasound regions, create the list of points for a new
   * graphic "dg" to be replicated from the "source" to the "dest".
   */
  public static List<Point2D> createNewPointsForUltrasoundRegion(
      Attributes source, Attributes dest, DragGraphic dg) {

    List<Point2D> newPts = new ArrayList<Point2D>();

    long sourceXOffset = Ultrasound.getMinX0(source);
    long sourceYOffset = Ultrasound.getMinY0(source);
    double sourceXScale = Ultrasound.getPhysicalDeltaX(source);
    double sourceYScale = Ultrasound.getPhysicalDeltaY(source);

    long destX0 = Ultrasound.getMinX0(dest);
    long destY0 = Ultrasound.getMinY0(dest);
    long destX1 = Ultrasound.getMaxX1(dest);
    long destY1 = Ultrasound.getMaxY1(dest);
    double destXScale = Ultrasound.getPhysicalDeltaX(dest);
    double destYScale = Ultrasound.getPhysicalDeltaY(dest);
    for (Point2D p : dg.getPts()) {
      double newX = destX0 + (((p.getX() - sourceXOffset) * sourceXScale) / destXScale);
      double newY = destY0 + (((p.getY() - sourceYOffset) * sourceYScale) / destYScale);
      newPts.add(new Point2D.Double(newX, newY));
    }

    return newPts;
  }


  private static void applyPaint(
      Graphic graphic, Graphics2D g2d, AffineTransform transform, Rectangle2D bounds) {
    if (graphic.getLayer().getVisible()) {
      if (bounds != null) {
        Rectangle repaintBounds = graphic.getRepaintBounds(transform);
        if (repaintBounds != null && repaintBounds.intersects(bounds)) {
          graphic.paint(g2d, transform);
        } else {
          GraphicLabel graphicLabel = graphic.getGraphicLabel();
          if (graphicLabel != null && graphicLabel.getLabels() != null) {
            Rectangle2D labelBounds = graphicLabel.getBounds(transform);
            if (labelBounds.intersects(bounds)) {
              graphic.paintLabel(g2d, transform);
            }
          }
        }
      } else { // convention is when bounds equals null graphic is repaint
        graphic.paint(g2d, transform);
        graphic.paintLabel(g2d, transform);
      }
    }
  }

  @Override
  public void addGraphicSelectionListener(GraphicSelectionListener listener) {
    if (Objects.nonNull(listener) && !selectedGraphicsListeners.contains(listener)) {
      selectedGraphicsListeners.add(listener);
    }
  }

  @Override
  public void removeGraphicSelectionListener(GraphicSelectionListener listener) {
    if (Objects.nonNull(listener)) {
      selectedGraphicsListeners.remove(listener);
    }
  }

  @Override
  public List<GraphicModelChangeListener> getChangeListeners() {
    return modelListeners;
  }

  @Override
  public void addChangeListener(GraphicModelChangeListener listener) {
    if (Objects.nonNull(listener) && !modelListeners.contains(listener)) {
      modelListeners.add(listener);
    }
  }

  @Override
  public void removeChangeListener(GraphicModelChangeListener listener) {
    Optional.ofNullable(listener).ifPresent(modelListeners::remove);
  }

  @Override
  public void fireChanged() {
    if (!changeFireingSuspended) {
      modelListeners.stream().forEach(l -> l.handleModelChanged(this));
    }
  }

  @Override
  public Boolean isChangeFireingSuspended() {
    return changeFireingSuspended;
  }

  @Override
  public void setChangeFireingSuspended(Boolean change) {
    this.changeFireingSuspended = Optional.ofNullable(change).orElse(Boolean.FALSE);
  }

  @Override
  public void dispose() {
    modelListeners.clear();
    graphicsListeners.clear();
    selectedGraphicsListeners.clear();
  }

  @Override
  public int getLayerCount() {
    return models.stream().collect(Collectors.groupingBy(getLayer)).size();
  }

  @Override
  public List<GraphicSelectionListener> getGraphicSelectionListeners() {
    return selectedGraphicsListeners;
  }

  public static Graphic drawFromCurrentGraphic(ViewCanvas<?> canvas, Graphic graphicCreator) {
    Objects.requireNonNull(canvas);
    Graphic newGraphic =
        Optional.ofNullable(graphicCreator).orElse(MeasureToolBar.selectionGraphic);
    GraphicLayer layer = getOrBuildLayer(canvas, newGraphic.getLayerType());

    if (!layer.getVisible() || !(Boolean) canvas.getActionValue(ActionW.DRAWINGS.cmd())) {
      JOptionPane.showMessageDialog(
          canvas.getJComponent(),
          Messages.getString("AbstractLayerModel.msg_not_vis"),
          Messages.getString("AbstractLayerModel.draw"),
          JOptionPane.ERROR_MESSAGE);
      return null;
    } else {
      Graphic graph = newGraphic.copy();
      if (graph != null) {
        graph.updateLabel(Boolean.TRUE, canvas);
        for (PropertyChangeListener listener : canvas.getGraphicManager().getGraphicsListeners()) {
          graph.addPropertyChangeListener(listener);
        }
        graph.setLayer(layer);
        canvas.getGraphicManager().addGraphic(graph);
      }
      return graph;
    }
  }

  public static void addGraphicToModel(ViewCanvas<?> canvas, GraphicLayer layer, Graphic graphic) {

    // check if this graphic has already been drawn (which can happen if a file with duplicated figures was previously saved is being loaded)
    boolean ptsSame = false;
    if ((graphic instanceof DragGraphic) && (graphic.getLayerType() == LayerType.MEASURE)) {
      DragGraphic dg1 = (DragGraphic) graphic;
      for (DragGraphic dg2 : canvas.getGraphicManager().getAllDragMeasureGraphics()) {
        if (dg1.getUuid() == dg2.getUuid()) {
          continue;
        } // don't process the identical graphic
        if (0 != dg1.getPts().size() && 0 != dg2.getPts().size() && dg1.arePtsSame(dg2.getPts())) {
          ptsSame = true;
          break;
        }
      }
      if (ptsSame) {
        LOGGER.debug("region with " + dg1.getPts() + " already present, not re-adding");
        return;
      }
    }

    GraphicModel gm = canvas.getGraphicManager();
    graphic.setLayer(
        Optional.ofNullable(layer)
            .orElseGet(() -> getOrBuildLayer(canvas, graphic.getLayerType())));
    graphic.updateLabel(Boolean.TRUE, canvas);
    for (PropertyChangeListener listener : canvas.getGraphicManager().getGraphicsListeners()) {
      graphic.addPropertyChangeListener(listener);
    }
    gm.addGraphic(graphic);
  }

  public static void addGraphicToModel(ViewCanvas<?> canvas, Graphic graphic) {
    AbstractGraphicModel.addGraphicToModel(canvas, null, graphic);
  }

  public static GraphicLayer getOrBuildLayer(ViewCanvas<?> canvas, LayerType layerType) {
    return canvas
        .getGraphicManager()
        .findLayerByType(layerType)
        .orElseGet(() -> new DefaultLayer(layerType));
  }

  private static Predicate<GraphicLayer> isLayerTypeEquals(LayerType type) {
    // Compare type and if the layer name is null => default layer
    return layer -> Objects.equals(layer.getType(), type) && layer.getName() == null;
  }
}
