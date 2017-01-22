/*
 * Copyright (c) 2015-2017, Regents the University of California
 * Author: Nico Stuurman
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package edu.valelab.gaussianfit.datasetdisplay;


import edu.valelab.gaussianfit.DataCollectionForm;
import edu.valelab.gaussianfit.ResultsTableListener;
import edu.valelab.gaussianfit.Terms;
import edu.valelab.gaussianfit.data.GsSpotPair;
import edu.valelab.gaussianfit.data.RowData;
import edu.valelab.gaussianfit.data.SpotData;
import edu.valelab.gaussianfit.fitting.FittingException;
import edu.valelab.gaussianfit.fitting.P2DFitter;
import edu.valelab.gaussianfit.spotoperations.NearestPoint2D;
import edu.valelab.gaussianfit.spotoperations.NearestPointGsSpotPair;
import edu.valelab.gaussianfit.utils.CalcUtils;
import edu.valelab.gaussianfit.utils.GaussianUtils;
import edu.valelab.gaussianfit.utils.ListUtils;
import edu.valelab.gaussianfit.utils.NumberUtils;
import edu.valelab.gaussianfit.utils.ReportingUtils;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Arrow;
import ij.gui.ImageWindow;
import ij.gui.MessageDialog;
import ij.gui.StackWindow;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;
import java.awt.Color;
import java.awt.Frame;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.jfree.data.xy.XYSeries;
import org.micromanager.internal.MMStudio;

/**
 *
 * @author nico
 */
public class ParticlePairLister {
      final private int[] rows_;
      final private Double maxDistanceNm_;
      final private Boolean showTrack_;
      final private Boolean showSummary_;
      final private Boolean showOverlay_;
      final private Boolean saveFile_;
      final private Boolean p2d_;
      final private Boolean fitSigma_;
      final private Boolean useSigmaEstimate_;
      final private Double sigmaEstimate_;
      final private String filePath_;
   
   
   public static class Builder {
      private int[] rows_;
      private Double maxDistanceNm_; //maximum distance in nm for two spots in different
                                    // channels to be considered a pair
      private Boolean showTrack_;
      private Boolean showSummary_;
      private Boolean showOverlay_;
      private Boolean saveFile_;
      private Boolean p2d_;
      private Boolean fitSigma_;
      private Boolean useSigmaEstimate_;
      private Double sigmaEstimate_;
      private String filePath_;
      
      public ParticlePairLister build() {
         return new ParticlePairLister(this);
      }
      
      public Builder rows(int[] rows) {
         rows_ = rows;
         return this;
      }
      
      public Builder maxDistanceNm(Double maxDistanceNm) {
         maxDistanceNm_ = maxDistanceNm;
         return this;
      }
      
      public Builder showTrack(Boolean showTrack) {
         showTrack_ = showTrack;
         return this;
      }
      
      public Builder showSummary(Boolean showSummary) {
         showSummary_ = showSummary;
         return this;
      }
      
      public Builder showOverlay(Boolean showOverlay) {
         showOverlay_ = showOverlay;
         return this;
      }
      
      public Builder saveFile(Boolean saveFile) {
         saveFile_ = saveFile;
         return this;
      }
      
      public Builder p2d(Boolean p2d) {
         p2d_ = p2d;
         return this;
      }
      
      public Builder fitSigma(Boolean fixSigma) {
         fitSigma_ = fixSigma;
         return this;
      }
              
      public Builder useSigmaEstimate(Boolean useSigmaEstimate) {
         useSigmaEstimate_ = useSigmaEstimate;
         return this;
      }        
      
      public Builder sigmaEstimate(Double sigmaEstimate) {
         sigmaEstimate_ = sigmaEstimate;
         return this;
      }
      
      public Builder filePath(String filePath) {
         filePath_ = filePath;
         return this;
      }
      
   }
   
   public ParticlePairLister(Builder builder) {
      rows_ = builder.rows_;
      maxDistanceNm_ = builder.maxDistanceNm_;
      showTrack_ = builder.showTrack_;
      showSummary_ = builder.showSummary_;
      showOverlay_ = builder.showOverlay_;
      saveFile_ = builder.saveFile_;
      p2d_ = builder.p2d_;
      fitSigma_ = builder.fitSigma_;
      useSigmaEstimate_ = builder.useSigmaEstimate_;
      sigmaEstimate_ = builder.sigmaEstimate_;
      filePath_ = builder.filePath_;
   }
   
