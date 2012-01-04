/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.gltrace.editors;

import com.android.ide.eclipse.gltrace.model.GLCall;
import com.android.ide.eclipse.gltrace.model.GLTrace;

import org.eclipse.jface.resource.FontRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ScrollBar;

import java.util.ArrayList;
import java.util.List;

/**
 * TimeLineSurface allows users to select GL frames/calls of interest. It shows two pieces of
 * information: A scale with markers indicating frame numbers, and for a particular selected
 * frame[s], it displays a graph of call durations for all OpenGL calls present within the
 * selection.
 */
public class TimeLineSurface extends Canvas {
    /** Default alpha value. */
    private static final int DEFAULT_ALPHA = 255;

    /** Alpha value for highlighting selected frame. */
    private static final int SELECTED_FRAME_HIGHLIGHT_ALPHA = 80;

    /** Alpha value for highlighting frame hovered on. */
    private static final int HOVER_FRAME_HIGHLIGHT_ALPHA = 40;

    private static final String FONT_KEY = "default.font";      //$NON-NLS-1$

    /** Clamp call durations at this value. */
    private static final long CALL_DURATION_CLAMP = 20000;

    /** Horizontal offset for frame number text in the frame selector scale. */
    private static final int FRAME_NUMBER_HOFFSET = 5;

    /** Vertical offset for frame number text in the frame selector scale. */
    private static final int FRAME_NUMBER_VOFFSET = 10;

    /** Maximum width of the frame selector scale. */
    private static final int MAX_FRAME_SCALE_WIDTH = 5000;

    /** In the frame scale, the submarkers are scaled by this value wrt the main frame markers. */
    private static final double SUBMARKER_SCALE = 0.2;

    /** In the frame scale, display marker numbers modulo this value. */
    private static final int FRAME_NUMBER_DIVISOR_FOR_DISPLAY = 1000;

    private Rectangle mClientArea;

    private Color mBackgroundColor;
    private Color mFrameMarkerColor;
    private Color mFrameHighlightColor;
    private Color mDurationLineColor;

    private Cursor mCursorHand;
    private Cursor mCursorArrow;

    private FontRegistry mFontRegistry;
    private int mFontWidth;
    private int mFontHeight;

    private Image mBackBufferImage;
    private GC mGcBackBuffer;

    private final GLTrace mTrace;
    private final XScaleHelper mXScaleHelper;
    private final YScaleHelper mYScaleHelper;
    private YPositionHelper mYPositionHelper;

    // mouse state
    private int mMouseX;
    private int mMouseY;
    private boolean mMouseInSelf;

    private int mCallStartIndex;
    private int mCallEndIndex;
    private int mSelectedFrameIndexStart;
    private int mSelectedFrameIndexEnd;

