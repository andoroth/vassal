/*
 *
 * Copyright (c) 2000-2003 by Brent Easton, Rodney Kinney
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */
package VASSAL.build.module.map.boardPicker.board;

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.documentation.HelpWindow;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.module.map.boardPicker.board.mapgrid.GridContainer;
import VASSAL.build.module.map.boardPicker.board.mapgrid.GridNumbering;
import VASSAL.build.module.map.boardPicker.board.mapgrid.Zone;
import VASSAL.configure.ConfigureTree;
import VASSAL.configure.Configurer;
import VASSAL.configure.EditPropertiesAction;
import VASSAL.configure.PropertiesWindow;
import VASSAL.configure.VisibilityCondition;
import VASSAL.i18n.Resources;
import VASSAL.tools.AdjustableSpeedScrollPane;
import VASSAL.tools.ErrorDialog;
import VASSAL.tools.image.ImageUtils;
import VASSAL.tools.swing.SwingUtils;
import org.apache.commons.lang3.SystemUtils;

import javax.swing.Action;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DragSourceMotionListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegionGrid extends AbstractConfigurable implements MapGrid, ConfigureTree.Mutable {
  private static final long serialVersionUID = 1L;

  // AreaList is the table of Map areas
  // pointList is a cross-reference of points to Area names

  protected Map<Point, Region> regionList = new HashMap<>();
  protected GridContainer container;
  protected boolean visible = false;
  protected static boolean inConfig = false;
  protected int fontSize = 9; // Size square to display when configuring
  protected boolean snapTo = true;
  protected Config regionConfigurer;

  protected GridNumbering gridNumbering;

  public Map<Point, Region> getRegionList() {
    return regionList;
  }

  public void addRegion(Region a) {
    regionList.put(a.getOrigin(), a);
    if (inConfig && regionConfigurer != null) {
      regionConfigurer.view.repaint();
    }
  }

  public void removeRegion(Region a) {
    regionList.remove(a.getOrigin());
  }

  public void removeAllRegions() {
    regionList.clear();
    buildComponents.clear();
  }

  /**
   * @return Parent zone (if any) of this grid.
   */
  public Zone getZone() {
    final Buildable ancestor = getNonFolderAncestor();
    if (ancestor instanceof Zone) {
      return (Zone)ancestor;
    }
    return null;
  }

  public boolean isOutsideZone(Point pt) {
    final Zone zone = getZone();
    if (zone == null) {
      return false;
    }

    final Polygon poly = zone.getPolygon();
    if (poly == null) {
      return false;
    }
    return !poly.contains(pt);
  }

  @Override
  public GridNumbering getGridNumbering() {
    return gridNumbering;
  }

  public void setGridNumbering(GridNumbering gridNumbering) {
    this.gridNumbering = gridNumbering;
  }

  public int getFontSize() {
    return fontSize;
  }

  public static final String SNAPTO = "snapto"; //$NON-NLS-1$
  public static final String VISIBLE = "visible"; //$NON-NLS-1$
  public static final String FONT_SIZE = "fontsize"; //$NON-NLS-1$

  @Override
  public String[] getAttributeNames() {
    return new String[]{
      SNAPTO,
      VISIBLE,
      FONT_SIZE
    };
  }

  @Override
  public String[] getAttributeDescriptions() {
    return new String[]{
      Resources.getString("Editor.Grid.snap"), //$NON-NLS-1$
      Resources.getString("Editor.IrregularGrid.draw"), //$NON-NLS-1$
      Resources.getString("Editor.font_size"), //$NON-NLS-1$
    };
  }

  @Override
  public Class<?>[] getAttributeTypes() {
    return new Class<?>[]{
      Boolean.class,
      Boolean.class,
      Integer.class
    };
  }

  @Override
  public Configurer getConfigurer() {
    final boolean buttonExists = config != null;
    final Configurer c = super.getConfigurer();
    if (!buttonExists) {
      final JButton b = new JButton(Resources.getString("Editor.IrregularGrid.define_regions")); //$NON-NLS-1$
      b.addActionListener(e -> configureRegions());
      ((Container) c.getControls()).add(b);
    }
    return c;
  }

  @Override
  public void addTo(Buildable b) {
    container = (GridContainer) b;
    container.setGrid(this);
  }

  @Override
  public void removeFrom(Buildable b) {
    container.removeGrid(this);
    container = null;
  }

  public static String getConfigureTypeName() {
    return Resources.getString("Editor.IrregularGrid.component_type"); //$NON-NLS-1$
  }

  @Override
  public String getConfigureName() {
    return null;
  }

  @Override
  public HelpFile getHelpFile() {
    return HelpFile.getReferenceManualPage("IrregularGrid.html"); //$NON-NLS-1$
  }

  @Override
  public String getAttributeValueString(String key) {
    if (VISIBLE.equals(key)) {
      return String.valueOf(visible);
    }
    else if (FONT_SIZE.equals(key)) {
      return String.valueOf(fontSize);
    }
    else if (SNAPTO.equals(key)) {
      return String.valueOf(snapTo);
    }
    return null;
  }

  @Override
  public VisibilityCondition getAttributeVisibility(String name) {
    if (FONT_SIZE.equals(name)) {
      return () -> visible;
    }
    else {
      return super.getAttributeVisibility(name);
    }
  }

  @Override
  public void setAttribute(String key, Object val) {
    if (val == null)
      return;
    if (VISIBLE.equals(key)) {

      if (val instanceof Boolean) {
        visible = (Boolean) val;
      }
      else if (val instanceof String) {
        visible = "true".equals(val); //$NON-NLS-1$
      }
    }
    else if (FONT_SIZE.equals(key)) {
      if (val instanceof String) {
        val = Integer.valueOf((String) val);
      }
      fontSize = (Integer) val;
    }
    else if (SNAPTO.equals(key)) {
      if (val instanceof Boolean) {
        snapTo = (Boolean) val;
      }
      else if (val instanceof String) {
        snapTo = "true".equals(val); //$NON-NLS-1$
      }
    }
  }

  public void configureRegions() {
    for (final Region r : regionList.values()) {
      r.setSelected(false);
    }
    regionConfigurer = new Config(this);
    regionConfigurer.setVisible(true);
    inConfig = true;
  }

  // Force Regions to be drawn when configuring
  @Override
  public boolean isVisible() {
    return (visible || inConfig);
  }

  public void setVisible(boolean b) {
    visible = b;
  }

  public Board getBoard() {
    return container.getBoard();
  }

  @Override
  public Class<?>[] getAllowableConfigureComponents() {
    return new Class<?>[]{Region.class};
  }

  @Override
  public Point getLocation(String name) throws BadCoords {
    final Region reg = findRegion(name);
    if (reg == null)
      throw new BadCoords();
    else
      return new Point(reg.getOrigin());
  }

  @Override
  public int range(Point p1, Point p2) {
    return (int)Math.round(p1.distance(p2));
  }

  //
  // Locate nearest point
  //
  @Override
  public Point snapTo(Point p, boolean force) {

    //
    // Need at least one point to snap to and snapping needs to be pn.
    //
    if ((!snapTo && !force) || regionList.isEmpty()) {
      return p;
    }

    return doSnap(p);
  }

  @Override
  public Point snapTo(Point p) {
    return snapTo(p, false);
  }

  @Override
  public boolean isLocationRestricted(Point p) {
    return snapTo;
  }

  //
  // Internal routine to find closest point for region name reporting
  //
  protected Point doSnap(Point p) {
    double distSq, minDistSq = Double.MAX_VALUE;
    Point snapPoint = p;

    // Iterate over each grid point and determine the closest.
    for (final Point checkPoint : regionList.keySet()) {
      distSq =
          (p.x - checkPoint.x) * (p.x - checkPoint.x)
          + (p.y - checkPoint.y) * (p.y - checkPoint.y);
      if (distSq < minDistSq) {
        minDistSq = distSq;
        snapPoint = checkPoint;
      }
    }

    return new Point(snapPoint);
  }

  @Override
  public String locationName(Point p) {

    if (regionList.isEmpty()) {
      return null;
    }

    final Region region = regionList.get(doSnap(p));
    return region != null ? region.getName() : null;
  }

  @Override
  public String localizedLocationName(Point p) {

    if (regionList.isEmpty()) {
      return null;
    }

    final Region region = regionList.get(doSnap(p));
    return region != null ? region.getLocalizedName() : null;
  }

  /**
   * Return Region selected by Point
   */
  public Region getRegion(Point p) {
    for (final Region checkRegion : regionList.values()) {
      if (checkRegion.contains(p))
        return checkRegion;
    }
    return null;
  }

  /**
   * Return Region by Name
   */
  public Region findRegion(String name) {
    for (final Region checkRegion : regionList.values()) {
      if (checkRegion.getConfigureName().equals(name)) {
        return checkRegion;
      }
    }
    return null;
  }

  //
  // Get each region to draw labels and dots
  //
  @Override
  public void draw(
    Graphics g,
    Rectangle bounds,
    Rectangle visibleRect,
    double scale,
    boolean reversed) {

    if (visible) {
      forceDraw(g, bounds, visibleRect, scale, reversed);
    }
  }

  public void forceDraw(
    Graphics g,
    Rectangle bounds,
    Rectangle visibleRect,
    double scale,
    boolean reversed) {

    for (final Region r : regionList.values()) {
      r.draw(g, bounds, visibleRect, scale, reversed);
    }
  }

  public void unSelectAll() {
    regionList.values().forEach(this::unSelect);
  }

  public void unSelect(Region r) {
    r.setSelected(false);
  }

  public static class Config extends JFrame implements MouseListener, MouseMotionListener, ActionListener, KeyListener {
    private static final long serialVersionUID = 1L;

    protected RegionGrid grid;
    protected Board board;

    protected JPanel view;
    protected JScrollPane scroll;
    protected JPopupMenu myPopup;
    protected JLabel coords;

    protected List<Region> selectedRegions = new ArrayList<>();
    protected Region lastClickedRegion = null;
    protected Point lastClick;
    protected Rectangle selectionRect = null;
    protected Point anchor;

    protected List<Region> saveRegions;

    protected boolean dirty = false;

    public Config(RegionGrid grid) {
      super(Resources.getString("Editor.IrregularGrid.regions_for", grid.container.getBoard().getName())); //$NON-NLS-1$
      board = grid.container.getBoard();
      this.grid = grid;
      initComponents();
      save();
    }

    // Main Entry Point
    protected void initComponents() {
      addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {
          doCancel();
        }
      });

      view = new Config.View(board, grid, this);

      view.addMouseListener(this);
      view.addMouseMotionListener(this);
      view.addKeyListener(this);
      view.setFocusable(true);

      scroll =
          new AdjustableSpeedScrollPane(
              view,
              JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
              JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

      scroll.setPreferredSize(new Dimension(800, 600));

      add(scroll, BorderLayout.CENTER);

      final Box bottomPanel = Box.createVerticalBox();
      final JPanel buttonPanel = new JPanel();

      final JButton okButton =
        new JButton(Resources.getString(Resources.OK));
      okButton.addActionListener(e -> close());
      buttonPanel.add(okButton);

      final JButton canButton =
        new JButton(Resources.getString(Resources.CANCEL));
      canButton.addActionListener(e -> doCancel());
      buttonPanel.add(canButton);

      final JLabel mess = new JLabel(SystemUtils.IS_OS_MAC ? Resources.getString("Editor.IrregularGrid.drag_and_drop_mac") : Resources.getString("Editor.IrregularGrid.drag_and_drop")); //$NON-NLS-1$
      mess.setAlignmentY(CENTER_ALIGNMENT);
      bottomPanel.add(mess);

      coords = new JLabel("");
      coords.setAlignmentY(CENTER_ALIGNMENT);
      bottomPanel.add(coords);
      updateCoords();

      bottomPanel.add(buttonPanel);

      add(bottomPanel, BorderLayout.SOUTH);

      // Default actions for Enter/ESC
      SwingUtils.setDefaultButtons(getRootPane(), okButton, canButton);

      //BR// Scroll the possibly-8000-pixel-wide map to some semi-sensible place to start :)
      if (!grid.regionList.isEmpty()) {
        // If any regions have been defined, then use that list
        Rectangle rect = null;
        for (final Region r : grid.regionList.values()) {
          final Point p = r.getOrigin();
          if (rect == null) {
            rect = new Rectangle(p);
          }
          else {
            rect.add(p);
          }
        }

        final Rectangle r = new Rectangle(0, 0, 800, 600);
        if (rect.getWidth() < r.getWidth()/2) {
          rect.x = (int) (rect.x + rect.getWidth()/2 - r.getWidth()/2);
          rect.width = (int)r.getWidth()/2;
        }

        if (rect.getHeight() < r.getHeight()/2) {
          rect.y = (int) (rect.y + rect.getHeight()/2 - r.getHeight()/2);
          rect.height = (int)r.getHeight()/2;
        }

        view.scrollRectToVisible(rect);
      }
      else {
        // If no regions yet, scroll to the Zone that we're in, if we're in a Zone
        scrollToZone();
      }

      scroll.revalidate();
      pack();
      repaint();
    }

    protected void updateCoords() {
      final StringBuilder sb = new StringBuilder();
      boolean any = false;
      for (final Region r : selectedRegions) {
        final Point pt = r.getOrigin();
        if (any) {
          sb.append(' ');
        }
        sb.append('(')
          .append(Math.round(pt.x))
          .append(',')
          .append(Math.round(pt.y))
          .append(')');
        any = true;
      }
      coords.setText(sb.toString());
      coords.repaint();
    }

    protected void setDirty(boolean b) {
      dirty = b;
    }

    protected void doCancel() {
      if (dirty) {
        if (JOptionPane.YES_OPTION ==
          JOptionPane.showConfirmDialog(this,
            Resources.getString("Editor.IrregularGrid.changes_made"), //$NON-NLS-1$
            "", JOptionPane.YES_NO_OPTION)) { //$NON-NLS-1$
          restore();
          close();
        }
      }
      else {
        close();
      }
    }

    protected void close() {
      inConfig = false;
      setVisible(false);
    }

    public void init() {
      for (final Region r : selectedRegions) {
        r.setSelected(false);
      }
    }

    /*
     * Clone a list of the existing regions in case we have to restore
     * after changes
     */
    public void save() {
      saveRegions = new ArrayList<>(grid.regionList.size());
      for (final Region r : grid.regionList.values()) {
        saveRegions.add(new Region(r));
      }
    }

    /*
     * Restore the original list of regions. Remove all existing regions,
     * then add the originals back in
     */
    public void restore() {
      grid.removeAllRegions();
      for (final Region r : saveRegions) {
        r.addTo(grid);
        grid.add(r);
      }
    }

    /*
     * Scrolls the map in the containing JScrollPane
     * @param dx number of pixels to scroll horizontally
     * @param dy number of pixels to scroll vertically
     */
    protected void doScroll(int dx, int dy) {
      Rectangle r = new Rectangle(scroll.getViewport().getViewRect());
      r.translate(dx, dy);
      r =
          r.intersection(
              new Rectangle(new Point(0, 0), view.getPreferredSize()));
      view.scrollRectToVisible(r);
    }

    /**
     * Scroll map so that the argument point is at least a certain distance from the visible edge
     * @param evtPt
     */
    protected void scrollAtEdge(Point evtPt, int dist) {
      final Point p =
          new Point(
              evtPt.x - scroll.getViewport().getViewPosition().x,
              evtPt.y - scroll.getViewport().getViewPosition().y);
      int dx = 0, dy = 0;
      final Dimension viewSize = scroll.getViewport().getSize();
      if (p.x < dist && p.x >= 0)
        dx = -1;
      if (p.x >= viewSize.width - dist
          && p.x < viewSize.width)
        dx = 1;
      if (p.y < dist && p.y >= 0)
        dy = -1;
      if (p.y >= viewSize.height - dist
          && p.y < viewSize.height)
        dy = 1;

      if (dx != 0 || dy != 0) {
        doScroll(2 * dist * dx, 2 * dist * dy);
      }
    }

    /* ------------------------------------------------------------------
     * The scrollpane client
     */
    public static class View extends JPanel implements DropTargetListener, DragGestureListener, DragSourceListener, DragSourceMotionListener {
      private static final long serialVersionUID = 1L;

      protected Board myBoard;
      protected RegionGrid grid;
      protected Config config;

      protected DragSource ds = DragSource.getDefaultDragSource();

      /**
       * @deprecated field is not used anywhere and will be removed, modules should introduce their own field
       */
      @Deprecated(since = "2021-12-01", forRemoval = true)
      protected boolean isDragging = false;

      protected JLabel dragCursor;
      protected JLayeredPane drawWin;
      protected Point dragStart;
      protected Point lastDragLocation = new Point();
      protected Point drawOffset = new Point();
      protected Rectangle boundingBox;
      protected int currentPieceOffsetX;
      protected int currentPieceOffsetY;
      protected int originalPieceOffsetX;
      protected int originalPieceOffsetY;

      public View(Board b, RegionGrid grid, Config config) {
        myBoard = b;
        this.grid = grid;
        this.config = config;
        new DropTarget(this, DnDConstants.ACTION_MOVE, this);
        ds.createDefaultDragGestureRecognizer(this,
          DnDConstants.ACTION_MOVE, this);
        setFocusTraversalKeysEnabled(false);
      }

      @Override
      public void paint(Graphics g) {
        final Rectangle b = getVisibleRect();
        g.clearRect(b.x, b.y, b.width, b.height);
        myBoard.draw(g, 0, 0, 1.0, this);

        final Zone zone = grid.getZone();
        if (zone != null) {
          final Polygon polygon = zone.getPolygon();
          if ((polygon != null) && (polygon.npoints > 0)) {
            final Graphics2D g2d = (Graphics2D) g;
            g2d.addRenderingHints(SwingUtils.FONT_HINTS);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
              RenderingHints.VALUE_ANTIALIAS_ON);

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.33F));

            // fill the zone
            g2d.setColor(Color.WHITE);
            g2d.fill(polygon);

            // draw the zone
            g2d.setComposite(AlphaComposite.SrcAtop);
            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(2.0F));
            g2d.drawPolygon(polygon);
          }
        }

        final Rectangle bounds =
          new Rectangle(new Point(), myBoard.bounds().getSize());
        grid.forceDraw(g, bounds, bounds, 1.0, false);
        final Rectangle selection = config.getSelectionRect();
        if (selection != null) {
          final Graphics2D g2d = (Graphics2D) g;
          final Stroke str = g2d.getStroke();
          g2d.setStroke(new BasicStroke(2.0f));
          g2d.setColor(Color.RED);
          g2d.drawRect(selection.x, selection.y, selection.width, selection.height);
          g2d.setStroke(str);
        }
      }

      @Override
      public void update(Graphics g) {
        // To avoid flicker, don't clear the display first *
        paint(g);
      }

      @Override
      public Dimension getPreferredSize() {
        return new Dimension(
            myBoard.bounds().width,
            myBoard.bounds().height);
      }

      @Override
      public void dragEnter(DropTargetDragEvent arg0) {
      }

      @Override
      public void dragExit(DropTargetEvent arg0) {
      }

      @Override
      public void dragOver(DropTargetDragEvent arg0) {
      }

      @Override
      public void drop(DropTargetDropEvent event) {
        removeDragCursor();
        final Point dragEnd = event.getLocation();
        final int x = dragEnd.x - dragStart.x;
        final int y = dragEnd.y - dragStart.y;

        for (final Region r : config.selectedRegions) {
          r.move(x, y, this);
          config.setDirty(true);
        }
        config.updateCoords();
        repaint();
      }

      @Override
      public void dropActionChanged(DropTargetDragEvent arg0) {
      }

      @Override
      public void dragGestureRecognized(DragGestureEvent dge) {
        if (!SwingUtils.isDragTrigger(dge)) {
          return;
        }

        final Point mousePosition = dge.getDragOrigin();
        dragStart = new Point(mousePosition);
        final Region r = grid.getRegion(mousePosition);
        if (r == null) {
          return;
        }

        final Point piecePosition = new Point(r.getOrigin());

        originalPieceOffsetX = piecePosition.x - mousePosition.x;
        originalPieceOffsetY = piecePosition.y - mousePosition.y;

        drawWin = null;

        makeDragCursor();
        setDragCursor();

        SwingUtilities.convertPointToScreen(drawOffset, drawWin);
        SwingUtilities.convertPointToScreen(mousePosition, drawWin);
        moveDragCursor(mousePosition.x, mousePosition.y);

        // begin dragging
        try {
          dge.startDrag(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                        new StringSelection(""), this); //$NON-NLS-1$
          dge.getDragSource().addDragSourceMotionListener(this);
        }
        catch (final InvalidDnDOperationException e) {
          ErrorDialog.bug(e);
        }
      }

      @Override
      public void dragDropEnd(DragSourceDropEvent arg0) {
        removeDragCursor();
      }

      @Override
      public void dragEnter(DragSourceDragEvent arg0) {
      }

      @Override
      public void dragExit(DragSourceEvent arg0) {
      }

      @Override
      public void dragOver(DragSourceDragEvent arg0) {
      }

      @Override
      public void dropActionChanged(DragSourceDragEvent arg0) {
      }

      @Override
      public void dragMouseMoved(DragSourceDragEvent event) {
        if (!event.getLocation().equals(lastDragLocation)) {
          lastDragLocation = event.getLocation();

          final Point pt = lastDragLocation;
          SwingUtilities.convertPointFromScreen(pt, event.getDragSourceContext().getComponent());
          grid.regionConfigurer.scrollAtEdge(pt, 15);

          moveDragCursor(event.getX(), event.getY());
          if (dragCursor != null && !dragCursor.isVisible()) {
            dragCursor.setVisible(true);
          }
        }
      }

      private void removeDragCursor() {
        if (drawWin != null) {
          if (dragCursor != null) {
            dragCursor.setVisible(false);
            drawWin.remove(dragCursor);
          }
          drawWin = null;
        }
      }

      /** Moves the drag cursor on the current draw window */
      protected void moveDragCursor(int dragX, int dragY) {
        if (drawWin != null) {
          dragCursor.setLocation(dragX - drawOffset.x, dragY - drawOffset.y);
        }
      }

      protected void setDragCursor() {
        final JRootPane rootWin = SwingUtilities.getRootPane(this);
        if (rootWin != null) {
          // remove cursor from old window
          if (dragCursor.getParent() != null) {
            dragCursor.getParent().remove(dragCursor);
          }
          drawWin = rootWin.getLayeredPane();

          dragCursor.setVisible(true);
          drawWin.add(dragCursor, JLayeredPane.DRAG_LAYER);
        }
      }

      private void makeDragCursor() {
// FIXME: make this an ImageOp?
        // create the cursor if necessary
        if (dragCursor == null) {
          dragCursor = new JLabel();
          dragCursor.setVisible(false);
        }

        currentPieceOffsetX = originalPieceOffsetX;
        currentPieceOffsetY = originalPieceOffsetY;

        // Record sizing info and resize our cursor
        boundingBox = config.getSelectedBox();
        drawOffset.move(dragStart.x - boundingBox.x, dragStart.y - boundingBox.y);

        final BufferedImage cursorImage =
          ImageUtils.createCompatibleTranslucentImage(
            boundingBox.width,
            boundingBox.height
          );
        final Graphics2D g = cursorImage.createGraphics();

        g.setComposite(
          AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));

        // Draw each region into the drag cursor in the correct place
        for (final Region r : config.selectedRegions) {
          final int x = -boundingBox.x * 2;
          final int y = -boundingBox.y * 2;
          r.draw(g, boundingBox, getVisibleRect(), 1.0f, false, x, y);
        }

        g.dispose();

        dragCursor.setSize(boundingBox.width, boundingBox.height);

        // store the bitmap in the cursor
        dragCursor.setIcon(new ImageIcon(cursorImage));
      }
    }
    /* ------------------------------------------------------------------
     * End View
     */

    /*
     * Mouse Listeners
     */

    // Mouse clicked, see if it is on a Region Point
    @Override
    public void mouseClicked(MouseEvent e) {
      lastClick = e.getPoint(); // Also used for right clicks and stuff
      if (SwingUtils.isMainMouseButtonDown(e)) {
        if (lastClickedRegion != null) {
          if (e.getClickCount() >= 2) { // Double click show properties
            if (lastClickedRegion.getConfigurer() != null) {
              final Action a = new EditRegionAction(lastClickedRegion, null, this);
              a.actionPerformed(new ActionEvent(e.getSource(), ActionEvent.ACTION_PERFORMED, "Edit")); //$NON-NLS-1$
              updateCoords();
            }
          }
        }
        view.repaint(); // Clean up selection
      }
    }

    protected void scrollToZone() {
      final Zone z = grid.getZone();
      if (z != null) {
        final Rectangle zb = z.getBounds();

        final Point zc = new Point(
          zb.x + zb.width / 2,
          zb.y + zb.height / 2
        );

        final Rectangle r = view.getVisibleRect();
        r.x = zc.x - r.width / 2;
        r.y = zc.y - r.height / 2;

        view.scrollRectToVisible(r);
      }
    }

    protected static final String ADD_REGION = Resources.getString("Editor.IrregularGrid.add_region"); //$NON-NLS-1$
    protected static final String DELETE_REGION = Resources.getString("Editor.IrregularGrid.delete_region"); //$NON-NLS-1$
    protected static final String PROPERTIES = Resources.getString("Editor.properties"); //$NON-NLS-1$
    protected static final String MOVE_INTO_ZONE = Resources.getString("Editor.IrregularGrid.move_into_zone");
    protected static final String SHOW_ZONE = Resources.getString("Editor.IrregularGrid.show_zone");
    protected static final String SELECT_ALL = Resources.getString("Editor.IrregularGrid.select_all");

    protected void doPopupMenu(MouseEvent e) {

      // Ctrl+Click or Shift+Click add region directly at cursor
      if (SwingUtils.isSelectionToggle(e) || e.isShiftDown()) {
        lastClick = mouseLoc;
        final ActionEvent a = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ADD_REGION);
        actionPerformed(a);
        e.consume();
        return;
      }

      myPopup = new JPopupMenu();

      JMenuItem menuItem = new JMenuItem(ADD_REGION);
      menuItem.addActionListener(this);
      menuItem.setEnabled(lastClickedRegion == null);
      myPopup.add(menuItem);

      menuItem = new JMenuItem(DELETE_REGION);
      menuItem.addActionListener(this);
      menuItem.setEnabled(lastClickedRegion != null);
      myPopup.add(menuItem);

      menuItem = new JMenuItem(MOVE_INTO_ZONE);
      menuItem.addActionListener(this);
      menuItem.setEnabled(lastClickedRegion != null && grid.isOutsideZone(lastClickedRegion.getOrigin()));
      myPopup.add(menuItem);

      menuItem = new JMenuItem(SHOW_ZONE);
      menuItem.addActionListener(this);
      menuItem.setEnabled(grid.getZone() != null);
      myPopup.add(menuItem);

      myPopup.addSeparator();

      menuItem = new JMenuItem(SELECT_ALL);
      menuItem.addActionListener(this);
      myPopup.add(menuItem);

      myPopup.addSeparator();

      menuItem = new JMenuItem(PROPERTIES);
      menuItem.addActionListener(this);
      menuItem.setEnabled(lastClickedRegion != null);
      myPopup.add(menuItem);

      final Point p = e.getPoint();

      myPopup.addPopupMenuListener(new PopupMenuListener() {
        @Override
        public void popupMenuCanceled(PopupMenuEvent evt) {
          view.repaint();
        }

        @Override
        public void popupMenuWillBecomeInvisible(PopupMenuEvent evt) {
          view.repaint();
        }

        @Override
        public void popupMenuWillBecomeVisible(PopupMenuEvent evt) {
        }
      });
      myPopup.show(e.getComponent(), p.x, p.y);
    }

    @Override
    public void actionPerformed(ActionEvent e) {

      final String command = e.getActionCommand();

      if (command.equals("close")) { //NON-NLS
        this.setVisible(false);
      }
      else if (command.equals("showhide")) {  //NON-NLS
        grid.setVisible(!grid.isVisible());
        view.repaint();
      }
      else if (command.equals(ADD_REGION)) {
        final Region r = new Region(lastClick);
        r.addTo(grid);
        grid.add(r);
        select(r);
        lastClickedRegion = r;
        setDirty(true);
        final Action a = new EditRegionAction(lastClickedRegion, null, this);
        a.actionPerformed(new ActionEvent(e.getSource(), ActionEvent.ACTION_PERFORMED, "Edit")); //$NON-NLS-1$
        updateCoords();
        view.repaint();
      }
      else if (command.equals(DELETE_REGION)) {
        if ((lastClickedRegion != null) && !selectedRegions.contains(lastClickedRegion)) {
          selectedRegions.add(lastClickedRegion);
        }
        for (final Region r : selectedRegions) {
          r.removeFrom(grid);
          grid.remove(r);
          lastClickedRegion = null;
          setDirty(true);
        }
        selectedRegions.clear();
        updateCoords();
        view.repaint();
      }
      else if (command.equals(SELECT_ALL)) {
        for (final Region r : grid.regionList.values()) {
          select(r);
        }
      }
      else if (command.equals(MOVE_INTO_ZONE)) {
        final Zone zone = grid.getZone();
        if (zone != null) {
          final Polygon polygon = zone.getPolygon();
          if ((polygon != null) && (polygon.npoints > 0)) {
            final Rectangle rect = polygon.getBounds();
            int num = 0;
            for (final Region r : selectedRegions) {
              final int offX = (num > 0) ? ((num - 1) % 3) - 1 : 0;
              final int offY = ((num % 9) < 3) ? 0 : ((num % 9) < 6) ? -1 : 1;
              r.setOrigin(new Point((int)rect.getCenterX() + offX * 10, (int)rect.getCenterY() + offY * 10));
              setDirty(true);
              num++;
            }
            updateCoords();
            scrollToZone();
            view.repaint();
          }
        }
      }
      else if (command.equals(SHOW_ZONE)) {
        scrollToZone();
        view.repaint();
      }
      else if (command.equals(PROPERTIES)) { //$NON-NLS-1$
        if (lastClickedRegion != null) {
          final Action a = new EditRegionAction(lastClickedRegion, null, this);
          a.actionPerformed(new ActionEvent(e.getSource(), ActionEvent.ACTION_PERFORMED, "Edit")); //$NON-NLS-1$
          updateCoords();
        }
      }
    }

    /*
     * Version of EditProperties Action that repaints it's owning frame
     */
    protected static class EditRegionAction extends EditPropertiesAction {

      Config owner;
      Region origRegion;
      Region region;

      private static final long serialVersionUID = 1L;

      public EditRegionAction(Region target, HelpWindow helpWindow, Config dialogOwner) {
        super(target, helpWindow, dialogOwner);
        owner = dialogOwner;
        origRegion = new Region(target);
        region = target;
      }

      @Override
      public void actionPerformed(ActionEvent evt) {
        PropertiesWindow w = openWindows.get(target);
        if (w == null) {
          w = new PropertiesWindow(dialogOwner, false, target, helpWindow);
          w.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
              openWindows.remove(target);
              owner.setDirty(
                  !region.getName().equals(origRegion.getName()) ||
                  !region.getOrigin().equals(origRegion.getOrigin()));
              owner.repaint();
              owner.updateCoords();
            }
          });
          openWindows.put(target, w);
          w.setVisible(true);
        }
        w.toFront();
      }
    }

    protected void select(Region r) {
      r.setSelected(true);
      if (!selectedRegions.contains(r)) {
        selectedRegions.add(r);
      }
      updateCoords();
      view.repaint(r.getSelectionRect());
    }

    protected void unselect(Region r) {
      if (r != null) {
        r.setSelected(false);
        selectedRegions.remove(r);
        if (lastClickedRegion == r) {
          lastClickedRegion = null;
        }
        updateCoords();
        view.repaint(r.getSelectionRect());
      }
    }

    protected void unSelectAll() {
      for (final Region r : selectedRegions) {
        r.setSelected(false);
        view.repaint(r.getSelectionRect());
      }
      selectedRegions.clear();
      updateCoords();
    }

    public Rectangle getSelectionRect() {
      return selectionRect;
    }

    public Rectangle getSelectedBox() {
      Rectangle rect = null;
      for (final Region r : selectedRegions) {
        final Rectangle sel = r.getSelectionRect();
        if (rect == null) {
          rect = sel;
        }
        else {
          rect.add(sel);
        }
      }
      return rect;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
      final Point p = e.getPoint();
      lastClick = p;                          // NB These things need assigning no matter what happens in the if blocks later.
      lastClickedRegion = grid.getRegion(p);

      if (e.isPopupTrigger()) {
        doPopupMenu(e);
      }
      else if (SwingUtils.isMainMouseButtonDown(e)) {

        if (!e.isShiftDown() && !SwingUtils.isSelectionToggle(e) &&
            (lastClickedRegion == null || !lastClickedRegion.isSelected())) {
          unSelectAll();
        }

        if (lastClickedRegion == null) {
          anchor = p;
          selectionRect = new Rectangle(anchor.x, anchor.y, 0, 0);
        }
        else {
          if (SwingUtils.isSelectionToggle(e)) {
            unselect(lastClickedRegion);
          }
          else {
            select(lastClickedRegion);
          }
        }
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      if (e.isPopupTrigger()) {
        doPopupMenu(e);
      }
      else if (selectionRect != null && SwingUtils.isMainMouseButtonDown(e)) {
        for (final Region r : grid.regionList.values()) {
          if (selectionRect.contains(r.getOrigin())) {
            if (SwingUtils.isSelectionToggle(e)) {
              unselect(r);
            }
            else {
              select(r);
            }
          }
        }
        selectionRect = null;
        view.repaint();
      }
    }

    protected Point mouseLoc;

    @Override
    public void mouseMoved(MouseEvent e) {
      mouseLoc = e.getPoint();
    }

    // Scroll map if necessary
    @Override
    public void mouseDragged(MouseEvent e) {
      if (SwingUtils.isMainMouseButtonDown(e)) {
        scrollAtEdge(e.getPoint(), 15);
      }

      if (selectionRect != null) {
        // FIXME: inefficient, could be done with only one new Rectangle
        final Rectangle repaintRect =
          new Rectangle(selectionRect.x - 1, selectionRect.y - 1,
                        selectionRect.width + 3, selectionRect.height + 3);

        selectionRect.x = Math.min(e.getX(), anchor.x);
        selectionRect.y = Math.min(e.getY(), anchor.y);
        selectionRect.width = Math.abs(e.getX() - anchor.x);
        selectionRect.height = Math.abs(e.getY() - anchor.y);

        repaintRect.add(
          new Rectangle(selectionRect.x - 1, selectionRect.y - 1,
                        selectionRect.width + 3, selectionRect.height + 3));
        view.repaint(repaintRect);
      }
    }

    @Override
    public void keyPressed(KeyEvent e) {

      // Insert key adds region at current mouse position
      if (e.getKeyCode() == KeyEvent.VK_INSERT) {
        lastClick = mouseLoc;
        final ActionEvent a = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ADD_REGION);
        actionPerformed(a);
        e.consume();
        return;
      }

      /*
       * Pass key onto window scroller if no region selected
       * or control key not used.
       */
      if (selectedRegions.isEmpty() || !SwingUtils.isModifierKeyDown(e))
        return;

      int dx = 0, dy = 0, delta = 1;

      if (e.isShiftDown()) {
        delta = 5;
      }

      switch (e.getKeyCode()) {
      case KeyEvent.VK_UP:
        dy = -delta;
        break;
      case KeyEvent.VK_DOWN:
        dy = delta;
        break;
      case KeyEvent.VK_LEFT:
        dx = -delta;
        break;
      case KeyEvent.VK_RIGHT:
        dx = delta;
        break;
      default :
        return;
      }

      for (final Region r : selectedRegions) {
        r.move(dx, dy, view);
      }
      updateCoords();
      view.repaint();
      e.consume();
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }
  }
}