   public Builder copy() {
      return new Builder().
              rows(rows_).
              maxDistanceNm(maxDistanceNm_).
              showTrack(showTrack_).
              showSummary(showSummary_).
              showOverlay(showOverlay_).
              saveFile(saveFile_).
              p2d(p2d_).
              fitSigma(fitSigma_).
              useSigmaEstimate(useSigmaEstimate_).              
              sigmaEstimate(sigmaEstimate_).
              filePath(filePath_);             
   }
   
   /**
    * Cycles through the spots of the selected data set and finds the most
    * nearby spot in channel 2. It will list this as a pair if the two spots are
    * within MAXMATCHDISTANCE nm of each other.
    *
    * Once all pairs are found, it will go through all frames and try to build
    * up tracks. If the spot is within MAXMATCHDISTANCE between frames, the code
    * will consider the particle to be identical.
    *
    * All "tracks" of particles will be listed
    *
    * In addition, it will list the average distance, and average distance in x
    * and y for each frame.
    *
    * spots in channel 2 that are within MAXMATCHDISTANCE of
    *
    * Needed input variables are set through a builder 
    * 
   */
   public void listParticlePairTracks()
   {

      Runnable doWorkRunnable = new Runnable() {

         @Override
         public void run() {

            final DataCollectionForm dc = DataCollectionForm.getInstance();
            
            // Show Particle List as linked Results Table
            ResultsTable rt = new ResultsTable();
            rt.reset();
            rt.setPrecision(2);

            // Show Particle Summary as Linked Results Table
            ResultsTable rt2 = new ResultsTable();
            rt2.reset();
            rt2.setPrecision(1);

            for (int row : rows_) {
               ArrayList<ArrayList<GsSpotPair>> spotPairsByFrame
                       = new ArrayList<ArrayList<GsSpotPair>>();

               ij.IJ.showStatus("Creating Pairs...");

               // First go through all frames to find all pairs
               int nrSpotPairsInFrame1 = 0;
               for (int frame = 1; frame <= dc.getSpotData(row).nrFrames_; frame++) {
                  ij.IJ.showProgress(frame, dc.getSpotData(row).nrFrames_);
                  spotPairsByFrame.add(new ArrayList<GsSpotPair>());

                  // Get points from both channels in first frame as ArrayLists        
                  ArrayList<SpotData> gsCh1 = new ArrayList<SpotData>();
                  ArrayList<Point2D.Double> xyPointsCh2 = new ArrayList<Point2D.Double>();
                  for (SpotData gs : dc.getSpotData(row).spotList_) {
                     if (gs.getFrame() == frame) {
                        if (gs.getChannel() == 1) {
                           gsCh1.add(gs);
                        } else if (gs.getChannel() == 2) {
                           Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                           xyPointsCh2.add(point);
                        }
                     }
                  }

                  if (xyPointsCh2.isEmpty()) {
                     ReportingUtils.logError(
                             "Pairs function in Localization plugin: no points found in second channel in frame "
                             + frame);
                     continue;
                  }

                  // Find matching points in the two ArrayLists
                  Iterator it2 = gsCh1.iterator();
                  NearestPoint2D np = new NearestPoint2D(xyPointsCh2, maxDistanceNm_);
                  while (it2.hasNext()) {
                     SpotData gs = (SpotData) it2.next();
                     Point2D.Double pCh1 = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                     Point2D.Double pCh2 = np.findKDWSE(pCh1);
                     if (pCh2 != null) {
                        GsSpotPair pair = new GsSpotPair(gs, pCh1, pCh2);
                        //spotPairs.add(pair);
                        spotPairsByFrame.get(frame - 1).add(pair);
                     }
                  }
               }

               // We have all pairs, assemble in tracks
               ij.IJ.showStatus("Assembling tracks...");

               // prepare NearestPoint objects to speed up finding closest pair 
               ArrayList<NearestPointGsSpotPair> npsp = new ArrayList<NearestPointGsSpotPair>();
               for (int frame = 1; frame <= dc.getSpotData(row).nrFrames_; frame++) {
                  npsp.add(new NearestPointGsSpotPair(
                          spotPairsByFrame.get(frame - 1), maxDistanceNm_));
               }

               ArrayList<ArrayList<GsSpotPair>> tracks = new ArrayList<ArrayList<GsSpotPair>>();

               Iterator<GsSpotPair> iSpotPairs = spotPairsByFrame.get(0).iterator();
               int i = 0;
               while (iSpotPairs.hasNext()) {
                  ij.IJ.showProgress(i++, nrSpotPairsInFrame1);
                  GsSpotPair spotPair = iSpotPairs.next();
                  // for now, we only start tracks at frame number 1
                  if (spotPair.getGSD().getFrame() == 1) {
                     ArrayList<GsSpotPair> track = new ArrayList<GsSpotPair>();
                     track.add(spotPair);
                     int frame = 2;
                     while (frame <= dc.getSpotData(row).nrFrames_) {

                        GsSpotPair newSpotPair = npsp.get(frame - 1).findKDWSE(
                                new Point2D.Double(spotPair.getfp().getX(), spotPair.getfp().getY()));
                        if (newSpotPair != null) {
                           spotPair = newSpotPair;
                           track.add(spotPair);
                        }
                        frame++;
                     }
                     tracks.add(track);
                  }
               }

               if (tracks.isEmpty()) {
                  MessageDialog md = new MessageDialog(DataCollectionForm.getInstance(),
                          "No Pairs found", "No Pairs found");
                  continue;
               }

               Iterator<ArrayList<GsSpotPair>> itTracks = tracks.iterator();
               int spotId = 0;
               while (itTracks.hasNext()) {
                  ArrayList<GsSpotPair> track = itTracks.next();
                  Iterator<GsSpotPair> itTrack = track.iterator();
                  while (itTrack.hasNext()) {
                     GsSpotPair spot = itTrack.next();
                     rt.incrementCounter();
                     rt.addValue("Spot ID", spotId);
                     rt.addValue(Terms.FRAME, spot.getGSD().getFrame());
                     rt.addValue(Terms.SLICE, spot.getGSD().getSlice());
                     rt.addValue(Terms.CHANNEL, spot.getGSD().getChannel());
                     rt.addValue(Terms.POSITION, spot.getGSD().getPosition());
                     rt.addValue(Terms.XPIX, spot.getGSD().getX());
                     rt.addValue(Terms.YPIX, spot.getGSD().getY());
                     double distance = Math.sqrt(
                             NearestPoint2D.distance2(spot.getfp(), spot.getsp()));
                     rt.addValue("Distance", distance);
                     if (spot.getGSD().hasKey("stdDev")) {
                        double stdDev = spot.getGSD().getValue("stdDev");
                        rt.addValue("stdDev1", stdDev);
                        SpotData spot2 = dc.getSpotData(row).get(
                                spot.getGSD().getFrame(),
                                2, spot.getsp().x, spot.getsp().y);
                        if (spot2 != null && spot2.hasKey("stdDev")) {
                           double stdDev2 = spot2.getValue("stdDev");
                           rt.addValue("stdDev2", stdDev2);
                           double distanceStdDev = CalcUtils.stdDev(
                                   spot.getfp().x, spot.getsp().x,
                                   spot.getfp().y, spot.getsp().y,
                                   spot.getGSD().getValue("stdDevX"),
                                   spot2.getValue("stdDevX"),
                                   spot.getGSD().getValue("stdDevY"),
                                   spot2.getValue("stdDevY"));
                           rt.addValue("stdDev-distance", distanceStdDev);
                        }
                     }
                     rt.addValue("Orientation (sine)",
                             NearestPoint2D.orientation(spot.getfp(), spot.getsp()));
                  }
                  spotId++;
               }
               
               TextPanel tp;
               TextWindow win;
               String rtName = dc.getSpotData(row).name_ + " Particle List";
               if (showTrack_) {
                  rt.show(rtName);
                  ImagePlus siPlus = ij.WindowManager.getImage(dc.getSpotData(row).title_);
                  Frame frame = WindowManager.getFrame(rtName);
                  if (frame != null && frame instanceof TextWindow && siPlus != null) {
                     win = (TextWindow) frame;
                     tp = win.getTextPanel();

                     // TODO: the following does not work, there is some voodoo going on here
                     for (MouseListener ms : tp.getMouseListeners()) {
                        tp.removeMouseListener(ms);
                     }
                     for (KeyListener ks : tp.getKeyListeners()) {
                        tp.removeKeyListener(ks);
                     }

                     ResultsTableListener myk = new ResultsTableListener(
                             MMStudio.getInstance(), dc.getSpotData(row).dw_, siPlus,
                             rt, win, dc.getSpotData(row).halfSize_);
                     tp.addKeyListener(myk);
                     tp.addMouseListener(myk);
                     frame.toFront();
                  }
               }
 
               if (saveFile_) {
                  try {
                     String fileName = filePath_ + File.separator
                             + dc.getSpotData(row).name_ + "_PairTracks.cvs";
                     rt.saveAs(fileName);
                     ij.IJ.log("Saved file: " + fileName);
                  } catch (IOException ex) {
                     ReportingUtils.showError(ex, "Failed to save file");
                  }
               }

               ImagePlus siPlus = ij.WindowManager.getImage(dc.getSpotData(row).title_);
               if (showOverlay_) {
                  if (siPlus != null && siPlus.getOverlay() != null) {
                     siPlus.getOverlay().clear();
                  }
                  Arrow.setDefaultWidth(0.5);
               }

               itTracks = tracks.iterator();
               spotId = 0;
               List<Double> avgDistances = new ArrayList<Double>(tracks.size());
               List<Double> stdDevs = new ArrayList<Double>(tracks.size());
               while (itTracks.hasNext()) {
                  ArrayList<GsSpotPair> track = itTracks.next();
                  ArrayList<Double> distances = new ArrayList<Double>();
                  ArrayList<Double> orientations = new ArrayList<Double>();
                  ArrayList<Double> xDiff = new ArrayList<Double>();
                  ArrayList<Double> yDiff = new ArrayList<Double>();
                  for (GsSpotPair pair : track) {
                     distances.add(Math.sqrt(
                             NearestPoint2D.distance2(pair.getfp(), pair.getsp())));
                     orientations.add(NearestPoint2D.orientation(pair.getfp(),
                             pair.getsp()));
                     xDiff.add(pair.getfp().getX() - pair.getsp().getX());
                     yDiff.add(pair.getfp().getY() - pair.getsp().getY());
                  }
                  GsSpotPair pair = track.get(0);
                  rt2.incrementCounter();
                  rt2.addValue("Row ID", dc.getSpotData(row).ID_);
                  rt2.addValue("Spot ID", spotId);
                  rt2.addValue(Terms.FRAME, pair.getGSD().getFrame());
                  rt2.addValue(Terms.SLICE, pair.getGSD().getSlice());
                  rt2.addValue(Terms.CHANNEL, pair.getGSD().getSlice());
                  rt2.addValue(Terms.POSITION, pair.getGSD().getPosition());
                  rt2.addValue(Terms.XPIX, pair.getGSD().getX());
                  rt2.addValue(Terms.YPIX, pair.getGSD().getY());
                  rt2.addValue("n", track.size());

                  double avg = ListUtils.listAvg(distances);
                  avgDistances.add(avg);
                  rt2.addValue("Distance-Avg", avg);
                  double std = ListUtils.listStdDev(distances, avg);
                  stdDevs.add(std);
                  rt2.addValue("Distance-StdDev", std);
                  double oAvg = ListUtils.listAvg(orientations);
                  rt2.addValue("Orientation-Avg", oAvg);
                  rt2.addValue("Orientation-StdDev",
                          ListUtils.listStdDev(orientations, oAvg));

                  double xDiffAvg = ListUtils.listAvg(xDiff);
                  double yDiffAvg = ListUtils.listAvg(yDiff);
                  double xDiffAvgStdDev = ListUtils.listStdDev(xDiff, xDiffAvg);
                  double yDiffAvgStdDev = ListUtils.listStdDev(yDiff, yDiffAvg);
                  rt2.addValue("Dist.Vect.Avg", Math.sqrt(
                          (xDiffAvg * xDiffAvg) + (yDiffAvg * yDiffAvg)));
                  rt2.addValue("Dist.Vect.StdDev", Math.sqrt(
                          (xDiffAvgStdDev * xDiffAvgStdDev)
                          + (yDiffAvgStdDev * yDiffAvgStdDev)));
                  
                  if (showOverlay_) {
                     /* draw arrows in overlay */
                     double mag = 100.0;  // factor that sets magnification of the arrow
                     double factor = mag * 1 / dc.getSpotData(row).pixelSizeNm_;  // factor relating mad and pixelSize
                     int xStart = track.get(0).getGSD().getX();
                     int yStart = track.get(0).getGSD().getY();
                     
                     Arrow arrow = new Arrow(xStart, yStart,
                             xStart + (factor * xDiffAvg),
                             yStart + (factor * yDiffAvg));
                     arrow.setHeadSize(3);
                     arrow.setOutline(false);
                     if (siPlus != null && siPlus.getOverlay() == null) {
                        siPlus.setOverlay(arrow, Color.yellow, 1, Color.yellow);
                     } else if (siPlus != null && siPlus.getOverlay() != null) {
                        siPlus.getOverlay().add(arrow);
                     }
                  }
                  
                  spotId++;
               }
               if (showOverlay_) {
                  if (siPlus != null) {
                     siPlus.setHideOverlay(false);
                  }
               }
               
               if (showSummary_) {
                  rtName = dc.getSpotData(row).name_ + " Particle Summary";
                  rt2.show(rtName);
                  siPlus = ij.WindowManager.getImage(dc.getSpotData(row).title_);
                  Frame frame = WindowManager.getFrame(rtName);
                  if (frame != null && frame instanceof TextWindow && siPlus != null) {
                     win = (TextWindow) frame;
                     tp = win.getTextPanel();

                     // TODO: the following does not work, there is some voodoo going on here
                     for (MouseListener ms : tp.getMouseListeners()) {
                        tp.removeMouseListener(ms);
                     }
                     for (KeyListener ks : tp.getKeyListeners()) {
                        tp.removeKeyListener(ks);
                     }
                     
                     ResultsTableListener myk = new ResultsTableListener(
                             MMStudio.getInstance(), dc.getSpotData(row).dw_, siPlus,
                             rt2, win, dc.getSpotData(row).halfSize_);
                     tp.addKeyListener(myk);
                     tp.addMouseListener(myk);
                     frame.toFront();
                  }
               }
               
               if (p2d_) {
                  double[] d = new double[avgDistances.size()];
                  for (int j = 0; j < avgDistances.size(); j++) {
                     d[j] = avgDistances.get(j);
                  }
                  P2DFitter p2df = new P2DFitter(d, fitSigma_);
                  double distMean = ListUtils.listAvg(avgDistances);
                  double distStd = sigmaEstimate_;
                  if (fitSigma_ || !useSigmaEstimate_) {
                     // how do we best estimate sigma? If we have multiple
                     // measurements per paricle, it seems best to calculate it 
                     // directly from the spread in those measurements
                     // if we have only one particle per track, we need to
                     // calculate it from the sigmas of the two spots in the particle
                     // But where is the cutoff between these two methods?
                     distStd = ListUtils.listStdDev(avgDistances, distMean);
                  }
                  p2df.setStartParams(distMean, distStd);
                  try {
                     double[] p2dfResult = p2df.solve();
                     if (p2dfResult.length == 2) {
                        ij.IJ.log("p2d fit: n = " + avgDistances.size() + ", mu = "
                                + NumberUtils.doubleToDisplayString(p2dfResult[0])
                                + " nm, sigma = "
                                + NumberUtils.doubleToDisplayString(p2dfResult[1])
                                + " nm");
                     } else if (p2dfResult.length == 1 && !fitSigma_) {
                        ij.IJ.log("p2d fit: n = " + avgDistances.size() + ", mu = "
                                + NumberUtils.doubleToDisplayString(p2dfResult[0])
                                + " nm, sigma = "
                                + distStd
                                + " nm");
                     } else {
                        ij.IJ.log("Error during p2d fit");
                     }                     
                     ij.IJ.log("Gaussian distribution: n = " + avgDistances.size()
                             + ", avg = "
                             + NumberUtils.doubleToDisplayString(distMean)
                             + " nm, std = "
                             + NumberUtils.doubleToDisplayString(distStd) + " nm");

                     // plot function and histogram
                     double[] muSigma = {p2dfResult[0], distStd};
                     if (fitSigma_) {
                        muSigma = p2dfResult;
                     }
                     GaussianUtils.plotP2D(dc.getSpotData(row).title_ + " distances",
                             d, maxDistanceNm_, muSigma);
                     
                  } catch (FittingException fe) {
                     ReportingUtils.showError(fe.getMessage());
                  }
               }

               ij.IJ.showStatus("");

            }
         }
      };
      
      if (showTrack_ || showSummary_ || showOverlay_ || saveFile_ || p2d_) {
         (new Thread(doWorkRunnable)).start();
      }

   }

