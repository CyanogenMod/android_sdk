/*
 *
 *  Copyright (C) 2013 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.android.draw9patch.ui;

import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

public class ImageViewer extends JComponent {
    private final Color CORRUPTED_COLOR = new Color(1.0f, 0.0f, 0.0f, 0.7f);
    private final Color LOCK_COLOR = new Color(0.0f, 0.0f, 0.0f, 0.7f);
    private final Color STRIPES_COLOR = new Color(1.0f, 0.0f, 0.0f, 0.5f);
    private final Color BACK_COLOR = new Color(0xc0c0c0);
    private final Color HELP_COLOR = new Color(0xffffe1);
    private final Color PATCH_COLOR = new Color(1.0f, 0.37f, 0.99f, 0.5f);
    private final Color PATCH_ONEWAY_COLOR = new Color(0.37f, 1.0f, 0.37f, 0.5f);

    private static final float STRIPES_WIDTH = 4.0f;
    private static final double STRIPES_SPACING = 6.0;
    private static final int STRIPES_ANGLE = 45;

    /** Default zoom level for the 9patch image. */
    public static final int DEFAULT_ZOOM = 8;

    /** Minimum zoom level for the 9patch image. */
    public static final int MIN_ZOOM = 1;

    /** Maximum zoom level for the 9patch image. */
    public static final int MAX_ZOOM = 16;

    /** Current 9patch zoom level, {@link #MIN_ZOOM} <= zoom <= {@link #MAX_ZOOM} */
    private int zoom = DEFAULT_ZOOM;
    private boolean showPatches;
    private boolean showLock = true;

    private final TexturePaint texture;
    private final Container container;
    private final StatusBar statusBar;

    private final Dimension size;

    private boolean locked;

    private int lastPositionX;
    private int lastPositionY;
    private int currentButton;
    private boolean showCursor;

    private JLabel helpLabel;
    private boolean eraseMode;

    private JButton checkButton;
    private List<Rectangle> corruptedPatches;
    private boolean showBadPatches;

    private JPanel helpPanel;
    private boolean drawingLine;
    private int lineFromX;
    private int lineFromY;
    private int lineToX;
    private int lineToY;
    private boolean showDrawingLine;

    private BufferedImage image;
    private PatchInfo patchInfo;

    ImageViewer(Container container, TexturePaint texture, BufferedImage image,
                StatusBar statusBar) {
        this.container = container;
        this.texture = texture;
        this.image = image;
        this.statusBar = statusBar;

        setLayout(new GridBagLayout());
        helpPanel = new JPanel(new BorderLayout());
        helpPanel.setBorder(new EmptyBorder(0, 6, 0, 6));
        helpPanel.setBackground(HELP_COLOR);
        helpLabel = new JLabel("Press Shift to erase pixels."
                + " Press Control to draw layout bounds");
        helpLabel.putClientProperty("JComponent.sizeVariant", "small");
        helpPanel.add(helpLabel, BorderLayout.WEST);
        checkButton = new JButton("Show bad patches");
        checkButton.putClientProperty("JComponent.sizeVariant", "small");
        checkButton.putClientProperty("JButton.buttonType", "roundRect");
        helpPanel.add(checkButton, BorderLayout.EAST);

        add(helpPanel, new GridBagConstraints(0, 0, 1, 1,
                1.0f, 1.0f, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
                new Insets(0, 0, 0, 0), 0, 0));

        setOpaque(true);

        // Exact size will be set by setZoom() in AncestorListener#ancestorMoved.
        size = new Dimension(0, 0);

        addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorRemoved(AncestorEvent event) {
            }
            @Override
            public void ancestorMoved(AncestorEvent event) {
                // Set exactly size.
                setZoom(DEFAULT_ZOOM);
                removeAncestorListener(this);
            }
            @Override
            public void ancestorAdded(AncestorEvent event) {
            }
        });

        updatePatchInfo();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                // Store the button here instead of retrieving it again in MouseDragged
                // below, because on linux, calling MouseEvent.getButton() for the drag
                // event returns 0, which appears to be technically correct (no button
                // changed state).
                currentButton = event.isShiftDown() ? MouseEvent.BUTTON3 : event.getButton();
                currentButton = event.isControlDown() ? MouseEvent.BUTTON2 : currentButton;
                startDrawingLine(event.getX(), event.getY());
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                endDrawingLine();
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                if (!checkLockedRegion(event.getX(), event.getY())) {
                    // use the stored button, see note above

                    moveLine(event.getX(), event.getY());
                }
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                checkLockedRegion(event.getX(), event.getY());
            }
        });
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            public void eventDispatched(AWTEvent event) {
                enableEraseMode((KeyEvent) event);
            }
        }, AWTEvent.KEY_EVENT_MASK);

        checkButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                if (!showBadPatches) {
                    corruptedPatches = CorruptPatch.findBadPatches(ImageViewer.this.image,
                            patchInfo);
                    checkButton.setText("Hide bad patches");
                } else {
                    checkButton.setText("Show bad patches");
                    corruptedPatches = null;
                }
                repaint();
                showBadPatches = !showBadPatches;
            }
        });
    }

    private void updatePatchInfo() {
        patchInfo = new PatchInfo(image);
    }

    private void enableEraseMode(KeyEvent event) {
        boolean oldEraseMode = eraseMode;
        eraseMode = event.isShiftDown();
        if (eraseMode != oldEraseMode) {
            if (eraseMode) {
                helpLabel.setText("Release Shift to draw pixels");
            } else {
                helpLabel.setText("Press Shift to erase pixels."
                        + " Press Control to draw layout bounds");
            }
        }
    }

    private void startDrawingLine(int x, int y) {
        int left = (getWidth() - size.width) / 2;
        int top = helpPanel.getHeight() + (getHeight() - size.height) / 2;

        x = (x - left) / zoom;
        y = (y - top) / zoom;

        int width = image.getWidth();
        int height = image.getHeight();
        if (((x == 0 || x == width - 1) && (y > 0 && y < height - 1))
                || ((x > 0 && x < width - 1) && (y == 0 || y == height - 1))) {
            drawingLine = true;
            lineFromX = x;
            lineFromY = y;
            lineToX = x;
            lineToY = y;

            showDrawingLine = true;

            showCursor = false;

            repaint();
        }
    }

    private void moveLine(int x, int y) {
        if (!drawingLine) {
            return;
        }

        int left = (getWidth() - size.width) / 2;
        int top = helpPanel.getHeight() + (getHeight() - size.height) / 2;

        x = (x - left) / zoom;
        y = (y - top) / zoom;

        int width = image.getWidth();
        int height = image.getHeight();

        showDrawingLine = false;

        if (((x == lineFromX) && (y > 0 && y < height - 1))
                || ((x > 0 && x < width - 1) && (y == lineFromY))) {
            lineToX = x;
            lineToY = y;

            showDrawingLine = true;
        }

        repaint();
    }

    private void endDrawingLine() {
        if (!drawingLine) {
            return;
        }

        drawingLine = false;

        if (!showDrawingLine) {
            return;
        }

        int color;
        switch (currentButton) {
            case MouseEvent.BUTTON1:
                color = PatchInfo.BLACK_TICK;
                break;
            case MouseEvent.BUTTON2:
                color = PatchInfo.RED_TICK;
                break;
            case MouseEvent.BUTTON3:
                color = 0;
                break;
            default:
                return;
        }

        int x = lineFromX;
        int y = lineFromY;

        int dx = 0;
        int dy = 0;

        if (lineToX != lineFromX)
            dx = lineToX > lineFromX ? 1 : -1;
        else if (lineToY != lineFromY)
            dy = lineToY > lineFromY ? 1 : -1;

        do {
            image.setRGB(x, y, color);

            if (x == lineToX && y == lineToY)
                break;

            x += dx;
            y += dy;
        } while (true);

        updatePatchInfo();
        notifyPatchesUpdated();
        if (showBadPatches) {
            corruptedPatches = CorruptPatch.findBadPatches(image, patchInfo);
        }

        repaint();
    }

    private boolean checkLockedRegion(int x, int y) {
        int oldX = lastPositionX;
        int oldY = lastPositionY;
        lastPositionX = x;
        lastPositionY = y;

        int left = (getWidth() - size.width) / 2;
        int top = helpPanel.getHeight() + (getHeight() - size.height) / 2;

        x = (x - left) / zoom;
        y = (y - top) / zoom;

        int width = image.getWidth();
        int height = image.getHeight();

        statusBar.setPointerLocation(Math.max(0, Math.min(x, width - 1)),
                Math.max(0, Math.min(y, height - 1)));

        boolean previousLock = locked;
        locked = x > 0 && x < width - 1 && y > 0 && y < height - 1;

        boolean previousCursor = showCursor;
        showCursor =
                !drawingLine &&
                        ( ((x == 0 || x == width - 1) && (y > 0 && y < height - 1)) ||
                                ((x > 0 && x < width - 1) && (y == 0 || y == height - 1)) );

        if (locked != previousLock) {
            repaint();
        } else if (showCursor || (showCursor != previousCursor)) {
            Rectangle clip = new Rectangle(lastPositionX - 1 - zoom / 2,
                    lastPositionY - 1 - zoom / 2, zoom + 2, zoom + 2);
            clip = clip.union(new Rectangle(oldX - 1 - zoom / 2,
                    oldY - 1 - zoom / 2, zoom + 2, zoom + 2));
            repaint(clip);
        }

        return locked;
    }

    @Override
    protected void paintComponent(Graphics g) {
        int x = (getWidth() - size.width) / 2;
        int y = helpPanel.getHeight() + (getHeight() - size.height) / 2;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(BACK_COLOR);
        g2.fillRect(0, 0, getWidth(), getHeight());

        g2.translate(x, y);
        g2.setPaint(texture);
        g2.fillRect(0, 0, size.width, size.height);
        g2.scale(zoom, zoom);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(image, 0, 0, null);

        if (showPatches) {
            g2.setColor(PATCH_COLOR);
            for (Rectangle patch : patchInfo.patches) {
                g2.fillRect(patch.x, patch.y, patch.width, patch.height);
            }
            g2.setColor(PATCH_ONEWAY_COLOR);
            for (Rectangle patch : patchInfo.horizontalPatches) {
                g2.fillRect(patch.x, patch.y, patch.width, patch.height);
            }
            for (Rectangle patch : patchInfo.verticalPatches) {
                g2.fillRect(patch.x, patch.y, patch.width, patch.height);
            }
        }

        if (corruptedPatches != null) {
            g2.setColor(CORRUPTED_COLOR);
            g2.setStroke(new BasicStroke(3.0f / zoom));
            for (Rectangle patch : corruptedPatches) {
                g2.draw(new RoundRectangle2D.Float(patch.x - 2.0f / zoom, patch.y - 2.0f / zoom,
                        patch.width + 2.0f / zoom, patch.height + 2.0f / zoom,
                        6.0f / zoom, 6.0f / zoom));
            }
        }

        if (showLock && locked) {
            int width = image.getWidth();
            int height = image.getHeight();

            g2.setColor(LOCK_COLOR);
            g2.fillRect(1, 1, width - 2, height - 2);

            g2.setColor(STRIPES_COLOR);
            g2.translate(1, 1);
            paintStripes(g2, width - 2, height - 2);
            g2.translate(-1, -1);
        }

        g2.dispose();

        if (drawingLine && showDrawingLine) {
            Graphics cursor = g.create();
            cursor.setXORMode(Color.WHITE);
            cursor.setColor(Color.BLACK);

            x = Math.min(lineFromX, lineToX);
            y = Math.min(lineFromY, lineToY);
            int w = Math.abs(lineFromX - lineToX) + 1;
            int h = Math.abs(lineFromY - lineToY) + 1;

            x = x * zoom;
            y = y * zoom;
            w = w * zoom;
            h = h * zoom;

            int left = (getWidth() - size.width) / 2;
            int top = helpPanel.getHeight() + (getHeight() - size.height)
                    / 2;

            x += left;
            y += top;

            cursor.drawRect(x, y, w, h);
            cursor.dispose();
        }

        if (showCursor) {
            Graphics cursor = g.create();
            cursor.setXORMode(Color.WHITE);
            cursor.setColor(Color.BLACK);
            cursor.drawRect(lastPositionX - zoom / 2, lastPositionY - zoom / 2, zoom, zoom);
            cursor.dispose();
        }
    }

    private void paintStripes(Graphics2D g, int width, int height) {
        //draws pinstripes at the angle specified in this class
        //and at the given distance apart
        Shape oldClip = g.getClip();
        Area area = new Area(new Rectangle(0, 0, width, height));
        if(oldClip != null) {
            area = new Area(oldClip);
        }
        area.intersect(new Area(new Rectangle(0,0,width,height)));
        g.setClip(area);

        g.setStroke(new BasicStroke(STRIPES_WIDTH));

        double hypLength = Math.sqrt((width * width) +
                (height * height));

        double radians = Math.toRadians(STRIPES_ANGLE);
        g.rotate(radians);

        double spacing = STRIPES_SPACING;
        spacing += STRIPES_WIDTH;
        int numLines = (int)(hypLength / spacing);

        for (int i=0; i<numLines; i++) {
            double x = i * spacing;
            Line2D line = new Line2D.Double(x, -hypLength, x, hypLength);
            g.draw(line);
        }
        g.setClip(oldClip);
    }

    @Override
    public Dimension getPreferredSize() {
        return size;
    }

    void setZoom(int value) {
        int width = image.getWidth();
        int height = image.getHeight();

        zoom = value;
        if (size.height == 0 || (getHeight() - size.height) == 0) {
            size.setSize(width * zoom, height * zoom + helpPanel.getHeight());
        } else {
            size.setSize(width * zoom, height * zoom);
        }

        if (!size.equals(getSize())) {
            setSize(size);
            container.validate();
            repaint();
        }
    }

    void setPatchesVisible(boolean visible) {
        showPatches = visible;
        updatePatchInfo();
        repaint();
    }

    void setLockVisible(boolean visible) {
        showLock = visible;
        repaint();
    }

    public void setImage(BufferedImage image) {
        this.image = image;
    }

    public BufferedImage getImage() {
        return image;
    }

    public PatchInfo getPatchInfo() {
        return patchInfo;
    }

    public interface StatusBar {
        void setPointerLocation(int x, int y);
    }

    public interface PatchUpdateListener {
        void patchesUpdated();
    }

    private final Set<PatchUpdateListener> listeners = new HashSet<PatchUpdateListener>();

    public void addPatchUpdateListener(PatchUpdateListener p) {
        listeners.add(p);
    }

    private void notifyPatchesUpdated() {
        for (PatchUpdateListener p: listeners) {
            p.patchesUpdated();
        }
    }
}