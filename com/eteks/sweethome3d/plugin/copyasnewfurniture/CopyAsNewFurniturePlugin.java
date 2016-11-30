/*
 * CopyAsNewFurniturePlugin.java 16 april 2010 
 *
 * Copyright (c) 2010 Emmanuel PUYBARET / eTeks <info@eteks.com>. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.eteks.sweethome3d.plugin.copyasnewfurniture;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import javax.jnlp.BasicService;
import javax.jnlp.ServiceManager;
import javax.jnlp.UnavailableServiceException;
import javax.media.j3d.BranchGroup;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.vecmath.Vector3f;

import com.eteks.sweethome3d.j3d.HomePieceOfFurniture3D;
import com.eteks.sweethome3d.j3d.ModelManager;
import com.eteks.sweethome3d.j3d.OBJWriter;
import com.eteks.sweethome3d.j3d.Room3D;
import com.eteks.sweethome3d.j3d.Wall3D;
import com.eteks.sweethome3d.model.CatalogLight;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.InterruptedRecorderException;
import com.eteks.sweethome3d.model.LightSource;
import com.eteks.sweethome3d.model.RecorderException;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.SelectionEvent;
import com.eteks.sweethome3d.model.SelectionListener;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.plugin.PluginAction;
import com.eteks.sweethome3d.swing.FurnitureTable;
import com.eteks.sweethome3d.swing.HomeTransferableList;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.tools.OperatingSystem;
import com.eteks.sweethome3d.tools.ResourceURLContent;
import com.eteks.sweethome3d.tools.TemporaryURLContent;
import com.eteks.sweethome3d.viewcontroller.HomeView;
import com.eteks.sweethome3d.viewcontroller.PlanView;
import com.eteks.sweethome3d.viewcontroller.ThreadedTaskController;

/**
 * A plug-in that copies selected items as new furniture into clipboard.
 * @author Emmanuel Puybaret
 */
public class CopyAsNewFurniturePlugin extends Plugin {
  @Override
  public PluginAction [] getActions() {
    return new PluginAction [] {new PluginAction(
            "com.eteks.sweethome3d.plugin.copyasnewfurniture.ApplicationPlugin", 
            "COPY_AS_NEW_FURNITURE", getPluginClassLoader()) {
        {
          if (System.getProperty("java.version").startsWith("1.5")) {
            // There's a bug in Java 1.5 that prevents to enable plugin actions afterwards
            setEnabled(true);
          }
          // Add a listener to enable action only when selection isn't empty
          getHome().addSelectionListener(new SelectionListener() {
              public void selectionChanged(SelectionEvent ev) {
                if (!System.getProperty("java.version").startsWith("1.5")) {
                  setEnabled(isSelectionCopiable());
                }
              }
            });
        }
        
        @Override
        public void execute() {
          if (isSelectionCopiable()) {
            copyAsNewPieceOfFurniture();
          }
        }
      }
    };
  }
  