   /**
    * Cycles through the spots of the selected data set and finds the most
    * nearby spot in channel 2. It will list this as a pair if the two spots are
    * within MAXMATCHDISTANCE nm of each other. In addition, it will list the
    * average distance, and average distance in x and y for each frame.
    *
    * spots in channel 2 that are within MAXMATCHDISTANCE of
    *
    * @param row
    * @param maxDistance
    * @param showPairs
    * @param showImage
    * @param savePairs
    * @param filePath
    * @param showSummary
    * @param showGraph
    */
   public static void ListParticlePairs(final int row, final double maxDistance,
           final boolean showPairs, final boolean showImage,
           final boolean savePairs, final String filePath,
           final boolean showSummary, final boolean showGraph) {

      Runnable doWorkRunnable = new Runnable() {

         @Override
         public void run() {
            
            RowData spotData = DataCollectionForm.getInstance().getSpotData(row);
            
            ResultsTable rt = new ResultsTable();
            rt.reset();
            rt.setPrecision(2);

            ResultsTable rt2 = new ResultsTable();
            rt2.reset();
            rt2.setPrecision(2);

            int width = spotData.width_;
            int height = spotData.height_;
            double factor = spotData.pixelSizeNm_;
            boolean useS = spotData.useSeconds();
            ij.ImageStack stack = new ij.ImageStack(width, height);

            XYSeries xData = new XYSeries("XError");
            XYSeries yData = new XYSeries("YError");

            ij.IJ.showStatus("Creating Pairs...");

            for (int frame = 1; frame <= spotData.nrFrames_; frame++) {
               ij.IJ.showProgress(frame, spotData.nrFrames_);
               ImageProcessor ip = new ShortProcessor(width, height);
               short pixels[] = new short[width * height];
               ip.setPixels(pixels);
               stack.addSlice("frame: " + frame, ip);

               // Get points from both channels in each frame as ArrayLists        
               ArrayList<SpotData> gsCh1 = new ArrayList<SpotData>();
               ArrayList<Point2D.Double> xyPointsCh2 = new ArrayList<Point2D.Double>();
               for (SpotData gs : spotData.spotList_) {
                  if (gs.getFrame() == frame) {
                     if (gs.getChannel() == 1) {
                        gsCh1.add(gs);
                     } else if (gs.getChannel() == 2) {
                        Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                        xyPointsCh2.add(point);
                     }
                  }
               }

               if (xyPointsCh2.isEmpty()) {
                  ReportingUtils.logError("Pairs function in Localization plugin: no points found in second channel in frame " + frame);
                  continue;
               }

               // Find matching points in the two ArrayLists
               Iterator it2 = gsCh1.iterator();
               NearestPoint2D np = new NearestPoint2D(xyPointsCh2,
                       maxDistance);
               ArrayList<Double> distances = new ArrayList<Double>();
               ArrayList<Double> errorX = new ArrayList<Double>();
               ArrayList<Double> errorY = new ArrayList<Double>();
               while (it2.hasNext()) {
                  SpotData gs = (SpotData) it2.next();
                  Point2D.Double pCh1 = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                  Point2D.Double pCh2 = np.findKDWSE(pCh1);
                  if (pCh2 != null) {
                     rt.incrementCounter();
                     rt.addValue(Terms.FRAME, gs.getFrame());
                     rt.addValue(Terms.SLICE, gs.getSlice());
                     rt.addValue(Terms.CHANNEL, gs.getSlice());
                     rt.addValue(Terms.POSITION, gs.getPosition());
                     rt.addValue(Terms.XPIX, gs.getX());
                     rt.addValue(Terms.YPIX, gs.getY());
                     rt.addValue("X1", pCh1.getX());
                     rt.addValue("Y1", pCh1.getY());
                     rt.addValue("X2", pCh2.getX());
                     rt.addValue("Y2", pCh2.getY());
                     double d2 = NearestPoint2D.distance2(pCh1, pCh2);
                     double d = Math.sqrt(d2);
                     rt.addValue("Distance", d);
                     rt.addValue("Orientation (sine)",
                             NearestPoint2D.orientation(pCh1, pCh2));
                     distances.add(d);

                     ip.putPixel((int) (pCh1.x / factor), (int) (pCh1.y / factor), (int) d);

                     double ex = pCh2.getX() - pCh1.getX();
                     //double ex = (pCh1.getX() - pCh2.getX()) * (pCh1.getX() - pCh2.getX());
                     //ex = Math.sqrt(ex);
                     errorX.add(ex);
                     //double ey = (pCh1.getY() - pCh2.getY()) * (pCh1.getY() - pCh2.getY());
                     //ey = Math.sqrt(ey);
                     double ey = pCh2.getY() - pCh1.getY();
                     errorY.add(ey);
                  }
               }
               Double avg = ListUtils.listAvg(distances);
               Double stdDev = ListUtils.listStdDev(distances, avg);
               Double avgX = ListUtils.listAvg(errorX);
               Double stdDevX = ListUtils.listStdDev(errorX, avgX);
               Double avgY = ListUtils.listAvg(errorY);
               Double stdDevY = ListUtils.listStdDev(errorY, avgY);
               rt2.incrementCounter();
               rt2.addValue("Frame Nr.", frame);
               rt2.addValue("Avg. distance", avg);
               rt2.addValue("StdDev distance", stdDev);
               rt2.addValue("X", avgX);
               rt2.addValue("StdDev X", stdDevX);
               rt2.addValue("Y", avgY);
               rt2.addValue("StdDevY", stdDevY);
               double timePoint = frame;
               if (spotData.timePoints_ != null) {
                  timePoint = spotData.timePoints_.get(frame);
                  if (useS) {
                     timePoint /= 1000;
                  }
               }
               xData.add(timePoint, avgX);
               yData.add(timePoint, avgY);
            }

            if (rt.getCounter() == 0) {
               MessageDialog md = new MessageDialog(DataCollectionForm.getInstance(),
                       "No Pairs found", "No Pairs found");
               return;
            }

            if (showSummary) {
               // show summary in resultstable
               rt2.show("Summary of Pairs found in " + spotData.name_);
            }

            if (showGraph) {
               String xAxis = "Time (frameNr)";
               if (spotData.timePoints_ != null) {
                  xAxis = "Time (ms)";
                  if (useS) {
                     xAxis = "Time (s)";
                  }
               }
               GaussianUtils.plotData2("Error in " + spotData.name_,
                       xData, yData, xAxis, "Error(nm)", 0, 400);

               ij.IJ.showStatus("");
            }

            if (showPairs) {
               //  show Pairs panel and attach listener
               TextPanel tp;
               TextWindow win;

               String rtName = "Pairs found in " + spotData.name_;
               rt.show(rtName);
               ImagePlus siPlus = ij.WindowManager.getImage(spotData.title_);
               Frame frame = WindowManager.getFrame(rtName);
               if (frame != null && frame instanceof TextWindow && siPlus != null) {
                  win = (TextWindow) frame;
                  tp = win.getTextPanel();

                  // TODO: the following does not work, there is some voodoo going on here
                  for (MouseListener ms : tp.getMouseListeners()) {
                     tp.removeMouseListener(ms);
                  }
                  for (KeyListener ks : tp.getKeyListeners()) {
                     tp.removeKeyListener(ks);
                  }

                  ResultsTableListener myk = new ResultsTableListener(
                          MMStudio.getInstance(), spotData.dw_, siPlus,
                          rt, win, spotData.halfSize_);
                  tp.addKeyListener(myk);
                  tp.addMouseListener(myk);
                  frame.toFront();
               }
            }

            if (showImage) {
               ImagePlus sp = new ImagePlus("Errors in pairs");
               sp.setOpenAsHyperStack(true);
               sp.setStack(stack, 1, 1, stack.getSize());
               sp.setDisplayRange(0, 20);
               sp.setTitle(spotData.title_);

               ImageWindow w = new StackWindow(sp);
               w.setTitle("Error in " + spotData.name_);

               w.setImage(sp);
               w.setVisible(true);
            }

            if (savePairs) {
               try {
                  String fileName = filePath + File.separator
                          + spotData.name_ + "_Pairs.cvs";
                  rt.saveAs(fileName);
                  ij.IJ.log("Saved file: " + fileName);
               } catch (IOException ex) {
                  ReportingUtils.showError(ex, "Failed to save file");
               }
            }

         }
      };

      (new Thread(doWorkRunnable)).start();

   }
}
