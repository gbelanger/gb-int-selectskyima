import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import cern.colt.list.DoubleArrayList;
import hep.aida.IAnalysisFactory;
import hep.aida.IFitFactory;
import hep.aida.IFitResult;
import hep.aida.IFitter;
import hep.aida.ref.histogram.FixedAxis;
import hep.aida.ref.histogram.Histogram1D;
import nom.tam.fits.Fits;
import nom.tam.fits.FitsException;
import nom.tam.fits.ImageHDU;
import nom.tam.util.ArrayFuncs;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;


public class SelectSkyIma {

/**   
 *     This class selects images based on the width of the distribution of significance or intensity values

September 2015
- Complete re-write and streamlining of both SelectSkyIma.java and SelectSkyImaUtils.java

July 2011 (never finished)
- Complete re-write based on re-written version of OG_merge-2.0

**/

    private static String version = "2.0";
    private static String sep = File.separator;
    private static DecimalFormat number = new DecimalFormat("0.000");
    private static DecimalFormat percent = new DecimalFormat("0.0");
    //  Defined in handleArguments()
    private static String scwLisName = null; 
    private static String[] dataDirNames = null;
    private static String selectionType = null;
    private static int band = 1;
    private static double cut = 1.2;
    private static String[] scwNums;

    // main
    public static void main(String[] args) throws IOException, NullPointerException, FitsException, Exception {
	configureLogger();
	handleArguments(args);
	ScwLis scwLis = new ScwLis(scwLisName);
	scwNums = scwLis.getScwIDs();
	ArrayList[] results = SelectSkyImaUtils.getImageFileArrayListFromScwNums(scwNums, dataDirNames);
 	ArrayList imagesFileArrayList = results[0];
	if ( imagesFileArrayList.size() == 0 ) {
	    logger.warn("No images found");
	}
	else {
	    makeSelection(new ArrayList[]{results[0], results[3]}, cut);
	    logger.info("Task SelectSkyIma completed");
	}
	loggerFile.delete();
    } 

    // makeSelection (according to standard deviations of distribution of pixel values)
    private static void makeSelection(ArrayList[] imageFilesAndScwNums, double cut)  throws Exception {
 	ArrayList imagesFileArrayList = imageFilesAndScwNums[0];
	ArrayList updatedScwNumsArrayList = imageFilesAndScwNums[1];
	String[] scwNums_selected;
	String[] scwNums_rejected;
	double[] standardDeviations;
	logger.info("Calculating standard deviation of distribution of "+selectionType+" values in each image ... ");
	String skyImaName = null;
	int rateExt = 2 + 4*(band-1);
	int varExt = 3 + 4*(band-1);
	int signifExt = 4 + 4*(band-1);
	int exten = 0;
	if ( selectionType.equals("signif") ) {
	    exten = signifExt;
	}
	else {
	    exten = rateExt;
	}
	DoubleArrayList allSigmas = new DoubleArrayList();
	ArrayList scwNumsArrayList_selected = new ArrayList();
	ArrayList scwNumsArrayList_rejected = new ArrayList();
	int nBad=0, nGood=0, nTot=0;
	for ( int i=0; i < imagesFileArrayList.size(); i++ ) {
	    File imageFile = (File)imagesFileArrayList.get(i);
	    String scwNum = (String)updatedScwNumsArrayList.get(i);
	    double sigma = getStdDevOfPixelValuesDistribution(imageFile, exten);
	    if ( !Double.isNaN(sigma) ) {
		nTot++;
		allSigmas.add(sigma);
		if ( sigma <= cut ) {
		    scwNumsArrayList_selected.add(scwNum);
		    nGood++;
		    //logger.info("  Image "+(i+1)+" "+scwNum+":  sigma="+number.format(sigma));
		}
		else {
		    scwNumsArrayList_rejected.add(scwNum);
		    nBad++;
		    logger.info("  Image "+(i+1)+" "+scwNum+":  sigma="+number.format(sigma)+" (>"+cut+" rejected)");
		}
	    }
	}
	scwNumsArrayList_selected.trimToSize();
	scwNumsArrayList_rejected.trimToSize();
	allSigmas.trimToSize();
	double[] sig = allSigmas.elements();
	double meanOfSigmas = SelectSkyImaUtils.getMean(sig);
	double stdDev = Math.sqrt(SelectSkyImaUtils.getVariance(sig));
	logger.info("Mean sigma = "+ number.format(meanOfSigmas));
	logger.info("Standard deviation of sigma values = "+ number.format(stdDev));
	logger.info(nGood+" (of "+nTot+" non-NaN) images were selected ("+percent.format(100.*(double)nGood/(double)nTot)+" %)");
	logger.info(nBad+" (of "+nTot+" non-NaN) images were rejected ("+percent.format(100.*(double)nBad/(double)nTot)+" %)");
	// Write scw lists
	String[] goodScwNums = new String[scwNumsArrayList_selected.size()];
	String[] badScwNums = new String[scwNumsArrayList_rejected.size()];
	for ( int i=0; i < goodScwNums.length; i++ ) {
	    goodScwNums[i] = (String)scwNumsArrayList_selected.get(i);
	}
	for ( int i=0; i < badScwNums.length; i++ ) {
	    badScwNums[i] = (String)scwNumsArrayList_rejected.get(i);
	}
	ScwLis goodLis = new ScwLis();
	goodLis.setScwIDs(goodScwNums);
	goodLis.write(selectedScwLisName);
	ScwLis badLis = new ScwLis();
	badLis.setScwIDs(badScwNums);
	badLis.write(rejectedScwLisName);
    }