  /** 
   * Returns <code>true</code> if selected items in home may be copied.
   */
  private boolean isSelectionCopiable() {
    List<Selectable> selectedItems = getHome().getSelectedItems();
    for (Selectable item : selectedItems) {
      if (item instanceof HomePieceOfFurniture
          || item instanceof Wall) {
        return true;
      }
    }
    for (Selectable item : selectedItems) {
      // Can't copy items  if selection contains only floors 
      if (item instanceof Room
          && ((Room)item).isCeilingVisible()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Copies the 3D view to clipboard as a new piece of furniture.
   */
  private void copyAsNewPieceOfFurniture() {
    final ResourceBundle resource = ResourceBundle.getBundle(
        "com.eteks.sweethome3d.plugin.copyasnewfurniture.ApplicationPlugin", 
        Locale.getDefault(), getPluginClassLoader());
    final HomeView homeView = getHomeController().getView();
    
    try {
      // Ignore plug-in in protected Java Web Start environment 
      ServiceManager.lookup("javax.jnlp.FileSaveService");
      // Use an uneditable editor pane to let user select text in dialog
      JEditorPane messagePane = new JEditorPane("text/html", 
          resource.getString("copyAsNewFurnitureJavaWebStartInfo.message"));
      messagePane.setOpaque(false);
      messagePane.setEditable(false);
      try { 
        // Lookup the javax.jnlp.BasicService object 
        final BasicService service = (BasicService)ServiceManager.lookup("javax.jnlp.BasicService");
        // If basic service supports  web browser
        if (service.isWebBrowserSupported()) {
          // Add a listener that displays hyperlinks content in browser
          messagePane.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent ev) {
              if (ev.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                service.showDocument(ev.getURL()); 
              }
            }
          });
        }
      } catch (UnavailableServiceException ex) {
        // Too bad : service is unavailable             
      } 
      
      String title = resource.getString("copyAsNewFurnitureJavaWebStartInfo.title");
      JOptionPane.showMessageDialog((JComponent)homeView, messagePane, title, JOptionPane.WARNING_MESSAGE);
      return;
    } catch (UnavailableServiceException ex) {
    }
    
    // Copy 3D view in a threaded task
    Callable<Void> exportToObjTask = new Callable<Void>() {
        public Void call() throws RecorderException {
          String pieceName = getHome().getName();
          if (pieceName == null) {
            pieceName = resource.getString("untitledPieceOfFurnitureName");
          } else {
            pieceName = new File(pieceName).getName();
            int index = pieceName.lastIndexOf(".");
            if (index != -1) {
              pieceName = pieceName.substring(0, index);
            }
          }

          Content modelContent = exportSelectedItems(pieceName);
          
          List<Selectable> selectedItems = getHome().getSelectedItems();
          float minX = Float.POSITIVE_INFINITY;
          float maxX = Float.NEGATIVE_INFINITY;
          float minY = Float.POSITIVE_INFINITY;
          float maxY = Float.NEGATIVE_INFINITY;
          float minElevation = Float.POSITIVE_INFINITY;
          float elevation = Float.POSITIVE_INFINITY;
          boolean movable = true;
          for (Wall wall : Home.getWallsSubList(selectedItems)) {
            for (float [] point : wall.getPoints()) {
              minX = Math.min(minX, point [0]);
              maxX = Math.max(maxX, point [0]);
              minY = Math.min(minY, point [1]);
              maxY = Math.max(maxY, point [1]);
            }
            minElevation = 0;
            movable = false;
          }
          List<HomePieceOfFurniture> furniture = getFurniture(selectedItems);
          for (HomePieceOfFurniture piece : furniture) {
            if (piece.isVisible()) {
              for (float [] point : piece.getPoints()) {
                minX = Math.min(minX, point [0]);
                maxX = Math.max(maxX, point [0]);
                minY = Math.min(minY, point [1]);
                maxY = Math.max(maxY, point [1]);
              }
              minElevation = Math.min(minElevation, piece.getElevation());
              movable &= piece.isMovable();
            }
          }
          for (Room room : Home.getRoomsSubList(selectedItems)) {
            for (float [] point : room.getPoints()) {
              minX = Math.min(minX, point [0]);
              maxX = Math.max(maxX, point [0]);
              minY = Math.min(minY, point [1]);
              maxY = Math.max(maxY, point [1]);
            }
            if (room.isFloorVisible()) {
              minElevation = 0;
              // Set a minimum elevation otherwise it won't be seen in 3D view
              elevation = 0.1f;
            }
            movable = false;
          }
          if (Float.isInfinite(elevation)) {
            if (Float.isInfinite(minElevation)) {
              elevation = 0;
            } else {
              elevation = minElevation;
            }
          }
          // Gather light sources
          float maxLightPower = 0;
          int lightCount = 0;
          for (HomePieceOfFurniture piece : furniture) {
            if (piece.isVisible() 
                && piece instanceof HomeLight) {
              maxLightPower = Math.max(maxLightPower, ((HomeLight)piece).getPower());
              lightCount++;
            }
          }
          List<LightSource> lightSources = new ArrayList<LightSource>();
          if (lightCount > 0) {
            for (HomePieceOfFurniture piece : furniture) {
              if (piece.isVisible() 
                  && piece instanceof HomeLight) {
                HomeLight light = (HomeLight)piece;
                float lightPower = light.getPower();
                float angle = light.getAngle();
                float cos = (float)Math.cos(angle);
                float sin = (float)Math.sin(angle);
                for (LightSource lightSource : ((HomeLight)light).getLightSources()) {
                  float lightSourceRadius = lightSource.getDiameter() != null 
                          ? lightSource.getDiameter() * light.getWidth() / 2 
                          : 3.25f; 
                  int lightSourceColor = lightSource.getColor();
                  if (maxLightPower > 0 || maxLightPower != lightPower) {
                    float powerFactor = Math.max(0.1f, lightPower / maxLightPower);
                    powerFactor *= powerFactor;
                    lightSourceColor = Math.round(powerFactor * (lightSourceColor >> 16)) << 16
                        | Math.round(powerFactor * ((lightSourceColor >> 8) & 0xFF)) << 8
                        | Math.round(powerFactor * (lightSourceColor & 0xFF));
                  }
                  float xLightSourceInLight = -light.getWidth() / 2 + (lightSource.getX() * light.getWidth());
                  float yLightSourceInLight = light.getDepth() / 2 - (lightSource.getY() * light.getDepth());
                  lightSources.add(new LightSource(
                      light.getX() + xLightSourceInLight * cos - yLightSourceInLight * sin - minX,
                      maxY - (light.getY() + xLightSourceInLight * sin + yLightSourceInLight * cos),
                      light.getElevation() + (lightSource.getZ() * light.getHeight()) - minElevation,
                      lightSourceColor, lightSourceRadius * 2));                    
                }
              }
            }
          }
          copyModelAsNewPieceOfFurniture(modelContent, pieceName, 
              (minX + maxX) / 2, (minY + maxY) / 2, elevation, movable, 
              lightSources, maxLightPower, homeView);
          return null;
        }
      };
    ThreadedTaskController.ExceptionHandler exceptionHandler = 
        new ThreadedTaskController.ExceptionHandler() {
          public void handleException(Exception ex) {
            if (!(ex instanceof InterruptedRecorderException)) {
              showError((JComponent)homeView, resource, ex.getMessage());
              ex.printStackTrace();
            }
          }
        };
    new ThreadedTaskController(exportToObjTask, 
        resource.getString("copyAsNewFurnitureMessage"), exceptionHandler, 
        getUserPreferences(), new SwingViewFactory()).executeTask(homeView);
  }
  