    public TimeLineSurface(Composite parent, GLTrace trace) {
        super(parent, SWT.NO_BACKGROUND | SWT.H_SCROLL);

        mClientArea = getClientArea();
        initializeColors();
        initializeCursors();
        initializeFonts();

        mTrace = trace;
        mXScaleHelper = new XScaleHelper(mTrace.getFrames().size(), mFontWidth,
                                            MAX_FRAME_SCALE_WIDTH);
        mYScaleHelper = new YScaleHelper(CALL_DURATION_CLAMP, mFontHeight * 2);
        mYPositionHelper = new YPositionHelper(mTrace.getContexts().size(), mFontHeight);

        mSelectedFrameIndexStart = 0;
        mSelectedFrameIndexEnd = mXScaleHelper.getFramesPerMarker();
        updateCallIndicesForSelection();

        addPaintListener(new PaintListener() {
            @Override
            public void paintControl(PaintEvent e) {
                draw(e.display, e.gc);
            }
        });

        addListener(SWT.Resize, new Listener() {
            @Override
            public void handleEvent(Event event) {
                controlResized();
            }
        });

        getHorizontalBar().addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                redraw();
            }
        });

        addMouseMoveListener(new MouseMoveListener() {
            @Override
            public void mouseMove(MouseEvent e) {
                mouseMoved(e);
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseUp(MouseEvent e) {
                mouseClicked(e);
            }
        });

        addMouseTrackListener(new MouseTrackListener() {
            @Override
            public void mouseHover(MouseEvent e) {
            }

            @Override
            public void mouseExit(MouseEvent e) {
                mMouseInSelf = false;
                redraw();
            }

            @Override
            public void mouseEnter(MouseEvent e) {
                mMouseInSelf = true;
                redraw();
            }
        });
    }

    @Override
    public void dispose() {
        disposeColors();
        disposeCursors();
        disposeBackBuffer();
        super.dispose();
    }

    private void initializeColors() {
        mBackgroundColor = new Color(getDisplay(), 0x33, 0x33, 0x33);
        mFrameMarkerColor = new Color(getDisplay(), 0xd1, 0xe5, 0xf0);
        mDurationLineColor = new Color(getDisplay(), 0x21, 0x66, 0xac);
        mFrameHighlightColor = new Color(getDisplay(), 0xcc, 0xcc, 0xcc);
    }

    private void disposeColors() {
        mBackgroundColor.dispose();
        mFrameMarkerColor.dispose();
        mDurationLineColor.dispose();
        mFrameHighlightColor.dispose();
    }

    private void initializeCursors() {
        mCursorHand = new Cursor(getDisplay(), SWT.CURSOR_HAND);
        mCursorArrow = new Cursor(getDisplay(), SWT.CURSOR_ARROW);
    }

    private void disposeCursors() {
        mCursorHand.dispose();
        mCursorArrow.dispose();
    }

    private void initializeFonts() {
        mFontRegistry = new FontRegistry(getDisplay());
        mFontRegistry.put(FONT_KEY,
                new FontData[] { new FontData("Arial", 8, SWT.NORMAL) });  //$NON-NLS-1$

        GC gc = new GC(getDisplay());
        gc.setFont(mFontRegistry.get(FONT_KEY));
        mFontWidth = gc.getFontMetrics().getAverageCharWidth();
        mFontHeight = gc.getFontMetrics().getHeight();

        gc.dispose();
    }

    private void initializeBackBuffer() {
        // render all contents to a new buffer
        mBackBufferImage = new Image(getDisplay(),
                mXScaleHelper.getScaleLength(),
                mClientArea.height);
        mGcBackBuffer = new GC(mBackBufferImage);
    }

    private void disposeBackBuffer() {
        if (mBackBufferImage != null) {
            mBackBufferImage.dispose();
        }

        if (mGcBackBuffer != null) {
            mGcBackBuffer.dispose();
        }
    }

    private void mouseMoved(MouseEvent e) {
        if (mYPositionHelper.getElementAt(e.y) == YPositionHelper.Element.FRAME_SELECTOR) {
            setCursor(mCursorHand);
        } else {
            setCursor(mCursorArrow);
        }

        mMouseX = e.x + getHorizontalBar().getSelection();
        mMouseY = e.y; // no offset for scrolling since we never have a vertical scrollbar.

        redraw();
    }

    private void mouseClicked(MouseEvent e) {
        if (mYPositionHelper.getElementAt(e.y) == YPositionHelper.Element.FRAME_SELECTOR) {
            int[] frames = mXScaleHelper.getFramesAt(getHorizontalBar().getSelection() + e.x);
            mSelectedFrameIndexStart = frames[0];
            mSelectedFrameIndexEnd = frames[frames.length - 1];
            updateCallIndicesForSelection();

            sendFrameSelectedEvent(frames);
            redraw();
        }
    }

    private void updateCallIndicesForSelection() {
        mCallStartIndex = mTrace.getFrames().get(mSelectedFrameIndexStart).getStartIndex();
        mCallEndIndex = mTrace.getFrames().get(mSelectedFrameIndexEnd).getEndIndex();
    }

    private void draw(Display display, GC gc) {
        if (mBackBufferImage == null) {
            initializeBackBuffer();
        }

        // draw contents onto the back buffer
        drawBackground(mGcBackBuffer, mBackBufferImage.getBounds());
        drawFrameMarkers(mGcBackBuffer);
        highlightSelectedFrame(mGcBackBuffer);
        highlightHoverFrame(mGcBackBuffer);
        drawCallDurations(mGcBackBuffer);

        // finally copy over the rendered back buffer onto screen
        int hScroll = getHorizontalBar().getSelection();
        gc.drawImage(mBackBufferImage,
                hScroll, 0, mClientArea.width, mClientArea.height,
                0, 0, mClientArea.width, mClientArea.height);
    }

    private void drawBackground(GC gc, Rectangle bounds) {
        gc.setBackground(mBackgroundColor);
        gc.fillRectangle(bounds);
    }

    private void drawFrameMarkers(GC gc) {
        gc.setForeground(mFrameMarkerColor);
        gc.setFont(mFontRegistry.get(FONT_KEY));

        int yOffset = mYPositionHelper.getFrameScaleOffset();
        int frameMarkerHeight = mYPositionHelper.getFrameScaleHeight();

        int framesPerMarker = mXScaleHelper.getFramesPerMarker();
        for (int i = 0; i < mTrace.getFrames().size(); i += framesPerMarker) {
            Rectangle hBounds = mXScaleHelper.getFrameBounds(i);
            int x = hBounds.x;

            // draw initial marker line
            gc.drawLine(x, yOffset, x, yOffset + frameMarkerHeight);

            // draw text indicating frame number
            gc.drawText(Integer.toString(i % FRAME_NUMBER_DIVISOR_FOR_DISPLAY),
                    x + FRAME_NUMBER_HOFFSET,
                    yOffset + FRAME_NUMBER_VOFFSET,
                    true);

            // draw sub markers
            if (framesPerMarker > 1) {
                float submarkerwidth = (float) hBounds.width / framesPerMarker;
                int submarkerheight = (int) (frameMarkerHeight * SUBMARKER_SCALE);
                for (int subMarkerIndex = 1; subMarkerIndex < framesPerMarker; subMarkerIndex++) {
                    gc.drawLine(x + (int)(subMarkerIndex * submarkerwidth),
                                yOffset,
                                x + (int)(subMarkerIndex * submarkerwidth),
                                yOffset + submarkerheight);
                }
            }

            // draw end marker line
            gc.drawLine(x + hBounds.width, yOffset, x + hBounds.width, yOffset + frameMarkerHeight);
        }
    }

    private void highlightSelectedFrame(GC gc) {
        Rectangle hBounds = mXScaleHelper.getFrameBounds(mSelectedFrameIndexStart);
        highlightFrameArea(gc, hBounds, SELECTED_FRAME_HIGHLIGHT_ALPHA);
    }

    private void highlightHoverFrame(GC gc) {
        // do not highlight if mouse is not inside the control
        if (!mMouseInSelf) {
            return;
        }

        // do not highlight if mouse is not over the frame selector area
        if (mYPositionHelper.getElementAt(mMouseY) != YPositionHelper.Element.FRAME_SELECTOR) {
            return;
        }

        Rectangle hBounds = mXScaleHelper.getFrameBoundsForCoordinates(mMouseX);
        highlightFrameArea(gc, hBounds, HOVER_FRAME_HIGHLIGHT_ALPHA);
    }

    private void highlightFrameArea(GC gc, Rectangle bounds, int alpha) {
        gc.setAlpha(alpha);
        gc.setBackground(mFrameHighlightColor);
        gc.fillRectangle(bounds.x, mYPositionHelper.getFrameScaleOffset(),
                bounds.width,
                mYPositionHelper.getFrameScaleHeight() + 1);
        gc.setAlpha(DEFAULT_ALPHA);
    }

    private void drawCallDurations(GC gc) {
        List<GLCall> calls = mTrace.getGLCalls().subList(mCallStartIndex, mCallEndIndex);

        gc.setBackground(mDurationLineColor);
        int width = 1;

        int xOffset = getHorizontalBar().getSelection();

        for (int i = 0; i < calls.size(); i++) {
            GLCall call = calls.get(i);
            Rectangle rect = new Rectangle(xOffset + i * width,
                    mYPositionHelper.getDurationOffset(call.getContextId()),
                    width,
                    mYScaleHelper.getScaledHeight(call.getDuration()));
            gc.fillRectangle(rect);
        }
    }

    private void controlResized() {
        mClientArea = getClientArea();

        // regenerate back buffer on size changes
        disposeBackBuffer();
        initializeBackBuffer();

        // update scrollbar settings
        if (mXScaleHelper.needsScroll(mClientArea.width)) {
            ScrollBar scrollBar = getHorizontalBar();
            scrollBar.setEnabled(true);

            int totalWidth = mXScaleHelper.getScaleLength();
            int pageWidth = mClientArea.width;

            scrollBar.setMinimum(0);
            scrollBar.setMaximum(totalWidth);
            scrollBar.setThumb(pageWidth);
            scrollBar.setPageIncrement(pageWidth);
        } else {
            getHorizontalBar().setEnabled(false);
        }

        redraw();
    }

    public int getMinimumHeight() {
        return mYPositionHelper.getTotalHeight();
    }

    public interface IFrameSelectionListener {
        void frameSelected(int[] selectedFrames);
    }

    private List<IFrameSelectionListener> mListeners = new ArrayList<IFrameSelectionListener>();

    public void addFrameSelectionListener(IFrameSelectionListener l) {
        mListeners.add(l);
    }

    private void sendFrameSelectedEvent(int[] frames) {
        for (IFrameSelectionListener l : mListeners) {
            l.frameSelected(frames);
        }
    }

    /** Utility class to help with positioning GL frame and GL call markers on X axis. */
    private static class XScaleHelper {
        /** Scale font width by this factor to get the marker width. */
        private static final int MARKER_WIDTH_MULTIPLIER = 6;

        /** Distance between tick markers on the X axis. */
        private final int mMarkerWidth;

        /** Total length of the X axis. */
        private final int mTotalScaleLength;

        /** Number of frames per marker tick. */
        private final int mFramesPerMarker;

        /**
         * Construct the X scale helper for a particular trace file.
         * @param frameCount number of frames in the trace file
         * @param fontWidth width of font used
         * @param maxWidth maximum width of the scale
         */
        public XScaleHelper(int frameCount, int fontWidth, int maxWidth) {
            // Distance between markers is a multiple of the font width.
            // The multiple is arbitrary and is just something that looks good.
            mMarkerWidth = fontWidth * MARKER_WIDTH_MULTIPLIER;

            // If the space required to show all the frames exceeds the max length,
            // then we have to pack multiple frames between the markers
            mFramesPerMarker = (frameCount * mMarkerWidth) / maxWidth + 1;

            // The total scale length = # of markers times the size of each marker
            mTotalScaleLength = (frameCount / mFramesPerMarker) * mMarkerWidth;
        }

        /** Compute the bounding rectangle for a given frame number. */
        public Rectangle getFrameBounds(int frameNumber) {
            return new Rectangle(frameNumber/mFramesPerMarker * mMarkerWidth, 0, mMarkerWidth, 0);
        }

        /** Does the X-axis need a scrollbar? It does if the scale length if greater
         * than the client width that can be displayed. **/
        public boolean needsScroll(int clientAreaWidth) {
            return mTotalScaleLength > clientAreaWidth ;
        }

        /** Obtain the total length of the x axis. */
        public int getScaleLength() {
            return mTotalScaleLength;
        }

        public int getFramesPerMarker() {
            return mFramesPerMarker;
        }

        /** Obtain the frames that are mapped to a given x coordinate. */
        public int[] getFramesAt(int x) {
            int[] frames = new int[mFramesPerMarker];

            int baseFrame = x / mMarkerWidth * mFramesPerMarker;
            for (int i = 0; i < frames.length; i++) {
                frames[i] = baseFrame + i;
            }

            return frames;
        }

        /** Obtain the bounding box for the frame at given x coordinate */
        public Rectangle getFrameBoundsForCoordinates(int x) {
            int baseFrame = x / mMarkerWidth * mFramesPerMarker;
            return getFrameBounds(baseFrame);
        }
    }

    /** Utility class to help figuring out heights of elements on the Y-axis. */
    private static class YScaleHelper {
        private final double mScale;
        private final long mMaxDuration;

        public YScaleHelper(long maxDuration, int maxHeight) {
            mMaxDuration = maxDuration;
            mScale = (double) maxHeight / maxDuration;
        }

        public int getScaledHeight(long duration) {
            if (duration <= 0) {
                duration = 1;
            } else if (duration > mMaxDuration)  {
                duration = mMaxDuration;
            }

            return (int) (duration * mScale);
        }
    }

    /** Utility class to help figure out locations of elements on the Y axis. */
    private static class YPositionHelper {
        public enum Element {
            FRAME_SELECTOR, CALL_SELECTOR, NONE,
        };

        private static final int DURATION_TOP_MARGIN = 10;
        private static final int DURATION_BOTTOM_MARGIN = 5;

        private final int mContextCount;
        private final int mFontHeight;

        public YPositionHelper(int contextCount, int fontHeight) {
            mContextCount = contextCount;
            mFontHeight = fontHeight;
        }

        public int getFrameScaleOffset() {
            return 2;
        }

        public int getFrameScaleHeight() {
            return mFontHeight * 2;
        }

        private int getDurationScaleHeight() {
            return mFontHeight * 2;
        }

        public int getDurationOffset(int contextId) {
            return getFrameScaleHeight()
                    + DURATION_TOP_MARGIN
                    + (getDurationScaleHeight() + DURATION_BOTTOM_MARGIN) * contextId;
        }

        public int getTotalHeight() {
            return getFrameScaleHeight()
                    + mContextCount * (getDurationScaleHeight() + DURATION_TOP_MARGIN);
        }

        public Element getElementAt(int y) {
            int frameStartY = getFrameScaleOffset();
            int frameEndY = frameStartY + getFrameScaleHeight();
            if (y > frameStartY && y < frameEndY) {
                return Element.FRAME_SELECTOR;
            }

            int callStartY = getDurationOffset(0);
            int callEndY = getDurationOffset(mContextCount);
            if (y > callStartY && y < callEndY) {
                return Element.CALL_SELECTOR;
            }

            return Element.NONE;
        }
    }
}