    private static double getStdDevOfPixelValuesDistribution(File imageFile, int ext) throws Exception {
	Fits fitsImage  = SelectSkyImaUtils.openFits(imageFile);
	ImageHDU hdu = (ImageHDU) fitsImage.getHDU(ext);
	float[][] pixelValues_2d = (float[][]) hdu.getKernel();
	float[] pixelValues = (float[]) ArrayFuncs.flatten(pixelValues_2d);
	// Make histo
	double min = -4; 
	double max = 4;
	int nbins = 1000;
	FixedAxis axis = new FixedAxis(nbins, min, max);
	Histogram1D histo = new Histogram1D("", "Pixel Values", axis);
	double minVar = SelectSkyImaUtils.getNonZeroMin(pixelValues);
	double weight = 0;
	for ( int i=0; i < pixelValues.length; i++ )  {
	    if ( Double.isNaN(pixelValues[i]) || (Math.abs(pixelValues[i]) > 10.0) ) {
		weight = 0;
	    }
	    else {
		weight = 1;
	    }
	    histo.fill(pixelValues[i], weight);
	}
	//  Fit histo
	IAnalysisFactory af = IAnalysisFactory.create();
	IFitFactory fitf = af.createFitFactory();
	IFitter fitter = fitf.createFitter("chi2");
	IFitResult fitResult = fitter.fit(histo, "g");
	double mean = fitResult.fittedParameter("mean");
	double sigma = fitResult.fittedParameter("sigma");
	//  Show plot 
	// String[] paramNames = fitResult.fittedParameterNames();
	// double[] paramValues = fitResult.fittedParameters();
	// System.out.println("Fit status = "+fitResult.fitStatus());
	// for ( int i=0; i < paramNames.length; i++ ) 
	//     System.out.println(paramNames[i]+" = "+paramValues[i]);
	// System.out.println("Quality = "+fitResult.quality());
	// IPlotterFactory plotf = af.createPlotterFactory();
	// IPlotter plotter = plotf.create("Plot");
	// plotter.region(0).plot(histo);
	// plotter.region(0).plot(fitResult.fittedFunction());
	// plotter.show();
	return sigma;
    }

    //  Logger
    public static Logger logger  = Logger.getLogger(SelectSkyIma.class);
    private static File loggerFile;
    private static void configureLogger() throws IOException {
	String loggerFilename= "logger.config";
	InputStream log = ClassLoader.getSystemResourceAsStream(loggerFilename);
	UUID uuid = UUID.randomUUID();
	String homeDir = System.getProperty("user.home");
	loggerFilename = new String(homeDir+File.pathSeparator+"logger.config_"+uuid.toString());
	loggerFile = new File(loggerFilename);
	loggerFile.deleteOnExit();
	inputStreamToFile(log, loggerFilename);
        PropertyConfigurator.configure(loggerFilename);
    }
    private static void inputStreamToFile(InputStream io, String fileName) throws IOException {       
	FileOutputStream fos = new FileOutputStream(fileName);
	byte[] buf = new byte[256];
	int read = 0;
	while ((read = io.read(buf)) > 0) {
	    fos.write(buf, 0, read);
	}
	fos.flush();
	fos.close();
    }

    //  Arguments
    private static String selectedScwLisName = null;
    private static String rejectedScwLisName = null;
    private static void handleArguments(String[] args) throws IOException {
	if ( args.length < 5 ) {
	    logger.error("Usage: java -jar SelectSkyIma-"+version+".jar input.lis selectType ([signif]|flux) band [1] stdDevCut [1.2] path/to/data ... path/to/dataN");
	    System.exit(-1);
	}
	else {
	    scwLisName  = args[0];
	    selectedScwLisName = scwLisName+".selected";
	    rejectedScwLisName = scwLisName+".rejected";
	    selectionType = args[1];
	    if ( ! selectionType.equals("signif") && ! selectionType.equals("flux") ) {
		logger.error("Selection type must be 'signif' or 'flux'");
		System.exit(-1);
	    }
	    band = (Integer.valueOf(args[2])).intValue();
	    cut = (Double.valueOf(args[3])).doubleValue();
	    dataDirNames = new String[args.length-4];
	    for ( int i=0; i < args.length-4; i++ ) {
		dataDirNames[i] = (new File(args[4+i])).getCanonicalPath();		
	    }
	    Arrays.sort(dataDirNames);
	    logger.info("Running SelectSkyIma "+version+" on "+selectionType+" images in band "+band+" with cut at "+cut);
	}
    }



}