  /**
   * Returns all the pieces of furniture among the given <code>items</code>.  
   */
  private List<HomePieceOfFurniture> getFurniture(List<? extends Selectable> items) {
    List<HomePieceOfFurniture> pieces = new ArrayList<HomePieceOfFurniture>();
    for (Selectable item : items) {
      if (item instanceof HomeFurnitureGroup) {
        pieces.addAll(getFurniture(((HomeFurnitureGroup)item).getFurniture()));
      } else if (item instanceof HomePieceOfFurniture) {
        pieces.add((HomePieceOfFurniture)item);
      }
    }
    return pieces;
  }

  /**
   * Exports the selected items in home and returns a content matching it. 
   */
  private Content exportSelectedItems(String pieceName) throws RecorderException {
    OBJWriter writer = null;
    try {
      List<Selectable> selectedItems = getHome().getSelectedItems();      
      
      BranchGroup root = new BranchGroup();
      // Add 3D walls 
      for (Wall wall : Home.getWallsSubList(selectedItems)) {
        root.addChild(new Wall3D(wall, getHome(), true, true));
      }
      // Add 3D furniture 
      for (HomePieceOfFurniture piece : Home.getFurnitureSubList(selectedItems)) {
        if (piece.isVisible()) {
          root.addChild(new HomePieceOfFurniture3D(piece.clone(), getHome(), true, true));
        }
      }
      // Add 3D rooms 
      for (Room room : Home.getRoomsSubList(selectedItems)) {
        try {
          root.addChild(new Room3D(room, getHome(), false, true));
        } catch (NoSuchMethodError ex) {
          // Try by reflection with constructor available in version < 4.6
          try {
            root.addChild((Room3D)Room3D.class.getConstructor(Room.class, Home.class, boolean.class, boolean.class, boolean.class).
                newInstance(room, getHome(), false, true, true));
          } catch (Exception ex2) {
            System.out.println("Unable to export room");
          }
        }
      }
      
      for (int i = 0; i < pieceName.length(); i++) {
        if (pieceName.charAt(i) > 127) {
          pieceName = "piece";
          break;
        }
      }
      while (pieceName.length() < 3) {
        pieceName += "_";
      }
      File tempZipFile = OperatingSystem.createTemporaryFile(pieceName, ".zip");
      String objFile = pieceName + ".obj";
      OBJWriter.writeNodeInZIPFile(root, tempZipFile, 0, objFile, null);
      return new TemporaryURLContent(new URL("jar:" + tempZipFile.toURI().toURL() + "!/" + objFile));
    } catch (InterruptedIOException ex) {
      throw new InterruptedRecorderException("Export to OBJ interrupted");
    } catch (IOException ex) {
      throw new RecorderException("Couldn't export to OBJ", ex);
    } finally {
      if (writer != null) {
        try {
          writer.close();
        } catch (IOException ex) {
          throw new RecorderException("Couldn't export to OBJ", ex);
        }
      }
    }
  }

  /**
   * Copies into clipboard a new piece of furniture based on the given model.
   */
  private void copyModelAsNewPieceOfFurniture(final Content modelContent, 
                                              final String pieceName,
                                              final float x,
                                              final float y,
                                              final float elevation,
                                              final boolean movable,
                                              final List<LightSource> lightSources, 
                                              final float lightPower, 
                                              final HomeView homeView) {
    ModelManager.getInstance().loadModel(modelContent, true, new ModelManager.ModelObserver() {
        public void modelUpdated(BranchGroup modelRoot) {
          Vector3f size = ModelManager.getInstance().getSize(modelRoot);
          Content iconContent = new ResourceURLContent(SwingViewFactory.class, "resources/aboutIcon.png");
          // Adjust light sources location relative to model size 
          for (int i = 0; i < lightSources.size(); i++) {
            LightSource lightSource = lightSources.get(i);
            lightSources.set(i, new LightSource(lightSource.getX() / size.x, lightSource.getY() / size.z, lightSource.getZ() / size.y, 
                lightSource.getColor(), lightSource.getDiameter() / size.x));
          }
          HomePieceOfFurniture piece;
          if (lightSources.size() == 0) {
            piece = new HomePieceOfFurniture(new CatalogPieceOfFurniture(
                null, pieceName, null, iconContent, modelContent, 
                size.x, size.z, size.y, 
                0, movable, null, System.getProperty("user.name"), true, null, null));
          } else {
            HomeLight light = new HomeLight(new CatalogLight(
                null, pieceName, null, iconContent, modelContent, 
                size.x, size.z, size.y, 
                0, movable, lightSources.toArray(new LightSource [lightSources.size()]), 
                null, System.getProperty("user.name"), true, null, null));
            light.setPower(lightPower);
            piece = light;
          }
          piece.setX(x);
          piece.setY(y);
          piece.setElevation(elevation);
          
          Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
              new HomeTransferableList(Arrays.asList(new Selectable [] {piece})), null);
          
          Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
          if (focusOwner instanceof PlanView || focusOwner instanceof FurnitureTable) {
            homeView.setEnabled(HomeView.ActionType.PASTE, true);
          }
        }
        
        public void modelError(Exception ex) {
          // Shouldn't happen since we import a model we just exported
        }
      });
  }

  /**
   * Shows a message error.
   */
  private void showError(Component parent,
                         ResourceBundle resource, 
                         String messageDetail) {
    String messageFormat = resource.getString("copyAsNewFurnitureError.message");
    JOptionPane.showMessageDialog(parent, String.format(messageFormat, messageDetail), 
        resource.getString("copyAsNewFurnitureError.title"), JOptionPane.ERROR_MESSAGE);
  }
}
